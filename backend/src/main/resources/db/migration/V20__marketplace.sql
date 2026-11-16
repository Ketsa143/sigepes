-- =====================================================================
-- SIGEPES · Sprint 3 · HU-16 · Marketplace de producto pesquero legal
--   AC-16.1: solo se oferta un lote trazable (HU-09) que sigue en manos del
--            pescador; la oferta copia especie, peso, puerto y desembarco.
--   AC-16.3: el comprador envía una solicitud; al aceptarla se registra la
--            entrega en la cadena de custodia del lote.
--   AC-16.4: la oferta vence a las 72 h o al venderse el lote.
--   AC-16.5: estas tablas no guardan datos personales del comprador ni del
--            vendedor más allá de su id; la API pública solo da alias y
--            municipio (Ley 1581 de 2012).
-- =====================================================================

CREATE TABLE market_offers (
    id              VARCHAR(40)    NOT NULL,
    lot_id          VARCHAR(40)    NOT NULL,
    seller_id       VARCHAR(40)    NOT NULL,
    species_id      VARCHAR(40)    NOT NULL,
    weight_kg       NUMERIC(10, 2) NOT NULL,
    price_per_kg    NUMERIC(12, 2) NOT NULL,
    port            VARCHAR(120)   NOT NULL,
    landing_date    DATE           NOT NULL,
    notes           VARCHAR(400),
    status          VARCHAR(10)    NOT NULL,
    published_at    TIMESTAMP      NOT NULL,
    expires_at      TIMESTAMP      NOT NULL,
    closed_at       TIMESTAMP,
    CONSTRAINT pk_market_offers PRIMARY KEY (id),
    CONSTRAINT fk_market_offers_lot FOREIGN KEY (lot_id) REFERENCES lots (id),
    CONSTRAINT fk_market_offers_seller FOREIGN KEY (seller_id) REFERENCES users (id),
    CONSTRAINT fk_market_offers_species FOREIGN KEY (species_id) REFERENCES species (id),
    CONSTRAINT ck_market_offers_status CHECK (status IN ('ACTIVE', 'SOLD', 'EXPIRED', 'WITHDRAWN')),
    CONSTRAINT ck_market_offers_values CHECK (weight_kg > 0 AND price_per_kg > 0)
);

CREATE INDEX ix_market_offers_catalog ON market_offers (status, expires_at, species_id);
CREATE INDEX ix_market_offers_lot ON market_offers (lot_id, status);

CREATE TABLE market_requests (
    id            VARCHAR(40)    NOT NULL,
    offer_id      VARCHAR(40)    NOT NULL,
    buyer_id      VARCHAR(40)    NOT NULL,
    quantity_kg   NUMERIC(10, 2) NOT NULL,
    message       VARCHAR(600)   NOT NULL,
    status        VARCHAR(10)    NOT NULL,
    created_at    TIMESTAMP      NOT NULL,
    decided_at    TIMESTAMP,
    CONSTRAINT pk_market_requests PRIMARY KEY (id),
    CONSTRAINT fk_market_requests_offer FOREIGN KEY (offer_id) REFERENCES market_offers (id),
    CONSTRAINT fk_market_requests_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT ck_market_requests_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_market_requests_quantity CHECK (quantity_kg > 0)
);

CREATE INDEX ix_market_requests_offer ON market_requests (offer_id, status);
CREATE INDEX ix_market_requests_buyer ON market_requests (buyer_id, created_at);

-- AC-16.3: el vendedor recibe las solicitudes y el comprador la respuesta (HU-11).
ALTER TABLE alerts DROP CONSTRAINT ck_alerts_type;
ALTER TABLE alerts ADD CONSTRAINT ck_alerts_type CHECK (alert_type IN
    ('CLOSURE', 'QUOTA', 'PERMIT', 'ZONE', 'REPORT', 'SECURITY', 'DOCUMENT', 'EXPORT', 'ENVIRONMENT',
     'INSPECTION', 'SANCTION', 'MARKET'));
