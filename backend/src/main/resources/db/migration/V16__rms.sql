-- =====================================================================
-- SIGEPES · Sprint 3 · HU-17 · Rendimiento Máximo Sostenible (RMS)
--   TASK-17.1: la serie captura/esfuerzo sale de las faenas cerradas; los
--              años anteriores a SIGEPES vienen de estadísticas históricas.
--   TASK-17.2: el ajuste de Schaefer corre en segundo plano (rms_runs) y
--              deja el resultado precalculado por especie (rms_estimates),
--              que es lo que leen las consultas (R5: < 300 ms).
--   AC-17.3:   la propuesta de cuota (≤ RMS) espera aprobación del ADMIN
--              y, al aprobarse, crea la cuota de HU-07.
-- =====================================================================

CREATE TABLE catch_effort_history (
    id            VARCHAR(40)    NOT NULL,
    species_id    VARCHAR(40)    NOT NULL,
    series_year   INTEGER        NOT NULL,
    catch_kg      NUMERIC(14, 2) NOT NULL,
    effort_trips  INTEGER        NOT NULL,
    source        VARCHAR(160)   NOT NULL,
    demo          BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_catch_effort_history PRIMARY KEY (id),
    CONSTRAINT fk_catch_effort_history_species FOREIGN KEY (species_id) REFERENCES species (id),
    CONSTRAINT uq_catch_effort_history UNIQUE (species_id, series_year),
    CONSTRAINT ck_catch_effort_history_values CHECK (catch_kg >= 0 AND effort_trips >= 0)
);

CREATE TABLE rms_runs (
    id             VARCHAR(40)   NOT NULL,
    requested_by   VARCHAR(40),
    status         VARCHAR(10)   NOT NULL,
    species_count  INTEGER,
    duration_ms    INTEGER,
    error          VARCHAR(300),
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at    TIMESTAMP,
    CONSTRAINT pk_rms_runs PRIMARY KEY (id),
    CONSTRAINT fk_rms_runs_user FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT ck_rms_runs_status CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED'))
);

CREATE TABLE rms_estimates (
    species_id     VARCHAR(40)    NOT NULL,
    run_id         VARCHAR(40)    NOT NULL,
    years_count    INTEGER        NOT NULL,
    first_year     INTEGER        NOT NULL,
    last_year      INTEGER        NOT NULL,
    fitted         BOOLEAN        NOT NULL,
    indicative     BOOLEAN        NOT NULL,
    r              NUMERIC(12, 6),
    k_kg           NUMERIC(18, 2),
    q              NUMERIC(24, 16),
    msy_kg         NUMERIC(16, 2),
    f_msy          NUMERIC(16, 2),
    biomass_ratio  NUMERIC(10, 4),
    fishing_ratio  NUMERIC(10, 4),
    r2             NUMERIC(6, 4),
    stock_status   VARCHAR(20),
    series_json    VARCHAR(4000)  NOT NULL,
    note           VARCHAR(300),
    computed_at    TIMESTAMP      NOT NULL,
    CONSTRAINT pk_rms_estimates PRIMARY KEY (species_id),
    CONSTRAINT fk_rms_estimates_species FOREIGN KEY (species_id) REFERENCES species (id),
    CONSTRAINT fk_rms_estimates_run FOREIGN KEY (run_id) REFERENCES rms_runs (id),
    CONSTRAINT ck_rms_estimates_status CHECK (stock_status IS NULL
        OR stock_status IN ('UNDER_EXPLOITED', 'FULLY_EXPLOITED', 'OVER_EXPLOITED'))
);

CREATE TABLE rms_quota_proposals (
    id              VARCHAR(40)    NOT NULL,
    species_id      VARCHAR(40)    NOT NULL,
    proposed_kg     NUMERIC(14, 2) NOT NULL,
    msy_kg          NUMERIC(16, 2) NOT NULL,
    indicative      BOOLEAN        NOT NULL,
    zone            VARCHAR(120)   NOT NULL,
    period_from     DATE           NOT NULL,
    period_to       DATE           NOT NULL,
    justification   VARCHAR(600),
    status          VARCHAR(10)    NOT NULL,
    proposed_by     VARCHAR(40)    NOT NULL,
    decided_by      VARCHAR(40),
    decision_note   VARCHAR(400),
    quota_id        VARCHAR(40),
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at      TIMESTAMP,
    CONSTRAINT pk_rms_quota_proposals PRIMARY KEY (id),
    CONSTRAINT fk_rms_proposals_species FOREIGN KEY (species_id) REFERENCES species (id),
    CONSTRAINT fk_rms_proposals_proposer FOREIGN KEY (proposed_by) REFERENCES users (id),
    CONSTRAINT fk_rms_proposals_decider FOREIGN KEY (decided_by) REFERENCES users (id),
    CONSTRAINT fk_rms_proposals_quota FOREIGN KEY (quota_id) REFERENCES quotas (id),
    CONSTRAINT ck_rms_proposals_status CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_rms_proposals_kg CHECK (proposed_kg > 0 AND proposed_kg <= msy_kg)
);

CREATE INDEX ix_rms_proposals_status ON rms_quota_proposals (status, created_at);
