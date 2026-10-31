-- Kullanici / kisi ayrimi (MIMARI §0, PR 1). Sermaye, arac paylari, konsinye sahibi ve evrak sahibi kisiden kullaniciya tasinir.
-- Dukkan sahibi = ADMIN rolu (birden fazla olabilir; havuzdan kalan simdilik en once acilan aktif ADMIN'in). Sermayedar = INVESTOR.
-- Eslesme: ayni galeride ad soyadi (buyuk/kucuk harf ve bosluk farki gozetmeden) ayni kullanici; yoksa girisi kapali sermayedar acilir.
-- Hareket defterine dokunulmaz (sadece eski gecmis senet / cek planlarindan yetim kalan acilis hareketleri ters hareketle kapanir).

-- ---------------------------------------------------------------- kullanici
ALTER TABLE panel_user DROP CONSTRAINT IF EXISTS panel_user_role_check;
ALTER TABLE panel_user ADD CONSTRAINT panel_user_role_check CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'INVESTOR', 'STAFF'));
ALTER TABLE panel_user ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE panel_user ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE panel_user
    ADD COLUMN can_login    BOOLEAN    NOT NULL DEFAULT TRUE,
    ADD COLUMN can_trade    BOOLEAN    NOT NULL DEFAULT FALSE,
    ADD COLUMN capital_base VARCHAR(8) NOT NULL DEFAULT 'ALL' CHECK (capital_base IN ('ALL', 'VEHICLES', 'CASH'));
ALTER TABLE panel_user ADD CONSTRAINT panel_user_login_check
    CHECK (NOT can_login OR (phone IS NOT NULL AND password_hash IS NOT NULL));

ALTER TABLE account      ADD COLUMN user_id            UUID REFERENCES panel_user(id);
ALTER TABLE vehicle_share ADD COLUMN user_id           UUID REFERENCES panel_user(id);
ALTER TABLE vehicle      ADD COLUMN consignor_user_id  UUID REFERENCES panel_user(id);
ALTER TABLE payment_plan ADD COLUMN holder_user_id     UUID REFERENCES panel_user(id);

-- ---------------------------------------------------------------- eslesme
-- Galeri basina havuz sahibi: en once acilan aktif ADMIN (yoksa en once acilan ADMIN)
CREATE TEMP TABLE pool_owner AS
SELECT DISTINCT ON (tenant_id) tenant_id, id AS user_id
FROM panel_user WHERE tenant_id IS NOT NULL AND role = 'ADMIN'
ORDER BY tenant_id, is_active DESC, created_at, id;

-- Kullaniciya tasinacak kisiler: dukkan sahibi, sermayesi, arac payi, konsinye araci ya da evraki olanlar
CREATE TEMP TABLE moved_contact AS
SELECT c.id AS contact_id, c.tenant_id, c.name, c.is_owner, c.capital_base, CAST(NULL AS UUID) AS user_id
FROM contact c
WHERE c.is_owner
   OR EXISTS (SELECT 1 FROM account a WHERE a.contact_id = c.id AND a.type = 'CAPITAL')
   OR EXISTS (SELECT 1 FROM vehicle_share s WHERE s.contact_id = c.id)
   OR EXISTS (SELECT 1 FROM vehicle v WHERE v.consignor_id = c.id)
   OR EXISTS (SELECT 1 FROM payment_plan p WHERE p.holder_contact_id = c.id);

-- Dukkan sahibi kisi: ayni adli ADMIN, yoksa havuz sahibi
UPDATE moved_contact m SET user_id = COALESCE(
    (SELECT u.id FROM panel_user u WHERE u.tenant_id = m.tenant_id AND u.role = 'ADMIN'
        AND lower(btrim(u.full_name)) = lower(btrim(m.name)) ORDER BY u.created_at, u.id LIMIT 1),
    (SELECT p.user_id FROM pool_owner p WHERE p.tenant_id = m.tenant_id))
WHERE m.is_owner;

-- Digerleri: ayni adli kullanici (en once acilan)
UPDATE moved_contact m SET user_id = (
    SELECT u.id FROM panel_user u WHERE u.tenant_id = m.tenant_id AND lower(btrim(u.full_name)) = lower(btrim(m.name))
    ORDER BY u.created_at, u.id LIMIT 1)
WHERE m.user_id IS NULL;

-- Eslesmeyenler: girisi kapali sermayedar
UPDATE moved_contact SET user_id = gen_random_uuid() WHERE user_id IS NULL;
INSERT INTO panel_user (id, tenant_id, phone, password_hash, full_name, role, is_active, can_login)
SELECT m.user_id, m.tenant_id, NULL, NULL, m.name, 'INVESTOR', TRUE, FALSE
FROM moved_contact m WHERE NOT EXISTS (SELECT 1 FROM panel_user u WHERE u.id = m.user_id);

-- Sermayesi / payi olan calisan sermayedar olur (bir kullanicinin tek rolu var)
UPDATE panel_user u SET role = 'INVESTOR' WHERE u.role = 'STAFF' AND u.id IN (SELECT user_id FROM moved_contact);
UPDATE panel_user u SET capital_base = m.capital_base FROM moved_contact m WHERE m.user_id = u.id;

-- ---------------------------------------------------------------- tasima
-- V3'teki isimsiz "cari / sermaye hesabinin kisisi olur" kisiti kalkar (yerine asagida account_owner_check)
DO $$
DECLARE c text;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'account'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%contact_id IS NOT NULL%'
    LOOP
        EXECUTE format('ALTER TABLE account DROP CONSTRAINT %I', c);
    END LOOP;
END $$;

-- Sermaye hesaplari her zaman; cari hesap kisinin baska (alici / satici / plan) baglantisi yoksa
UPDATE account a SET user_id = m.user_id, contact_id = NULL
FROM moved_contact m WHERE a.contact_id = m.contact_id AND a.type = 'CAPITAL';
UPDATE account a SET user_id = m.user_id, contact_id = NULL
FROM moved_contact m
WHERE a.contact_id = m.contact_id AND a.type = 'BALANCE'
  AND NOT EXISTS (SELECT 1 FROM vehicle v WHERE v.seller_id = m.contact_id OR v.buyer_id = m.contact_id)
  AND NOT EXISTS (SELECT 1 FROM payment_plan p WHERE p.contact_id = m.contact_id)
  AND NOT EXISTS (SELECT 1 FROM installment i WHERE i.payer_contact_id = m.contact_id OR i.endorsed_to_contact_id = m.contact_id);

-- Arac paylari: kisi -> kullanici, Havuz -> havuz sahibi
UPDATE vehicle_share s SET user_id = m.user_id FROM moved_contact m WHERE s.contact_id = m.contact_id;
UPDATE vehicle_share s SET user_id = p.user_id FROM pool_owner p WHERE s.contact_id IS NULL AND s.tenant_id = p.tenant_id;
-- Ayni araca ayni kullanici iki kez dustuyse (Havuz + dukkan sahibi kisi): tek satirda toplanir
UPDATE vehicle_share k SET share_bp = d.share_bp, profit_share_bp = d.profit_share_bp,
       contribution_source = 'CAPITAL', pocket_cents = NULL
FROM (SELECT min(id::text)::uuid AS keep_id, sum(share_bp) AS share_bp, sum(profit_share_bp) AS profit_share_bp
      FROM vehicle_share GROUP BY vehicle_id, user_id HAVING count(*) > 1) d
WHERE k.id = d.keep_id;
DELETE FROM vehicle_share s
WHERE EXISTS (SELECT 1 FROM vehicle_share o WHERE o.vehicle_id = s.vehicle_id AND o.user_id = s.user_id AND o.id::text < s.id::text);
-- Dukkan sahibinin payi havuz gibi: parasi kasadan
UPDATE vehicle_share s SET contribution_source = 'CAPITAL', pocket_cents = NULL
FROM panel_user u WHERE u.id = s.user_id AND u.role = 'ADMIN';

ALTER TABLE vehicle_share DROP CONSTRAINT IF EXISTS vehicle_share_source_check;
DROP INDEX IF EXISTS uq_vehicle_share_pool;
ALTER TABLE vehicle_share DROP COLUMN contact_id;
ALTER TABLE vehicle_share ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE vehicle_share ADD CONSTRAINT uq_vehicle_share_user UNIQUE (vehicle_id, user_id);
ALTER TABLE vehicle_share ADD CONSTRAINT vehicle_share_source_check
    CHECK ((contribution_source = 'HYBRID') = (pocket_cents IS NOT NULL));

-- Konsinye sahibi
UPDATE vehicle v SET consignor_user_id = m.user_id FROM moved_contact m WHERE v.consignor_id = m.contact_id;
ALTER TABLE vehicle DROP CONSTRAINT vehicle_consignment_check;
ALTER TABLE vehicle DROP COLUMN consignor_id;
ALTER TABLE vehicle ADD CONSTRAINT vehicle_consignment_check CHECK (
    (ownership = 'CONSIGNMENT'
        AND consignor_user_id IS NOT NULL
        AND ((commission_type = 'RATE' AND commission_rate_bp IS NOT NULL AND commission_cents IS NULL)
          OR (commission_type = 'FIXED' AND commission_cents IS NOT NULL AND commission_rate_bp IS NULL)))
    OR
    (ownership <> 'CONSIGNMENT'
        AND consignor_user_id IS NULL AND commission_type IS NULL AND commission_rate_bp IS NULL AND commission_cents IS NULL)
);

-- Evrak sahibi
UPDATE payment_plan p SET holder_user_id = m.user_id FROM moved_contact m WHERE p.holder_contact_id = m.contact_id;
ALTER TABLE payment_plan DROP COLUMN holder_contact_id;

-- Hesap: sermaye hesabi kullanicinin; cari kisinin ya da kullanicinin
ALTER TABLE account ADD CONSTRAINT account_owner_check CHECK (
    (type = 'CAPITAL' AND user_id IS NOT NULL AND contact_id IS NULL)
    OR (type = 'BALANCE' AND (contact_id IS NULL) <> (user_id IS NULL))
    OR (type NOT IN ('CAPITAL', 'BALANCE') AND user_id IS NULL));
CREATE INDEX idx_account_user ON account(user_id) WHERE user_id IS NOT NULL;

-- Kisi: dukkan sahibi isareti ve ortaklik tabani kullaniciya gecti; baska kaydi kalmayan tasinan kisiler silinir
ALTER TABLE contact DROP COLUMN is_owner;
ALTER TABLE contact DROP COLUMN capital_base;
DELETE FROM contact c
WHERE c.id IN (SELECT contact_id FROM moved_contact)
  AND NOT EXISTS (SELECT 1 FROM account a WHERE a.contact_id = c.id)
  AND NOT EXISTS (SELECT 1 FROM vehicle v WHERE v.seller_id = c.id OR v.buyer_id = c.id)
  AND NOT EXISTS (SELECT 1 FROM payment_plan p WHERE p.contact_id = c.id)
  AND NOT EXISTS (SELECT 1 FROM installment i WHERE i.payer_contact_id = c.id OR i.endorsed_to_contact_id = c.id);

-- ---------------------------------------------------------------- yetim acilis hareketleri
-- Plani silinmis (installment / payment_plan elle bosaltilmis) gecmis senet / cek acilislari ters hareketle kapanir
INSERT INTO ledger_entry (id, tenant_id, entry_date, type, source_account_id, target_account_id, amount_cents, description,
                          corrected_entry_id, created_by_id)
SELECT gen_random_uuid(), e.tenant_id, e.entry_date, e.type, e.target_account_id, e.source_account_id, e.amount_cents,
       'Düzeltme: ' || e.description, e.id, e.created_by_id
FROM ledger_entry e
WHERE e.type = 'OPENING_BALANCE' AND e.description LIKE 'Geçmiş %(senet / çek)'
  AND e.corrected_entry_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM ledger_entry r WHERE r.corrected_entry_id = e.id)
  AND NOT EXISTS (SELECT 1 FROM payment_plan p WHERE p.opening_entry_id = e.id);
