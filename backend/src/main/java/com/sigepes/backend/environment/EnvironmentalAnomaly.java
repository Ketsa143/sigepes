package com.sigepes.backend.environment;

import java.time.LocalDate;

/**
 * HU-14 · AC-14.3 · Una variable ambiental de una zona superó su umbral. Lo escucha el módulo de
 * alertas (HU-11) para avisar a los analistas.
 *
 * @param kind  {@code SST_ANOMALY} (anomalía térmica) o {@code HIGH_WAVES} (oleaje fuerte)
 * @param value anomalía en °C o altura de ola en m
 * @param day   día (hora de Colombia) de la lectura: una alerta por zona, tipo y día
 */
public record EnvironmentalAnomaly(String zoneId, String zoneName, String kind, double value, double threshold,
        LocalDate day) {

    public static final String SST_ANOMALY = "SST_ANOMALY";
    public static final String HIGH_WAVES = "HIGH_WAVES";
}
