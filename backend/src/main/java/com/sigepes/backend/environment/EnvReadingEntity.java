package com.sigepes.backend.environment;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** HU-14 · AC-14.1 · Lectura ambiental de una zona en un instante (cualquier variable puede faltar). */
@Entity
@Table(name = "env_readings")
public class EnvReadingEntity {

    @Id
    @Column(name = "id", length = 40, nullable = false)
    private String id;

    @Column(name = "zone_id", length = 40, nullable = false)
    private String zoneId;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "sst_c", precision = 5, scale = 2)
    private BigDecimal sstC;

    @Column(name = "chlorophyll_mg_m3", precision = 8, scale = 3)
    private BigDecimal chlorophyllMgM3;

    @Column(name = "wave_height_m", precision = 5, scale = 2)
    private BigDecimal waveHeightM;

    @Column(name = "source", length = 60, nullable = false)
    private String source;

    @Column(name = "demo", nullable = false)
    private boolean demo;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected EnvReadingEntity() {
    }

    public EnvReadingEntity(String id, String zoneId, Instant observedAt, BigDecimal sstC, BigDecimal chlorophyllMgM3,
            BigDecimal waveHeightM, String source) {
        this.id = id;
        this.zoneId = zoneId;
        this.observedAt = observedAt;
        this.sstC = sstC;
        this.chlorophyllMgM3 = chlorophyllMgM3;
        this.waveHeightM = waveHeightM;
        this.source = source;
    }

    @PrePersist
    void onCreate() {
        if (fetchedAt == null) {
            fetchedAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public String getZoneId() {
        return zoneId;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public BigDecimal getSstC() {
        return sstC;
    }

    public BigDecimal getChlorophyllMgM3() {
        return chlorophyllMgM3;
    }

    public BigDecimal getWaveHeightM() {
        return waveHeightM;
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

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
