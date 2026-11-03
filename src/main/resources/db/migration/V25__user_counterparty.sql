-- Arac alip satilan taraf panel kullanicisi olabilir (MIMARI §0 madde 5, "satmak icin al").
-- seller_user_id: araci kullanicidan aldik; bedelin bir kismi onun sermayesine eklenebilir (purchase_capital_entry_id).
-- buyer_user_id: araci kullaniciya sattik; bedel sermayesinden ve/veya nakit (sale_capital_entry_id).
-- transfer_from_vehicle_id: "satmak icin al" ile alanin stoguna acilan yeni kayit, satilan eski kayda baglanir.
ALTER TABLE vehicle
    ADD COLUMN seller_user_id           UUID REFERENCES panel_user(id),
    ADD COLUMN buyer_user_id            UUID REFERENCES panel_user(id),
    ADD COLUMN transfer_from_vehicle_id UUID UNIQUE REFERENCES vehicle(id),
    ADD COLUMN purchase_capital_entry_id UUID UNIQUE REFERENCES ledger_entry(id),
    ADD COLUMN sale_capital_entry_id    UUID UNIQUE REFERENCES ledger_entry(id),
    ADD CONSTRAINT chk_vehicle_one_seller CHECK (seller_id IS NULL OR seller_user_id IS NULL),
    ADD CONSTRAINT chk_vehicle_one_buyer  CHECK (buyer_id IS NULL OR buyer_user_id IS NULL);
