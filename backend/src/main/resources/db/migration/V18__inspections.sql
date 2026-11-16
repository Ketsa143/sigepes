-- =====================================================================
-- SIGEPES · Sprint 3 · HU-22 · Inspecciones en campo con acta digital
--   AC-22.1: el acta reúne embarcación, permiso, capturas medidas, artes,
--            fotos, posición GPS y resultado.
--   AC-22.2: los hallazgos (talla bajo la mínima, arte prohibido o no
--            registrado, permiso no vigente...) se calculan en el servidor.
--   AC-22.3: el acta llega firmada por el inspector y el responsable.
--   AC-22.4: client_id (UUID del dispositivo) hace idempotente el envío
--            desde la cola sin señal: 0 pérdidas y 0 duplicados (D5).
--   AC-22.5: content_hash (SHA-256) sella el acta; no hay operación que la
--            modifique y los intentos quedan auditados (S4).
-- =====================================================================

CREATE TABLE inspections (
    id                         VARCHAR(40)    NOT NULL,
    client_id                  VARCHAR(60)    NOT NULL,
    code                       VARCHAR(20)    NOT NULL,
    vessel_id                  VARCHAR(40)    NOT NULL,
    owner_id                   VARCHAR(40)    NOT NULL,
    inspector_id               VARCHAR(40)    NOT NULL,
    permit_number              VARCHAR(60),
    latitude                   NUMERIC(9, 6),
    longitude                  NUMERIC(9, 6),
    location_text              VARCHAR(160)   NOT NULL,
    inspected_at               TIMESTAMP      NOT NULL,
    result                     VARCHAR(20)    NOT NULL,
    observations               VARCHAR(1500),
    responsible_name           VARCHAR(160)   NOT NULL,
    responsible_id_number      VARCHAR(30),
    inspector_signature_path   VARCHAR(300)   NOT NULL,
    responsible_signature_path VARCHAR(300)   NOT NULL,
    content_hash               VARCHAR(64)    NOT NULL,
    saved_offline              BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at                 TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_inspections PRIMARY KEY (id),
    CONSTRAINT uq_inspections_client UNIQUE (client_id),
    CONSTRAINT uq_inspections_code UNIQUE (code),
    CONSTRAINT fk_inspections_vessel FOREIGN KEY (vessel_id) REFERENCES vessels (id),
    CONSTRAINT fk_inspections_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_inspections_inspector FOREIGN KEY (inspector_id) REFERENCES users (id),
    CONSTRAINT ck_inspections_result CHECK (result IN ('COMPLIANT', 'WITH_FINDINGS'))
);

CREATE INDEX ix_inspections_vessel ON inspections (vessel_id, inspected_at);
CREATE INDEX ix_inspections_owner ON inspections (owner_id, inspected_at);

-- Lo revisado a bordo: capturas medidas (especie, talla, peso) y artes encontradas.
CREATE TABLE inspection_items (
    id              VARCHAR(40)    NOT NULL,
    inspection_id   VARCHAR(40)    NOT NULL,
    position        INTEGER        NOT NULL,
    kind            VARCHAR(10)    NOT NULL,
    species_id      VARCHAR(40),
    gear_id         VARCHAR(40),
    label           VARCHAR(160)   NOT NULL,
    size_cm         NUMERIC(6, 2),
    weight_kg       NUMERIC(10, 2),
    quantity        INTEGER,
    CONSTRAINT pk_inspection_items PRIMARY KEY (id),
    CONSTRAINT fk_inspection_items_inspection FOREIGN KEY (inspection_id) REFERENCES inspections (id),
    CONSTRAINT fk_inspection_items_species FOREIGN KEY (species_id) REFERENCES species (id),
    CONSTRAINT fk_inspection_items_gear FOREIGN KEY (gear_id) REFERENCES fishing_gears (id),
    CONSTRAINT ck_inspection_items_kind CHECK (kind IN ('CATCH', 'GEAR'))
);

CREATE INDEX ix_inspection_items_inspection ON inspection_items (inspection_id, position);

-- Hallazgos tipificados; HU-23 abre el proceso sancionatorio a partir de ellos.
CREATE TABLE inspection_findings (
    id              VARCHAR(40)    NOT NULL,
    inspection_id   VARCHAR(40)    NOT NULL,
    position        INTEGER        NOT NULL,
    code            VARCHAR(30)    NOT NULL,
    description     VARCHAR(400)   NOT NULL,
    automatic       BOOLEAN        NOT NULL,
    CONSTRAINT pk_inspection_findings PRIMARY KEY (id),
    CONSTRAINT fk_inspection_findings_inspection FOREIGN KEY (inspection_id) REFERENCES inspections (id),
    CONSTRAINT ck_inspection_findings_code CHECK (code IN ('SIZE_BELOW_MIN', 'PROHIBITED_GEAR',
        'UNREGISTERED_GEAR', 'NO_VALID_PERMIT', 'VESSEL_NOT_ACTIVE', 'OTHER'))
);

CREATE INDEX ix_inspection_findings_inspection ON inspection_findings (inspection_id, position);

CREATE TABLE inspection_photos (
    id              VARCHAR(40)    NOT NULL,
    inspection_id   VARCHAR(40)    NOT NULL,
    position        INTEGER        NOT NULL,
    storage_path    VARCHAR(300)   NOT NULL,
    content_type    VARCHAR(40)    NOT NULL,
    size_bytes      BIGINT         NOT NULL,
    sha256          VARCHAR(64)    NOT NULL,
    CONSTRAINT pk_inspection_photos PRIMARY KEY (id),
    CONSTRAINT fk_inspection_photos_inspection FOREIGN KEY (inspection_id) REFERENCES inspections (id)
);

-- El responsable de la embarcación recibe el acta en su buzón (HU-11).
ALTER TABLE alerts DROP CONSTRAINT ck_alerts_type;
ALTER TABLE alerts ADD CONSTRAINT ck_alerts_type CHECK (alert_type IN
    ('CLOSURE', 'QUOTA', 'PERMIT', 'ZONE', 'REPORT', 'SECURITY', 'DOCUMENT', 'EXPORT', 'ENVIRONMENT',
     'INSPECTION'));
