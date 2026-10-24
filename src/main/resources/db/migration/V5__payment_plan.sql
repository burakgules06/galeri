-- ============================================================================
-- V5: odeme plani ve taksitler (yol haritasi 3. adim, bkz. docs/MIMARI.md §6)
-- Plan ve taksitler degistirilebilir (vade, senet no; satis geri alininca plan silinir),
-- para hareketi her zaman ledger_entry'de: taksit odenince paid_entry_id ile baglanir.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- PAYMENT_PLAN: bir aracin satis (SALE) ya da alis (PURCHASE) odeme plani. Her yonde en fazla bir plan.
-- ----------------------------------------------------------------------------
CREATE TABLE payment_plan (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenant(id),
    vehicle_id     UUID         NOT NULL REFERENCES vehicle(id),
    direction      VARCHAR(10)  NOT NULL CHECK (direction IN ('SALE', 'PURCHASE')),
    contact_id     UUID         NOT NULL REFERENCES contact(id),          -- karsi taraf: alici ya da satici
    total_cents    BIGINT       NOT NULL CHECK (total_cents > 0),
    created_by_id  UUID         NOT NULL REFERENCES panel_user(id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (vehicle_id, direction)
);
CREATE INDEX idx_payment_plan_tenant ON payment_plan(tenant_id);

-- ----------------------------------------------------------------------------
-- INSTALLMENT: planin taksiti. Odeyen varsayilan karsi taraftir; INSTITUTION'da kurum (Evim, banka).
-- ----------------------------------------------------------------------------
CREATE TABLE installment (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL REFERENCES tenant(id),
    plan_id           UUID         NOT NULL REFERENCES payment_plan(id) ON DELETE CASCADE,
    seq               SMALLINT     NOT NULL CHECK (seq > 0),
    due_date          DATE         NOT NULL,
    amount_cents      BIGINT       NOT NULL CHECK (amount_cents > 0),
    method            VARCHAR(16)  NOT NULL
                      CHECK (method IN ('CASH', 'TRANSFER', 'PROMISSORY_NOTE', 'INSTITUTION')),
    note_no           VARCHAR(40),
    payer_contact_id  UUID         NOT NULL REFERENCES contact(id),
    paid_entry_id     UUID         UNIQUE REFERENCES ledger_entry(id),
    paid_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (plan_id, seq),
    CHECK ((paid_entry_id IS NULL) = (paid_at IS NULL))
);
CREATE INDEX idx_installment_tenant ON installment(tenant_id);
CREATE INDEX idx_installment_plan   ON installment(plan_id);
-- Geciken / yaklasan listeleri ve gunluk bildirim taramasi
CREATE INDEX idx_installment_open_due ON installment(tenant_id, due_date) WHERE paid_entry_id IS NULL;
