-- ============================================================================
-- V2: kisiler, araclar ve arac hasar semasi (yol haritasi 1. adim)
-- Para kolonlari (kurus, BIGINT) simdiden eklendi; 2. adima kadar bos kalabilir.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- KISI: musteri, arac aldigimiz kisi, ortak, kurum (Evim sirketi, banka)
-- ----------------------------------------------------------------------------
CREATE TABLE kisi (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID         NOT NULL REFERENCES tenant(id),
    ad          VARCHAR(120) NOT NULL,
    telefon     VARCHAR(20),
    tur         VARCHAR(10)  NOT NULL DEFAULT 'KISI' CHECK (tur IN ('KISI', 'KURUM')),
    notlar      TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_kisi_tenant ON kisi(tenant_id);

-- ----------------------------------------------------------------------------
-- ARAC
-- ----------------------------------------------------------------------------
CREATE TABLE arac (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL REFERENCES tenant(id),
    plaka              VARCHAR(12)  NOT NULL,                       -- "34 ABC 123" bicimine normalize edilir
    marka              VARCHAR(40)  NOT NULL,
    model              VARCHAR(60)  NOT NULL,
    model_yili         SMALLINT     NOT NULL,
    yakit              VARCHAR(12)  NOT NULL
                       CHECK (yakit IN ('BENZIN', 'DIZEL', 'LPG', 'BENZIN_LPG', 'HIBRIT', 'ELEKTRIK')),
    vites              VARCHAR(14)  NOT NULL
                       CHECK (vites IN ('MANUEL', 'OTOMATIK', 'YARI_OTOMATIK')),
    km                 INT          NOT NULL DEFAULT 0 CHECK (km >= 0),
    renk               VARCHAR(30),
    tramer_kurus       BIGINT       CHECK (tramer_kurus >= 0),      -- hasar kaydi tutari
    notlar             TEXT,
    sahiplik           VARCHAR(14)  NOT NULL DEFAULT 'GALERI'
                       CHECK (sahiplik IN ('GALERI', 'KONSINYE', 'OZEL_ORTAKLI')),
    durum              VARCHAR(10)  NOT NULL DEFAULT 'STOKTA'
                       CHECK (durum IN ('STOKTA', 'SATILDI', 'KAPANDI')),

    -- alis
    alis_tarihi        DATE         NOT NULL,
    satici_id          UUID         REFERENCES kisi(id),
    alan_calisan_id    UUID         REFERENCES panel_user(id),
    gercek_alis_kurus  BIGINT       CHECK (gercek_alis_kurus >= 0),
    resmi_alis_kurus   BIGINT       CHECK (resmi_alis_kurus >= 0),

    -- satis
    satis_tarihi       DATE,
    alici_id           UUID         REFERENCES kisi(id),
    satan_calisan_id   UUID         REFERENCES panel_user(id),
    satis_kurus        BIGINT       CHECK (satis_kurus >= 0),

    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CHECK (durum = 'STOKTA' OR satis_tarihi IS NOT NULL),
    CHECK (satis_tarihi IS NULL OR satis_tarihi >= alis_tarihi)
);
CREATE INDEX idx_arac_tenant       ON arac(tenant_id);
CREATE INDEX idx_arac_alis_tarihi  ON arac(tenant_id, alis_tarihi);
CREATE INDEX idx_arac_satis_tarihi ON arac(tenant_id, satis_tarihi);
-- Ayni plaka ayni galeride ayni anda iki kez stokta olamaz (satilip geri alinabilir)
CREATE UNIQUE INDEX uq_arac_stokta_plaka ON arac(tenant_id, plaka) WHERE durum = 'STOKTA';

-- ----------------------------------------------------------------------------
-- ARAC_PARCA: Sahibinden tarzi hasar semasi, 13 parca.
-- Kaydi olmayan parca ORIJINAL kabul edilir.
-- ----------------------------------------------------------------------------
CREATE TABLE arac_parca (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenant(id),
    arac_id     UUID        NOT NULL REFERENCES arac(id) ON DELETE CASCADE,
    parca       VARCHAR(24) NOT NULL CHECK (parca IN (
                    'ON_TAMPON', 'MOTOR_KAPUTU', 'TAVAN', 'BAGAJ_KAPAGI', 'ARKA_TAMPON',
                    'SOL_ON_CAMURLUK', 'SOL_ON_KAPI', 'SOL_ARKA_KAPI', 'SOL_ARKA_CAMURLUK',
                    'SAG_ON_CAMURLUK', 'SAG_ON_KAPI', 'SAG_ARKA_KAPI', 'SAG_ARKA_CAMURLUK')),
    durum       VARCHAR(14) NOT NULL CHECK (durum IN ('ORIJINAL', 'LOKAL_BOYALI', 'BOYALI', 'DEGISEN')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (arac_id, parca)
);
CREATE INDEX idx_arac_parca_tenant ON arac_parca(tenant_id);
