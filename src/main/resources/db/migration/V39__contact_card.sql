-- Iletisim karti gorseli (istege bagli): sahibinden aciklamasinda logo banner'inin hemen altinda, logoyla ayni en ve boyda.
-- Dosya logo gibi diskte <upload-dir>/<tenantId>/logo-<contact_card_id>.png; her yuklemede yeni id (onbellek icin).
ALTER TABLE tenant ADD COLUMN contact_card_id UUID;
CREATE UNIQUE INDEX uq_tenant_contact_card_id ON tenant (contact_card_id) WHERE contact_card_id IS NOT NULL;
