-- ============================================================================
-- V10: ortak finansmani (revizyon PR 3, MIMARI §4 Sahiplik ve §5)
-- - vehicle_share artik ortak aracin "sahip satiri": contact_id NULL = Havuz (galeri kasasi, butun ortaklarin ortak parasi).
--   share_bp sahiplik payi (katki = gercek alis x pay), profit_share_bp kar payi orani, contribution_source katkinin
--   kaynagi (sermayeden / cepten / hibrit; hibritte cepten kisim pocket_cents).
-- - vehicle'a istege bagli dukkan payi: ortak aracta kar payindan havuza kalan oran ya da sabit tutar.
-- - ledger_entry.type'a VEHICLE_PROFIT_SHARE: satista ortagin kar payi CAPITAL(ortak) -> INCOME, zararda ters yon
--   (katkiyi asan zarar INCOME -> BALANCE(ortak), ortak galeriye borclanir).
-- Mevcut ortakli satirlar: kar payi orani = sahiplik payi, katki kaynagi = sermayeden. Veri silinmez.
-- ============================================================================

ALTER TABLE vehicle_share ALTER COLUMN contact_id DROP NOT NULL;
ALTER TABLE vehicle_share
    ADD COLUMN profit_share_bp     INTEGER CHECK (profit_share_bp BETWEEN 0 AND 10000),
    ADD COLUMN contribution_source VARCHAR(7) NOT NULL DEFAULT 'CAPITAL'
                                   CHECK (contribution_source IN ('CAPITAL', 'POCKET', 'HYBRID')),
    ADD COLUMN pocket_cents        BIGINT CHECK (pocket_cents > 0);
UPDATE vehicle_share SET profit_share_bp = share_bp;
ALTER TABLE vehicle_share ALTER COLUMN profit_share_bp SET NOT NULL;
ALTER TABLE vehicle_share ADD CONSTRAINT vehicle_share_source_check CHECK (
    (contact_id IS NOT NULL OR contribution_source = 'CAPITAL')
    AND ((contribution_source = 'HYBRID') = (pocket_cents IS NOT NULL))
);
-- Aracta en fazla bir Havuz satiri (UNIQUE (vehicle_id, contact_id) NULL'lari ayri sayar)
CREATE UNIQUE INDEX uq_vehicle_share_pool ON vehicle_share(vehicle_id) WHERE contact_id IS NULL;

ALTER TABLE vehicle
    ADD COLUMN shop_cut_type     VARCHAR(5) CHECK (shop_cut_type IN ('RATE', 'FIXED')),
    ADD COLUMN shop_cut_rate_bp  INTEGER    CHECK (shop_cut_rate_bp BETWEEN 0 AND 10000),
    ADD COLUMN shop_cut_cents    BIGINT     CHECK (shop_cut_cents >= 0);
ALTER TABLE vehicle ADD CONSTRAINT vehicle_shop_cut_check CHECK (
    (shop_cut_type IS NULL OR ownership = 'CO_OWNED')
    AND ((shop_cut_type = 'RATE') = (shop_cut_rate_bp IS NOT NULL))
    AND ((shop_cut_type = 'FIXED') = (shop_cut_cents IS NOT NULL))
);

ALTER TABLE ledger_entry DROP CONSTRAINT ledger_entry_type_check;
ALTER TABLE ledger_entry ADD CONSTRAINT ledger_entry_type_check
    CHECK (type IN ('VEHICLE_PURCHASE', 'VEHICLE_EXPENSE', 'GENERAL_EXPENSE', 'CAPITAL',
                    'VEHICLE_SALE', 'COLLECTION', 'PAYMENT', 'CONSIGNMENT_SETTLEMENT',
                    'PROFIT_ALLOCATION', 'TRANSFER', 'OPENING_BALANCE', 'VEHICLE_PROFIT_SHARE'));
