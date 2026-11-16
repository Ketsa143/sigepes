package com.sigepes.backend.alerts;

/** AC-11.1 · Origen de la alerta. */
public enum AlertType {
    CLOSURE,
    QUOTA,
    PERMIT,
    ZONE,
    REPORT,
    SECURITY,
    DOCUMENT,
    EXPORT,
    /** HU-14 · AC-14.3: anomalía térmica u oleaje fuerte en una zona de pesca. */
    ENVIRONMENT,
    /** HU-22: acta de inspección levantada a una embarcación del usuario. */
    INSPECTION,
    /** HU-23: cambio de etapa en un proceso sancionatorio del usuario. */
    SANCTION,
    /** HU-16: solicitud recibida o respondida en el marketplace. */
    MARKET
}
