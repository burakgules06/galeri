-- Galerinin kendi logosu (istege bagli): sahibinden aciklamasindaki galeri kartinda ve hasar resminde.
-- Dosya diskte <upload-dir>/<tenantId>/logo-<logo_id>.png; her yuklemede yeni id (onbellek icin).
ALTER TABLE tenant ADD COLUMN logo_id UUID;
CREATE UNIQUE INDEX uq_tenant_logo_id ON tenant (logo_id) WHERE logo_id IS NOT NULL;
