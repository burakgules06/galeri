-- Gecmis senet / cek kime ait (sermayedar): tahsil edildikce (ya da ciro edildikce) tutar onun sermayesine yazilir.
-- Yazim dukkan sahibinden sermayedara CAPITAL_TRANSFER hareketidir; taksitteki credit_entry_id geri almada terslenir.
ALTER TABLE payment_plan ADD COLUMN beneficiary_contact_id UUID REFERENCES contact(id);
ALTER TABLE installment ADD COLUMN credit_entry_id UUID UNIQUE REFERENCES ledger_entry(id);
