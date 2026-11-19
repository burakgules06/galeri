-- Sahibinden aciklamasindaki bilgi kartlari icin (hepsi istege bagli; bos ise kart cikmaz):
-- aracin ithalat durumu (serbest metin, or. "Distribütör"), muayene bitisi (ayin ilk gunu tutulur, ay/yil gosterilir)
-- ve dukkanin yetki belge numarasi.
ALTER TABLE vehicle ADD COLUMN import_status VARCHAR(60);
ALTER TABLE vehicle ADD COLUMN inspection_valid_until DATE;
ALTER TABLE tenant ADD COLUMN dealer_license_no VARCHAR(40);
