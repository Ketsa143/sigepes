package com.sigepes.backend.geo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sigepes.backend.users.UserEntity;

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
import jakarta.persistence.Transient;

/** RF-04 · Zona restringida (AMP o zona en veda) delimitada por un polígono. */
@Entity
@Table(name = "restricted_zones")
public class RestrictedZoneEntity {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Id
    @Column(name = "id", length = 40, nullable = false)
    private String id;

    @Column(name = "name", length = 160, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", length = 20, nullable = false)
    private ZoneType zoneType;

    @Column(name = "polygon", length = 12000, nullable = false)
    private String polygonJson;

    @Column(name = "min_lat", precision = 9, scale = 6, nullable = false)
    private BigDecimal minLat;

    @Column(name = "max_lat", precision = 9, scale = 6, nullable = false)
    private BigDecimal maxLat;

    @Column(name = "min_lon", precision = 9, scale = 6, nullable = false)
    private BigDecimal minLon;

    @Column(name = "max_lon", precision = 9, scale = 6, nullable = false)
    private BigDecimal maxLon;

    @Column(name = "resolution_number", length = 120, nullable = false)
    private String resolutionNumber;

    @Column(name = "description", length = 400)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserEntity createdBy;

    @Column(name = "demo", nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** HU-13: AMP al que pertenece la zona de manejo; nulo en zonas restringidas de RF-04. */
    @Column(name = "mpa_id", length = 40)
    private String mpaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mpa_zone_kind", length = 20)
    private MpaZoneKind mpaZoneKind;

    /** Ids de artes permitidas separados por coma (solo aplica a uso sostenible). */
    @Column(name = "allowed_gear_ids", length = 1000)
    private String allowedGearIds;

    /** Ids de especies permitidas separados por coma (solo aplica a uso sostenible). */
    @Column(name = "allowed_species_ids", length = 2000)
    private String allowedSpeciesIds;

    @Transient
    private Polygon polygon;

    protected RestrictedZoneEntity() {
    }

    public RestrictedZoneEntity(String id, String name, ZoneType zoneType, Polygon polygon, String resolutionNumber) {
        this.id = id;
        this.name = name;
        this.zoneType = zoneType;
        this.resolutionNumber = resolutionNumber;
        setPolygon(polygon);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Polygon polygon() {
        if (polygon == null) {
            try {
                polygon = new Polygon(JSON.readValue(polygonJson, new TypeReference<List<double[]>>() {
                }));
            } catch (Exception e) {
                throw new IllegalStateException("Polígono corrupto en la zona " + id, e);
            }
        }
        return polygon;
    }

    public final void setPolygon(Polygon polygon) {
        this.polygon = polygon;
        try {
            this.polygonJson = JSON.writeValueAsString(polygon.vertices());
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible serializar el polígono", e);
        }
        this.minLat = scale(polygon.minLat());
        this.maxLat = scale(polygon.maxLat());
        this.minLon = scale(polygon.minLon());
        this.maxLon = scale(polygon.maxLon());
    }

    /** AC-04.5: el prefiltro de caja envolvente ya lo hizo la consulta; aquí va la prueba exacta. */
    public boolean contains(double lat, double lon) {
        return active && polygon().contains(lat, lon);
    }

    /**
     * HU-13 · ¿Estar aquí con el arte dado es una intrusión?
     *
     * <p>Las zonas restringidas de RF-04 (sin zonificación de AMP) prohíben siempre. En un AMP la
     * zona intangible prohíbe siempre; la de uso sostenible solo si la faena declaró un arte que no
     * está en la lista de permitidas; la de amortiguación nunca.
     */
    public boolean prohibits(String gearId) {
        if (mpaZoneKind == null) {
            return true;
        }
        return switch (mpaZoneKind) {
            case INTANGIBLE -> true;
            case SUSTAINABLE_USE -> gearId != null && !allowedGearIds().isEmpty()
                    && !allowedGearIds().contains(gearId);
            case BUFFER -> false;
        };
    }

    /** HU-13 · ¿Se permite capturar la especie dentro de esta zona de manejo? */
    public boolean allowsSpecies(String speciesId) {
        if (mpaZoneKind == null) {
            return true;
        }
        return switch (mpaZoneKind) {
            case INTANGIBLE -> false;
            case SUSTAINABLE_USE -> allowedSpeciesIds().isEmpty() || allowedSpeciesIds().contains(speciesId);
            case BUFFER -> true;
        };
    }

    public List<String> allowedGearIds() {
        return split(allowedGearIds);
    }

    public void setAllowedGearIds(Collection<String> ids) {
        this.allowedGearIds = join(ids);
    }

    public List<String> allowedSpeciesIds() {
        return split(allowedSpeciesIds);
    }

    public void setAllowedSpeciesIds(Collection<String> ids) {
        this.allowedSpeciesIds = join(ids);
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    private static String join(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return String.join(",", ids.stream().map(String::trim).filter(value -> !value.isEmpty()).distinct()
                .toList());
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ZoneType getZoneType() {
        return zoneType;
    }

    public void setZoneType(ZoneType zoneType) {
        this.zoneType = zoneType;
    }

    public String getResolutionNumber() {
        return resolutionNumber;
    }

    public void setResolutionNumber(String resolutionNumber) {
        this.resolutionNumber = resolutionNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UserEntity getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UserEntity createdBy) {
        this.createdBy = createdBy;
    }

    public boolean isDemo() {
        return demo;
    }

    public void setDemo(boolean demo) {
        this.demo = demo;
    }

    public String getMpaId() {
        return mpaId;
    }

    public void setMpaId(String mpaId) {
        this.mpaId = mpaId;
    }

    public MpaZoneKind getMpaZoneKind() {
        return mpaZoneKind;
    }

    public void setMpaZoneKind(MpaZoneKind mpaZoneKind) {
        this.mpaZoneKind = mpaZoneKind;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
