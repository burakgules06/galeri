-- ============================================================================
-- V9: arac katalogu, galeriye ozel eklemeler
-- Ana katalog uygulamada (resources/catalog/vehicles.json, salt okunur). "Listede yok" ile ADMIN'in eklemeyi onayladigi
-- marka / model burada galeri basina tutulur. Mevcut veriye dokunmaz.
-- ============================================================================

CREATE TABLE catalog_entry (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant(id),
    make        VARCHAR(40)  NOT NULL,
    model       VARCHAR(60)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_catalog_entry_tenant ON catalog_entry(tenant_id);
-- Ayni galeride ayni marka + model bir kez (harf buyuklugunden bagimsiz)
CREATE UNIQUE INDEX uq_catalog_entry ON catalog_entry(tenant_id, lower(make), lower(model));
