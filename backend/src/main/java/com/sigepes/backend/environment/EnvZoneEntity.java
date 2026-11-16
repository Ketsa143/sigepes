package com.sigepes.backend.environment;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** HU-14 · Zona de pesca con el punto donde se leen las variables ambientales. */
@Entity
@Table(name = "env_zones")
public class EnvZoneEntity {

    @Id
    @Column(name = "id", length = 40, nullable = false)
    private String id;

    /** Mismo nombre que usan las faenas en su campo «zona», para cruzar capturas (AC-14.4). */
    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "region", length = 20, nullable = false)
    private String region;

    @Column(name = "latitude", precision = 9, scale = 6, nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6, nullable = false)
    private BigDecimal longitude;

    /** Temperatura superficial media de referencia (°C); la anomalía se mide contra ella. */
    @Column(name = "sst_baseline_c", precision = 5, scale = 2)
    private BigDecimal sstBaselineC;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected EnvZoneEntity() {
    }

    public EnvZoneEntity(String id, String name, String region, BigDecimal latitude, BigDecimal longitude,
            BigDecimal sstBaselineC) {
        this.id = id;
        this.name = name;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
        this.sstBaselineC = sstBaselineC;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRegion() {
        return region;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getSstBaselineC() {
        return sstBaselineC;
    }

    public boolean isActive() {
        return active;
    }
}
