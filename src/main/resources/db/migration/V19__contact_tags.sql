-- Kisilerin birden fazla is rolunu tasiyabilmesi; ilk rol arac alicisi olan MUSTERI'dir.
ALTER TABLE contact ADD CONSTRAINT uq_contact_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE contact_tag (
    tenant_id   UUID         NOT NULL REFERENCES tenant(id),
    contact_id  UUID         NOT NULL,
    tag          VARCHAR(32)  NOT NULL CHECK (tag IN ('CUSTOMER')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (contact_id, tag),
    CONSTRAINT fk_contact_tag_contact FOREIGN KEY (tenant_id, contact_id)
        REFERENCES contact(tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX idx_contact_tag_tenant ON contact_tag(tenant_id);

-- Gecmiste araci satilmis kisiler de Musteriler gorunumunde yer alsin.
INSERT INTO contact_tag (tenant_id, contact_id, tag)
SELECT tenant_id, buyer_id, 'CUSTOMER'
FROM vehicle
WHERE buyer_id IS NOT NULL
GROUP BY tenant_id, buyer_id;
