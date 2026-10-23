-- SADECE dev profilinde calisir (application-dev.properties). Prod'a hic gitmez.
-- Giris: telefon 5550000000, sifre demo1234
INSERT INTO tenant (id, slug, display_name)
VALUES ('00000000-0000-0000-0000-000000000001', 'demo-galeri', 'Demo Galeri')
ON CONFLICT (id) DO NOTHING;

INSERT INTO panel_user (tenant_id, phone, password_hash, full_name, role)
VALUES ('00000000-0000-0000-0000-000000000001', '905550000000',
        '$2b$10$x2nG.NANpgalZyfDs3w.4enIbpeoWlBLLvpacvPx8CAx3iarYrIOW', 'Demo Yonetici', 'ADMIN')
ON CONFLICT (phone) DO NOTHING;
