-- ============================================================================
-- V12: cek takibi, ciro, karsiliksiz ve kismi tahsilat (galerici geri bildirimi)
-- - installment.method'a CHEQUE. Cekte banka ve keside eden (cekin asil sahibi) tutulur.
-- - Ciro: alinan cek / senet baska birine odeme olarak verilir. Defterde PORTFOLIO (cek / senet portfoyu, galeri basina tek)
--   ara hesap olur: BALANCE(veren) -> PORTFOLIO (taksit odendi), PORTFOLIO -> BALANCE(alan) (ona odeme). Ikisi ayni islemde
--   yazildigi icin PORTFOLIO bakiyesi hep 0'dir. endorse_entry_id ikinci hareket, endorsed_to_contact_id cekin verildigi kisi.
-- - bounced_at: cek / senet karsiliksiz cikti (odeme geri alinir, borc yeniden acilir).
-- Sadece kolon ve kisit ekleniyor; veri degismez.
-- ============================================================================

ALTER TABLE installment DROP CONSTRAINT installment_method_check;
ALTER TABLE installment ADD CONSTRAINT installment_method_check
    CHECK (method IN ('CASH', 'TRANSFER', 'PROMISSORY_NOTE', 'INSTITUTION', 'CHEQUE'));

ALTER TABLE installment
    ADD COLUMN bank_name              VARCHAR(60),
    ADD COLUMN drawer_name            VARCHAR(120),
    ADD COLUMN endorsed_to_contact_id UUID REFERENCES contact(id),
    ADD COLUMN endorse_entry_id       UUID UNIQUE REFERENCES ledger_entry(id),
    ADD COLUMN bounced_at             TIMESTAMPTZ;
ALTER TABLE installment ADD CONSTRAINT installment_endorse_check CHECK (
    (endorse_entry_id IS NULL) = (endorsed_to_contact_id IS NULL)
    AND (endorse_entry_id IS NULL OR paid_entry_id IS NOT NULL)
);

ALTER TABLE account DROP CONSTRAINT account_type_check;
ALTER TABLE account ADD CONSTRAINT account_type_check
    CHECK (type IN ('CASH', 'BANK', 'BALANCE', 'CAPITAL', 'VEHICLE', 'EXPENSE', 'INCOME', 'OPENING', 'PORTFOLIO'));

DROP INDEX uq_account_singleton;
CREATE UNIQUE INDEX uq_account_singleton ON account(tenant_id, type) WHERE type IN ('CASH', 'EXPENSE', 'INCOME', 'OPENING', 'PORTFOLIO');
