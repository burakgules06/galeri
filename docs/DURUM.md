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
    Gönderim `aes128gcm` (iOS için şart). Saat artık Hesabım > Bildirimler'den değiştirilir (bildirim saati geliştirmesi).
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

- **Adım 4B: konsinye ve ortaklı araç** (`feat/consignment`, `feat/pwa` üstünde). Kurallar `MIMARI.md` §4 Sahiplik.
  - `V7__consignment.sql`: `vehicle.consignor_id`, `commission_type` (RATE/FIXED), `commission_rate_bp`, `commission_cents` + CHECK
    (konsinyede dolu, değilse boş); `vehicle_share` (vehicle_id, contact_id, share_bp); hareket türüne `CONSIGNMENT_SETTLEMENT`
    (`ledger_entry.type` 20'den 30 karaktere genişledi).
  - Konsinye: alış deftere yazılmaz (alış bedeli, satıcı, alış planı tutulmaz). Satışta normal satış + plan, ek olarak
    BALANCE(sahibi) → INCOME (satış − komisyon). Komisyon kuruşa yarım yukarı (`util/ConsignmentMath`). Satış geri alınınca ya da
    sahip/komisyon değişince bu hareket ters hareketle yeniden yazılır; Kasa'dan elle düzeltilemez. Sabit komisyon satıştan büyükse 1034.
  - Konsinye masraf: "Sahibine yansıt" → BALANCE(sahibi); değilse EXPENSE (genel gider). İkisi de araca bağlı; araç kârı =
    komisyon − galerinin üstlendiği masraf. Sahibine ödeme: `POST /api/contacts/{id}/payments` (PAYMENT, kısmi olabilir), kişi carisinde "Öde".
  - Ortaklı: paylar toplamı 10000 (1033); defter galeri aracı gibi; maliyet kartında kâr paylara bölünür (kalan kuruş büyük paya).
  - Ana ekran: konsinye stok ayrı sayılır, stok maliyetine ve ortalama güne girmez; aylık kârda komisyon ayrı (`MonthStat.commissionCents`).
  - Düzeltilen hata: araç masraf toplamları VEHICLE hesabına dokunmayan masrafları eksi sayıyordu; artık sadece VEHICLE hesabı.
  - STAFF: listede ve detayda sadece `ownership` rozeti; sahip, komisyon, tutar dönmez.
  - Hata kodları: 1032 konsinyede sahip/komisyon yok, 1033 ortak payları, 1034 komisyon satıştan büyük.
  - Dev seed: 214 Golf (Burak Gules, %3, stokta, yansıtılan + üstlenilen masraf), 215 C-HR (Deniz Aksoy, sabit 40.000, satıldı,
    sahibine 1.410.000 borç, 1.000.000 ödendi), 216 Megane (ortaklı Furkan %60 / Kerem %40). **Seed değişti: `docker compose down -v`, `docker compose up -d`.**

- **Adım 5: sermaye, kâr ve kasa işlemleri** (`feat/capital`). Kurallar `MIMARI.md` §5.
  - `V8__capital_and_treasury.sql`: hesap türüne `OPENING` (galeri başına tek), hareket türlerine `PROFIT_ALLOCATION`, `TRANSFER`,
    `OPENING_BALANCE`. Sadece kısıt ve index; veri değişmez.
  - Ortaklar (`CapitalService`): kişiyi ortak yap (CAPITAL hesabı açılır), dükkân sahibini işaretle (eski sahip kalkar), sermaye girişi
    CAPITAL(ortak) → kasa/banka, çekişi ters yön (sermayeden fazla olamaz). Paylar her zaman CAPITAL hareketlerinden.
  - Dönem kârı (iki tarih dahil): satılan galeri araçlarının kârı + konsinye komisyonu (ayrı satır) − genel giderler (EXPENSE hesabına
    net giriş). Özel ortaklı araç kârı ayrı gösterilir, nete girmez. Ortak payı = net × (dönem sonu sermayesi, bu dönemin kâr payı
    eklemeleri hariç); öneri kâr payı eklendikçe kaymaz. Kâr payını sermayeye ekleme elle: **CAPITAL(ortak) → INCOME**
    (MIMARI'deki "INCOME → CAPITAL" defterin işaret kuralıyla sermayeyi azaltırdı; düzeltildi).
  - Virman (TRANSFER, iki para hesabı arasında) ve açılış bakiyesi (OPENING ↔ kasa / banka / cari, hesap başına bir kez; Kasa'dan
    düzeltilirse yeniden girilebilir). Hepsi Kasa'dan "Düzelt" ile terslenebilir.
  - Uçlar (ADMIN): `GET/POST /api/partners`, `PUT /api/partners/{id}/owner`, `POST /api/partners/{id}/capital`,
    `GET /api/reports/period-profit?from=&to=`, `POST /api/profit-allocations`, `POST /api/transfers`, `POST /api/opening-balances`.
  - Hata kodları: 1035 ortak değil, 1036 çekiş sermayeden büyük, 1037 virman aynı hesap, 1038 açılış bakiyesi var, 1039 dönem ters.
  - Panel: Kasa'da "Hareketler / Ortaklar ve sermaye / Dönem kârı" sekmeleri, başlıkta "Açılış bakiyesi" ve "Virman".
  - Seed değişmedi.

- **Sayı girişleri** (`fix/number-inputs`). Bütün tutar, kilometre, taksit sayısı, yüzde ve telefon alanları `NumberInput`:
  yazarken `1.250.000,50` / `64.000` / `0530 111 22 33`. Önceden "1250.50" yazılınca noktalar silinip 125.050 TL oluyordu;
  artık nokta yok sayılır ve kullanıcı farkı ekranda görür. Kuralı `design/README.md` "Sayı girişi". Sadece panel.

- **Revizyon PR 1: hızlı düzeltmeler** (`fix/quick-fixes`; talimat `revizyon-ortak-finansman.md`). Migration yok, seed değişmedi.
  - Masraf düzenle / sil: `PUT /api/expenses/{id}` (eski kayıt terslenir + yeni kayıt, tek transaction), `DELETE /api/expenses/{id}`
    (ters kayıt). Sadece geçerli `VEHICLE_EXPENSE` / `GENERAL_EXPENSE`; başka hareket 1040, iki kez silme 1015. Defter değişmez.
    Panelde masraflarda "Düzenle / Sil", düzeltme çiftleri varsayılan gizli ("Düzeltmeleri göster"), Maliyet kartı ve Kasa aynı.
  - Eşzamanlılık: taksit tahsilatı koşullu güncelleme (`markPaid ... where paid_entry_id is null`; 0 satır → 1019, transaction geri
    alınır). Sermaye çekişinde sermaye hesabı `SELECT … FOR UPDATE`. Ters kayıtta hareket satırı kilitlenir (aynı kayıt iki kez terslenmez).
    Gerçek Postgres testi: `ConcurrentCollectTest`.
  - Kişi / kurum seçicide "+ Yeni kişi / kurum" (plan kurucudaki kurum seçicileri dahil).
  - Tutar alanında önizleme ve "Kuruş için virgül kullanın" uyarısı. `NumberInput`'ta tümü değiştirilen değerden rakam silen hata düzeltildi.
  - Not: iki istek aynı kişinin carisini ilk kez aynı anda açarsa tekillik kısıtı ikincisini 500 ile reddeder (veri bozulmaz). Gerçek
    akışta cari satışta açıldığı için tahsilatta görülmez; gerekirse "insert … on conflict" ile çözülür.
- **Revizyon PR 2: marka / model kataloğu** (`feat/vehicle-catalog`). Migration `V9__vehicle_catalog.sql` (yeni tablo, veri silmez).
  Seed değişti (BMW modeli "3 Serisi 320i M Sport"): lokal DB'yi sıfırla.
  - Katalog `catalog/vehicles.json` (50 marka, 465 model) + galeri eklemeleri (`catalog_entry`). `GET /api/catalog/makes`,
    `GET /api/catalog/makes/{make}/models` (giriş yapan herkes). Kayıtta marka/model doğrulanır (1041), yazım katalogdakine çevrilir.
  - Panel: araç formunda aranabilir marka → model seçici + "Paket / motor"; "Listede yok" serbest yazım ve "kataloğa eklensin mi?"
    (eklenmezse kaydedilmez). Galeri eklemeleri listede "galeri eklemesi" rozetiyle. Katalog öncesi kayıt "dokunmadıkça aynen kalır".
  - Araç detayında adres başka araca geçince açık düzenleme penceresi eski aracı gösteriyordu; sayfa araç değişince sıfırlanıyor.
  - Para doğruluğu: `MoneyInvariantsIT` (gerçek Postgres, `./mvnw verify`'da failsafe ile). Sermaye, virman, açılış, peşin/planlı alış,
    masraf ekle/düzenle/sil, taksitli satış, tahsilat/geri al, satışı geri al, konsinye, genel gider, kâr payı, sermaye çekişi, düzeltme;
    her adımdan sonra: bakiyeler toplamı 0, toplam mal = sermaye + açılış + dağıtılmamış kâr, rapor kârı − eklenen = defter,
    ortak sermayeleri = defter, plan toplamı = taksitler, ödenmiş taksitin hareketi geçerli ve aynı tutarda, bağsız tahsilat yok.
  - TC0-TC13 altın senaryosu `GoldenScenarioIT` ile gerçek Postgres'te çalışıyor; PR 3 ortak araç adımları ayrıca `CoFinancingIT`'de.
    PR 3, PR 4'teki varlık/borç denge ekranından bağımsız olarak her ortakta sermaye = nakit + araçta bağlı katkı invariant'ını da sınar.

- **Revizyon PR 3: ortak finansmanı** (`feat/co-financing`). Migration `V10__co_financing.sql`; dev seed ortak sahipleri/kaynaklarıyla güncellendi.
  Lokal DB'yi sıfırla: `docker compose down -v`, ardından `docker compose up -d`.
  - `CO_OWNED` artık Havuz ve/veya ortak sahip satırlarından oluşur. Sahiplik ve kâr payı oranları ayrı ayrı toplam 10000 baz puan;
    Havuz için kişi/cari/sermaye hesabı açılmaz. Eski paylar kâr oranına taşınır, mevcut ortak kayıtları korunur.
  - Katkı kaynağı `CAPITAL` / `POCKET` / `HYBRID`; katkı ödenmiş alış hareketlerinden hesaplanır. Cepten kısım aynı işlemde
    `CAPITAL(ortak) → CASH/BANK` olarak yazılır; taksitli alışta her ödeme sahiplik payına göre bölünür, kuruş farkı Havuz'a gider.
    Nakit sermaye yetersizse 1042. Bağlı sermaye saklanmaz; `ortak sermayesi − stoktaki ortak araç katkısı` ile nakit sermaye bulunur.
  - Satışta politika `CoOwnerSettlementPolicy` üzerinden `AT_SALE`: alıcı taksitleri beklenmeden `VEHICLE_PROFIT_SHARE` hareketleri
    yazılır. Ortak kârı sermayeye, zarar katkıya kadar sermayeden; katkıyı aşan zarar ortağın carisine yansır. Havuz payı ve dükkân payı
    dönem net kârına katılır; ortaklara yazılan kâr payı katılmaz. Satışta ortak başına sermayede bırak / nakit öde / hibrit dönüş seçilir.
    Satışı geri almadan önce nakit dönüş hareketi düzeltilmeli (1043); geçersiz dönüş 1044.
  - Havuz kâr ağırlığı sadece ortakların nakitteki sermayesinden hesaplanır. Yeni ortak seçilirse otomatik ortak yapılır. Panel araç formu
    sahiplik, katkı kaynağı, farklı kâr oranı ve dükkân payını; satış formu ortak dönüşünü sunar.
  - Doğrulama: izole PostgreSQL `galeri_codex_test` üzerinde `./mvnw -B verify` başarılı (14 `CoFinancingIT`, `GoldenScenarioIT`,
    `MoneyInvariantsIT`); panel `npm run lint` ve `npm run build` başarılı. Satış payı zamanlaması bilinçli olarak satış anıdır;
    PR açıklamasında değiştirilebilir `AT_SALE` kararı belirtilecek.

- **Revizyon PR 4: toplam mal ve dağılım** (`feat/net-worth`). Migration, yeni ortam değişkeni ve seed değişikliği yok.
  - ADMIN için `GET /api/net-worth`; hareketlerden kasa/banka, brüt stok maliyeti, alacak/verecek, sermaye, dağıtılmamış kâr,
    açılış karşılığı ve denge farkı hesaplanır. Okuma aynı veritabanı görünümüyle (`REPEATABLE_READ`) yapılır.
  - Kasa > Varlıklar: sermaye halkası, nakit/bağlı sermaye şeritleri, ortakların hangi araçta ne kadar katkısı olduğu,
    araç sahiplerinin brüt mal / ödenmemiş senet / net payları. Konsinye stok maliyetine girmez. Denge farkı kırmızı gösterilir.
  - Açılış bakiyesi ayrı denge kalemidir; masraf dahil net araç varlığı, masraf hariç bağlı sermayeden ayrı gösterilir.
  - Testler: senetli 2M araçta başlangıçta kişi başına 1M mal / 1M borç / 0 net; 800K ödeme sonrasında 1M / 600K / 400K;
    tenant izolasyonu, ADMIN/STAFF yetkisi; altın senaryo ve para değişmezlerinin her adımında yeni uç için denge kontrolü.
  - Görsel tarayıcı kontrolü bekliyor: oturumda bağlı tarayıcı bulunamadı.
  - Doğrulama: izole `galeri_codex_test` üzerinde `./mvnw -B verify` başarılı (173 test + 19 entegrasyon testi);
    panel `npm run lint` ve `npm run build` başarılı. Kuruşlu taksitlerin tamamı ödendiğinde sahip borçlarının sıfırlandığı da sınandı.

- **Dükkân sahibi modeli** (`feat/owner-pool`, `V16__owner_pool.sql`). Yeni ortam değişkeni yok.
  - Havuz = kasa + bankalar + araçlar + alacaklar − borçlar. Sermayedarın sermayesi defterden; dükkân sahibininki havuzdan kalan.
    Giderler ve çekişler sadece dükkân sahibini küçültür. Kâr sadece araç satışında, araç alınırken girilen kâr yüzdesiyle;
    kasadaki nakit kâr getirmez, ay sonu dağıtımı yok (Dönem kârı sekmesindeki "sermayeye ekle" kaldırıldı).
  - Dükkân sahibinin araç payı, stok net araç değerinden diğer sermayedarların ödenmiş katkıları çıkarılarak bulunur; ortak araç masrafları artık araç değerinde kalır, alacak/borç artığına yazılmaz.
  - `GET /api/holders`: kişi başına sermaye, nakit, araçta (araç araç gerçek tutar), alacak/borç ve bilgi amaçlı yüzde.
    Yüzde ortaklık tabanına göre (`contact.capital_base`: Tüm varlık / Araçlar / Nakit), ortak eklerken seçilir, sonra değişir.
  - `POST /api/capital-transfers` (`CAPITAL_TRANSFER`): herkes herkese aktarır; kasa değişmez. Sermayedar en fazla nakitteki parasını
    verir/çeker (1036); dükkân sahibi sınırsız, kasadaki payı biterse panel uyarır (masraf, çekiş, aktarım).
  - Panel: ana ekran, Kasa > Hareketler ve Ortaklar sekmesi aynı sermayedar kartını (`/api/holders`) kullanır.
  - Test: `HoldersIT` (50M + 6M örneği; galeri aracı, ortak araç, gider, aktarım, taban, satış, sahip çekişi; her adımda değişmezler).

- **Araç ve ortak kaldırma** (`feat/remove-vehicle-partner`, `V17__remove_vehicle_and_partner.sql`). Yeni ortam değişkeni yok.
  - `DELETE /api/vehicles/{id}`: sadece stoktaki araç; alış, peşinat, masraflar ve ortak katkıları terslenir (para geldiği yere döner),
    alış planı silinir, araç `REMOVED` olur ve listelerden / raporlardan kalkar. Satılmışsa ya da peşinat dışında ödenmiş alış
    taksiti varsa 1054.
  - `DELETE /api/partners/{contactId}`: nakitteki parası girdiği hesaplara (en çok girilenden) çekiş olarak iade edilir, kalan
    (aktarım / kâr payı) kasadan; sermaye hesabı `closed` olur. Dükkân sahibi ya da araçta parası olan kaldırılamaz (1055).
    Yeniden ortak yapılınca aynı hesap açılır.
  - Panel: araç detayında "Kaldır", Ortaklar listesinde "Kaldır" (onaylı). Test: `RemoveIT`.

- **Sermayedarın geçmiş evrakı** (`feat/open-item-holder`, `V18__open_item_holder.sql`). Yeni ortam değişkeni yok.
  - Geçmiş senet / çek (bize borçlu) girilirken "Kimin" seçilir (Dükkân ya da bir sermayedar; `payment_plan.holder_contact_id`).
  - Sermayedarın evrakı tahsil edildikçe (ya da ciro edilince) tutar onun sermayesine yazılır: sermaye hesabı → açılış hesabı
    (`CAPITAL_TRANSFER`, taksitte `holder_entry_id`). Ödeme geri alınınca / karşılıksız çıkınca terslenir. Tahsil edilene kadar
    alacak dükkân sahibinin payında görünür. Dükkân sahibi ya da ortak olmayan seçilemez (1035). Test: `HolderItemsIT`.
  - Güncelleme: tahsil edilmemiş evrak sermayedarın kartında "Alacak" olarak görünür ve sermayesine sayılır (riski onda;
    dükkân sahibinin payı etkilenmez). Tahsilde alacaktan nakde geçer, geri alınınca / karşılıksız çıkınca yine alacak olur.
    Adına tahsil edilmemiş evrak olan ortak kaldırılamaz (1055).

## Sıradaki

Galerici geri bildirimi 4 (`feat/basket-sale`): toplu satış. `V14__basket_sale.sql`: `payment_plan_vehicle` (sepet planı araçsızdır;
araç ve satış bedeli burada, bir araç en fazla bir sepette). `POST /api/vehicles/basket-sale` { saleDate, buyerId, soldById, items[{vehicleId,
salePriceCents}], installments } → her araç SOLD ve kendi bedeliyle; satış hareketi araç başına, ödeyenlerin tutarları araçlara sırayla
(şelale) dağıtılır; taksitler toplam üzerinden tek plan. Sepetteki herhangi bir araçtan "Satışı geri al" bütün sepeti geri alır.
Ortak araçta ortağa dönüş seçilmez (pay sermayede kalır). Hata kodu 1053. Panel: satış formunda "+ Aynı alıcıya araç ekle",
ödeme planı kartında sepet listesi. Test: `BasketSaleIT`.

Galerici geri bildirimi 3 (`feat/open-items`): geçmiş senet / çek aktarımı. `V13__open_items.sql`: `payment_plan.vehicle_id`
boş olabilir, `opening_entry_id` (plan toplamını cariye yazan `OPENING_BALANCE`). `POST /api/contacts/{id}/open-items`
{ direction SALE (bize borçlu) | PURCHASE (biz borçluyuz), date, installments } → OPENING → BALANCE ya da BALANCE → OPENING,
taksitler açık girer ve aracı olanlarla aynı tahsil / öde / ciro / karşılıksız akışını kullanır. `DELETE /api/contacts/open-items/{planId}`
hiç ödenmemişse açılış hareketini tersler, planı siler (1052). Bu açılış hareketi Kasa'dan düzeltilemez ve kişinin ayrı
açılış bakiyesini engellemez. Panel: kişi detayında "Geçmiş senet / çek" formu ve "Kaydı sil". Test: `OpenItemsIT`.

Galerici geri bildirimi 2 (`feat/cheques`): çek ve senet takibi. `V12__cheques_and_endorsement.sql`: ödeme yöntemine `CHEQUE`
(banka, keşideci), taksite ciro (`endorsed_to_contact_id`, `endorse_entry_id`) ve karşılıksız (`bounced_at`), hesap türüne
`PORTFOLIO` (ciroda ara hesap, bakiyesi hep 0). Ciro: `POST /api/installments/{id}/endorse` { toContactId, targetInstallmentId?, date }
→ BALANCE(veren) → PORTFOLIO (taksit ödendi) + PORTFOLIO → BALANCE(alan); hedef alış taksiti çekten büyükse bölünür, seçilmezse
kişinin carisine ödeme. Ciro iki taraftan da geri alınır (ikisi birlikte). Karşılıksız: `POST /{id}/bounce` tahsilatı ya da ciroyu geri alır,
borç açılır. Kısmi tahsilat: `collect` { amountCents } taksitten azsa taksit bölünür (çek bölünmez). Hata kodları 1048-1051.
Panel: plan kurucuda Çek (no, banka, keşideci), Ödemeler'de "Çek / Senet" sekmesi (Alınan / Verilen), satırda Ciro et / Karşılıksız,
tahsilde tutar. Test: `ChequeIT` (gerçek Postgres, her adımda `LedgerInvariants` + portföy bakiyesi 0).
Galerici geri bildirimi 1 (`feat/bulk-expense`): Kasa > "Masraf ekle" artık toplu giriş (tarih ve hesap üstte bir kez,
satırlarda araç / kategori / tutar / açıklama, Enter yeni satır). `POST /api/expenses/bulk` hepsini tek transaction'da yazar.
`NameUtil`: tamamı büyük ya da tamamı küçük yazılmış kişi adı, kullanıcı adı ve renk kelime başı büyük kaydedilir (karışık yazıma dokunulmaz;
mevcut kayıtlar değişmedi). Alış tarihi araç formunda tekrar görünür; satış formunda tarih alıştan önceyse açık uyarı.
Migration yok.

Bildirim saati (`feat/reminder-time`, `bildirim-saati.md`): `V11__reminder_time.sql` ile tenant'a `reminder_time` (varsayılan 09:00)
ve `reminder_sent_on` eklendi. Görev her dakika Europe/Istanbul'a göre saati gelen galerileri günde bir kez işler; taksit/abone/VAPID
yoksa da günlük işaret yazılır. Saat değişince işaret sıfırlanır, mevcut `notification_log` aynı taksite aynı gün tekrar gönderimi engeller.
ADMIN için `GET/PUT /api/settings/reminder` ve `POST /api/push/test`; test yalnız çağıranın cihazlarına gider, log/günlük işaret yazmaz.
Hesabım'da Bildirimler kartı mevcut NotificationButton ile izin durumunu, saat seçimini ve test düğmesini gösterir.
Migration var (`migration` etiketi); yeni `.env` yok. Eski cron ayarı kaldırıldı. Seed ve bildirim metni/taksit seçimi değişmedi.
Doğrulama: izole `galeri_codex_test` üzerinde Maven `verify` başarılı (183 test + 20 entegrasyon testi); panel lint/build başarılı.
Sabit Clock ile 08:59/09:00/09:01, 14:29/14:30, gecikmeli sunucu açılışı ve aynı gün saat değişikliği sınandı.
Gerçek PostgreSQL testi saat filtresini, tenant izolasyonunu ve test gönderiminin log/günlük işareti değiştirmediğini doğruladı.

Ana ekran yerleşimi: sermaye + alım/satım grafiği ve kompakt alacak/verecek + kâr grafiği bağımsız sütunlarda akar;
kapalı detay kartı sermaye kartının yüksekliğine gerilmez. Telefonda özet kartları grafiklerden önce gelir.
Alacak/verecek kartında net rozeti, tam satır detay düğmesi ve kişilerin carisine bağlantı vardır. Panel lint/build başarılı.

Panel sadeleştirmesi (`feat/simplify-ui`, `sadelestirme.md`) uygulandı: açıklama/hint metinleri temizlendi; alacak/verecek
detayları varsayılan kapalı, sermaye kartında sahibe oranı kaldırıldı. Araç formunda sık kullanılan alanlar öne alındı,
diğer bilgiler kapalı bölüme taşındı; resmi bedel farklı değilse alış bedeli gönderilir, yeni araçta alan çalışan mevcut kullanıcıdır.
Yakıt/vites seçilen modelin galerideki kayıtlarında en sık değerden, kayıt yoksa ilk seçenekten gelir; düzenlemede mevcut değerler korunur.
Dükkân payı girişi kaldırıldı, istek alanları null gönderilir. Varlıklar iki karta indirildi, boyalı parça mavi yapıldı.
Backend, hesaplar, migration ve seed değişmedi. Panel lint/build ve ekle/düzenle istek gövdesi kontrolleri başarılı.
Görsel kontrol açık: bilgisayar kullanım aracı Chrome'un mevcut URL'sini doğrulayamadığı için durdu; masaüstü/telefon ekranları elle kontrol edilmeli.

Revizyon PR 5: kâr detayı (`feat/profit-drilldown`); yol haritasının raporlar ve ana ekranın son hali galerici geri bildirimiyle planlanacak.

Adım 5'ten açık sorular:
- Komisyon şimdilik dönem net kârına dahil (MIMARI §13); ayrı satırda gösteriliyor.
- Açılış bakiyesi sermaye için yok: sermaye girişi zaten "Sermaye girişi" ile yapılır.

Adım 4B'den açık sorular:
- Konsinye sahibinin galeriye borcu (yansıtılan masraf) için ayrı "tahsil et" yok; satışta sahibine borçla netleşir. Gerekirse eklenir.

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

