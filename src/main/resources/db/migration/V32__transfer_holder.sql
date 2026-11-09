-- Hesaplar arasi transferde (kasa <-> banka) tasinan para kimin: bos = dukkan sahibi (eskisi gibi),
-- dolu = o sermayedarin parasi (hesap basina dagilimda onun payi kaynak hesaptan hedef hesaba gecer).
ALTER TABLE ledger_entry
    ADD COLUMN transfer_holder_id UUID REFERENCES panel_user(id);

CREATE INDEX idx_ledger_entry_transfer_holder ON ledger_entry (tenant_id, transfer_holder_id) WHERE transfer_holder_id IS NOT NULL;
