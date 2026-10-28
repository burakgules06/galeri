-- Ortagin alici oldugu arac satisinda taksit sermayesinden karsilanabilsin.
ALTER TABLE installment
    ADD COLUMN capital_cents BIGINT NOT NULL DEFAULT 0 CHECK (capital_cents >= 0 AND capital_cents <= amount_cents),
    ADD COLUMN capital_entry_id UUID REFERENCES ledger_entry(id);

ALTER TABLE ledger_entry DROP CONSTRAINT ledger_entry_type_check;
ALTER TABLE ledger_entry ADD CONSTRAINT ledger_entry_type_check
    CHECK (type IN ('VEHICLE_PURCHASE', 'VEHICLE_EXPENSE', 'GENERAL_EXPENSE', 'CAPITAL',
                    'VEHICLE_SALE', 'COLLECTION', 'PAYMENT', 'CONSIGNMENT_SETTLEMENT',
                    'PROFIT_ALLOCATION', 'TRANSFER', 'OPENING_BALANCE', 'VEHICLE_PROFIT_SHARE',
                    'CAPITAL_TRANSFER', 'PARTNER_SALE_SETTLEMENT'));
