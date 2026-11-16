package com.sigepes.backend.inspections;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** HU-22 · Contratos de la API de inspecciones en campo. */
public final class InspectionDtos {

    private InspectionDtos() {
    }

    /** AC-22.1 · Captura revisada a bordo: especie y talla medida (y, si se tomó, peso y número). */
    public record MeasuredCatch(
            @NotBlank(message = "La especie es obligatoria") String speciesId,
            @NotNull(message = "La talla medida es obligatoria")
            @DecimalMin(value = "0.1", message = "La talla debe ser mayor que cero")
            @DecimalMax(value = "999", message = "La talla no es válida")
            BigDecimal sizeCm,
            @DecimalMin(value = "0", message = "El peso no puede ser negativo") BigDecimal weightKg,
            @Min(value = 0, message = "La cantidad no puede ser negativa") @Max(value = 100000) Integer quantity) {
    }

    /**
     * Acta firmada en el dispositivo. {@code clientId} es el UUID que le asigna el teléfono al crearla:
     * con él, reenviarla desde la cola sin señal no crea un duplicado (AC-22.4).
     */
    public record CreateInspectionRequest(
            @NotBlank(message = "El identificador del acta es obligatorio")
            @Pattern(regexp = "[0-9a-fA-F-]{16,60}", message = "El identificador del acta no es válido")
            String clientId,
            @NotBlank(message = "La embarcación es obligatoria") String vesselId,
            @NotNull(message = "La fecha de la inspección es obligatoria") Instant inspectedAt,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @NotBlank(message = "El lugar de la inspección es obligatorio")
            @Size(max = 160, message = "El lugar no puede superar 160 caracteres")
            String locationText,
            @Size(max = 40, message = "Máximo 40 capturas medidas") List<@Valid MeasuredCatch> catches,
            @Size(max = 20, message = "Máximo 20 artes") List<@NotBlank String> gearIds,
            @Size(max = 10, message = "Máximo 10 hallazgos anotados")
            List<@NotBlank @Size(max = 400, message = "Cada hallazgo puede tener hasta 400 caracteres") String> otherFindings,
            @Size(max = 1500, message = "Las observaciones no pueden superar 1500 caracteres") String observations,
            @NotBlank(message = "El nombre del responsable de la embarcación es obligatorio")
            @Size(max = 160) String responsibleName,
            @Size(max = 30) String responsibleIdNumber,
            @NotBlank(message = "Falta la firma del inspector") String inspectorSignature,
            @NotBlank(message = "Falta la firma del responsable") String responsibleSignature,
            boolean savedOffline) {
    }

    public record ItemResponse(String kind, String speciesId, String gearId, String label, BigDecimal sizeCm,
            BigDecimal minSizeCm, BigDecimal weightKg, Integer quantity, boolean finding) {
    }

    public record FindingResponse(String id, String code, String label, String description, boolean automatic) {
    }

    public record InspectionResponse(
            String id,
            String code,
            String clientId,
            String vesselId,
            String vesselName,
            String vesselRegistration,
            String ownerName,
            String inspectorName,
            String permitNumber,
            BigDecimal latitude,
            BigDecimal longitude,
            String locationText,
            Instant inspectedAt,
            String result,
            String observations,
            String responsibleName,
            List<ItemResponse> items,
            List<FindingResponse> findings,
            int photos,
            String contentHash,
            boolean savedOffline,
            Instant receivedAt) {
    }

    public record InspectionSummary(String id, String code, String vesselName, String vesselRegistration,
            String inspectorName, String locationText, Instant inspectedAt, String result, int findings,
            boolean savedOffline) {
    }

    /** Verificación pública del PDF: sin datos personales del responsable. */
    public record PublicInspection(String code, String vesselName, String vesselRegistration, Instant inspectedAt,
            String locationText, String result, List<String> findings, boolean integrityOk) {
    }
}
