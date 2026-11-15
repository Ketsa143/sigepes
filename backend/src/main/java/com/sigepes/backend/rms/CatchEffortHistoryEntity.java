package com.sigepes.backend.rms;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * HU-17 · TASK-17.1 · Captura y esfuerzo anual de una especie anteriores a SIGEPES (por ejemplo, las
 * estadísticas pesqueras oficiales). Completa la serie de las faenas: un año que ya tiene faenas
 * cerradas en SIGEPES usa esas faenas y no este registro.
 */
@Entity
@Table(name = "catch_effort_history")
public class CatchEffortHistoryEntity {

    @Id
    @Column(name = "id", length = 40, nullable = false)
    private String id;

    @Column(name = "species_id", length = 40, nullable = false)
    private String speciesId;

    @Column(name = "series_year", nullable = false)
    private int year;

    @Column(name = "catch_kg", precision = 14, scale = 2, nullable = false)
    private BigDecimal catchKg;

    @Column(name = "effort_trips", nullable = false)
    private int effortTrips;

    @Column(name = "source", length = 160, nullable = false)
    private String source;

    @Column(name = "demo", nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CatchEffortHistoryEntity() {
    }

    public CatchEffortHistoryEntity(String id, String speciesId, int year, BigDecimal catchKg, int effortTrips,
            String source) {
        this.id = id;
        this.speciesId = speciesId;
        this.year = year;
        this.catchKg = catchKg;
        this.effortTrips = effortTrips;
        this.source = source;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getSpeciesId() {
        return speciesId;
    }

    public int getYear() {
        return year;
    }

    public BigDecimal getCatchKg() {
        return catchKg;
    }

    public int getEffortTrips() {
        return effortTrips;
    }

    public String getSource() {
        return source;
    }

    public boolean isDemo() {
        return demo;
    }

    public void setDemo(boolean demo) {
        this.demo = demo;
    }
}
