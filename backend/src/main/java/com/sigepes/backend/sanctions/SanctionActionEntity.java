package com.sigepes.backend.sanctions;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

import com.sigepes.backend.sanctions.SanctionTypes.ActionKind;
import com.sigepes.backend.sanctions.SanctionTypes.Stage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** HU-23 · AC-23.5 · Actuación del expediente: queda tal como se registró. */
@Entity
@Immutable
@Table(name = "sanction_actions")
public class SanctionActionEntity {

    @Id
    @Column(name = "id", length = 40, nullable = false)
    private String id;

    @Column(name = "process_id", length = 40, nullable = false)
    private String processId;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20, nullable = false)
    private ActionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_from", length = 12)
    private Stage stageFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_to", length = 12, nullable = false)
    private Stage stageTo;

    @Column(name = "note", length = 1500)
    private String note;

    @Column(name = "actor_id", length = 40, nullable = false)
    private String actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected SanctionActionEntity() {
    }

    SanctionActionEntity(String id, String processId, int sequenceNo, ActionKind kind, Stage stageFrom, Stage stageTo,
            String note, String actorId) {
        this.id = id;
        this.processId = processId;
        this.sequenceNo = sequenceNo;
        this.kind = kind;
        this.stageFrom = stageFrom;
        this.stageTo = stageTo;
        this.note = note;
        this.actorId = actorId;
        this.occurredAt = Instant.now();
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public ActionKind getKind() {
        return kind;
    }

    public Stage getStageFrom() {
        return stageFrom;
    }

    public Stage getStageTo() {
        return stageTo;
    }

    public String getNote() {
        return note;
    }

    public String getActorId() {
        return actorId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
