-- ============================================================================
-- V3: para defteri (yol haritasi 2. adim): hesaplar (account), masraf kategorileri
-- (expense_category), hareketler (ledger_entry).
-- Bakiye hicbir yerde saklanmaz; hesabin bakiyesi = giren hareketler - cikan hareketler.
-- Hareket silinmez ve degistirilmez (asagidaki trigger), duzeltme ters hareketle yapilir.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- ACCOUNT: CASH (kasa), BANK, BALANCE (kisinin carisi), CAPITAL (ortak sermayesi),
-- VEHICLE (arac maliyeti), EXPENSE (gider), INCOME (gelir)
-- ----------------------------------------------------------------------------
CREATE TABLE account (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant(id),
    type        VARCHAR(10)  NOT NULL
                CHECK (type IN ('CASH', 'BANK', 'BALANCE', 'CAPITAL', 'VEHICLE', 'EXPENSE', 'INCOME')),
    name        VARCHAR(120) NOT NULL,
    contact_id  UUID         REFERENCES contact(id),
    vehicle_id  UUID         REFERENCES vehicle(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CHECK ((type IN ('BALANCE', 'CAPITAL')) = (contact_id IS NOT NULL)),
    CHECK ((type = 'VEHICLE') = (vehicle_id IS NOT NULL))
);
CREATE INDEX idx_account_tenant ON account(tenant_id);
-- Galeri basina tek CASH, tek EXPENSE, tek INCOME; kisi basina tek BALANCE / CAPITAL; arac basina tek VEHICLE
CREATE UNIQUE INDEX uq_account_singleton ON account(tenant_id, type) WHERE type IN ('CASH', 'EXPENSE', 'INCOME');
CREATE UNIQUE INDEX uq_account_contact   ON account(tenant_id, type, contact_id) WHERE contact_id IS NOT NULL;
CREATE UNIQUE INDEX uq_account_vehicle   ON account(vehicle_id) WHERE vehicle_id IS NOT NULL;

-- ----------------------------------------------------------------------------
-- EXPENSE_CATEGORY: iki kok (VEHICLE / GENERAL), en fazla 3 seviye. Masraf girerken olusur.
-- ----------------------------------------------------------------------------
CREATE TABLE expense_category (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant(id),
    parent_id   UUID         REFERENCES expense_category(id),
    name        VARCHAR(60)  NOT NULL,
    root        VARCHAR(10)  NOT NULL CHECK (root IN ('VEHICLE', 'GENERAL')),
    archived    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_expense_category_tenant ON expense_category(tenant_id);
-- Ayni ust altinda ayni ad (harf buyuklugunden bagimsiz) iki kez olmaz
CREATE UNIQUE INDEX uq_expense_category_name
    ON expense_category(tenant_id, root, COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name));

-- ----------------------------------------------------------------------------
-- LEDGER_ENTRY: source_account -> target_account, amount (kurus)
-- ----------------------------------------------------------------------------
CREATE TABLE ledger_entry (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL REFERENCES tenant(id),
    entry_date           DATE         NOT NULL,
    type                 VARCHAR(20)  NOT NULL,
    source_account_id    UUID         NOT NULL REFERENCES account(id),
    target_account_id    UUID         NOT NULL REFERENCES account(id),
    amount_cents         BIGINT       NOT NULL CHECK (amount_cents > 0),
    vehicle_id           UUID         REFERENCES vehicle(id),
    expense_category_id  UUID         REFERENCES expense_category(id),
    description          VARCHAR(500),
    corrected_entry_id   UUID         UNIQUE REFERENCES ledger_entry(id),   -- bir hareket en fazla bir kez duzeltilir
    created_by_id        UUID         NOT NULL REFERENCES panel_user(id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ledger_entry_type_check
        CHECK (type IN ('VEHICLE_PURCHASE', 'VEHICLE_EXPENSE', 'GENERAL_EXPENSE', 'CAPITAL')),
    CHECK (source_account_id <> target_account_id)
);
CREATE INDEX idx_ledger_entry_tenant  ON ledger_entry(tenant_id, entry_date);
CREATE INDEX idx_ledger_entry_source  ON ledger_entry(source_account_id);
CREATE INDEX idx_ledger_entry_target  ON ledger_entry(target_account_id);
CREATE INDEX idx_ledger_entry_vehicle ON ledger_entry(vehicle_id) WHERE vehicle_id IS NOT NULL;

-- Defter kaydi degistirilemez ve silinemez; uygulama hatasi olsa bile veritabani reddeder.
CREATE FUNCTION ledger_entry_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'hareket kayitlari degistirilemez ve silinemez, ters hareketle duzeltin';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entry_immutable
    BEFORE UPDATE OR DELETE ON ledger_entry
    FOR EACH ROW EXECUTE FUNCTION ledger_entry_immutable();
