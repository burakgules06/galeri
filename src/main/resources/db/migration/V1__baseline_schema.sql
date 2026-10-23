-- ============================================================================
-- GALERI - V1 temel sema: isletme (tenant) ve panel kullanicilari
-- Arac, musteri vb. tablolar sonraki migration'larda (V2, V3 ...) eklenir.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;     -- gen_random_uuid()

-- ----------------------------------------------------------------------------
-- TENANT: galeri (multi-tenancy koku)
-- ----------------------------------------------------------------------------
CREATE TABLE tenant (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug                 VARCHAR(60)  NOT NULL UNIQUE,
    display_name         VARCHAR(120) NOT NULL,
    timezone             VARCHAR(40)  NOT NULL DEFAULT 'Europe/Istanbul',
    is_active            BOOLEAN      NOT NULL DEFAULT TRUE,
    subscription_status  VARCHAR(20)  NOT NULL DEFAULT 'TRIAL'
                         CHECK (subscription_status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELLED')),
    subscription_until   DATE,
    plan                 VARCHAR(20)  NOT NULL DEFAULT 'BASIC',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- PANEL_USER: panele giris yapan kullanicilar
-- ----------------------------------------------------------------------------
CREATE TABLE panel_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID REFERENCES tenant(id),     -- SUPER_ADMIN icin NULL
    phone         VARCHAR(20)  NOT NULL UNIQUE,   -- giris kimligi, 90XXXXXXXXXX
    password_hash VARCHAR(100) NOT NULL,          -- BCrypt
    full_name     VARCHAR(120),
    role          VARCHAR(20)  NOT NULL DEFAULT 'STAFF'
                  CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'STAFF')),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_panel_user_tenant ON panel_user(tenant_id);
