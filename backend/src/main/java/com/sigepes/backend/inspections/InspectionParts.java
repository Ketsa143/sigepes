package com.sigepes.backend.inspections;

import java.math.BigDecimal;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** HU-22 · Partes del acta: lo revisado a bordo, los hallazgos y las fotos. Inmutables como el acta. */
public final class InspectionParts {

    private InspectionParts() {
    }

    /** AC-22.1 · Captura medida o arte encontrado a bordo. */
    @Entity(name = "InspectionItem")
    @Immutable
    @Table(name = "inspection_items")
    public static class ItemEntity {

        public enum Kind { CATCH, GEAR }

        @Id
        @Column(name = "id", length = 40, nullable = false)
        private String id;

        @Column(name = "inspection_id", length = 40, nullable = false)
        private String inspectionId;

        @Column(name = "position", nullable = false)
        private int position;

        @Enumerated(EnumType.STRING)
        @Column(name = "kind", length = 10, nullable = false)
        private Kind kind;

        @Column(name = "species_id", length = 40)
        private String speciesId;

        @Column(name = "gear_id", length = 40)
        private String gearId;

        @Column(name = "label", length = 160, nullable = false)
        private String label;

        @Column(name = "size_cm", precision = 6, scale = 2)
        private BigDecimal sizeCm;

        @Column(name = "weight_kg", precision = 10, scale = 2)
        private BigDecimal weightKg;

        @Column(name = "quantity")
        private Integer quantity;

        protected ItemEntity() {
        }

        ItemEntity(String id, String inspectionId, int position, Kind kind, String speciesId, String gearId,
                String label, BigDecimal sizeCm, BigDecimal weightKg, Integer quantity) {
            this.id = id;
            this.inspectionId = inspectionId;
            this.position = position;
            this.kind = kind;
            this.speciesId = speciesId;
            this.gearId = gearId;
            this.label = label;
            this.sizeCm = sizeCm;
            this.weightKg = weightKg;
            this.quantity = quantity;
        }

        public String getId() {
            return id;
        }

        public int getPosition() {
            return position;
        }

        public Kind getKind() {
            return kind;
        }

        public String getSpeciesId() {
            return speciesId;
        }

        public String getGearId() {
            return gearId;
        }

        public String getLabel() {
            return label;
        }

        public BigDecimal getSizeCm() {
            return sizeCm;
        }

        public BigDecimal getWeightKg() {
            return weightKg;
        }

        public Integer getQuantity() {
            return quantity;
        }
    }

    /** AC-22.2 · Hallazgo tipificado (automático o anotado por el inspector). */
    @Entity(name = "InspectionFinding")
    @Immutable
    @Table(name = "inspection_findings")
    public static class FindingEntity {

        @Id
        @Column(name = "id", length = 40, nullable = false)
        private String id;

        @Column(name = "inspection_id", length = 40, nullable = false)
        private String inspectionId;

        @Column(name = "position", nullable = false)
        private int position;

        @Enumerated(EnumType.STRING)
        @Column(name = "code", length = 30, nullable = false)
        private FindingCode code;

        @Column(name = "description", length = 400, nullable = false)
        private String description;

        @Column(name = "automatic", nullable = false)
        private boolean automatic;

        protected FindingEntity() {
        }

        FindingEntity(String id, String inspectionId, int position, FindingCode code, String description,
                boolean automatic) {
            this.id = id;
            this.inspectionId = inspectionId;
            this.position = position;
            this.code = code;
            this.description = description;
            this.automatic = automatic;
        }

        public String getId() {
            return id;
        }

        public String getInspectionId() {
            return inspectionId;
        }

        public int getPosition() {
            return position;
        }

        public FindingCode getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        public boolean isAutomatic() {
            return automatic;
        }
    }

    @Entity(name = "InspectionPhoto")
    @Immutable
    @Table(name = "inspection_photos")
    public static class PhotoEntity {

        @Id
        @Column(name = "id", length = 40, nullable = false)
        private String id;

        @Column(name = "inspection_id", length = 40, nullable = false)
        private String inspectionId;

        @Column(name = "position", nullable = false)
        private int position;

        @Column(name = "storage_path", length = 300, nullable = false)
        private String storagePath;

        @Column(name = "content_type", length = 40, nullable = false)
        private String contentType;

        @Column(name = "size_bytes", nullable = false)
        private long sizeBytes;

        @Column(name = "sha256", length = 64, nullable = false)
        private String sha256;

        protected PhotoEntity() {
        }

        PhotoEntity(String id, String inspectionId, int position, String storagePath, String contentType,
                long sizeBytes, String sha256) {
            this.id = id;
            this.inspectionId = inspectionId;
            this.position = position;
            this.storagePath = storagePath;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
            this.sha256 = sha256;
        }

        public String getId() {
            return id;
        }

        public int getPosition() {
            return position;
        }

        public String getStoragePath() {
            return storagePath;
        }

        public String getContentType() {
            return contentType;
        }

        public String getSha256() {
            return sha256;
        }
    }
}
