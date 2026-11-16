-- =====================================================================
-- SIGEPES · Sprint 3 · HU-23 · Procesos sancionatorios (Ley 1851 de 2017)
--   AC-23.1: el proceso nace de un acta con hallazgos (HU-22) o de una
--            denuncia fundada (HU-10), con la infracción tipificada.
--   AC-23.2: etapas Apertura → Descargos → Pruebas → Decisión → Recurso →
--            Cerrado, con plazos en días hábiles.
--   AC-23.4: la decisión fija la sanción (amonestación, multa en SMMLV,
--            decomiso, suspensión del permiso) o el archivo.
--   AC-23.5: cada actuación queda en sanction_actions y en la auditoría
--            con el estado anterior y el nuevo.
-- =====================================================================

CREATE TABLE sanction_processes (
    id                 VARCHAR(40)    NOT NULL,
    number             VARCHAR(20)    NOT NULL,
    origin             VARCHAR(12)    NOT NULL,
    inspection_id      VARCHAR(40),
    report_id          VARCHAR(40),
    vessel_id          VARCHAR(40)    NOT NULL,
    investigated_id    VARCHAR(40)    NOT NULL,
    infraction         VARCHAR(30)    NOT NULL,
    facts              VARCHAR(1500)  NOT NULL,
    stage              VARCHAR(12)    NOT NULL,
    stage_deadline     DATE,
    defense_submitted  BOOLEAN        NOT NULL DEFAULT FALSE,
    appeal_submitted   BOOLEAN        NOT NULL DEFAULT FALSE,
    sanction           VARCHAR(20),
    fine_smmlv         NUMERIC(8, 2),
    decision_text      VARCHAR(1500),
    opened_by          VARCHAR(40)    NOT NULL,
    opened_at          TIMESTAMP      NOT NULL,
    closed_at          TIMESTAMP,
    updated_at         TIMESTAMP      NOT NULL,
    CONSTRAINT pk_sanction_processes PRIMARY KEY (id),
    CONSTRAINT uq_sanction_processes_number UNIQUE (number),
    CONSTRAINT uq_sanction_processes_inspection UNIQUE (inspection_id),
    CONSTRAINT fk_sanction_processes_inspection FOREIGN KEY (inspection_id) REFERENCES inspections (id),
    CONSTRAINT fk_sanction_processes_report FOREIGN KEY (report_id) REFERENCES illegal_reports (id),
    CONSTRAINT fk_sanction_processes_vessel FOREIGN KEY (vessel_id) REFERENCES vessels (id),
    CONSTRAINT fk_sanction_processes_investigated FOREIGN KEY (investigated_id) REFERENCES users (id),
    CONSTRAINT fk_sanction_processes_opened_by FOREIGN KEY (opened_by) REFERENCES users (id),
    CONSTRAINT ck_sanction_processes_origin CHECK (origin IN ('INSPECTION', 'REPORT')),
    CONSTRAINT ck_sanction_processes_origin_ref CHECK (
        (origin = 'INSPECTION' AND inspection_id IS NOT NULL) OR (origin = 'REPORT' AND report_id IS NOT NULL)),
    CONSTRAINT ck_sanction_processes_stage CHECK (stage IN
        ('OPENED', 'DEFENSE', 'EVIDENCE', 'DECISION', 'APPEAL', 'CLOSED')),
    CONSTRAINT ck_sanction_processes_sanction CHECK (sanction IS NULL OR sanction IN
        ('WARNING', 'FINE', 'CONFISCATION', 'PERMIT_SUSPENSION', 'DISMISSED'))
);

CREATE INDEX ix_sanction_processes_investigated ON sanction_processes (investigated_id, opened_at);
CREATE INDEX ix_sanction_processes_stage ON sanction_processes (stage, stage_deadline);

CREATE TABLE sanction_actions (
    id           VARCHAR(40)    NOT NULL,
    process_id   VARCHAR(40)    NOT NULL,
    sequence_no  INTEGER        NOT NULL,
    kind         VARCHAR(20)    NOT NULL,
    stage_from   VARCHAR(12),
    stage_to     VARCHAR(12)    NOT NULL,
    note         VARCHAR(1500),
    actor_id     VARCHAR(40)    NOT NULL,
    occurred_at  TIMESTAMP      NOT NULL,
    CONSTRAINT pk_sanction_actions PRIMARY KEY (id),
    CONSTRAINT uq_sanction_actions_sequence UNIQUE (process_id, sequence_no),
    CONSTRAINT fk_sanction_actions_process FOREIGN KEY (process_id) REFERENCES sanction_processes (id),
    CONSTRAINT fk_sanction_actions_actor FOREIGN KEY (actor_id) REFERENCES users (id),
    CONSTRAINT ck_sanction_actions_kind CHECK (kind IN
        ('OPENED', 'ADVANCED', 'DEFENSE_FILED', 'DECIDED', 'APPEAL_FILED', 'CLOSED'))
);

-- AC-23.3: cada cambio de etapa se notifica al investigado (HU-11).
ALTER TABLE alerts DROP CONSTRAINT ck_alerts_type;
ALTER TABLE alerts ADD CONSTRAINT ck_alerts_type CHECK (alert_type IN
    ('CLOSURE', 'QUOTA', 'PERMIT', 'ZONE', 'REPORT', 'SECURITY', 'DOCUMENT', 'EXPORT', 'ENVIRONMENT',
     'INSPECTION', 'SANCTION'));
