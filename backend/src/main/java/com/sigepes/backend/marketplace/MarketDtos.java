package com.sigepes.backend.marketplace;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * HU-16 · Contratos del marketplace.
 *
 * <p>AC-16.5 · S5: ningún DTO lleva cédula, correo ni teléfono. Del vendedor y del comprador solo
 * viajan un alias (nombre e inicial del apellido) y el municipio (puerto de la embarcación).
 */
public final class MarketDtos {

    private MarketDtos() {
    }

    /** AC-16.6 · Paso 1 del asistente: lotes propios que se pueden ofertar, con sus datos precargados. */
    public record EligibleLot(String lotId, String lotCode, String species, BigDecimal weightKg, String port,
            LocalDate landingDate, String zone) {
    }

    public record PublishRequest(
            @NotBlank(message = "Elige el lote que vas a ofertar") String lotId,
            @NotNull(message = "El precio por kilo es obligatorio")
            @DecimalMin(value = "100", message = "El precio por kilo debe ser de al menos $100")
            @DecimalMax(value = "10000000", message = "El precio por kilo no es válido")
            BigDecimal pricePerKg,
            @Size(max = 400, message = "La nota no puede superar 400 caracteres") String notes) {
    }

    /** AC-16.2 · Tarjeta del catálogo, con el enlace a la verificación pública del lote. */
    public record OfferCard(String id, String lotCode, String species, String scientificName, BigDecimal weightKg,
            BigDecimal pricePerKg, BigDecimal totalPrice, String port, LocalDate landingDate, String sellerAlias,
            String municipality, String notes, Instant publishedAt, Instant expiresAt, String verificationPath) {
    }

    public record RequestCreate(
            @NotNull(message = "Indica cuántos kilos quieres")
            @DecimalMin(value = "0.1", message = "La cantidad debe ser mayor que cero")
            BigDecimal quantityKg,
            @NotBlank(message = "Escribe un mensaje para el vendedor")
            @Size(max = 600, message = "El mensaje no puede superar 600 caracteres")
            String message) {
    }

    /** Solicitud vista por el vendedor: el comprador también aparece solo con su alias. */
    public record RequestView(String id, String buyerAlias, BigDecimal quantityKg, String message, String status,
            Instant createdAt, Instant decidedAt) {
    }

    public record MyOffer(OfferCard offer, String status, List<RequestView> requests) {
    }

    /** Solicitud vista por el comprador. */
    public record MyRequest(String id, OfferCard offer, String offerStatus, BigDecimal quantityKg, String message,
            String status, Instant createdAt, Instant decidedAt) {
    }
}
