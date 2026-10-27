-- Arac kaldirma (yanlis giris): hareketleri terslenen arac REMOVED olur ve listelerden kalkar (satir silinmez, hareketler ona bagli).
-- Ortak kaldirma: sermaye hesabi kapanir (closed); kisi yeniden ortak yapilirsa ayni hesap acilir.

-- V2'deki isimsiz durum kisitlari (vehicle_status_check ve "stokta degilse satis tarihi var") tanimlarindan bulunup degistirilir
DO $$
DECLARE c text;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'vehicle'::regclass AND contype = 'c'
               AND (pg_get_constraintdef(oid) LIKE '%''CLOSED''%' OR pg_get_constraintdef(oid) LIKE '%sale_date IS NOT NULL%')
    LOOP
        EXECUTE format('ALTER TABLE vehicle DROP CONSTRAINT %I', c);
    END LOOP;
END $$;

ALTER TABLE vehicle ADD CONSTRAINT vehicle_status_check CHECK (status IN ('IN_STOCK', 'SOLD', 'CLOSED', 'REMOVED'));
ALTER TABLE vehicle ADD CONSTRAINT vehicle_sale_date_check CHECK (status IN ('IN_STOCK', 'REMOVED') OR sale_date IS NOT NULL);

ALTER TABLE account ADD COLUMN closed BOOLEAN NOT NULL DEFAULT false;
