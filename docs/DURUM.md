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
- **Ana ekran ve görsel yenileme** (`feat/dashboard`, `refactor/english-names` üzerine). Tasarım kuralları **`design/README.md`**'de.
  - `V4__owner_and_balance_entries.sql`: `contact.is_owner` (galeri başına tek dükkân sahibi) ve hareket türlerine
    `VEHICLE_SALE` (INCOME → BALANCE(alıcı)), `COLLECTION` (BALANCE → CASH/BANK), `PAYMENT` (CASH/BANK → BALANCE).
    Bu türleri şimdilik sadece dev seed yazıyor; panelden satış/tahsilat girişi Adım 3'te.
  - `GET /api/dashboard` (ADMIN): kasa/banka, ortak sermayeleri ve payları (baz puan), alacak/verecek (cari bakiyesi +/−,
    en büyük 5), stok sayısı/maliyeti/ortalama gün, son 6 ay alım-satım adedi, ciro ve kâr.
  - `GET /api/reports/yearly-trades?year=` (ADMIN herkes, STAFF kendisi): Alım ve satım ekranı bunu aya/kişiye göre gruplar.
  - Araç araması ADMIN'de alıcı/satıcı adı ve telefonuyla da arar (`0530 111 22 33` biçimi normalize edilir); STAFF'ta kapalı.
    `VehicleSummary.buyer` sadece ADMIN'de dolu.
  - Panel: ana ekran (`#/`), koyu katlanır sol menü, Kasa'da ortak payları + cari, Alım-satım'da kişi seçimi + 12 aylık grafik +
    seçili ayın araçları, satılanlarda alıcı sütunu. Grafikler bağımlılıksız SVG (`panel/src/shared/charts`).
  - Dev seed zenginleşti: 2. çalışan Mert (`5552222222`), ortaklar Furkan (sahip) / Selin / Kerem, 8 aya yayılmış 13 araç,
    alacak (Ayşe, Emre, Eminevim) ve verecek (Mehmet, senetli alış). Seed değiştiği için lokal DB'yi yine sıfırla.
- Lokal not: panel `localhost:5173`'te açılmalı (backend dev'de sadece bunu kabul eder); 5173 başka projede doluysa onu kapat.

- **Adım 3A: satış deftere, ödeme planı, tahsilat** (`feat/sale-payment-plan`). Kurallar `MIMARI.md` §6.
  - `V5__payment_plan.sql`: `payment_plan`, `installment`. Satış artık alıcı + plan ister, deftere ödeyen başına `VEHICLE_SALE`
    yazılır (Evim kısmı kurumun carisine), "şimdi alındı" satırlar aynı işlemde `COLLECTION` ile kapanır.
  - Uçlar: `POST/DELETE /api/vehicles/{id}/sale`, `GET /api/vehicles/{id}/payment-plans`,
    `GET /api/installments?status=OVERDUE|UPCOMING|PAID&direction=&from=&to=`, `POST /api/installments/{id}/collect|undo`,
    `PATCH /api/installments/{id}` (vade, senet no), `GET /api/contacts/{id}/statement`. `/api/dashboard`'a geciken tahsilat
    ve bu hafta (bugün + 6 gün) eklendi. Hepsi sadece ADMIN.
  - Hata kodları: 1017 toplam tutmuyor, 1018 ödenmiş taksitli satış geri alınamaz, 1019/1020 taksit zaten ödenmiş / ödenmemiş,
    1021 kurum taksitinde kurum yok, 1022 araç satılmamış, 1023 bağlı hareket kasadan düzeltilemez.
  - Panel: plan kurucu satış formu (peşin, taksit/senet, Evim/kredi, karma), `#/payments` (Geciken / Bu hafta / Tümü),
    araç detayında "Ödeme planı" + "Satışı geri al", `#/contacts/<id>` kişi detayı ve cari özeti, ana ekranda geciken + bu hafta.
  - Dev seed: satışlar plana bağlandı (peşin: 206, 207, 208, 210; senetli: 211 Emre, 5 gün gecikmiş / bugün / gelecek ay;
    havale taksitli: 203 Ayşe, dün gecikmiş; Evim: 209 Hakan + Eminevim). **Seed değişti: `docker compose down -v`, `docker compose up -d`.**

- **Adım 3B: senetli ve taksitli alış** (`feat/purchase-payment-plan`, 3A'nın üstünde). Kurallar `MIMARI.md` §6 "Alış kuralları".
  - Migration yok. `VehicleRequest.purchasePlan` (ödenmemiş satırlar); alış ödemesi artık `PaymentPlanService.syncPurchase`
    üzerinden: peşinse eski akış, planlıysa BALANCE(satıcı) → VEHICLE ve "şimdi ödendi" satırlar için `PAYMENT`.
  - Hata kodları: 1024 plan toplamı alış bedeline eşit değil, 1025 satıcı yok, 1026 ödenmiş taksitli alış değiştirilemez.
  - Panel: plan kurucu ortak bileşen oldu (`features/payments/PlanBuilder.tsx`, satış ve araç formu kullanır); araç formunda
    "Peşin / Senetli-taksitli", ödenmiş taksitler kilitli gösterilir; araç detayında "Alış ödeme planı"; Ödemeler'de
    "Ödenecekler" sekmesi ("Öde"). Kişi carisinde sadece tahsilat/ödeme satırları yeşil.
  - Dev seed: 205 BMW (Mehmet) gerçek alış planı: 600K ödendi, 500K 5 gün sonra, 500K 35 gün sonra.
    **Seed değişti: `docker compose down -v`, `docker compose up -d`.**

- **Adım 3C: ödeme günü bildirimleri** (`feat/payment-notifications`). Kurallar `MIMARI.md` §9.
  - `V6__push_notifications.sql`: `push_subscription` (endpoint tekil, kullanıcı silinirse düşer), `notification_log`
    (unique `installment_id, notify_date`).
  - `PaymentReminderJob` her gün 09:00 Europe/Istanbul `PaymentReminderService.sendDailyReminders()`'ı çağırır: bugün vadeli +
    1-3 gün gecikmiş ödenmemiş taksitler, galeri başına tek bildirim, sadece aktif ADMIN abonelikleri. Hiç ADMIN abonesi
    yoksa kayıt yazılmaz (sonra izin verilirse pencere içinde yine hatırlatılır). 404/410 dönen abonelik silinir.
    Gönderim `aes128gcm` (iOS için şart). Lokal denemede saat `push.reminder-cron` ile değiştirilebilir.
  - Uçlar (sadece ADMIN): `GET /api/push/public-key` (boşsa bildirim kapalı), `POST/DELETE /api/push/subscribe`.
  - Yeni `.env`: `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT` (`npx web-push generate-vapid-keys`). Boşsa görev çalışmaz.
  - Panel: `public/sw.js` (bildirim, tıklayınca `#/payments`), menünün altında / telefonda üst çubukta zil düğmesi
    (turuncu nokta kapalı, yeşil açık, üstü çizili engelli). Çıkışta cihazın aboneliği silinir.
  - Migration var, seed değişmedi.

- **Adım 4A: ilk kurulum ve kullanıcı yönetimi** (`feat/users-bootstrap`). Migration yok.
  - İlk kurulum: `BootstrapRunner` → `BootstrapService`. Beş `BOOTSTRAP_*` değişkeninin hepsi doluysa ve hiç galeri yoksa
    galeriyi (`ACTIVE`) ve ADMIN'i oluşturur. Galeri varsa "Bootstrap atlandi" loglar, eksik/geçersiz değerde çalışmaz.
    Şifre loglanmaz. Kullanım aşağıda "Prod kurulumu".
  - Uçlar (ADMIN): `GET/POST /api/users`, `PATCH /api/users/{id}` (ad, rol, aktif), `POST /api/users/{id}/reset-password`.
    Herkes: `POST /api/auth/change-password` (mevcut şifre zorunlu). Şifre en az 8 karakter. Kullanıcı silinmez, pasifleştirilir.
  - Kurallar: telefon `PhoneUtil` ile normalize, geçerli cep telefonu ve tüm sistemde tekil. ADMIN kendini pasifleştiremez
    ve rolünü düşüremez; galeride en az bir aktif ADMIN kalır. Telefon sonradan değiştirilemez.
  - **JWT filtresi her istekte kullanıcıyı DB'den okur**: pasifleştirilen kullanıcının açık oturumu hemen 401 alır, rol değişikliği
    hemen geçerli olur (token'daki rol artık kullanılmıyor).
  - Hata kodları: 1027 telefon kayıtlı, 1028 son aktif yönetici, 1029 kendini değiştiremez, 1030 mevcut şifre hatalı, 1031 geçersiz telefon.
  - Panel: `#/users` Kullanıcılar (sol menü, sadece yönetici; telefonda Hesabım'dan), `#/account` Hesabım + Şifremi değiştir
    (herkes; sol menü altında isim, telefonda üst çubukta kişi ikonu).
  - Seed değişmedi.

- **Telefona kurulabilir panel (PWA)** (`feat/pwa`). Sadece panel.
  - `public/manifest.webmanifest` (Galeri Paneli, standalone, gece/zemin renkleri), plaka mavisi ikonlar (`icon.svg`,
    `icon-180/192/512.png`; 512 maskable da), `index.html`'de manifest, `theme-color`, `apple-touch-icon`, iOS etiketleri ve `viewport-fit=cover`.
  - Hesabım'da "Telefona kur" kartı: Android/Chrome'da "Ana ekrana ekle" düğmesi (`beforeinstallprompt`, `shared/pwa/install.ts`),
    iPhone'da Paylaş → Ana Ekrana Ekle tarifi; kurulu uygulamada görünmez. iPhone'da ödeme bildirimleri bu kurulumla çalışır.
  - Service worker (`sw.js`) önbellek tutmuyor; çevrimdışı çalışma yok (isteyen olursa ayrıca).
  - İkon değişirse: `icon.svg`'yi düzenle, PNG'leri ondan yeniden üret (180, 192, 512 px).

## Sıradaki

PR 4B: konsinye ve ortaklı araç (`feat/consignment`). Sonra PR 5: sermaye, kâr ve kasa işlemleri.

Adım 4A'dan açık sorular:
- Şifre sıfırlama/değiştirme açık oturumları kapatmıyor (token 24 saat geçerli). Gerekirse `panel_user.token_version` ile yapılır.
- Kullanıcı telefonu değiştirilemiyor; gerekirse yeni kullanıcı + eskisini pasifleştir.

Adım 3C'den açık sorular:
- iPhone'da web push sadece "Ana Ekrana Ekle" ile kurulmuş PWA'da çalışır (manifest PWA PR'ında eklendi; gerçek iPhone'da denenmedi).
- Bildirim gönderilen ADMIN'ler arasında hepsi başarısız olursa kayıt yazılmaz ve ertesi tetiklemede tekrar denenir (günde bir).

Adım 3A / 3B'den açık sorular:
- Planlı alışta Evim/kredi (kurumdan alış) yöntemi panelde sunulmuyor; gerekirse eklenir (backend destekliyor).
- **Kısmi tahsilat** yok: taksit tek seferde tahsil edilir. Müşteri senedin bir kısmını öderse ne olacak (taksiti bölmek mi,
  kısmi ödeme kaydı mı)? Galericiye sorulacak.
- Bütün taksitler ödenince araç `CLOSED` olmuyor (MIMARI §4'te var); listeler `SOLD`'a göre süzdüğü için ayrı ele alınmalı.
- 3A öncesi girilmiş (planı olmayan) satışlarda "Satışı geri al" sadece satış hareketlerini tersler; o satışlara bağlı
  elle girilmiş tahsilat varsa cari bozulur. Prod'da eski satış olmadığı için sadece dev için geçerli.

Adım 2'den kalan küçükler (gerekirse):
- Kasa ↔ banka aktarımı (virman) ve açılış bakiyesi yok; sermaye girişi 5. adımda (dev seed'de örnek CAPITAL hareketi var).
- Alt kategori backend'de var (`parentCategoryId`, en fazla 3 seviye), panelde henüz seçilemiyor.

## Prod kurulumu (henüz yapılmadı)

1. DNS: `galeri.akillirandevum.com` ve `api-galeri.akillirandevum.com` → sunucu IP'si (A kaydı).
2. Sunucuda root: `bash deploy/setup-galeri.sh` (aynı `randevu-db` container'ında ayrı `galeri` veritabanı,
   `/opt/galeri/.env`, `galeri.service`, Nginx + SSL). Tekrar çalıştırmak güvenli.
3. Repo secret'ları (`DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`, `DEPLOY_HOST`) eklenmiş mi kontrol et, sonra Actions > Deploy (prod).
4. Panel: `npm run build`, `dist` içeriği `/var/www/galeri-panel/`.
5. **İlk galeri ve ADMIN** (prod'da seed yok): `/opt/galeri/.env`'e bir kez yaz:
   ```
   BOOTSTRAP_TENANT_NAME=Galeri Adı
   BOOTSTRAP_TENANT_SLUG=galeri-adi          # küçük harf, rakam, tire
   BOOTSTRAP_ADMIN_NAME=Ad Soyad
   BOOTSTRAP_ADMIN_PHONE=0530 111 22 33      # giriş telefonu
   BOOTSTRAP_ADMIN_PASSWORD=...              # en az 8 karakter, girişten sonra Hesabım'dan değiştir
   ```
   `systemctl restart galeri` → `journalctl -u galeri | grep Bootstrap` ile "Bootstrap tamam" gör → bu beş satırı `.env`'den sil →
   tekrar restart. Galeri varken çalıştırılırsa hiçbir şey yapmaz ("Bootstrap atlandi"). Çalışanları sonra panelden Kullanıcılar'dan ekle.

## Açık sorular

`MIMARI.md` §13: konsinye komisyonu, takas. (Ana ekranın ilk hali yapıldı; galericiye gösterilip geri bildirim alınacak.)
