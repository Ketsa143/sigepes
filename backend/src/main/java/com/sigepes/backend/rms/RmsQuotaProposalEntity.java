package com.sigepes.backend.rms;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * HU-17 · AC-17.3 · Propuesta de cuota basada en el RMS. Guarda el RMS y la calidad del cálculo
 * del momento en que se propuso, para que la decisión del ADMIN quede trazable aunque el RMS se
 * recalcule después.
 */
@Entity
@Table(name = "rms_quota_proposals")
public class RmsQuotaProposalEntity {

    public enum Status { PROPOSED, APPROVED, REJECTED }

    @Id
    @Column(name = "id", length = 40, nullable = false)
    private String id;

    @Column(name = "species_id", length = 40, nullable = false)
    private String speciesId;

    @Column(name = "proposed_kg", precision = 14, scale = 2, nullable = false)
    private BigDecimal proposedKg;

    @Column(name = "msy_kg", precision = 16, scale = 2, nullable = false)
    private BigDecimal msyKg;

    @Column(name = "indicative", nullable = false)
    private boolean indicative;

    @Column(name = "zone", length = 120, nullable = false)
    private String zone;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "justification", length = 600)
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private Status status = Status.PROPOSED;

    @Column(name = "proposed_by", length = 40, nullable = false)
    private String proposedBy;

    @Column(name = "decided_by", length = 40)
    private String decidedBy;

    @Column(name = "decision_note", length = 400)
    private String decisionNote;

    @Column(name = "quota_id", length = 40)
    private String quotaId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected RmsQuotaProposalEntity() {
    }

    public RmsQuotaProposalEntity(String id, String speciesId, BigDecimal proposedKg, BigDecimal msyKg,
            boolean indicative, String zone, LocalDate periodFrom, LocalDate periodTo, String justification,
            String proposedBy) {
        this.id = id;
        this.speciesId = speciesId;
        this.proposedKg = proposedKg;
        this.msyKg = msyKg;
        this.indicative = indicative;
        this.zone = zone;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.justification = justification;
        this.proposedBy = proposedBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void approve(String adminId, String note, String quotaId) {
        this.status = Status.APPROVED;
        this.decidedBy = adminId;
        this.decisionNote = note;
        this.quotaId = quotaId;
        this.decidedAt = Instant.now();
    }

    public void reject(String adminId, String note) {
        this.status = Status.REJECTED;
        this.decidedBy = adminId;
        this.decisionNote = note;
        this.decidedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getSpeciesId() {
        return speciesId;
    }

    public BigDecimal getProposedKg() {
        return proposedKg;
    }

    public BigDecimal getMsyKg() {
        return msyKg;
    }

    public boolean isIndicative() {
        return indicative;
    }

    public String getZone() {
        return zone;
    }

    public LocalDate getPeriodFrom() {
        return periodFrom;
    }

    public LocalDate getPeriodTo() {
        return periodTo;
    }

    public String getJustification() {
        return justification;
    }

    public Status getStatus() {
        return status;
    }

    public String getProposedBy() {
        return proposedBy;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public String getQuotaId() {
        return quotaId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
