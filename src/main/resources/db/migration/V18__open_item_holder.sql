-- Gecmis senet / cek kimin (V18): alinan evrak bir sermayedara aitse tahsil edildikce onun sermayesine yazilir.
-- Kredi hareketi: sermayedarin sermaye hesabi -> acilis hesabi (acilistaki alacak sermayedarin sermayesine donusur).
ALTER TABLE payment_plan ADD COLUMN holder_contact_id UUID REFERENCES contact(id);
ALTER TABLE installment ADD COLUMN holder_entry_id UUID UNIQUE REFERENCES ledger_entry(id);
