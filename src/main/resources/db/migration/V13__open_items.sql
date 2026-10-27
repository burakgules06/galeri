-- ============================================================================
-- V13: arac olmadan acik senet / cek (gecmis kayitlarin aktarimi, galerici geri bildirimi)
-- Kagittan takip edilen eski senet ve cekler kisinin carisine aracsiz bir plan olarak girilir. Plan toplami defterde
-- OPENING_BALANCE hareketiyle cariye yazilir (alacak: OPENING -> BALANCE, borc: BALANCE -> OPENING); opening_entry_id bu hareket.
-- Taksitler aracli planlardaki gibi tahsil / ode / ciro / karsiliksiz akisini kullanir.
-- Sadece kolon ekleniyor ve NOT NULL kaldiriliyor; veri degismez.
-- ============================================================================

ALTER TABLE payment_plan ALTER COLUMN vehicle_id DROP NOT NULL;
ALTER TABLE payment_plan ADD COLUMN opening_entry_id UUID UNIQUE REFERENCES ledger_entry(id);
CREATE INDEX idx_payment_plan_contact ON payment_plan(contact_id);
