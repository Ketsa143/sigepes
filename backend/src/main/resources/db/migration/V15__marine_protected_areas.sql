-- =====================================================================
-- SIGEPES · Sprint 3 · HU-13 · Áreas Marinas Protegidas (AMP)
--   TASK-13.1: el AMP (nombre, categoría, acto administrativo) agrupa sus
--              zonas de manejo: intangible, uso sostenible y amortiguación.
--   Cada zona de manejo es una fila de restricted_zones (RF-04) con mpa_id,
--   así reutiliza el prefiltro por caja envolvente y los eventos de control.
--   Las artes y especies permitidas se guardan como ids separados por coma
--   (lista corta y solo de lectura en el control punto-en-zona).
-- =====================================================================

CREATE TABLE marine_protected_areas (
    id            VARCHAR(40)   NOT NULL,
    name          VARCHAR(160)  NOT NULL,
    category      VARCHAR(30)   NOT NULL,
    legal_act     VARCHAR(160)  NOT NULL,
    authority     VARCHAR(120),
    description   VARCHAR(1000),
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    demo          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_marine_protected_areas PRIMARY KEY (id),
    CONSTRAINT uq_marine_protected_areas_name UNIQUE (name),
    CONSTRAINT ck_marine_protected_areas_category
        CHECK (category IN ('NATIONAL_PARK', 'DMI', 'SANCTUARY', 'OTHER'))
);

ALTER TABLE restricted_zones ADD COLUMN mpa_id VARCHAR(40);
ALTER TABLE restricted_zones ADD COLUMN mpa_zone_kind VARCHAR(20);
ALTER TABLE restricted_zones ADD COLUMN allowed_gear_ids VARCHAR(1000);
ALTER TABLE restricted_zones ADD COLUMN allowed_species_ids VARCHAR(2000);

ALTER TABLE restricted_zones
    ADD CONSTRAINT fk_restricted_zones_mpa FOREIGN KEY (mpa_id) REFERENCES marine_protected_areas (id);
ALTER TABLE restricted_zones
    ADD CONSTRAINT ck_restricted_zones_mpa_kind
        CHECK (mpa_zone_kind IS NULL OR mpa_zone_kind IN ('INTANGIBLE', 'SUSTAINABLE_USE', 'BUFFER'));

CREATE INDEX ix_restricted_zones_mpa ON restricted_zones (mpa_id);
