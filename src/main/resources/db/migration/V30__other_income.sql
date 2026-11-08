-- Diger kazanclar (noter / kredi / sigorta komisyonu, kira geliri ...): INCOME -> CASH/BANK, istege bagli araca bagli.
-- Kategoriler masraf kategorileri tablosunda, ayri kok (INCOME) ile.
ALTER TABLE ledger_entry DROP CONSTRAINT ledger_entry_type_check;
ALTER TABLE ledger_entry ADD CONSTRAINT ledger_entry_type_check
    CHECK (type IN ('VEHICLE_PURCHASE', 'VEHICLE_EXPENSE', 'GENERAL_EXPENSE', 'CAPITAL',
                    'VEHICLE_SALE', 'COLLECTION', 'PAYMENT', 'CONSIGNMENT_SETTLEMENT',
                    'PROFIT_ALLOCATION', 'TRANSFER', 'OPENING_BALANCE', 'VEHICLE_PROFIT_SHARE',
                    'CAPITAL_TRANSFER', 'PARTNER_SALE_SETTLEMENT', 'OTHER_INCOME'));

ALTER TABLE expense_category DROP CONSTRAINT expense_category_root_check;
ALTER TABLE expense_category ADD CONSTRAINT expense_category_root_check CHECK (root IN ('VEHICLE', 'GENERAL', 'INCOME'));
