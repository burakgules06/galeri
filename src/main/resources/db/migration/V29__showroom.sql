-- Musteri vitrini: secilen stok araclari fotograf ve ilan bilgileriyle herkese acik sayfada listelenir.
ALTER TABLE vehicle
    ADD COLUMN show_in_showroom    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN listing_price_cents BIGINT CHECK (listing_price_cents IS NULL OR listing_price_cents > 0),
    ADD COLUMN listing_description TEXT;

-- Vitrin sayfasindaki WhatsApp / ara butonunun numarasi (bos ise buton cikmaz)
ALTER TABLE tenant ADD COLUMN showroom_phone VARCHAR(20);

-- Arac fotograflari. Dosyalar sunucu diskinde: <upload-dir>/<tenant_id>/<id>.jpg ve <id>_t.jpg (kucuk).
CREATE TABLE vehicle_photo (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenant(id),
    vehicle_id  UUID        NOT NULL REFERENCES vehicle(id),
    position    INT         NOT NULL,
    width       INT         NOT NULL,
    height      INT         NOT NULL,
    size_bytes  INT         NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vehicle_photo_tenant ON vehicle_photo (tenant_id);
CREATE INDEX idx_vehicle_photo_vehicle ON vehicle_photo (vehicle_id, position);
