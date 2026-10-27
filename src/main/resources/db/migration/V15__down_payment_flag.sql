-- ============================================================================
-- V15: pesinat isareti. Plan kurulurken ayni islemde tahsil / odenen satir ("simdi alindi") ekranda "Pesinat" yazilir,
-- taksit numaralari ondan sonra 1'den baslar. Mevcut kayitlar: kayitla ayni dakikalarda odenmis satirlar pesinattir.
-- ============================================================================

ALTER TABLE installment ADD COLUMN down_payment BOOLEAN NOT NULL DEFAULT false;
UPDATE installment SET down_payment = true
WHERE paid_at IS NOT NULL AND abs(extract(epoch FROM paid_at - created_at)) < 120;
