package com.sigepes.backend.geo;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * HU-13 · TASK-13.1 · Área Marina Protegida.
 *
 * <p>El área agrupa sus zonas de manejo; cada zona es una {@link RestrictedZoneEntity} con
 * {@code mpaId} apuntando aquí, de modo que el control punto-en-zona de RF-04 las evalúa igual que
 * a cualquier otra zona restringida.
 */
@Entity
@Table(name = "marine_protected_areas")
public class MarineProtectedAreaEntity {

    @Id
    @Column(name = "id", length = 40, nullable = false)
    private String id;

    @Column(name = "name", length = 160, nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private MpaCategory category;

    /** Acto administrativo que declara el área (resolución, decreto o acuerdo). */
    @Column(name = "legal_act", length = 160, nullable = false)
    private String legalAct;

    @Column(name = "authority", length = 120)
    private String authority;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "demo", nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MarineProtectedAreaEntity() {
    }

    public MarineProtectedAreaEntity(String id, String name, MpaCategory category, String legalAct) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.legalAct = legalAct;
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

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public MpaCategory getCategory() {
        return category;
    }

    public void setCategory(MpaCategory category) {
        this.category = category;
    }

    public String getLegalAct() {
        return legalAct;
    }

    public void setLegalAct(String legalAct) {
        this.legalAct = legalAct;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
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

    public boolean isDemo() {
        return demo;
    }

    public void setDemo(boolean demo) {
        this.demo = demo;
    }
}
