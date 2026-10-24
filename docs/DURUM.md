# Nerede kaldık

Yeni bir oturuma başlarken önce bunu, sonra `docs/MIMARI.md`'yi oku. Her adım bitince bu dosyayı güncelle.

## Yapılanlar

- **Adım 1: kişiler ve araçlar** (PR #2, merge edildi). Kişi, araç, 13 parçalı hasar şeması, alan/satan çalışan,
  araç listesi/detayı/formu, satış formu, çalışan bazında aylık alım-satım. STAFF para alanı görmez.
  Dev seed'de ADMIN `5550000000` ve STAFF `5551111111` (ikisi de `demo1234`), örnek kişi ve araçlar.
- **Lokal CORS** (PR #3): dev profilinde her `localhost` portu kabul. (Merge edilmediyse önce onu merge et.)

## Sıradaki: Adım 2, para defteri

Kapsam `MIMARI.md` §3, §7 ve §12-2'de. Özet:
- Hesaplar (KASA, BANKA ve diğer türler, §3 "Hesap türleri") ve hareket tablosu. Hareketler **silinmez**, düzeltme ters hareketle.
- Tutarlar **kuruş, BIGINT**. Bakiye hareketlerden hesaplanır.
- Araç alışı (peşin) deftere hareket olarak yazılır; araç maliyeti = gerçek alış + araca bağlı masraflar.
- Masraf girişi: kategori ağacı, kategori **yazarken oluşur** (ayrı kategori ekranı yok).
- Panel: kasa/banka bakiyeleri, hareket listesi, masraf ekleme, araç detayında maliyet. Sadece ADMIN.
- Testler: Mockito + sabit `Clock` (`AracServiceImplTest` deseni). Yeni migration: `V3__...`.

## Prod kurulumu (henüz yapılmadı)

1. DNS: `galeri.akillirandevum.com` ve `api-galeri.akillirandevum.com` → sunucu IP'si (A kaydı).
2. Sunucuda root: `bash deploy/setup-galeri.sh` (aynı `randevu-db` container'ında ayrı `galeri` veritabanı,
   `/opt/galeri/.env`, `galeri.service`, Nginx + SSL). Tekrar çalıştırmak güvenli.
3. Repo secret'ları (`DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`, `DEPLOY_HOST`) eklenmiş mi kontrol et, sonra Actions > Deploy (prod).
4. Panel: `npm run build`, `dist` içeriği `/var/www/galeri-panel/`.
5. **İlk ADMIN kullanıcısı**: prod'da seed yok (dev seed sadece dev profilinde). Galeri (tenant) ve ADMIN'i
   eklemenin yolu henüz yok; tek seferlik SQL ya da küçük bir komut olarak hazırlanacak.

## Açık sorular

`MIMARI.md` §13: konsinye komisyonu, takas, ana ekran.
