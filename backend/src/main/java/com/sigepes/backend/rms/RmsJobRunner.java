package com.sigepes.backend.rms;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigepes.backend.audit.AuditActionType;
import com.sigepes.backend.audit.AuditService;
import com.sigepes.backend.users.UserRepository;

/**
 * HU-17 · TASK-17.2 · Ajusta el modelo de Schaefer para todas las especies con datos, fuera de la
 * petición HTTP, y deja el resultado en {@code rms_estimates}. Cada paso va en su propia
 * transacción: si el cálculo falla, el trabajo queda marcado como fallido en lugar de perderse.
 */
@Component
class RmsJobRunner {

    private static final Logger log = LoggerFactory.getLogger(RmsJobRunner.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RmsRunRepository runRepository;
    private final RmsEstimateRepository estimateRepository;
    private final RmsSeries series;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final TransactionTemplate tx;

    RmsJobRunner(RmsRunRepository runRepository, RmsEstimateRepository estimateRepository, RmsSeries series,
            AuditService auditService, UserRepository userRepository, PlatformTransactionManager transactionManager) {
        this.runRepository = runRepository;
        this.estimateRepository = estimateRepository;
        this.series = series;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    void run(String runId) {
        long start = System.nanoTime();
        try {
            tx.executeWithoutResult(status -> runRepository.findById(runId).ifPresent(run -> {
                run.start();
                runRepository.save(run);
            }));
            Integer count = tx.execute(status -> compute(runId));
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            tx.executeWithoutResult(status -> runRepository.findById(runId).ifPresent(run -> {
                run.finish(count == null ? 0 : count, elapsedMs);
                runRepository.save(run);
                String actorLabel = run.getRequestedBy() == null ? "sistema"
                        : userRepository.findById(run.getRequestedBy()).map(user -> user.getFullName()).orElse("sistema");
                auditService.recordAs(run.getRequestedBy(), actorLabel,
                        AuditActionType.UPDATE, "RMS_COMPUTED", AuditService.MODULE_RMS, "Cálculo de RMS", run.getId(),
                        "RMS recalculado para " + count + " especies en " + elapsedMs + " ms", null, null);
            }));
        } catch (RuntimeException e) {
            log.warn("El cálculo de RMS {} falló: {}", runId, e.getMessage());
            tx.executeWithoutResult(status -> runRepository.findById(runId).ifPresent(run -> {
                run.fail(e.getMessage());
                runRepository.save(run);
            }));
        }
    }

    private int compute(String runId) {
        Map<String, List<RmsSeries.Point>> all = series.build();
        all.forEach((speciesId, points) -> {
            List<SchaeferModel.YearPoint> usable = points.stream()
                    .map(RmsSeries.Point::toModel)
                    .filter(point -> point.catchKg() > 0 && point.effort() > 0)
                    .toList();
            SchaeferModel.Fit fit = null;
            String note;
            if (usable.size() < SchaeferModel.MIN_YEARS) {
                note = "Solo " + usable.size() + " año(s) con captura y esfuerzo: se necesitan al menos "
                        + SchaeferModel.MIN_YEARS + " para ajustar el modelo.";
            } else {
                fit = safeFit(usable);
                note = fit == null
                        ? "La serie no permitió un ajuste estable del modelo (revisa capturas y esfuerzo)."
                        : usable.size() < SchaeferModel.RELIABLE_YEARS
                                ? "Indicativo: menos de " + SchaeferModel.RELIABLE_YEARS + " años de datos (AC-17.4)."
                                : null;
            }
            RmsEstimateEntity estimate = estimateRepository.findById(speciesId)
                    .orElseGet(() -> new RmsEstimateEntity(speciesId));
            int first = points.get(0).year();
            int last = points.get(points.size() - 1).year();
            estimate.update(runId, usable.size(), first, last, toJson(points), fit, note);
            estimateRepository.save(estimate);
        });
        return all.size();
    }

    private static SchaeferModel.Fit safeFit(List<SchaeferModel.YearPoint> usable) {
        try {
            SchaeferModel.Fit fit = SchaeferModel.fit(usable);
            boolean finite = Double.isFinite(fit.r()) && Double.isFinite(fit.k()) && Double.isFinite(fit.q())
                    && Double.isFinite(fit.msy()) && Double.isFinite(fit.fMsy()) && fit.q() > 0;
            return finite && fitsColumns(fit) ? fit : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Un ajuste degenerado (por ejemplo, una biomasa que colapsa en el último año) da razones de
     * pesca de millones que no caben en {@code rms_estimates}. Si uno solo de esos valores desborda
     * su columna, la base rechaza la escritura y el cálculo de todas las especies falla; por eso se
     * trata como un ajuste no estable de esa especie.
     */
    static boolean fitsColumns(SchaeferModel.Fit fit) {
        return within(fit.r(), 1e6) && within(fit.k(), 1e16) && within(fit.q(), 1e8) && within(fit.msy(), 1e14)
                && within(fit.fMsy(), 1e14) && within(fit.biomassRatio(), 1e6) && within(fit.fishingRatio(), 1e6)
                && within(fit.r2(), 100);
    }

    private static boolean within(double value, double limit) {
        return Double.isFinite(value) && value >= 0 && value < limit;
    }

    private static String toJson(List<RmsSeries.Point> points) {
        try {
            return JSON.writeValueAsString(points);
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible serializar la serie de RMS", e);
        }
    }
}
