-- Arac donanimlari (cam tavan, 360 kamera, ses sistemi ...): hazir listeden ya da serbest yazilarak; sirasi korunur.
-- Arac detayinda, musteri vitrininde ve sahibinden aciklamasinda listelenir. Para bilgisi degildir.
CREATE TABLE vehicle_equipment (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant(id),
    vehicle_id  UUID         NOT NULL REFERENCES vehicle(id) ON DELETE CASCADE,
    name        VARCHAR(80)  NOT NULL,
    position    INT          NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_vehicle_equipment_tenant ON vehicle_equipment(tenant_id);
CREATE INDEX idx_vehicle_equipment_vehicle ON vehicle_equipment(vehicle_id, position);
