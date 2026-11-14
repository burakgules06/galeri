-- Kisiler arasi borc (V33). Bir kisi (dukkan sahibi ya da sermayedar) baska birine borc verir, borclu geri oder.
-- LOAN: CAPITAL(alan) -> CAPITAL(veren)   (alanin sermayesi artar, verenin azalir)
-- LOAN_REPAYMENT: CAPITAL(veren) -> CAPITAL(alan)   (geri odeme, ters yon)
-- Kalan borc saklanmaz: bu hareketlerden hesaplanir. Para kasada / bankada kalir; loan_account_id odunc verilen ya da
-- geri odenen paranin durdugu kasa / banka: hesap basina dagilimda paylar o hesapta el degistirir.
ALTER TABLE ledger_entry
    ADD COLUMN loan_account_id UUID REFERENCES account(id);

ALTER TABLE ledger_entry DROP CONSTRAINT ledger_entry_type_check;
ALTER TABLE ledger_entry ADD CONSTRAINT ledger_entry_type_check
    CHECK (type IN ('VEHICLE_PURCHASE', 'VEHICLE_EXPENSE', 'GENERAL_EXPENSE', 'CAPITAL',
                    'VEHICLE_SALE', 'COLLECTION', 'PAYMENT', 'CONSIGNMENT_SETTLEMENT',
                    'PROFIT_ALLOCATION', 'TRANSFER', 'OPENING_BALANCE', 'VEHICLE_PROFIT_SHARE',
                    'CAPITAL_TRANSFER', 'PARTNER_SALE_SETTLEMENT', 'OTHER_INCOME', 'LOAN', 'LOAN_REPAYMENT'));

CREATE INDEX idx_ledger_entry_loan ON ledger_entry (tenant_id, type) WHERE type IN ('LOAN', 'LOAN_REPAYMENT');
