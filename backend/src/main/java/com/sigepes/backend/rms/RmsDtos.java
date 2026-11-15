package com.sigepes.backend.rms;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** HU-17 · Contratos de la API de Rendimiento Máximo Sostenible. */
public final class RmsDtos {

    private RmsDtos() {
    }

    public record RunResponse(String id, String status, Integer speciesCount, Integer durationMs, String error,
            Instant createdAt, Instant finishedAt) {

        static RunResponse from(RmsRunEntity run) {
            return new RunResponse(run.getId(), run.getStatus().name(), run.getSpeciesCount(), run.getDurationMs(),
                    run.getError(), run.getCreatedAt(), run.getFinishedAt());
        }
    }

    public record SeriesPoint(int year, double catchKg, double effort, double cpue, String source) {
    }

    /** Punto de la curva de producción de equilibrio: esfuerzo (faenas/año) y captura (kg/año). */
    public record CurvePoint(double effort, double yieldKg) {
    }

    /**
     * AC-17.2 · Resultado de una especie. En el listado {@code series} y {@code curve} van vacías;
     * la ficha de la especie las trae completas.
     */
    public record EstimateResponse(
            String speciesId,
            String species,
            String scientificName,
            int yearsCount,
            int firstYear,
            int lastYear,
            boolean fitted,
            boolean indicative,
            BigDecimal r,
            BigDecimal kKg,
            BigDecimal q,
            BigDecimal msyKg,
            BigDecimal fMsy,
            BigDecimal biomassRatio,
            BigDecimal fishingRatio,
            BigDecimal r2,
            String stockStatus,
            String note,
            Instant computedAt,
            List<SeriesPoint> series,
            List<CurvePoint> curve) {
    }

    public record CreateProposalRequest(
            @NotNull(message = "La cuota propuesta es obligatoria")
            @DecimalMin(value = "0.01", message = "La cuota debe ser mayor que cero")
            BigDecimal proposedKg,

            @NotBlank(message = "La zona es obligatoria")
            @Size(max = 120, message = "La zona no puede superar 120 caracteres")
            String zone,

            @NotNull(message = "La fecha de inicio es obligatoria")
            LocalDate from,

            @NotNull(message = "La fecha de finalización es obligatoria")
            LocalDate to,

            @Size(max = 600, message = "La justificación no puede superar 600 caracteres")
            String justification) {
    }

    public record DecisionRequest(
            @Size(max = 400, message = "La nota no puede superar 400 caracteres")
            String note) {
    }

    public record ProposalResponse(
            String id,
            String speciesId,
            String species,
            BigDecimal proposedKg,
            BigDecimal msyKg,
            boolean indicative,
            String zone,
            LocalDate from,
            LocalDate to,
            String justification,
            String status,
            String proposedBy,
            String decidedBy,
            String decisionNote,
            String quotaId,
            Instant createdAt,
            Instant decidedAt) {
    }
}
