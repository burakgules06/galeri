# Nerede kaldık

Yeni bir oturuma başlarken önce bunu, sonra `docs/MIMARI.md`'yi oku. Her adım bitince bu dosyayı güncelle.

## Yapılanlar

- **Adım 1: kişiler ve araçlar** (PR #2, merge edildi). Kişi, araç, 13 parçalı hasar şeması, alan/satan çalışan,
  araç listesi/detayı/formu, satış formu, çalışan bazında aylık alım-satım. STAFF para alanı görmez.
  Dev seed'de ADMIN `5550000000` ve STAFF `5551111111` (ikisi de `demo1234`), örnek kişi ve araçlar.
- **Adım 2: para defteri** (`feat/para-defteri`). `V3__money_ledger.sql`: `account`, `expense_category`, `ledger_entry`.
  - Bakiye saklanmaz, hareketlerden hesaplanır. Hareket UPDATE/DELETE'i DB trigger'ı reddeder; "Düzelt" ters hareket atar
    (aynı tarihle, `corrected_entry_id` ile; bir hareket bir kez düzeltilir, düzeltme düzeltilmez).
  - Hareket `type`: `VEHICLE_PURCHASE`, `VEHICLE_EXPENSE`, `GENERAL_EXPENSE`, `CAPITAL` (yeni tür = yeni migration'da `ledger_entry_type_check`).
  - CASH, EXPENSE, VEHICLE hesapları ilk ihtiyaçta kendiliğinden açılır. Banka hesabı panelden eklenir.
  - Peşin araç alışı: araç formunda "Ödeme nereden" seçilir → CASH/BANK → VEHICLE. Bedel/tarih/hesap değişirse eski hareket
    terslenip yenisi yazılır (`LedgerService.syncVehiclePurchase`).
  - Masraf: araca bağlı (aracın maliyetine) ya da genel gider. Kategori yazarken oluşur (ilk harfi büyütülür,
    harf büyüklüğünden bağımsız tekil). En sık 5 kategori hızlı buton.
  - Araç maliyeti = gerçek alış (araçtaki alan) + defterdeki araç masrafları; kâr sadece satılmış araçta.
  - Panel: "Kasa" sekmesi (bakiyeler, hareketler, düzelt, banka ekle, masraf ekle), araç detayında "Maliyet" kartı.
  - Uçlar: `/api/accounts`, `/api/ledger-entries`, `/api/ledger-entries/{id}/correct`, `/api/expenses`,
    `/api/expense-categories?root=`, `/api/vehicles/{id}/cost`. Hepsi sadece ADMIN.
- **İngilizce isimlendirme** (`refactor/english-names`, `feat/para-defteri` üzerine). Kod, DB, API, JSON ve panel
  dosya/değişken adları İngilizce; sözlük `MIMARI.md` §14. Prod'a hiç çıkılmadığı için V2/V3 yerinde yeniden yazıldı
  (`V2__contact_and_vehicle.sql`, `V3__money_ledger.sql`); **lokal DB'yi sıfırla**: `docker compose down -v`, `docker compose up -d`.
  Panel rotaları da İngilizce: `#/vehicles`, `#/ledger`, `#/monthly`, `#/contacts`. CSS `@theme` token adları (`yuzey`, `plaka` ...) değişmedi.
- Lokal not: panel `localhost:5173`'te açılmalı (backend dev'de sadece bunu kabul eder); 5173 başka projede doluysa onu kapat.

## Sıradaki: Adım 3, satış ve tahsilat

Kapsam `MIMARI.md` §6 ve §12-3: satış kaydı deftere (INCOME → BALANCE(alıcı)), ödeme planı (peşin, taksit, senet, Evim, kredi),
tahsilat, geciken ödemeler, müşteri carisi. Satış şu an sadece araçtaki alanlara yazılıyor, deftere hareket atmıyor.

Adım 2'den kalan küçükler (gerekirse):
- Kasa ↔ banka aktarımı (virman) ve açılış bakiyesi yok; sermaye girişi 5. adımda (dev seed'de örnek CAPITAL hareketi var).
- Alt kategori backend'de var (`parentCategoryId`, en fazla 3 seviye), panelde henüz seçilemiyor.

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
