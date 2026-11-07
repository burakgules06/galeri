-- Calisan icin "arac masrafi girebilir" yetkisi (can_trade'den bagimsiz). Sadece STAFF'ta anlamli.
ALTER TABLE panel_user ADD COLUMN can_add_expense BOOLEAN NOT NULL DEFAULT FALSE;
