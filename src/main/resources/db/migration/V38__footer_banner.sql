-- Alt banner (istege bagli): sahibinden aciklamasinin en altinda, ustteki logo banner'iyla ayni genislikte.
-- Dosya logo gibi diskte <upload-dir>/<tenantId>/logo-<footer_banner_id>.png; her yuklemede yeni id (onbellek icin).
ALTER TABLE tenant ADD COLUMN footer_banner_id UUID;
CREATE UNIQUE INDEX uq_tenant_footer_banner_id ON tenant (footer_banner_id) WHERE footer_banner_id IS NOT NULL;
