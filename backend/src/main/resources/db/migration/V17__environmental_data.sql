-- =====================================================================
-- SIGEPES · Sprint 3 · HU-14 · Monitoreo de datos ambientales (RF-14)
--   AC-14.1: lecturas de temperatura del mar, clorofila-a y altura de ola
--            por zona de pesca, importadas cada 6 h de la fuente oficial.
--   AC-14.3: la línea base de temperatura de cada zona define la anomalía
--            que dispara la alerta para analistas.
--   AC-14.5: las lecturas guardadas son la caché que se muestra cuando la
--            fuente externa no responde (degradación controlada).
-- =====================================================================

CREATE TABLE env_zones (
    id              VARCHAR(40)   NOT NULL,
    name            VARCHAR(120)  NOT NULL,
    region          VARCHAR(20)   NOT NULL,
    latitude        NUMERIC(9, 6) NOT NULL,
    longitude       NUMERIC(9, 6) NOT NULL,
    sst_baseline_c  NUMERIC(5, 2),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_env_zones PRIMARY KEY (id),
    CONSTRAINT uq_env_zones_name UNIQUE (name),
    CONSTRAINT ck_env_zones_region CHECK (region IN ('PACIFICO', 'CARIBE', 'INSULAR'))
);

CREATE TABLE env_readings (
    id                  VARCHAR(40)   NOT NULL,
    zone_id             VARCHAR(40)   NOT NULL,
    observed_at         TIMESTAMP     NOT NULL,
    sst_c               NUMERIC(5, 2),
    chlorophyll_mg_m3   NUMERIC(8, 3),
    wave_height_m       NUMERIC(5, 2),
    source              VARCHAR(60)   NOT NULL,
    demo                BOOLEAN       NOT NULL DEFAULT FALSE,
    fetched_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_env_readings PRIMARY KEY (id),
    CONSTRAINT fk_env_readings_zone FOREIGN KEY (zone_id) REFERENCES env_zones (id),
    CONSTRAINT uq_env_readings_zone_time UNIQUE (zone_id, observed_at)
);

CREATE INDEX ix_env_readings_zone_time ON env_readings (zone_id, observed_at);

-- Zonas de pesca de referencia: un punto representativo mar adentro por zona y la temperatura
-- superficial media anual aproximada (°C) con la que se mide la anomalía.
INSERT INTO env_zones (id, name, region, latitude, longitude, sst_baseline_c) VALUES
    ('env-pac-norte',  'Pacífico Norte',  'PACIFICO', 6.500000, -77.600000, 28.60),
    ('env-pac-centro', 'Pacífico Centro', 'PACIFICO', 3.900000, -77.500000, 28.30),
    ('env-pac-sur',    'Pacífico Sur',    'PACIFICO', 2.100000, -78.900000, 27.90),
    ('env-car-norte',  'Caribe Norte',    'CARIBE',  12.000000, -72.600000, 28.00),
    ('env-car-centro', 'Caribe Centro',   'CARIBE',  10.500000, -75.900000, 28.60),
    ('env-car-sur',    'Caribe Sur',      'CARIBE',   8.900000, -76.900000, 29.00),
    ('env-san-andres', 'San Andrés',      'INSULAR', 12.550000, -81.750000, 28.40);

-- AC-14.3: las anomalías ambientales llegan por el buzón de alertas (HU-11).
ALTER TABLE alerts DROP CONSTRAINT ck_alerts_type;
ALTER TABLE alerts ADD CONSTRAINT ck_alerts_type CHECK (alert_type IN
    ('CLOSURE', 'QUOTA', 'PERMIT', 'ZONE', 'REPORT', 'SECURITY', 'DOCUMENT', 'EXPORT', 'ENVIRONMENT'));
