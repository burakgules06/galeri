-- ============================================================================
-- V16: dukkan sahibi modeli (galerici geri bildirimi). Galeri ayri bir tuzel kisi degil: kasa, bankalar, araclar, alacaklar
-- eksi borclar bir havuz; havuzdan sermayedarlarin payi cikinca kalani dukkan sahibinin. Kar sadece arac satisinda, aracin
-- kar yuzdelerine gore dagitilir (degismedi).
-- - contact.capital_base: sermayedarin havuzdaki yuzdesinin (sadece bilgi) neye gore gosterilecegi: ALL (tum varlik),
--   VEHICLES (araclar), CASH (nakit). Ortak yapilirken secilir, sonra degistirilebilir.
-- - ledger_entry.type'a CAPITAL_TRANSFER: kisiler arasi sermaye aktarimi (CAPITAL -> CAPITAL); kasada para hareket etmez.
-- ============================================================================

ALTER TABLE contact ADD COLUMN capital_base VARCHAR(8) NOT NULL DEFAULT 'ALL'
    CHECK (capital_base IN ('ALL', 'VEHICLES', 'CASH'));

ALTER TABLE ledger_entry DROP CONSTRAINT ledger_entry_type_check;
ALTER TABLE ledger_entry ADD CONSTRAINT ledger_entry_type_check
    CHECK (type IN ('VEHICLE_PURCHASE', 'VEHICLE_EXPENSE', 'GENERAL_EXPENSE', 'CAPITAL',
                    'VEHICLE_SALE', 'COLLECTION', 'PAYMENT', 'CONSIGNMENT_SETTLEMENT',
                    'PROFIT_ALLOCATION', 'TRANSFER', 'OPENING_BALANCE', 'VEHICLE_PROFIT_SHARE', 'CAPITAL_TRANSFER'));
