-- Konsinye araca cepten yapilan masraf: sadece takip icin. Hicbir hesaptan para dusmez, deftere (ledger_entry) yazilmaz;
-- aracin karina, sahibinin sermayesine ve galerinin parasina etkisi yoktur. Veri silinmez, sadece yeni tablo.
CREATE TABLE vehicle_pocket_expense (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL REFERENCES tenant(id),
    vehicle_id           UUID         NOT NULL REFERENCES vehicle(id),
    expense_date         DATE         NOT NULL,
    amount_cents         BIGINT       NOT NULL CHECK (amount_cents > 0),
    expense_category_id  UUID         REFERENCES expense_category(id),
    description          VARCHAR(500),
    created_by_id        UUID         NOT NULL REFERENCES panel_user(id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_vehicle_pocket_expense_tenant ON vehicle_pocket_expense(tenant_id);
CREATE INDEX idx_vehicle_pocket_expense_vehicle ON vehicle_pocket_expense(vehicle_id);
