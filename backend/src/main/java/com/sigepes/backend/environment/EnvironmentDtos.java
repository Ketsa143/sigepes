package com.sigepes.backend.environment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** HU-14 · Contratos de la API de monitoreo ambiental. */
public final class EnvironmentDtos {

    private EnvironmentDtos() {
    }

    /** Valores de una lectura; {@code anomalyC} es la diferencia con la temperatura de referencia. */
    public record ReadingResponse(Instant observedAt, BigDecimal sstC, BigDecimal chlorophyllMgM3,
            BigDecimal waveHeightM, BigDecimal anomalyC, String source) {
    }

    /**
     * Zona con su última lectura guardada. {@code ageHours} y {@code stale} permiten el aviso de D4
     * ("dato de hace X h") cuando la importación no trae datos nuevos.
     */
    public record ZoneSnapshot(String id, String name, String region, double latitude, double longitude,
            BigDecimal sstBaselineC, ReadingResponse latest, Long ageHours, boolean stale, boolean sstAlert,
            boolean waveAlert, LastValue lastChlorophyll) {
    }

    /**
     * AC-14.5 · Si la última lectura no trae clorofila (su fuente no respondió), el último valor
     * guardado con su antigüedad.
     */
    public record LastValue(BigDecimal value, Instant observedAt, long ageHours) {
    }

    public record SourceStatus(String name, boolean available, Instant lastSuccessAt, Instant lastFailureAt,
            String lastError) {
    }

    public record OverviewResponse(List<ZoneSnapshot> zones, List<SourceStatus> sources, Instant lastImportAt,
            double sstThresholdC, double waveThresholdM) {
    }

    /** AC-14.4 · Kilos de la especie capturados en la zona ese día. */
    public record CatchPoint(LocalDate date, BigDecimal weightKg) {
    }

    public record SeriesResponse(ZoneSnapshot zone, LocalDate from, LocalDate to, List<ReadingResponse> readings,
            String speciesId, String species, List<CatchPoint> catches) {
    }

    public record ImportResponse(int readingsStored, int anomalies, List<SourceStatus> sources) {
    }
}
