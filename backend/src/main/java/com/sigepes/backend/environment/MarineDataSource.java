package com.sigepes.backend.environment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * HU-14 · AC-14.1 · Fuente externa de variables ambientales. Cada implementación aporta las variables
 * que conoce; el importador combina las muestras de todas por zona.
 */
public interface MarineDataSource {

    /** Nombre corto que queda en la lectura y en el estado de la fuente. */
    String name();

    /**
     * Muestra más reciente de cada zona que la fuente pudo leer (clave: id de la zona). Lanza una
     * excepción si la fuente no responde; el importador la cuenta para el circuit breaker.
     */
    Map<String, Sample> fetch(List<EnvZoneEntity> zones) throws Exception;

    /** Valores de una zona; los que la fuente no ofrece van en null. */
    record Sample(Instant observedAt, Double sstC, Double chlorophyllMgM3, Double waveHeightM) {
    }
}
