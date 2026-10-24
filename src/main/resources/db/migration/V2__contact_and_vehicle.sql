-- ============================================================================
-- V2: kisiler (contact), araclar (vehicle) ve arac hasar semasi (yol haritasi 1. adim)
-- Para kolonlari (kurus = cents, BIGINT) simdiden eklendi; 2. adima kadar bos kalabilir.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- CONTACT: musteri, arac aldigimiz kisi, ortak, kurum (Evim sirketi, banka)
-- ----------------------------------------------------------------------------
CREATE TABLE contact (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant(id),
    name        VARCHAR(120) NOT NULL,
    phone       VARCHAR(20),
    type        VARCHAR(12)  NOT NULL DEFAULT 'PERSON' CHECK (type IN ('PERSON', 'ORGANIZATION')),
    notes       TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_contact_tenant ON contact(tenant_id);

-- ----------------------------------------------------------------------------
-- VEHICLE
-- ----------------------------------------------------------------------------
CREATE TABLE vehicle (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID         NOT NULL REFERENCES tenant(id),
    plate                    VARCHAR(12)  NOT NULL,                       -- "34 ABC 123" bicimine normalize edilir
    make                     VARCHAR(40)  NOT NULL,
    model                    VARCHAR(60)  NOT NULL,
    model_year               SMALLINT     NOT NULL,
    fuel                     VARCHAR(12)  NOT NULL
                             CHECK (fuel IN ('GASOLINE', 'DIESEL', 'LPG', 'GASOLINE_LPG', 'HYBRID', 'ELECTRIC')),
    transmission             VARCHAR(14)  NOT NULL
                             CHECK (transmission IN ('MANUAL', 'AUTOMATIC', 'SEMI_AUTOMATIC')),
    mileage_km               INT          NOT NULL DEFAULT 0 CHECK (mileage_km >= 0),
    color                    VARCHAR(30),
    tramer_cents             BIGINT       CHECK (tramer_cents >= 0),      -- hasar kaydi tutari
    notes                    TEXT,
    ownership                VARCHAR(14)  NOT NULL DEFAULT 'DEALER'
                             CHECK (ownership IN ('DEALER', 'CONSIGNMENT', 'CO_OWNED')),
    status                   VARCHAR(10)  NOT NULL DEFAULT 'IN_STOCK'
                             CHECK (status IN ('IN_STOCK', 'SOLD', 'CLOSED')),

    -- alis
    purchase_date            DATE         NOT NULL,
    seller_id                UUID         REFERENCES contact(id),
    bought_by_id             UUID         REFERENCES panel_user(id),
    actual_purchase_cents    BIGINT       CHECK (actual_purchase_cents >= 0),
    official_purchase_cents  BIGINT       CHECK (official_purchase_cents >= 0),

    -- satis
    sale_date                DATE,
    buyer_id                 UUID         REFERENCES contact(id),
    sold_by_id               UUID         REFERENCES panel_user(id),
    sale_price_cents         BIGINT       CHECK (sale_price_cents >= 0),

    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CHECK (status = 'IN_STOCK' OR sale_date IS NOT NULL),
    CHECK (sale_date IS NULL OR sale_date >= purchase_date)
);
CREATE INDEX idx_vehicle_tenant        ON vehicle(tenant_id);
CREATE INDEX idx_vehicle_purchase_date ON vehicle(tenant_id, purchase_date);
CREATE INDEX idx_vehicle_sale_date     ON vehicle(tenant_id, sale_date);
-- Ayni plaka ayni galeride ayni anda iki kez stokta olamaz (satilip geri alinabilir)
CREATE UNIQUE INDEX uq_vehicle_in_stock_plate ON vehicle(tenant_id, plate) WHERE status = 'IN_STOCK';

-- ----------------------------------------------------------------------------
-- VEHICLE_PART: Sahibinden tarzi hasar semasi, 13 parca.
-- Kaydi olmayan parca ORIGINAL kabul edilir.
-- ----------------------------------------------------------------------------
CREATE TABLE vehicle_part (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenant(id),
    vehicle_id  UUID        NOT NULL REFERENCES vehicle(id) ON DELETE CASCADE,
    part        VARCHAR(24) NOT NULL CHECK (part IN (
                    'FRONT_BUMPER', 'HOOD', 'ROOF', 'TRUNK_LID', 'REAR_BUMPER',
                    'LEFT_FRONT_FENDER', 'LEFT_FRONT_DOOR', 'LEFT_REAR_DOOR', 'LEFT_REAR_FENDER',
                    'RIGHT_FRONT_FENDER', 'RIGHT_FRONT_DOOR', 'RIGHT_REAR_DOOR', 'RIGHT_REAR_FENDER')),
    condition   VARCHAR(16) NOT NULL CHECK (condition IN ('ORIGINAL', 'LOCALLY_PAINTED', 'PAINTED', 'REPLACED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (vehicle_id, part)
);
CREATE INDEX idx_vehicle_part_tenant ON vehicle_part(tenant_id);
