# Nerede kaldık

Oturum başında önce bunu oku (kısa tutulur). Bir özelliğe başlamadan önce `docs/MIMARI.md`'nin ilgili bölümünü oku.
Eski işlerin ayrıntısı `docs/GECMIS.md`'de; gerekmedikçe okuma, gerekirse `grep` ile ilgili bölümü bul.
Her iş bitince bu dosyayı güncelle: "Şu an" ve "Sıradaki"yi değiştir, bitmiş işi tek satırla "Son işler"e ekle. 150 satırı geçmesin.

## Şu an (4 Kasım 2026)

- Prod canlı ve kullanılıyor. main'e merge = otomatik prod deploy. Son migration `V25__user_counterparty.sql` (#76; yenisi V26).
- Açık PR: #75 (Kasa toplu hareketler), #76 (kullanıcıyla alış / satış, "satmak için al").
- Alacak / verecek sıfırlama scripti (`alacak-verecek-onizleme.sql` + `alacak-verecek-sifirla.sql`) Burak'a verildi; sonucu bildirilmedi.
- Prod veri sıfırlaması: `sifirla-pgadmin.sql` (pgAdmin Query Tool) Burak'a verildi. Test galerisi tamamen siliniyor; gerçek galeride
  araçlar, araç masrafları, çek / senet planları ve kullanıcılar dışındaki her şey (sermaye dahil) siliniyor. Sonucu henüz bildirilmedi;
  script sonunda kullanıcıyla aynı adlı kişi kalırsa listeler.

## Temel kurallar (ayrıntı MIMARI.md)

- Para kuruş (`long` cents). `ledger_entry` değişmez (trigger), düzeltme ters hareketle.
- Sermaye bakiyesi = CAPITAL hesabından çıkan − giren.
- Ortaklı satış: kâr = satış − alış − araç masrafları; sadece kâr kâr payı oranıyla bölünür. Ortağın alacağı = katkı + kâr payı − dükkân payı (≥ 0).
  Tahsil edildikçe orantılı dağıtılır (`CoOwnershipServiceImpl.distribution`, BigInteger `mulDiv`).
  Örnek: alış 2M, satış 2.3M, %60 → 180k / 120k.
- Ortaklı araçta masraf payı hemen ortağın sermayesinden araca bağlanır.
- Konsinye: alacak = satış − komisyon. Yansıtılan masraf önce bu paydan düşer.
- Roller: ADMIN (dükkân sahibi), INVESTOR (sermayedar), STAFF (para alanı görmez).
- Sermayedar sadece kendi araçlarını görür ve alıp satabilir. Alış hep CO_OWNED ve sermayeden. Tahsilatta "Sermayeme ekle" ya da "Nakit aldım".
- Kullanıcıyla alış / satış (V25): araç kullanıcıdan alınabilir (bedelin bir kısmı onun sermayesine: kasadan tam alış + CAPITAL→kasa girişi,
  `vehicle.purchase_capital_entry_id`). Kullanıcıya satış taksitsiz: INCOME→kasa (tahsil edilmiş sayılır, `directSaleCollections`).
  "Satmak için al": alanın nakit kısmı sermaye girişi, araç yeni kayıtla onun stoğuna (%100 ortaklı, sermayeden; dükkân sahibine galeri aracı),
  `transfer_from_vehicle_id`. Devirsiz: sermaye kısmı kasa→CAPITAL çekişi (`sale_capital_entry_id`). Geri alınca kopya kaldırılır.
- Kişiler (`contact.kind`): CUSTOMER / SELLER / SUPPLIER. Tüzel / bireysel ayrımı var. Kullanıcılar (çalışan, sermayedar) kişi olarak görünmez.

## Son işler

- #76 Kullanıcıyla alış / satış ve "satmak için al" (MIMARI §0 madde 5), `UserCounterpartyIT`.
- #75 Kasa son hareketlerde aynı gün / araç / kişi / hesap toplu işlemleri tek satır (`features/ledger/entryGroups.ts`).

- #59–#64 gece paketi: tahsilatla para dağıtımı, ödeme yöntemleri + kredi kartı (V23), Ödemeler yeni ekran, Kasa/Banka kişi bazında,
  Masraflar ve Dükkân sayfaları (cepten gider), sermayedar girişi (salt okunur).
- #65 Ödemelerde plan satırı: taksit sayısı, kimin hangi tarihte oluşturduğu.
- #66 İşlem geçmişinde "kimle alakalı" filtresi (işlemi yapan değil).
- #67 Kişiler sekmeleri: Tedarikçiler / Müşteriler / Alış yaptıklarım + tüzel/bireysel (V24); Kasa son hareketlerde saat.
- #68–#71 gece paketinin dalları (ödemeler, kasa, masraf/dükkân, sermayedar) main'e alındı.
- #72 Ana sayfada kasa, banka ve stok maliyeti kuruşuna kadar.
- #73 Sermayedar araç alıp satabiliyor; ortaklı araç masraf payı sermayeden hemen düşüyor.

## Sıradaki / Burak'ın kararını bekleyenler

- **Konsinye yeniden tasarımı (4 Kasım 2026, Burak onayladı, henüz kodlanmadı).** Kurallar:
  1. Konsinye formuna "Sahibi aracı kaça aldı" (maliyet) + alım türü (sermayeden / hibrit / cepten) eklenir. Araç girilince
     maliyet sermayedarın malvarlığına (araçlarına) yazılır; nakitteki parası alım türüne göre düşer (sermayeden: tamamı,
     hibrit: girilen sermaye kısmı, cepten: düşmez; cepten kısım sermayesine eklenir). Dükkân sahibinin parasına dokunulmaz.
  2. Konsinye araca masraf: girildiği anda seçilen kasa / bankadan, sermayedarın parasından düşer; toplam sermayesi de hemen
     düşer (örn. 1.000.000 → 980.000). Satışta bir daha düşülmez. Dükkân sahibinin parasına dokunulmaz.
     "Masrafı galeri üstlenir" seçeneği kalkar. Sermayedarın nakitteki parası yetmezse uyarı ("eksiye düşecek, devam?"),
     onaylanırsa eksiye düşer. Cepten masraf sadece takip içindir, paraya etkisi yok (nasıl saklanacağı açık).
  3. Satışta kâr = satış − maliyet (masraflar hariç). Galeri komisyonu **kârın yüzdesi** (ya da sabit tutar); zararda 0.
     Galerinin kârı komisyon; kalan kâr sermayedarın. Maliyet boşsa kâr = satış bedeli (bugünkü sonuçla aynı).
     Örnek: maliyet 800.000, satış 1.000.000, kâr 200.000, %3 → galeri 6.000, Zeki 194.000 kâr.
  4. Eski kayıtlar: "sahibine yansıtılan" (BALANCE) masraflar eski kuralla satışta netleşir; yeni masraflar yeni kuralla.

- Dükkân sahibinin komisyon kuralı (gece paketi soru 2, "şimdilik geç" dendi).
- Konsinye para dağıtımının zamanlaması (soru 4, "sonra soracağım" dendi).
- Token 24 saat sabit ve yenilenmiyor: kullanıcı gün içinde atılabilir. Akıllı Randevum'da kayan yenileme yapıldı (appointment #13);
  istenirse aynısı buraya.
- Konsinye komisyonu ve takas (MIMARI §13).

## Çalışma notları (agent için)

- Lokal Maven yok (indirme engelli); backend testleri sadece CI'da. CI PR açılınca çalışır.
- PR'ı agent REST API ile açar (`gh api repos/burakgules06/galeri/pulls`); `gh pr create` GraphQL engelli.
- Panel kontrolü: `npx tsc -b --noEmit`, `npx eslint <dosya>`; ekran için vite + Playwright.
- Entegrasyon testleri `*IT` (gerçek Postgres, `LedgerInvariants`).
