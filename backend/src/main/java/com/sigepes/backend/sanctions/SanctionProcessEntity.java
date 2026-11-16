package com.sigepes.backend.sanctions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.sigepes.backend.sanctions.SanctionTypes.Infraction;
import com.sigepes.backend.sanctions.SanctionTypes.Sanction;
import com.sigepes.backend.sanctions.SanctionTypes.Stage;
import com.sigepes.backend.users.UserEntity;
import com.sigepes.backend.vessels.VesselEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** HU-23 · Proceso sancionatorio contra el armador de una embarcación. */
@Entity
@Table(name = "sanction_processes")
public class SanctionProcessEntity {

    public enum Origin { INSPECTION, REPORT }

    @Id
    @Column(name = "id", length = 40, nullable = false)
    private String id;

    @Column(name = "number", length = 20, nullable = false)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", length = 12, nullable = false)
    private Origin origin;

    @Column(name = "inspection_id", length = 40)
    private String inspectionId;

    @Column(name = "report_id", length = 40)
    private String reportId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vessel_id")
    private VesselEntity vessel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investigated_id")
    private UserEntity investigated;

    @Enumerated(EnumType.STRING)
    @Column(name = "infraction", length = 30, nullable = false)
    private Infraction infraction;

    @Column(name = "facts", length = 1500, nullable = false)
    private String facts;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", length = 12, nullable = false)
    private Stage stage = Stage.OPENED;

    @Column(name = "stage_deadline")
    private LocalDate stageDeadline;

    @Column(name = "defense_submitted", nullable = false)
    private boolean defenseSubmitted;

    @Column(name = "appeal_submitted", nullable = false)
    private boolean appealSubmitted;

    @Enumerated(EnumType.STRING)
    @Column(name = "sanction", length = 20)
    private Sanction sanction;

    @Column(name = "fine_smmlv", precision = 8, scale = 2)
    private BigDecimal fineSmmlv;

    @Column(name = "decision_text", length = 1500)
    private String decisionText;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by")
    private UserEntity openedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SanctionProcessEntity() {
    }

    SanctionProcessEntity(String id, String number, Origin origin, String inspectionId, String reportId,
            VesselEntity vessel, Infraction infraction, String facts, UserEntity openedBy, LocalDate deadline) {
        this.id = id;
        this.number = number;
        this.origin = origin;
        this.inspectionId = inspectionId;
        this.reportId = reportId;
        this.vessel = vessel;
        this.investigated = vessel.getOwner();
        this.infraction = infraction;
        this.facts = facts;
        this.openedBy = openedBy;
        this.stageDeadline = deadline;
    }

    @PrePersist
    void onCreate() {
        openedAt = Instant.now();
        updatedAt = openedAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    void moveTo(Stage next, LocalDate deadline) {
        this.stage = next;
        this.stageDeadline = deadline;
        if (next == Stage.CLOSED) {
            this.closedAt = Instant.now();
        }
    }

    void markDefense() {
        this.defenseSubmitted = true;
    }

    void markAppeal() {
        this.appealSubmitted = true;
    }

    void decide(Sanction sanction, BigDecimal fineSmmlv, String decisionText) {
        this.sanction = sanction;
        this.fineSmmlv = fineSmmlv;
        this.decisionText = decisionText;
    }

    /** Estado legible para la auditoría con antes/después (AC-23.5). */
    String describe() {
        return "etapa=" + stage + ", plazo=" + stageDeadline + ", descargos=" + defenseSubmitted + ", recurso="
                + appealSubmitted + ", sanción=" + sanction + (fineSmmlv == null ? "" : " (" + fineSmmlv + " SMMLV)");
    }

    public String getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public Origin getOrigin() {
        return origin;
    }

    public String getInspectionId() {
        return inspectionId;
    }

    public String getReportId() {
        return reportId;
    }

    public VesselEntity getVessel() {
        return vessel;
    }

    public UserEntity getInvestigated() {
        return investigated;
    }

    public Infraction getInfraction() {
        return infraction;
    }

    public String getFacts() {
        return facts;
    }

    public Stage getStage() {
        return stage;
    }

    public LocalDate getStageDeadline() {
        return stageDeadline;
    }

    public boolean isDefenseSubmitted() {
        return defenseSubmitted;
    }

    public boolean isAppealSubmitted() {
        return appealSubmitted;
    }

    public Sanction getSanction() {
        return sanction;
    }

    public BigDecimal getFineSmmlv() {
        return fineSmmlv;
    }

    public String getDecisionText() {
        return decisionText;
    }

    public UserEntity getOpenedBy() {
        return openedBy;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
