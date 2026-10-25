-- ============================================================================
-- V7: konsinye ve ozel ortakli arac (MIMARI §4 Sahiplik, §3 konsinye ornegi)
-- - vehicle: konsinye sahibi ve komisyon (oran baz puan ya da sabit kurus). Konsinye degilse hepsi bos.
-- - vehicle_share: CO_OWNED aracta kisi + pay (baz puan, toplam 10000; toplam ve sahiplik servis katmaninda dogrulanir).
-- - ledger_entry.type'a CONSIGNMENT_SETTLEMENT: konsinye satista BALANCE(sahibi) -> INCOME (satis - komisyon),
--   galeri sahibine borclanir.
-- Mevcut araclarin hepsi DEALER; yeni kisitlar mevcut satirlarla uyumlu.
-- ============================================================================

ALTER TABLE vehicle
    ADD COLUMN consignor_id       UUID REFERENCES contact(id),
    ADD COLUMN commission_type    VARCHAR(5) CHECK (commission_type IN ('RATE', 'FIXED')),
    ADD COLUMN commission_rate_bp INTEGER    CHECK (commission_rate_bp BETWEEN 0 AND 10000),
    ADD COLUMN commission_cents   BIGINT     CHECK (commission_cents >= 0);

ALTER TABLE vehicle ADD CONSTRAINT vehicle_consignment_check CHECK (
    (ownership = 'CONSIGNMENT'
        AND consignor_id IS NOT NULL
        AND ((commission_type = 'RATE' AND commission_rate_bp IS NOT NULL AND commission_cents IS NULL)
          OR (commission_type = 'FIXED' AND commission_cents IS NOT NULL AND commission_rate_bp IS NULL)))
    OR
    (ownership <> 'CONSIGNMENT'
        AND consignor_id IS NULL AND commission_type IS NULL AND commission_rate_bp IS NULL AND commission_cents IS NULL)
);

CREATE INDEX idx_vehicle_consignor ON vehicle(consignor_id) WHERE consignor_id IS NOT NULL;

CREATE TABLE vehicle_share (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenant(id),
    vehicle_id  UUID        NOT NULL REFERENCES vehicle(id) ON DELETE CASCADE,
    contact_id  UUID        NOT NULL REFERENCES contact(id),
    share_bp    INTEGER     NOT NULL CHECK (share_bp > 0 AND share_bp <= 10000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (vehicle_id, contact_id)
);

CREATE INDEX idx_vehicle_share_tenant ON vehicle_share(tenant_id);
CREATE INDEX idx_vehicle_share_vehicle ON vehicle_share(vehicle_id);

ALTER TABLE ledger_entry DROP CONSTRAINT ledger_entry_type_check;
ALTER TABLE ledger_entry ADD CONSTRAINT ledger_entry_type_check
    CHECK (type IN ('VEHICLE_PURCHASE', 'VEHICLE_EXPENSE', 'GENERAL_EXPENSE', 'CAPITAL',
                    'VEHICLE_SALE', 'COLLECTION', 'PAYMENT', 'CONSIGNMENT_SETTLEMENT'));
