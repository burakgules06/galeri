-- ============================================================================
-- V3: para defteri (yol haritasi 2. adim): hesaplar, masraf kategorileri, hareketler
-- Bakiye hicbir yerde saklanmaz; hesabin bakiyesi = giren hareketler - cikan hareketler.
-- Hareket silinmez ve degistirilmez (asagidaki trigger), duzeltme ters hareketle yapilir.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- HESAP: KASA, BANKA, CARI (kisi), SERMAYE (ortak), ARAC (arac maliyeti), GIDER, GELIR
-- ----------------------------------------------------------------------------
CREATE TABLE hesap (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant(id),
    tur         VARCHAR(10)  NOT NULL
                CHECK (tur IN ('KASA', 'BANKA', 'CARI', 'SERMAYE', 'ARAC', 'GIDER', 'GELIR')),
    ad          VARCHAR(120) NOT NULL,
    kisi_id     UUID         REFERENCES kisi(id),
    arac_id     UUID         REFERENCES arac(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CHECK ((tur IN ('CARI', 'SERMAYE')) = (kisi_id IS NOT NULL)),
    CHECK ((tur = 'ARAC') = (arac_id IS NOT NULL))
);
CREATE INDEX idx_hesap_tenant ON hesap(tenant_id);
-- Galeri basina tek KASA, tek GIDER, tek GELIR; kisi basina tek CARI / SERMAYE; arac basina tek ARAC
CREATE UNIQUE INDEX uq_hesap_tekil      ON hesap(tenant_id, tur) WHERE tur IN ('KASA', 'GIDER', 'GELIR');
CREATE UNIQUE INDEX uq_hesap_kisi       ON hesap(tenant_id, tur, kisi_id) WHERE kisi_id IS NOT NULL;
CREATE UNIQUE INDEX uq_hesap_arac       ON hesap(arac_id) WHERE arac_id IS NOT NULL;

-- ----------------------------------------------------------------------------
-- MASRAF_KATEGORI: iki kok (ARAC / GENEL), en fazla 3 seviye. Masraf girerken olusur.
-- ----------------------------------------------------------------------------
CREATE TABLE masraf_kategori (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant(id),
    ust_id      UUID         REFERENCES masraf_kategori(id),
    ad          VARCHAR(60)  NOT NULL,
    kok         VARCHAR(6)   NOT NULL CHECK (kok IN ('ARAC', 'GENEL')),
    arsivlendi  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_masraf_kategori_tenant ON masraf_kategori(tenant_id);
-- Ayni ust altinda ayni ad (harf buyuklugunden bagimsiz) iki kez olmaz
CREATE UNIQUE INDEX uq_masraf_kategori_ad
    ON masraf_kategori(tenant_id, kok, COALESCE(ust_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(ad));

-- ----------------------------------------------------------------------------
-- HAREKET: kaynak_hesap -> hedef_hesap, tutar (kurus)
-- ----------------------------------------------------------------------------
CREATE TABLE hareket (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID         NOT NULL REFERENCES tenant(id),
    tarih                   DATE         NOT NULL,
    tur                     VARCHAR(12)  NOT NULL,
    kaynak_hesap_id         UUID         NOT NULL REFERENCES hesap(id),
    hedef_hesap_id          UUID         NOT NULL REFERENCES hesap(id),
    tutar_kurus             BIGINT       NOT NULL CHECK (tutar_kurus > 0),
    arac_id                 UUID         REFERENCES arac(id),
    masraf_kategori_id      UUID         REFERENCES masraf_kategori(id),
    aciklama                VARCHAR(500),
    duzeltilen_hareket_id   UUID         UNIQUE REFERENCES hareket(id),   -- bir hareket en fazla bir kez duzeltilir
    olusturan_kullanici_id  UUID         NOT NULL REFERENCES panel_user(id),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT hareket_tur_check CHECK (tur IN ('ARAC_ALIS', 'ARAC_MASRAF', 'GENEL_GIDER', 'SERMAYE')),
    CHECK (kaynak_hesap_id <> hedef_hesap_id)
);
CREATE INDEX idx_hareket_tenant ON hareket(tenant_id, tarih);
CREATE INDEX idx_hareket_kaynak ON hareket(kaynak_hesap_id);
CREATE INDEX idx_hareket_hedef  ON hareket(hedef_hesap_id);
CREATE INDEX idx_hareket_arac   ON hareket(arac_id) WHERE arac_id IS NOT NULL;

-- Defter kaydi degistirilemez ve silinemez; uygulama hatasi olsa bile veritabani reddeder.
CREATE FUNCTION hareket_degistirilemez() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'hareket kayitlari degistirilemez ve silinemez, ters hareketle duzeltin';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_hareket_degistirilemez
    BEFORE UPDATE OR DELETE ON hareket
    FOR EACH ROW EXECUTE FUNCTION hareket_degistirilemez();
