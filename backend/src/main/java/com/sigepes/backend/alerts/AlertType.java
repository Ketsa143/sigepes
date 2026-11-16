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
    ENVIRONMENT
}
