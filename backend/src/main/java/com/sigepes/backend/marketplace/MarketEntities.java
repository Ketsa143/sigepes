package com.sigepes.backend.marketplace;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.sigepes.backend.species.SpeciesEntity;
import com.sigepes.backend.traceability.LotEntity;
import com.sigepes.backend.users.UserEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** HU-16 · Entidades del marketplace de producto pesquero legal. */
public final class MarketEntities {

    private MarketEntities() {
    }

    /** AC-16.1 · Oferta de un lote trazable. */
    @Entity(name = "MarketOffer")
    @Table(name = "market_offers")
    public static class OfferEntity {

        public enum Status { ACTIVE, SOLD, EXPIRED, WITHDRAWN }

        @Id
        @Column(name = "id", length = 40, nullable = false)
        private String id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "lot_id")
        private LotEntity lot;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "seller_id")
        private UserEntity seller;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "species_id")
        private SpeciesEntity species;

        @Column(name = "weight_kg", precision = 10, scale = 2, nullable = false)
        private BigDecimal weightKg;

        @Column(name = "price_per_kg", precision = 12, scale = 2, nullable = false)
        private BigDecimal pricePerKg;

        @Column(name = "port", length = 120, nullable = false)
        private String port;

        @Column(name = "landing_date", nullable = false)
        private LocalDate landingDate;

        @Column(name = "notes", length = 400)
        private String notes;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", length = 10, nullable = false)
        private Status status = Status.ACTIVE;

        @Column(name = "published_at", nullable = false)
        private Instant publishedAt;

        @Column(name = "expires_at", nullable = false)
        private Instant expiresAt;

        @Column(name = "closed_at")
        private Instant closedAt;

        protected OfferEntity() {
        }

        OfferEntity(String id, LotEntity lot, BigDecimal pricePerKg, String port, String notes, Instant publishedAt,
                Instant expiresAt) {
            this.id = id;
            this.lot = lot;
            this.seller = lot.getOwner();
            this.species = lot.getSpecies();
            this.weightKg = lot.getWeightKg();
            this.pricePerKg = pricePerKg;
            this.port = port;
            this.landingDate = lot.getLandingDate();
            this.notes = notes;
            this.publishedAt = publishedAt;
            this.expiresAt = expiresAt;
        }

        void close(Status next) {
            this.status = next;
            this.closedAt = Instant.now();
        }

        /** AC-16.4 · Una oferta activa cuyo plazo pasó ya no está a la venta, aunque nadie la haya cerrado. */
        public boolean isOpen(Instant now) {
            return status == Status.ACTIVE && now.isBefore(expiresAt);
        }

        public String getId() {
            return id;
        }

        public LotEntity getLot() {
            return lot;
        }

        public UserEntity getSeller() {
            return seller;
        }

        public SpeciesEntity getSpecies() {
            return species;
        }

        public BigDecimal getWeightKg() {
            return weightKg;
        }

        public BigDecimal getPricePerKg() {
            return pricePerKg;
        }

        public String getPort() {
            return port;
        }

        public LocalDate getLandingDate() {
            return landingDate;
        }

        public String getNotes() {
            return notes;
        }

        public Status getStatus() {
            return status;
        }

        public Instant getPublishedAt() {
            return publishedAt;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public Instant getClosedAt() {
            return closedAt;
        }
    }

    /** AC-16.3 · Solicitud de compra de un comprador registrado. */
    @Entity(name = "MarketRequest")
    @Table(name = "market_requests")
    public static class RequestEntity {

        public enum Status { PENDING, ACCEPTED, REJECTED }

        @Id
        @Column(name = "id", length = 40, nullable = false)
        private String id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "offer_id")
        private OfferEntity offer;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "buyer_id")
        private UserEntity buyer;

        @Column(name = "quantity_kg", precision = 10, scale = 2, nullable = false)
        private BigDecimal quantityKg;

        @Column(name = "message", length = 600, nullable = false)
        private String message;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", length = 10, nullable = false)
        private Status status = Status.PENDING;

        @Column(name = "created_at", nullable = false)
        private Instant createdAt;

        @Column(name = "decided_at")
        private Instant decidedAt;

        protected RequestEntity() {
        }

        RequestEntity(String id, OfferEntity offer, UserEntity buyer, BigDecimal quantityKg, String message) {
            this.id = id;
            this.offer = offer;
            this.buyer = buyer;
            this.quantityKg = quantityKg;
            this.message = message;
            this.createdAt = Instant.now();
        }

        void decide(Status next) {
            this.status = next;
            this.decidedAt = Instant.now();
        }

        public String getId() {
            return id;
        }

        public OfferEntity getOffer() {
            return offer;
        }

        public UserEntity getBuyer() {
            return buyer;
        }

        public BigDecimal getQuantityKg() {
            return quantityKg;
        }

        public String getMessage() {
            return message;
        }

        public Status getStatus() {
            return status;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getDecidedAt() {
            return decidedAt;
        }
    }
}
