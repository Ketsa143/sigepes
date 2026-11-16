package com.sigepes.backend.sanctions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** HU-23 · Contratos de la API de procesos sancionatorios. */
public final class SanctionDtos {

    private SanctionDtos() {
    }

    /**
     * AC-23.1 · Apertura desde un acta con hallazgos ({@code inspectionId}) o desde una denuncia
     * fundada ({@code reportId} + la embarcación investigada). La infracción y los hechos se proponen
     * a partir del origen si no se envían.
     */
    public record OpenRequest(
            String inspectionId,
            String reportId,
            String vesselId,
            @Pattern(regexp = "PROHIBITED_GEAR|FISHING_WITHOUT_PERMIT|PROTECTED_AREA|UNDERSIZED_CATCH|UNAUTHORIZED_GEAR|UNREGISTERED_VESSEL|OTHER",
                    message = "La infracción no es válida")
            String infraction,
            @Size(max = 1500, message = "Los hechos no pueden superar 1500 caracteres") String facts) {
    }

    public record NoteRequest(@Size(max = 1500, message = "La anotación no puede superar 1500 caracteres") String note) {
    }

    /** Escrito del investigado: descargos o recurso. */
    public record FilingRequest(
            @NotBlank(message = "El escrito es obligatorio")
            @Size(max = 1500, message = "El escrito no puede superar 1500 caracteres")
            String text) {
    }

    /** AC-23.4 · Decisión: sanción o archivo. La multa se expresa en salarios mínimos (SMMLV). */
    public record DecisionRequest(
            @NotBlank(message = "La sanción es obligatoria")
            @Pattern(regexp = "WARNING|FINE|CONFISCATION|PERMIT_SUSPENSION|DISMISSED", message = "La sanción no es válida")
            String sanction,
            @DecimalMin(value = "0.01", message = "La multa debe ser mayor que cero")
            @DecimalMax(value = "100000", message = "La multa no es válida")
            BigDecimal fineSmmlv,
            @NotBlank(message = "La motivación de la decisión es obligatoria")
            @Size(max = 1500, message = "La decisión no puede superar 1500 caracteres")
            String text) {
    }

    public record ActionResponse(int sequence, String kind, String stageFrom, String stageTo, String note,
            String actorName, Instant occurredAt) {
    }

    public record ProcessSummary(String id, String number, String vesselName, String investigatedName,
            String infraction, String infractionLabel, String stage, String stageLabel, LocalDate stageDeadline,
            boolean overdue, Instant openedAt) {
    }

    public record ProcessResponse(
            String id,
            String number,
            String origin,
            String inspectionId,
            String inspectionCode,
            String reportId,
            String reportCode,
            String vesselId,
            String vesselName,
            String vesselRegistration,
            String investigatedName,
            String infraction,
            String infractionLabel,
            String facts,
            String stage,
            String stageLabel,
            LocalDate stageDeadline,
            boolean overdue,
            boolean defenseSubmitted,
            boolean appealSubmitted,
            String sanction,
            String sanctionLabel,
            BigDecimal fineSmmlv,
            String decisionText,
            String openedBy,
            Instant openedAt,
            Instant closedAt,
            List<ActionResponse> actions,
            /** Qué puede hacer quien consulta en este momento (la interfaz solo muestra eso). */
            List<String> allowedActions) {
    }
}
