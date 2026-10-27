-- ============================================================================
-- V14: toplu satis (ayni aliciya birden fazla arac, tek odeme plani)
-- Sepet plani aracsizdir (payment_plan.vehicle_id bos, opening_entry_id bos); araclar ve her birinin satis bedeli bu tabloda.
-- Satis hareketi yine arac basina yazilir (kar arac basina). Sepet butunuyle geri alinir.
-- Yeni tablo; mevcut veri degismez.
-- ============================================================================

CREATE TABLE payment_plan_vehicle (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL REFERENCES tenant(id),
    plan_id           UUID         NOT NULL REFERENCES payment_plan(id) ON DELETE CASCADE,
    vehicle_id        UUID         NOT NULL REFERENCES vehicle(id),
    sale_price_cents  BIGINT       NOT NULL CHECK (sale_price_cents > 0),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (plan_id, vehicle_id)
);
CREATE INDEX idx_payment_plan_vehicle_tenant ON payment_plan_vehicle(tenant_id);
-- Bir arac ayni anda en fazla bir sepette
CREATE UNIQUE INDEX uq_payment_plan_vehicle_vehicle ON payment_plan_vehicle(vehicle_id);
