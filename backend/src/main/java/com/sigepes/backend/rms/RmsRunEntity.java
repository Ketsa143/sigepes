package com.sigepes.backend.rms;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** HU-17 · TASK-17.2 · Cálculo de RMS en segundo plano y su estado. */
@Entity
@Table(name = "rms_runs")
public class RmsRunEntity {

    public enum Status { PENDING, RUNNING, DONE, FAILED }

    @Id
    @Column(name = "id", length = 40, nullable = false)
    private String id;

    @Column(name = "requested_by", length = 40)
    private String requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "species_count")
    private Integer speciesCount;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "error", length = 300)
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected RmsRunEntity() {
    }

    public RmsRunEntity(String id, String requestedBy) {
        this.id = id;
        this.requestedBy = requestedBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void start() {
        status = Status.RUNNING;
    }

    public void finish(int speciesCount, long durationMs) {
        this.status = Status.DONE;
        this.speciesCount = speciesCount;
        this.durationMs = (int) Math.min(Integer.MAX_VALUE, durationMs);
        this.finishedAt = Instant.now();
    }

    public void fail(String message) {
        this.status = Status.FAILED;
        this.error = message == null ? "Error desconocido" : message.substring(0, Math.min(message.length(), 300));
        this.finishedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public Status getStatus() {
        return status;
    }

    public Integer getSpeciesCount() {
        return speciesCount;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
