-- ============================================================================
-- V4: galeri sahibi isareti ve cari (alacak / verecek) hareket turleri
-- - contact.is_owner: sermaye ortaklari arasinda dukkan sahibi (galeri basina en fazla bir)
-- - ledger_entry.type'a VEHICLE_SALE (INCOME -> BALANCE(alici)), COLLECTION (BALANCE -> CASH/BANK,
--   tahsilat) ve PAYMENT (CASH/BANK -> BALANCE, borc odemesi) eklenir.
-- ============================================================================

ALTER TABLE contact ADD COLUMN is_owner BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX uq_contact_owner ON contact(tenant_id) WHERE is_owner;

ALTER TABLE ledger_entry DROP CONSTRAINT ledger_entry_type_check;
ALTER TABLE ledger_entry ADD CONSTRAINT ledger_entry_type_check
    CHECK (type IN ('VEHICLE_PURCHASE', 'VEHICLE_EXPENSE', 'GENERAL_EXPENSE', 'CAPITAL',
                    'VEHICLE_SALE', 'COLLECTION', 'PAYMENT'));
