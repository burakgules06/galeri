-- Dukkan sahibinin araci cebinden almasi: bedelin purchase_pocket_cents kadari kasadan / bankadan cikmaz,
-- girisi yapan dukkan sahibinin sermayesine eklenir (purchase_pocket_entry_id: CAPITAL(sahibi) -> VEHICLE).
-- Kalan kisim her zamanki gibi secilen kasa / bankadan.
ALTER TABLE vehicle
    ADD COLUMN purchase_pocket_cents    BIGINT NOT NULL DEFAULT 0 CHECK (purchase_pocket_cents >= 0),
    ADD COLUMN purchase_pocket_entry_id UUID UNIQUE REFERENCES ledger_entry(id);
