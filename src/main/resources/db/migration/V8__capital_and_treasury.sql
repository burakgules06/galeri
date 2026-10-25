-- ============================================================================
-- V8: sermaye, kar payi ve kasa islemleri (MIMARI §5)
-- - account.type'a OPENING: acilis bakiyelerinin karsi hesabi, galeri basina tek.
-- - ledger_entry.type'a PROFIT_ALLOCATION (kar payini sermayeye ekleme), TRANSFER (kasa <-> banka virmani),
--   OPENING_BALANCE (sisteme geciste kasa / banka / cari acilis bakiyesi).
-- Sadece kisit ve index degisikligi; veri degismez.
-- ============================================================================

ALTER TABLE account DROP CONSTRAINT account_type_check;
ALTER TABLE account ADD CONSTRAINT account_type_check
    CHECK (type IN ('CASH', 'BANK', 'BALANCE', 'CAPITAL', 'VEHICLE', 'EXPENSE', 'INCOME', 'OPENING'));

DROP INDEX uq_account_singleton;
CREATE UNIQUE INDEX uq_account_singleton ON account(tenant_id, type) WHERE type IN ('CASH', 'EXPENSE', 'INCOME', 'OPENING');

ALTER TABLE ledger_entry DROP CONSTRAINT ledger_entry_type_check;
ALTER TABLE ledger_entry ADD CONSTRAINT ledger_entry_type_check
    CHECK (type IN ('VEHICLE_PURCHASE', 'VEHICLE_EXPENSE', 'GENERAL_EXPENSE', 'CAPITAL',
                    'VEHICLE_SALE', 'COLLECTION', 'PAYMENT', 'CONSIGNMENT_SETTLEMENT',
                    'PROFIT_ALLOCATION', 'TRANSFER', 'OPENING_BALANCE'));
