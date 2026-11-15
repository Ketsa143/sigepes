package com.sigepes.backend.rms;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigepes.backend.audit.AuditActionType;
import com.sigepes.backend.audit.AuditService;
import com.sigepes.backend.common.ApiException;
import com.sigepes.backend.common.ErrorCode;
import com.sigepes.backend.common.Ids;
import com.sigepes.backend.quotas.QuotaDtos.CreateQuotaRequest;
import com.sigepes.backend.quotas.QuotaEntity;
import com.sigepes.backend.quotas.QuotaService;
import com.sigepes.backend.rms.RmsDtos.CreateProposalRequest;
import com.sigepes.backend.rms.RmsDtos.CurvePoint;
import com.sigepes.backend.rms.RmsDtos.DecisionRequest;
import com.sigepes.backend.rms.RmsDtos.EstimateResponse;
import com.sigepes.backend.rms.RmsDtos.ProposalResponse;
import com.sigepes.backend.rms.RmsDtos.RunResponse;
import com.sigepes.backend.rms.RmsDtos.SeriesPoint;
import com.sigepes.backend.security.AuthenticatedUser;
import com.sigepes.backend.security.CurrentUser;
import com.sigepes.backend.species.SpeciesEntity;
import com.sigepes.backend.species.SpeciesRepository;
import com.sigepes.backend.users.UserEntity;
import com.sigepes.backend.users.UserRepository;

/**
 * HU-17 · RF-17 · Rendimiento Máximo Sostenible.
 *
 * <p>Las consultas solo leen resultados precalculados ({@code rms_estimates}); el ajuste del modelo
 * corre en {@link RmsJobRunner}, en un hilo propio, después de confirmar la transacción que lo pide
 * (AC-17.5). Las propuestas de cuota no pueden superar el RMS y, al aprobarlas el ADMIN, crean la
 * cuota de HU-07.
 */
@Service
public class RmsService {

    static final int CURVE_POINTS = 40;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RmsRunRepository runRepository;
    private final RmsEstimateRepository estimateRepository;
    private final RmsQuotaProposalRepository proposalRepository;
    private final SpeciesRepository speciesRepository;
    private final UserRepository userRepository;
    private final QuotaService quotaService;
    private final RmsJobRunner runner;
    private final TaskExecutor executor;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public RmsService(RmsRunRepository runRepository, RmsEstimateRepository estimateRepository,
            RmsQuotaProposalRepository proposalRepository, SpeciesRepository speciesRepository,
            UserRepository userRepository, QuotaService quotaService, RmsJobRunner runner,
            @Qualifier("rmsExecutor") TaskExecutor executor, CurrentUser currentUser, AuditService auditService) {
        this.runRepository = runRepository;
        this.estimateRepository = estimateRepository;
        this.proposalRepository = proposalRepository;
        this.speciesRepository = speciesRepository;
        this.userRepository = userRepository;
        this.quotaService = quotaService;
        this.runner = runner;
        this.executor = executor;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Cálculo en segundo plano (TASK-17.2)
    // ------------------------------------------------------------------

    /** Encola un recálculo de todas las especies; si ya hay uno pendiente, devuelve ese. */
    @Transactional
    public RunResponse requestRun() {
        AuthenticatedUser actor = currentUser.require();
        List<RmsRunEntity> pending = runRepository.findByStatus(RmsRunEntity.Status.PENDING);
        if (!pending.isEmpty()) {
            return RunResponse.from(pending.get(0));
        }
        RmsRunEntity run = runRepository.save(new RmsRunEntity(Ids.generate("rms"), actor.id()));
        auditService.record(AuditActionType.CREATE, "RMS_RUN_QUEUED", AuditService.MODULE_RMS, "Cálculo de RMS",
                run.getId(), "Recálculo del RMS de todas las especies en segundo plano");
        submitAfterCommit(run.getId());
        return RunResponse.from(run);
    }

    @Transactional(readOnly = true)
    public RunResponse run(String id) {
        return runRepository.findById(id).map(RunResponse::from)
                .orElseThrow(() -> ApiException.of(ErrorCode.RMS_RUN_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<RunResponse> latestRun() {
        return runRepository.findTopByOrderByCreatedAtDesc().map(RunResponse::from);
    }

    /** Procesa los cálculos pendientes en el hilo actual (arranque del servidor y pruebas). */
    public int runPending() {
        List<RmsRunEntity> pending = runRepository.findByStatus(RmsRunEntity.Status.PENDING);
        pending.forEach(run -> runner.run(run.getId()));
        return pending.size();
    }

    // ------------------------------------------------------------------
    // Consultas desde la caché (AC-17.2, AC-17.5)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EstimateResponse> estimates() {
        List<RmsEstimateEntity> estimates = estimateRepository.findAll();
        Map<String, SpeciesEntity> species = speciesById(estimates.stream().map(RmsEstimateEntity::getSpeciesId)
                .toList());
        return estimates.stream()
                .map(estimate -> toResponse(estimate, species.get(estimate.getSpeciesId()), false))
                .sorted(Comparator.comparing(EstimateResponse::species, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @Transactional(readOnly = true)
    public EstimateResponse estimate(String speciesId) {
        RmsEstimateEntity estimate = estimateRepository.findById(speciesId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RMS_ESTIMATE_NOT_FOUND));
        return toResponse(estimate, speciesRepository.findById(speciesId).orElse(null), true);
    }

    // ------------------------------------------------------------------
    // Propuesta de cuota (AC-17.3)
    // ------------------------------------------------------------------

    @Transactional
    public ProposalResponse propose(String speciesId, CreateProposalRequest request) {
        AuthenticatedUser actor = currentUser.require();
        RmsEstimateEntity estimate = estimateRepository.findById(speciesId)
                .orElseThrow(() -> ApiException.of(ErrorCode.RMS_ESTIMATE_NOT_FOUND));
        if (!estimate.isFitted() || estimate.getMsyKg() == null) {
            throw ApiException.of(ErrorCode.RMS_NOT_FITTED);
        }
        if (request.proposedKg().compareTo(estimate.getMsyKg()) > 0) {
            throw ApiException.of(ErrorCode.RMS_PROPOSAL_ABOVE_MSY,
                    "La cuota propuesta (" + request.proposedKg() + " kg) supera el RMS de "
                            + estimate.getMsyKg() + " kg.");
        }
        if (request.to().isBefore(request.from())) {
            throw ApiException.of(ErrorCode.INVALID_QUOTA_PERIOD,
                    "La fecha de finalización no puede ser anterior a la de inicio.");
        }
        RmsQuotaProposalEntity proposal = proposalRepository.save(new RmsQuotaProposalEntity(Ids.generate("prc"),
                speciesId, request.proposedKg(), estimate.getMsyKg(), estimate.isIndicative(), request.zone().trim(),
                request.from(), request.to(), blankToNull(request.justification()), actor.id()));
        auditService.record(AuditActionType.CREATE, "RMS_QUOTA_PROPOSED", AuditService.MODULE_RMS,
                "Propuesta de cuota", proposal.getId(), "Propuesta de " + proposal.getProposedKg() + " kg (RMS "
                        + proposal.getMsyKg() + " kg" + (proposal.isIndicative() ? ", indicativo" : "") + ") en "
                        + proposal.getZone());
        return toResponse(proposal);
    }

    @Transactional(readOnly = true)
    public List<ProposalResponse> proposals() {
        return proposalRepository.findTop100ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    /**
     * Solo el ADMIN aprueba. Con un RMS indicativo (menos de 5 años) hace falta una nota
     * justificativa, en la propuesta o en la aprobación. Al aprobar se crea la cuota de HU-07.
     */
    @Transactional
    public ProposalResponse approve(String id, DecisionRequest request) {
        AuthenticatedUser actor = currentUser.require();
        RmsQuotaProposalEntity proposal = pendingProposal(id);
        String note = request == null ? null : blankToNull(request.note());
        if (proposal.isIndicative() && proposal.getJustification() == null && note == null) {
            throw ApiException.of(ErrorCode.RMS_JUSTIFICATION_REQUIRED);
        }
        String notes = "Cuota basada en el RMS (" + proposal.getMsyKg() + " kg"
                + (proposal.isIndicative() ? ", cálculo indicativo" : "") + "). Propuesta " + proposal.getId() + ".";
        QuotaEntity quota = quotaService.create(new CreateQuotaRequest(proposal.getSpeciesId(), proposal.getZone(),
                proposal.getPeriodFrom(), proposal.getPeriodTo(), proposal.getProposedKg(), null,
                "RMS-" + proposal.getId(), notes));
        proposal.approve(actor.id(), note, quota.getId());
        proposalRepository.save(proposal);
        auditService.record(AuditActionType.UPDATE, "RMS_QUOTA_APPROVED", AuditService.MODULE_RMS,
                "Propuesta de cuota", proposal.getId(), "Aprobada como cuota " + quota.getId()
                        + (note == null ? "" : ". Nota: " + note));
        return toResponse(proposal);
    }

    @Transactional
    public ProposalResponse reject(String id, DecisionRequest request) {
        AuthenticatedUser actor = currentUser.require();
        RmsQuotaProposalEntity proposal = pendingProposal(id);
        String note = request == null ? null : blankToNull(request.note());
        proposal.reject(actor.id(), note);
        proposalRepository.save(proposal);
        auditService.record(AuditActionType.UPDATE, "RMS_QUOTA_REJECTED", AuditService.MODULE_RMS,
                "Propuesta de cuota", proposal.getId(), note == null ? "Rechazada" : "Rechazada: " + note);
        return toResponse(proposal);
    }

    // ------------------------------------------------------------------

    private RmsQuotaProposalEntity pendingProposal(String id) {
        RmsQuotaProposalEntity proposal = proposalRepository.findById(id)
                .orElseThrow(() -> ApiException.of(ErrorCode.RMS_PROPOSAL_NOT_FOUND));
        if (proposal.getStatus() != RmsQuotaProposalEntity.Status.PROPOSED) {
            throw ApiException.of(ErrorCode.RMS_PROPOSAL_ALREADY_DECIDED);
        }
        return proposal;
    }

    private EstimateResponse toResponse(RmsEstimateEntity estimate, SpeciesEntity species, boolean detail) {
        List<SeriesPoint> series = List.of();
        List<CurvePoint> curve = List.of();
        if (detail) {
            series = readSeries(estimate.getSeriesJson()).stream()
                    .map(point -> new SeriesPoint(point.year(), point.catchKg(), point.effort(), point.cpue(),
                            point.source()))
                    .toList();
            SchaeferModel.Fit fit = estimate.fit();
            if (fit != null) {
                curve = SchaeferModel.productionCurve(fit, CURVE_POINTS).stream()
                        .map(point -> new CurvePoint(point[0], point[1]))
                        .toList();
            }
        }
        return new EstimateResponse(estimate.getSpeciesId(), species == null ? null : species.getCommonName(),
                species == null ? null : species.getScientificName(), estimate.getYearsCount(),
                estimate.getFirstYear(), estimate.getLastYear(), estimate.isFitted(), estimate.isIndicative(),
                estimate.getR(), estimate.getKKg(), estimate.getQ(), estimate.getMsyKg(), estimate.getFMsy(),
                estimate.getBiomassRatio(), estimate.getFishingRatio(), estimate.getR2(),
                estimate.getStockStatus() == null ? null : estimate.getStockStatus().name(), estimate.getNote(),
                estimate.getComputedAt(), series, curve);
    }

    private ProposalResponse toResponse(RmsQuotaProposalEntity proposal) {
        String species = speciesRepository.findById(proposal.getSpeciesId()).map(SpeciesEntity::getCommonName)
                .orElse(null);
        return new ProposalResponse(proposal.getId(), proposal.getSpeciesId(), species, proposal.getProposedKg(),
                proposal.getMsyKg(), proposal.isIndicative(), proposal.getZone(), proposal.getPeriodFrom(),
                proposal.getPeriodTo(), proposal.getJustification(), proposal.getStatus().name(),
                userName(proposal.getProposedBy()), userName(proposal.getDecidedBy()), proposal.getDecisionNote(),
                proposal.getQuotaId(), proposal.getCreatedAt(), proposal.getDecidedAt());
    }

    private String userName(String userId) {
        return userId == null ? null
                : userRepository.findById(userId).map(UserEntity::getFullName).orElse(null);
    }

    private Map<String, SpeciesEntity> speciesById(List<String> ids) {
        return speciesRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(SpeciesEntity::getId, Function.identity()));
    }

    private static List<RmsSeries.Point> readSeries(String json) {
        try {
            return JSON.readValue(json, new TypeReference<List<RmsSeries.Point>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** El cálculo se lanza después del commit: el hilo de fondo debe poder leer el trabajo guardado. */
    private void submitAfterCommit(String runId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executor.execute(() -> runner.run(runId));
                }
            });
        } else {
            executor.execute(() -> runner.run(runId));
        }
    }
}
