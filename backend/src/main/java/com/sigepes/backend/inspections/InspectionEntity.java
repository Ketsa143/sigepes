package com.sigepes.backend.inspections;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Immutable;

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
import jakarta.persistence.Table;

/**
 * HU-22 · Acta de inspección firmada. Es {@link Immutable}: una vez guardada, Hibernate no emite
 * ningún UPDATE sobre ella (AC-22.5) y su {@code contentHash} permite comprobar que nada cambió.
 */
@Entity
@Immutable
@Table(name = "inspections")
public class InspectionEntity {

    public enum Result { COMPLIANT, WITH_FINDINGS }

    @Id
    @Column(name = "id", length = 40, nullable = false)
    private String id;

    @Column(name = "client_id", length = 60, nullable = false)
    private String clientId;

    @Column(name = "code", length = 20, nullable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vessel_id")
    private VesselEntity vessel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private UserEntity owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspector_id")
    private UserEntity inspector;

    @Column(name = "permit_number", length = 60)
    private String permitNumber;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "location_text", length = 160, nullable = false)
    private String locationText;

    @Column(name = "inspected_at", nullable = false)
    private Instant inspectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 20, nullable = false)
    private Result result;

    @Column(name = "observations", length = 1500)
    private String observations;

    @Column(name = "responsible_name", length = 160, nullable = false)
    private String responsibleName;

    @Column(name = "responsible_id_number", length = 30)
    private String responsibleIdNumber;

    @Column(name = "inspector_signature_path", length = 300, nullable = false)
    private String inspectorSignaturePath;

    @Column(name = "responsible_signature_path", length = 300, nullable = false)
    private String responsibleSignaturePath;

    @Column(name = "content_hash", length = 64, nullable = false)
    private String contentHash;

    @Column(name = "saved_offline", nullable = false)
    private boolean savedOffline;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InspectionEntity() {
    }

    InspectionEntity(String id, String clientId, String code, VesselEntity vessel, UserEntity inspector,
            String permitNumber, BigDecimal latitude, BigDecimal longitude, String locationText, Instant inspectedAt,
            Result result, String observations, String responsibleName, String responsibleIdNumber,
            String inspectorSignaturePath, String responsibleSignaturePath, String contentHash, boolean savedOffline) {
        this.id = id;
        this.clientId = clientId;
        this.code = code;
        this.vessel = vessel;
        this.owner = vessel.getOwner();
        this.inspector = inspector;
        this.permitNumber = permitNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationText = locationText;
        this.inspectedAt = inspectedAt;
        this.result = result;
        this.observations = observations;
        this.responsibleName = responsibleName;
        this.responsibleIdNumber = responsibleIdNumber;
        this.inspectorSignaturePath = inspectorSignaturePath;
        this.responsibleSignaturePath = responsibleSignaturePath;
        this.contentHash = contentHash;
        this.savedOffline = savedOffline;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public String getCode() {
        return code;
    }

    public VesselEntity getVessel() {
        return vessel;
    }

    public UserEntity getOwner() {
        return owner;
    }

    public UserEntity getInspector() {
        return inspector;
    }

    public String getPermitNumber() {
        return permitNumber;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getLocationText() {
        return locationText;
    }

    public Instant getInspectedAt() {
        return inspectedAt;
    }

    public Result getResult() {
        return result;
    }

    public String getObservations() {
        return observations;
    }

    public String getResponsibleName() {
        return responsibleName;
    }

    public String getResponsibleIdNumber() {
        return responsibleIdNumber;
    }

    public String getInspectorSignaturePath() {
        return inspectorSignaturePath;
    }

    public String getResponsibleSignaturePath() {
        return responsibleSignaturePath;
    }

    public String getContentHash() {
        return contentHash;
    }

    public boolean isSavedOffline() {
        return savedOffline;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
