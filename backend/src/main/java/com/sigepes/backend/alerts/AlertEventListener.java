package com.sigepes.backend.alerts;

import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.sigepes.backend.environment.EnvironmentalAnomaly;
import com.sigepes.backend.geo.RestrictedZoneEntered;
import com.sigepes.backend.inspections.InspectionSigned;
import com.sigepes.backend.quotas.QuotaThresholdReached;
import com.sigepes.backend.reports.ReportSubmitted;
import com.sigepes.backend.users.Role;

/**
 * AC-11.1 · Alertas por evento de dominio.
 *
 * <p>Se escuchan de forma síncrona, dentro de la transacción de quien publica: la alerta se guarda
 * junto con el hecho que la origina o no se guarda ninguno (patrón outbox, AC-11.5). Así no hace
 * falta revisar periódicamente todos los registros para descubrir los cambios (AC-11.6).
 */
@Component
public class AlertEventListener {

    private static final Set<Role> QUOTA_AUDIENCE = Set.of(Role.ANALYST, Role.ADMIN);

    private final AlertService alerts;

    public AlertEventListener(AlertService alerts) {
        this.alerts = alerts;
    }

    /** AC-07.4 · Cuota al 80 %, 95 % o agotada: analistas y el armador de la embarcación. */
    @EventListener
    public void onQuotaThreshold(QuotaThresholdReached event) {
        AlertSeverity severity = event.threshold() >= 95 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
        String title = event.threshold() >= 100
                ? "Cuota agotada: " + event.speciesName()
                : "Cuota al " + event.threshold() + " %: " + event.speciesName();
        String message = "La cuota de " + event.speciesName() + " en " + event.zone() + " lleva un "
                + event.consumedPercent() + " % consumido; quedan " + event.remainingKg() + " kg.";
        String key = "quota:" + event.quotaId() + ":" + event.threshold();
        alerts.notifyRoles(QUOTA_AUDIENCE, AlertType.QUOTA, severity, title, message, "/app/quotas", key);
        alerts.notify(event.vesselOwnerId(), AlertType.QUOTA, severity, title, message, "/app/quotas", key);
    }

    /** RN-08 · Ingreso a zona restringida: el pescador y la autoridad, siempre como crítica. */
    @EventListener
    public void onRestrictedZone(RestrictedZoneEntered event) {
        String place = String.format(java.util.Locale.ROOT, "%.4f, %.4f", event.latitude(), event.longitude());
        String key = "zone:" + event.eventId();
        alerts.notify(event.ownerId(), AlertType.ZONE, AlertSeverity.CRITICAL,
                "Estás dentro de una zona restringida",
                "La embarcación " + event.vesselName() + " entró a «" + event.zoneName() + "» (" + place
                        + "). Sal de la zona: la pesca está prohibida allí.",
                "/app/trips", key);
        alerts.notifyRoles(AlertService.AUTHORITY, AlertType.ZONE, AlertSeverity.CRITICAL,
                "Ingreso a zona restringida",
                "La embarcación " + event.vesselName() + " entró a «" + event.zoneName() + "» (" + place
                        + "). Faena " + event.tripId() + ".",
                "/app/zones", key);
    }

    /**
     * HU-14 · AC-14.3 · Anomalía ambiental para los analistas (y el administrador): una alerta por
     * zona, tipo y día, aunque la importación de cada 6 h la vuelva a detectar.
     */
    @EventListener
    public void onEnvironmentalAnomaly(EnvironmentalAnomaly event) {
        boolean sst = EnvironmentalAnomaly.SST_ANOMALY.equals(event.kind());
        String value = String.format(java.util.Locale.ROOT, "%.1f", event.value());
        String limit = String.format(java.util.Locale.ROOT, "%.1f", event.threshold());
        String title = sst
                ? "Anomalía térmica en " + event.zoneName() + ": " + (event.value() > 0 ? "+" : "") + value + " °C"
                : "Oleaje fuerte en " + event.zoneName() + ": " + value + " m";
        String message = sst
                ? "La temperatura superficial del mar en " + event.zoneName() + " se aparta " + value
                        + " °C de su valor de referencia (umbral ±" + limit + " °C). Revisa si conviene un cierre"
                        + " preventivo."
                : "La altura de ola en " + event.zoneName() + " llegó a " + value + " m (umbral " + limit
                        + " m). Considera avisar a la flota artesanal.";
        alerts.notifyRoles(QUOTA_AUDIENCE, AlertType.ENVIRONMENT, AlertSeverity.WARNING, title, message,
                "/app/environment", "env:" + event.zoneId() + ":" + event.kind() + ":" + event.day());
    }

    /** HU-22 · El armador recibe el acta que le levantaron, con o sin hallazgos. */
    @EventListener
    public void onInspection(InspectionSigned event) {
        boolean findings = event.findings() > 0;
        alerts.notify(event.ownerId(), AlertType.INSPECTION, findings ? AlertSeverity.WARNING : AlertSeverity.INFO,
                findings ? "Acta con hallazgos: " + event.vesselName() : "Inspección conforme: " + event.vesselName(),
                "Se levantó el acta " + event.code() + " a la embarcación " + event.vesselName()
                        + (findings ? " con " + event.findings() + " hallazgo(s). Puedes consultarla y descargarla."
                                : " sin hallazgos."),
                "/app/inspections/" + event.inspectionId(), "inspection:" + event.inspectionId());
    }

    /** AC-11.1 · Denuncia nueva para la bandeja de inspección. Sin datos del denunciante (RN-20). */
    @EventListener
    public void onReport(ReportSubmitted event) {
        alerts.notifyRoles(AlertService.AUTHORITY, AlertType.REPORT, AlertSeverity.WARNING,
                "Nueva denuncia " + event.trackingCode(),
                "Se recibió una denuncia de pesca ilegal pendiente de revisión.",
                "/app/reports", "report:" + event.reportId());
    }
}
