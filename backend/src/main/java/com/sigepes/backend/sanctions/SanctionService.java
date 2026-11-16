package com.sigepes.backend.sanctions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sigepes.backend.audit.AuditActionType;
import com.sigepes.backend.audit.AuditService;
import com.sigepes.backend.common.ApiException;
import com.sigepes.backend.common.AppClock;
import com.sigepes.backend.common.BusinessDays;
import com.sigepes.backend.common.ErrorCode;
import com.sigepes.backend.common.Ids;
import com.sigepes.backend.inspections.InspectionEntity;
import com.sigepes.backend.inspections.InspectionParts.FindingEntity;
import com.sigepes.backend.inspections.InspectionRepository;
import com.sigepes.backend.inspections.InspectionService;
import com.sigepes.backend.permits.PermitDtos.EvaluatePermitRequest;
import com.sigepes.backend.permits.PermitEntity;
import com.sigepes.backend.permits.PermitRepository;
import com.sigepes.backend.permits.PermitService;
import com.sigepes.backend.permits.PermitStatus;
import com.sigepes.backend.reports.IllegalReportEntity;
import com.sigepes.backend.reports.IllegalReportRepository;
import com.sigepes.backend.reports.ReportStatus;
import com.sigepes.backend.sanctions.SanctionDtos.ActionResponse;
import com.sigepes.backend.sanctions.SanctionDtos.DecisionRequest;
import com.sigepes.backend.sanctions.SanctionDtos.OpenRequest;
import com.sigepes.backend.sanctions.SanctionDtos.ProcessResponse;
import com.sigepes.backend.sanctions.SanctionDtos.ProcessSummary;
import com.sigepes.backend.sanctions.SanctionTypes.ActionKind;
import com.sigepes.backend.sanctions.SanctionTypes.Infraction;
import com.sigepes.backend.sanctions.SanctionTypes.Sanction;
import com.sigepes.backend.sanctions.SanctionTypes.Stage;
import com.sigepes.backend.security.AuthenticatedUser;
import com.sigepes.backend.security.CurrentUser;
import com.sigepes.backend.users.Role;
import com.sigepes.backend.users.UserEntity;
import com.sigepes.backend.users.UserRepository;
import com.sigepes.backend.vessels.VesselEntity;
import com.sigepes.backend.vessels.VesselRepository;

/**
 * HU-23 · Procesos sancionatorios (Ley 1851 de 2017).
 *
 * <p>Apertura → Descargos (15 días hábiles) → Pruebas (30) → Decisión → Recurso (10) → Cerrado. Los
 * plazos se cuentan en días hábiles de Colombia desde el «hoy» regulatorio ({@link AppClock}). Solo el
 * INSPECTOR y el ADMIN gestionan; el investigado ve solo sus procesos y presenta descargos y recurso.
 * Cada actuación queda en el expediente y en la auditoría con el estado anterior y el nuevo.
 */
@Service
public class SanctionService {

    private final SanctionProcessRepository repository;
    private final SanctionActionRepository actionRepository;
    private final InspectionRepository inspectionRepository;
    private final InspectionService inspectionService;
    private final IllegalReportRepository reportRepository;
    private final VesselRepository vesselRepository;
    private final UserRepository userRepository;
    private final PermitRepository permitRepository;
    private final PermitService permitService;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public SanctionService(SanctionProcessRepository repository, SanctionActionRepository actionRepository,
            InspectionRepository inspectionRepository, InspectionService inspectionService,
            IllegalReportRepository reportRepository, VesselRepository vesselRepository,
            UserRepository userRepository, PermitRepository permitRepository, PermitService permitService,
            CurrentUser currentUser, AuditService auditService, ApplicationEventPublisher events) {
        this.repository = repository;
        this.actionRepository = actionRepository;
        this.inspectionRepository = inspectionRepository;
        this.inspectionService = inspectionService;
        this.reportRepository = reportRepository;
        this.vesselRepository = vesselRepository;
        this.userRepository = userRepository;
        this.permitRepository = permitRepository;
        this.permitService = permitService;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.events = events;
    }

    // ------------------------------------------------------------------
    // Apertura (AC-23.1)
    // ------------------------------------------------------------------

    @Transactional
    public ProcessResponse open(OpenRequest request) {
        AuthenticatedUser actor = requireManager();
        UserEntity opener = userRepository.findById(actor.id()).orElseThrow(() -> ApiException.of(ErrorCode.UNAUTHORIZED));
        boolean fromInspection = request.inspectionId() != null && !request.inspectionId().isBlank();
        boolean fromReport = request.reportId() != null && !request.reportId().isBlank();
        if (fromInspection == fromReport) {
            throw ApiException.of(ErrorCode.VALIDATION_ERROR,
                    "El proceso se abre desde un acta de inspección o desde una denuncia fundada (uno de los dos).");
        }

        VesselEntity vessel;
        Infraction suggested;
        String facts;
        SanctionProcessEntity.Origin origin;
        if (fromInspection) {
            InspectionEntity inspection = inspectionRepository.findById(request.inspectionId())
                    .orElseThrow(() -> ApiException.of(ErrorCode.INSPECTION_NOT_FOUND));
            if (inspection.getResult() != InspectionEntity.Result.WITH_FINDINGS) {
                throw ApiException.of(ErrorCode.SANCTION_NOT_APPLICABLE,
                        "El acta " + inspection.getCode() + " es conforme: no hay hallazgos que investigar.");
            }
            if (repository.existsByInspectionId(inspection.getId())) {
                throw ApiException.of(ErrorCode.SANCTION_ALREADY_OPEN);
            }
            List<FindingEntity> findings = inspectionService.findings(inspection.getId());
            vessel = inspection.getVessel();
            suggested = Infraction.mostSevere(findings.stream().map(FindingEntity::getCode).toList());
            facts = "Acta " + inspection.getCode() + ": " + findings.stream()
                    .map(finding -> finding.getCode().label() + " — " + finding.getDescription())
                    .collect(Collectors.joining("; "));
            origin = SanctionProcessEntity.Origin.INSPECTION;
        } else {
            IllegalReportEntity report = reportRepository.findById(request.reportId())
                    .orElseThrow(() -> ApiException.of(ErrorCode.REPORT_NOT_FOUND));
            if (report.getStatus() != ReportStatus.CLOSED_FOUNDED) {
                throw ApiException.of(ErrorCode.SANCTION_NOT_APPLICABLE,
                        "Solo una denuncia cerrada como fundada da lugar a un proceso sancionatorio.");
            }
            if (request.vesselId() == null || request.vesselId().isBlank()) {
                throw ApiException.of(ErrorCode.VALIDATION_ERROR,
                        "Indica la embarcación investigada: la denuncia no identifica al responsable.");
            }
            vessel = vesselRepository.findById(request.vesselId())
                    .orElseThrow(() -> ApiException.of(ErrorCode.VESSEL_NOT_FOUND));
            suggested = Infraction.of(report.getReportType());
            facts = "Denuncia " + report.getTrackingCode() + " (fundada): " + report.getDescription();
            origin = SanctionProcessEntity.Origin.REPORT;
        }
        Infraction infraction = request.infraction() == null || request.infraction().isBlank() ? suggested
                : Infraction.valueOf(request.infraction());
        if (request.facts() != null && !request.facts().isBlank()) {
            facts = request.facts().trim();
        }

        LocalDate today = AppClock.today();
        LocalDate defenseDeadline = BusinessDays.add(today, SanctionTypes.DEFENSE_DAYS);
        SanctionProcessEntity process = repository.save(new SanctionProcessEntity(Ids.generate("pso"), nextNumber(today),
                origin, fromInspection ? request.inspectionId() : null, fromReport ? request.reportId() : null, vessel,
                infraction, truncate(facts, 1500), opener, defenseDeadline));
        action(process, ActionKind.OPENED, null, Stage.OPENED,
                "Infracción tipificada: " + infraction.label() + ". Plazo de descargos: " + defenseDeadline + ".", actor);
        auditService.recordChange("SANCTION_OPENED", AuditService.MODULE_SANCTIONS, "Proceso sancionatorio",
                process.getId(), "Proceso " + process.getNumber() + " contra «" + vessel.getName() + "»: "
                        + infraction.label(), null, process.describe());
        notifyInvestigated(process, "Se abrió el proceso sancionatorio " + process.getNumber() + " por «"
                + infraction.label() + "». Tienes hasta el " + defenseDeadline + " (" + SanctionTypes.DEFENSE_DAYS
                + " días hábiles) para presentar tus descargos.");
        return toResponse(process, actor);
    }

    // ------------------------------------------------------------------
    // Etapas (AC-23.2) y decisión (AC-23.4)
    // ------------------------------------------------------------------

    /** Apertura → Descargos y Descargos → Pruebas. */
    @Transactional
    public ProcessResponse advance(String id, String note) {
        AuthenticatedUser actor = requireManager();
        SanctionProcessEntity process = byId(id);
        String before = process.describe();
        LocalDate today = AppClock.today();
        Stage from = process.getStage();
        String annotation = blankToNull(note);
        switch (from) {
            case OPENED -> process.moveTo(Stage.DEFENSE, process.getStageDeadline());
            case DEFENSE -> {
                if (!process.isDefenseSubmitted() && !today.isAfter(process.getStageDeadline())) {
                    throw ApiException.of(ErrorCode.SANCTION_DEADLINE,
                            "El plazo de descargos vence el " + process.getStageDeadline()
                                    + ": no se puede pasar a pruebas antes, salvo que el investigado ya los presente.");
                }
                if (!process.isDefenseSubmitted()) {
                    // Gherkin HU-23: vencido el plazo sin respuesta, el expediente lo deja anotado.
                    annotation = "Sin descargos" + (annotation == null ? "" : ". " + annotation);
                }
                process.moveTo(Stage.EVIDENCE, BusinessDays.add(today, SanctionTypes.EVIDENCE_DAYS));
            }
            default -> throw ApiException.of(ErrorCode.SANCTION_INVALID_TRANSITION,
                    "Desde la etapa «" + from.label() + "» no se avanza así: "
                            + (from == Stage.EVIDENCE ? "registra la decisión." : "usa el cierre."));
        }
        return record(process, ActionKind.ADVANCED, from, annotation, before, actor,
                "El proceso " + process.getNumber() + " pasó a la etapa «" + process.getStage().label() + "»"
                        + (process.getStageDeadline() == null ? "." : ", con plazo hasta el " + process.getStageDeadline()
                                + ".") + (annotation == null ? "" : " Anotación: " + annotation + "."));
    }

    /** Pruebas → Decisión: fija la sanción o archiva. Suspender el permiso lo cambia en HU-08. */
    @Transactional
    public ProcessResponse decide(String id, DecisionRequest request) {
        AuthenticatedUser actor = requireManager();
        SanctionProcessEntity process = byId(id);
        if (process.getStage() != Stage.EVIDENCE) {
            throw ApiException.of(ErrorCode.SANCTION_INVALID_TRANSITION,
                    "La decisión se registra al terminar la etapa de pruebas.");
        }
        Sanction sanction = Sanction.valueOf(request.sanction());
        BigDecimal fine = null;
        if (sanction == Sanction.FINE) {
            if (request.fineSmmlv() == null) {
                throw ApiException.of(ErrorCode.VALIDATION_ERROR, "Indica el valor de la multa en SMMLV.");
            }
            fine = request.fineSmmlv().setScale(2, RoundingMode.HALF_UP);
        }
        String before = process.describe();
        Stage from = process.getStage();
        process.decide(sanction, fine, request.text().trim());
        List<String> effects = new ArrayList<>();
        if (sanction == Sanction.PERMIT_SUSPENSION) {
            for (PermitEntity permit : permitRepository.findByOwnerIdAndStatus(process.getInvestigated().getId(),
                    PermitStatus.APPROVED)) {
                permitService.evaluate(permit.getId(), new EvaluatePermitRequest("SUSPENDED",
                        "Suspendido por el proceso sancionatorio " + process.getNumber() + ".", null, null));
                effects.add(permit.getNumber());
            }
        }
        if (sanction == Sanction.DISMISSED) {
            process.moveTo(Stage.CLOSED, null);
        } else {
            process.moveTo(Stage.DECISION, BusinessDays.add(AppClock.today(), SanctionTypes.APPEAL_DAYS));
        }
        String detail = sanction.label() + (fine == null ? "" : " de " + fine.stripTrailingZeros().toPlainString()
                + " SMMLV") + (effects.isEmpty() ? "" : ". Permisos suspendidos: " + String.join(", ", effects));
        return record(process, ActionKind.DECIDED, from, detail + ". " + request.text().trim(), before, actor,
                sanction == Sanction.DISMISSED
                        ? "El proceso " + process.getNumber() + " se archivó sin sanción."
                        : "Decisión del proceso " + process.getNumber() + ": " + detail + ". Puedes presentar recurso "
                                + "hasta el " + process.getStageDeadline() + ".");
    }

    /** Decisión → Cerrado (vencido el plazo sin recurso) o Recurso → Cerrado (recurso resuelto). */
    @Transactional
    public ProcessResponse close(String id, String note) {
        AuthenticatedUser actor = requireManager();
        SanctionProcessEntity process = byId(id);
        String before = process.describe();
        Stage from = process.getStage();
        String annotation = blankToNull(note);
        if (from == Stage.DECISION) {
            if (!AppClock.today().isAfter(process.getStageDeadline())) {
                throw ApiException.of(ErrorCode.SANCTION_DEADLINE,
                        "El plazo para presentar recurso vence el " + process.getStageDeadline() + ".");
            }
            annotation = "Decisión en firme: no se presentó recurso" + (annotation == null ? "" : ". " + annotation);
        } else if (from == Stage.APPEAL) {
            if (annotation == null) {
                throw ApiException.of(ErrorCode.VALIDATION_ERROR, "Escribe cómo se resolvió el recurso.");
            }
            annotation = "Recurso resuelto: " + annotation;
        } else {
            throw ApiException.of(ErrorCode.SANCTION_INVALID_TRANSITION,
                    "Solo se cierra un proceso decidido o con el recurso resuelto.");
        }
        process.moveTo(Stage.CLOSED, null);
        return record(process, ActionKind.CLOSED, from, annotation, before, actor,
                "El proceso " + process.getNumber() + " quedó cerrado. " + annotation + ".");
    }

    // ------------------------------------------------------------------
    // Escritos del investigado
    // ------------------------------------------------------------------

    @Transactional
    public ProcessResponse fileDefense(String id, String text) {
        AuthenticatedUser actor = currentUser.require();
        SanctionProcessEntity process = byId(id);
        requireInvestigated(process, actor);
        if (process.getStage() != Stage.OPENED && process.getStage() != Stage.DEFENSE) {
            throw ApiException.of(ErrorCode.SANCTION_INVALID_TRANSITION, "La etapa de descargos ya terminó.");
        }
        if (process.isDefenseSubmitted()) {
            throw ApiException.of(ErrorCode.SANCTION_INVALID_TRANSITION, "Ya presentaste tus descargos.");
        }
        if (AppClock.today().isAfter(process.getStageDeadline())) {
            throw ApiException.of(ErrorCode.SANCTION_DEADLINE,
                    "El plazo de descargos venció el " + process.getStageDeadline() + ".");
        }
        String before = process.describe();
        process.markDefense();
        return record(process, ActionKind.DEFENSE_FILED, process.getStage(), text.trim(), before, actor, null);
    }

    @Transactional
    public ProcessResponse fileAppeal(String id, String text) {
        AuthenticatedUser actor = currentUser.require();
        SanctionProcessEntity process = byId(id);
        requireInvestigated(process, actor);
        if (process.getStage() != Stage.DECISION) {
            throw ApiException.of(ErrorCode.SANCTION_INVALID_TRANSITION, "Solo se recurre una decisión.");
        }
        if (AppClock.today().isAfter(process.getStageDeadline())) {
            throw ApiException.of(ErrorCode.SANCTION_DEADLINE,
                    "El plazo para presentar recurso venció el " + process.getStageDeadline() + ".");
        }
        String before = process.describe();
        Stage from = process.getStage();
        process.markAppeal();
        process.moveTo(Stage.APPEAL, null);
        return record(process, ActionKind.APPEAL_FILED, from, text.trim(), before, actor, null);
    }

    // ------------------------------------------------------------------
    // Consulta (AC-23.5)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ProcessSummary> list() {
        AuthenticatedUser actor = currentUser.require();
        List<SanctionProcessEntity> processes = isManager(actor) ? repository.findAllByOrderByOpenedAtDesc()
                : repository.findByInvestigatedIdOrderByOpenedAtDesc(actor.id());
        LocalDate today = AppClock.today();
        return processes.stream()
                .map(p -> new ProcessSummary(p.getId(), p.getNumber(), p.getVessel().getName(),
                        p.getInvestigated().getFullName(), p.getInfraction().name(), p.getInfraction().label(),
                        p.getStage().name(), p.getStage().label(), p.getStageDeadline(), overdue(p, today),
                        p.getOpenedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProcessResponse detail(String id) {
        AuthenticatedUser actor = currentUser.require();
        SanctionProcessEntity process = byId(id);
        if (!isManager(actor) && !process.getInvestigated().getId().equals(actor.id())) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Solo puedes consultar los procesos en los que eres investigado.");
        }
        return toResponse(process, actor);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<String> processForInspection(String inspectionId) {
        return repository.findByInspectionId(inspectionId).map(SanctionProcessEntity::getId);
    }

    // ------------------------------------------------------------------

    private ProcessResponse record(SanctionProcessEntity process, ActionKind kind, Stage from, String note,
            String before, AuthenticatedUser actor, String notification) {
        SanctionProcessEntity saved = repository.save(process);
        action(saved, kind, from, saved.getStage(), note, actor);
        auditService.recordChange("SANCTION_" + kind.name(), AuditService.MODULE_SANCTIONS, "Proceso sancionatorio",
                saved.getId(), "Proceso " + saved.getNumber() + ": " + kind.name() + " (" + (from == null ? "—"
                        : from.label()) + " → " + saved.getStage().label() + ")" + (note == null ? "" : ". " + note),
                before, saved.describe());
        if (notification != null) {
            notifyInvestigated(saved, notification);
        }
        return toResponse(saved, actor);
    }

    private void action(SanctionProcessEntity process, ActionKind kind, Stage from, Stage to, String note,
            AuthenticatedUser actor) {
        int next = actionRepository.findByProcessIdOrderBySequenceNoAsc(process.getId()).size() + 1;
        actionRepository.save(new SanctionActionEntity(Ids.generate("asn"), process.getId(), next, kind, from, to,
                note == null ? null : truncate(note, 1500), actor.id()));
    }

    private void notifyInvestigated(SanctionProcessEntity process, String message) {
        events.publishEvent(new SanctionStageChanged(process.getId(), process.getNumber(),
                process.getInvestigated().getId(), process.getStage().label(), process.getStageDeadline(), message));
    }

    private ProcessResponse toResponse(SanctionProcessEntity p, AuthenticatedUser actor) {
        List<SanctionActionEntity> actions = actionRepository.findByProcessIdOrderBySequenceNoAsc(p.getId());
        Map<String, String> names = userRepository.findAllById(actions.stream().map(SanctionActionEntity::getActorId)
                .distinct().toList()).stream().collect(Collectors.toMap(UserEntity::getId, UserEntity::getFullName));
        String inspectionCode = p.getInspectionId() == null ? null
                : inspectionRepository.findById(p.getInspectionId()).map(InspectionEntity::getCode).orElse(null);
        String reportCode = p.getReportId() == null ? null
                : reportRepository.findById(p.getReportId()).map(IllegalReportEntity::getTrackingCode).orElse(null);
        LocalDate today = AppClock.today();
        return new ProcessResponse(p.getId(), p.getNumber(), p.getOrigin().name(), p.getInspectionId(), inspectionCode,
                p.getReportId(), reportCode, p.getVessel().getId(), p.getVessel().getName(),
                p.getVessel().getRegistration(), p.getInvestigated().getFullName(), p.getInfraction().name(),
                p.getInfraction().label(), p.getFacts(), p.getStage().name(), p.getStage().label(), p.getStageDeadline(),
                overdue(p, today), p.isDefenseSubmitted(), p.isAppealSubmitted(),
                p.getSanction() == null ? null : p.getSanction().name(),
                p.getSanction() == null ? null : p.getSanction().label(), p.getFineSmmlv(), p.getDecisionText(),
                p.getOpenedBy().getFullName(), p.getOpenedAt(), p.getClosedAt(),
                actions.stream().map(a -> new ActionResponse(a.getSequenceNo(), a.getKind().name(),
                        a.getStageFrom() == null ? null : a.getStageFrom().label(), a.getStageTo().label(), a.getNote(),
                        names.getOrDefault(a.getActorId(), "—"), a.getOccurredAt())).toList(),
                allowedActions(p, actor, today));
    }

    /** Lo que quien consulta puede hacer ahora; la interfaz solo muestra esos botones. */
    private List<String> allowedActions(SanctionProcessEntity p, AuthenticatedUser actor, LocalDate today) {
        List<String> allowed = new ArrayList<>();
        if (isManager(actor)) {
            switch (p.getStage()) {
                case OPENED -> allowed.add("ADVANCE");
                case DEFENSE -> {
                    if (p.isDefenseSubmitted() || today.isAfter(p.getStageDeadline())) {
                        allowed.add("ADVANCE");
                    }
                }
                case EVIDENCE -> allowed.add("DECIDE");
                case DECISION -> {
                    if (today.isAfter(p.getStageDeadline())) {
                        allowed.add("CLOSE");
                    }
                }
                case APPEAL -> allowed.add("CLOSE");
                case CLOSED -> {
                }
            }
        }
        if (p.getInvestigated().getId().equals(actor.id())) {
            if ((p.getStage() == Stage.OPENED || p.getStage() == Stage.DEFENSE) && !p.isDefenseSubmitted()
                    && !today.isAfter(p.getStageDeadline())) {
                allowed.add("FILE_DEFENSE");
            }
            if (p.getStage() == Stage.DECISION && !today.isAfter(p.getStageDeadline())) {
                allowed.add("FILE_APPEAL");
            }
        }
        return allowed;
    }

    private static boolean overdue(SanctionProcessEntity p, LocalDate today) {
        return p.getStage() != Stage.CLOSED && p.getStageDeadline() != null && today.isAfter(p.getStageDeadline());
    }

    private SanctionProcessEntity byId(String id) {
        return repository.findById(id).orElseThrow(() -> ApiException.of(ErrorCode.SANCTION_NOT_FOUND));
    }

    /** AC-23.5 · Solo INSPECTOR y ADMIN gestionan procesos. */
    private AuthenticatedUser requireManager() {
        AuthenticatedUser actor = currentUser.require();
        if (!isManager(actor)) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Solo un inspector o un administrador gestiona procesos sancionatorios.");
        }
        return actor;
    }

    private static boolean isManager(AuthenticatedUser actor) {
        return actor.role() == Role.INSPECTOR || actor.role() == Role.ADMIN;
    }

    private static void requireInvestigated(SanctionProcessEntity process, AuthenticatedUser actor) {
        if (!process.getInvestigated().getId().equals(actor.id())) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Solo el investigado presenta escritos en su proceso.");
        }
    }

    private String nextNumber(LocalDate today) {
        String prefix = "PS-" + today.getYear() + "-";
        long next = repository.countByNumberStartingWith(prefix) + 1;
        return prefix + String.format("%04d", next);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
