-- Kisinin ana turu (kalici, kayitta secilir): CUSTOMER musteri, SELLER arac aldigimiz kisi (alis yaptiklarim), SUPPLIER tedarikci.
-- Bicim (PERSON bireysel / ORGANIZATION tuzel) ayri kolon (type) olarak kalir. Veri silinmez.
ALTER TABLE contact ADD COLUMN kind VARCHAR(12) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE contact ADD CONSTRAINT contact_kind_check CHECK (kind IN ('CUSTOMER', 'SELLER', 'SUPPLIER'));

-- Mevcut kayitlar: bize arac satmis ama bizden arac almamis kisi "alis yaptiklarim"; digerleri musteri.
UPDATE contact c SET kind = 'SELLER'
WHERE (EXISTS (SELECT 1 FROM vehicle v WHERE v.seller_id = c.id)
       OR EXISTS (SELECT 1 FROM payment_plan p WHERE p.contact_id = c.id AND p.direction = 'PURCHASE' AND p.vehicle_id IS NOT NULL))
  AND NOT EXISTS (SELECT 1 FROM vehicle v WHERE v.buyer_id = c.id);

CREATE INDEX idx_contact_tenant_kind ON contact (tenant_id, kind);
