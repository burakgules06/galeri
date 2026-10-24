# Galeri: alan modeli ve mimari

Bu belge galericiyle yapılan görüşmelerden çıkan kararları tutar. Kod yazarken buradaki
kavramlara ve kurallara uy; bir karar değişirse önce bu belgeyi güncelle.

## 1. Uygulama ne işe yarar

Oto galeri sahibinin parasını takip etmesi için:

- Her aracın teknik bilgisi ve hasar durumu
- Her aracın maliyeti (alış + masraflar) ve satışta ne kazandırdığı
- Satış yapılan müşteriler ve onlardan alınacak ödemeler (peşin, taksit, senet, Evim sistemi)
- Kasada ve bankada ne kadar para olduğu
- Sermaye ortaklarının payı ve galerinin kârı
- Masrafların kategoriye göre raporu
- Hangi çalışanın hangi ay kaç araç aldığı ve sattığı
- Ödeme günü gelen tahsilat ve ödemeler için telefona bildirim

Fotoğraf yok. Döviz yok (her şey TL; ileride gerekirse eklenir).

## 2. Kavramlar

| Kavram | Anlamı |
|---|---|
| **Galeri (tenant)** | Uygulamayı kullanan işletme. Örnek: Furkan'ın dükkânı. |
| **Kişi** | Uygulamada adı geçen her insan ya da kurum: ortaklar, müşteriler, araç sattığımız kişiler, Evim şirketi, banka. Rolü sabit değildir; aynı kişi bir araçta alıcı, başka araçta konsinye sahibi olabilir. |
| **Sermaye ortağı** | Galeriye para koymuş kişi. Payı = kendi sermayesi / toplam sermaye. Örnek: 50 milyonluk sermayenin 2 milyonu Burak'ın → Burak %4. Galerinin toplam kâr ve zararı bu oranlarla paylaşılır. |
| **Konsinye araç** | Aracın sahibi galeri değil bir kişidir (örn. Burak'ın kendi aracı). Galeride satılır, galeri satıştan komisyon alır. Galerinin bu araca maliyeti yoktur, kârı sadece komisyondur. |
| **Özel ortaklı araç** | İstisna: bir araç havuz dışında, belirli kişilerle belirli oranlarla (örn. %50-%50) alınmış olabilir. Varsayılan değildir. |
| **Hareket** | Bir para hareketi. Sistemdeki bütün bakiyeler hareketlerden hesaplanır. |

## 3. Temel kural: hareket defteri

**Hiçbir bakiye tabloda saklanmaz.** Kasa, banka, müşteri borcu, ortak sermayesi, araç maliyeti:
hepsi `ledger_entry` (hareket) tablosundaki kayıtların toplamıdır.

Her hareket "hangi hesaptan → hangi hesaba, ne kadar" bilgisini taşır.

```
ledger_entry: entry_date | source_account → target_account | amount_cents | vehicle (ops.) | expense_category (ops.) | description
```

Bir hesabın bakiyesi = hesaba giren hareketler − hesaptan çıkan hareketler.

Kurallar:
1. **Hareket silinmez ve değiştirilmez.** Yanlış girildiyse aynı tutarda ters yönde bir düzeltme hareketi atılır (`corrected_entry_id` ile bağlanır). Ekranda "düzelt" böyle çalışır.
2. **Tutarlar kuruş cinsinden tam sayı** (`BIGINT`). 1.250.000,50 TL = `125000050`. `double`/`float` asla kullanılmaz.
3. Her hareketi kimin, ne zaman girdiği tutulur.

### Hesap türleri

| Tür | Kaç tane | Ne tutar |
|---|---|---|
| `CASH` (kasa) | Galeri başına 1 | Nakit |
| `BANK` | Galeri başına 1 veya daha fazla | Banka hesapları |
| `BALANCE` (cari) | Kişi başına 1 | O kişiyle aramızdaki alacak/borç |
| `CAPITAL` (sermaye) | Ortak başına 1 | Ortağın galerideki sermayesi |
| `VEHICLE` | Araç başına 1 | Aracın maliyeti (alış + araç masrafları) |
| `EXPENSE` (gider) | 1 | Araca bağlı olmayan genel giderler |
| `INCOME` (gelir) | 1 | Satış gelirleri ve komisyonlar |

Gösterimde işaret hesap türüne göre çevrilir: `CAPITAL` hesabından 2 milyon çıkmışsa ekranda
"Burak'ın sermayesi: 2.000.000 TL" yazar.

### Örnek akışlar

| Olay | Hareket(ler) |
|---|---|
| Burak 2.000.000 TL sermaye koydu | CAPITAL(Burak) → CASH 2.000.000 |
| Araç peşin alındı, 1.000.000 TL | CASH → VEHICLE(#12) 1.000.000 |
| Araç senetle alındı | BALANCE(satıcı) → VEHICLE(#12) 1.000.000; her senet ödendiğinde CASH → BALANCE(satıcı) |
| Araca boya masrafı, kasadan 5.000 TL | CASH → VEHICLE(#12) 5.000, kategori: Boya |
| Masrafı Furkan kendi cebinden ödedi | BALANCE(Furkan) → VEHICLE(#12) 5.000 (galeri Furkan'a borçlu) |
| Kira ödendi | CASH → EXPENSE 40.000, kategori: Kira |
| Araç 1.300.000 TL'ye satıldı | INCOME → BALANCE(alıcı) 1.300.000; araç "satıldı" olur |
| Alıcı 300.000 TL peşinat verdi | BALANCE(alıcı) → CASH 300.000 |
| Evim şirketi kalan 1.000.000 TL'yi ödedi | BALANCE(Evim şirketi) → BANK 1.000.000 (satışta bu tutar alıcının değil şirketin carisine yazılır) |
| Konsinye araç satıldı, komisyon %3 | INCOME → BALANCE(alıcı) 1.300.000 ve BALANCE(araç sahibi) → INCOME 1.261.000 (galeri sahibine borçlu); INCOME'da net 39.000 komisyon kalır |
| Ortaklar kâr payını sermayeye eklemeye karar verdi | INCOME → CAPITAL(Burak) X (elle girilir, bkz. 5) |

## 4. Araç

### Teknik bilgiler
Plaka, marka, model, model yılı, yakıt tipi, vites, kilometre, renk, tramer (hasar kaydı) tutarı, notlar.

### Hasar şeması (Sahibinden'deki gibi)
Aracın çizimi üzerinde 13 parça, her biri dört durumdan biri:

`ORIGINAL` (orijinal) · `LOCALLY_PAINTED` (lokal boyalı) · `PAINTED` (boyalı) · `REPLACED` (değişen)

Parçalar: ön tampon, motor kaputu, tavan, bagaj kapağı, arka tampon, sol ön çamurluk, sol ön kapı,
sol arka kapı, sol arka çamurluk, sağ ön çamurluk, sağ ön kapı, sağ arka kapı, sağ arka çamurluk.

### Sahiplik
- `DEALER` (galeri): varsayılan. Maliyeti VEHICLE hesabında birikir, kârı galerinin kârıdır.
- `CONSIGNMENT` (konsinye): sahibi bir kişidir, komisyon oranı ya da sabit tutarı tutulur. VEHICLE hesabına maliyet yazılmaz.
- `CO_OWNED` (özel ortaklı): havuz dışı; `vehicle_share` tablosunda kişi + yüzde (toplam %100). Kârı bu oranlarla gösterilir.

### Alış bilgileri
Alış tarihi, kimden alındı, **aracı alan çalışan**, **gerçek alış bedeli** ve **resmi (beyan edilen) alış bedeli**, ödeme şekli.
Kâr hesapları her zaman gerçek bedelle yapılır.

### Satış bilgileri
Satış tarihi, alıcı, **aracı satan çalışan**, satış bedeli, ödeme şekli.

### Durum
`IN_STOCK` → `SOLD` → `CLOSED` (satışın bütün tahsilatı tamamlandığında).
Stokta kaç gündür beklediği alış tarihinden hesaplanır.

### Kâr
Kâr satış anında yazılır: satış bedeli − VEHICLE hesabının bakiyesi (alış + araç masrafları).
Stoktaki araç kâr/zarar sayılmaz. Tahsilat ayrı takip edilir.

## 5. Sermaye ve kâr

Sistem **kâr dağıtmaz**. Sadece gösterir:

- Her ortağın sermayesi ve payı (%), toplam sermaye
- Seçilen dönemin kârı: satılan araçların kârı + komisyonlar − genel giderler
- Bilgi amaçlı: "Bu oranlarla Burak'ın payı X TL olur"

Ortaklar kendi aralarında konuşup karar verince, kâr payını sermayeye ekleme işlemini
**elle** girerler (tek ekran: dönem, kime, ne kadar). Bu işlem bir harekettir ve payları otomatik günceller.

## 6. Ödeme planı (alışta ve satışta)

Her alış ve satışın bir ödeme planı vardır:

```
payment_plan: vehicle_id | direction (SALE/PURCHASE) | contact_id (karşı taraf) | total_cents | created_by_id
              unique (vehicle_id, direction): aracın her yönde en fazla bir planı
installment:  plan_id | seq | due_date | amount_cents | method (CASH/TRANSFER/PROMISSORY_NOTE/INSTITUTION)
              | note_no | payer_contact_id | paid_entry_id (→ ledger_entry) | paid_at
```

- **Peşin:** tek satır, vade satış günü, "şimdi alındı".
- **Taksitli / senetli:** peşinat (şimdi alındı) + kalan eşit bölünür, kuruş farkı son taksite; her taksit ya da senet bir satır.
- **Evim sistemi / banka kredisi:** satır yöntemi `INSTITUTION`, ödeyen (`payer`) Evim şirketi ya da banka (ORGANIZATION, zorunlu).
- **Karma:** yukarıdakilerin karışımı; satırlar elle eklenir.

Satış kuralları (3A ile kesinleşti):
- Alıcı zorunlu; taksitlerin toplamı satış bedeline kuruşu kuruşuna eşit (değilse hata 1017).
- Deftere ödeyen başına `VEHICLE_SALE` INCOME → BALANCE(ödeyen) yazılır: Evim/kredi kısmı kurumun carisine gider.
- "Şimdi alındı" satırlar aynı işlemde `COLLECTION` BALANCE(ödeyen) → CASH/BANK ile kapanır.
- Tahsilat taksitin tamamı tek seferde (kısmi tahsilat yok); geri alma bağlı hareketi tersler, taksit bekliyora döner.
- Satışı geri al: sadece hiç taksit ödenmemişse; satış hareketleri terslenir, plan silinir, araç `IN_STOCK`'a döner.
- Ödenmemiş taksitte sadece vade ve senet no değişir; tutar/ödeyen değişecekse satış geri alınıp yeniden girilir.
- Satışa ya da taksite bağlı hareket kasa ekranından düzeltilmez (hata 1023).

Durum (saklanmaz, bugüne göre hesaplanır): `PAID` (ödendi, yeşil), `UPCOMING` (bekliyor, turuncu; vadesi bugün dahil),
`OVERDUE` (gecikti, kırmızı). Geciken ve bu haftanın ödemeleri ana ekranda ve `#/payments`'ta görünür.

## 7. Masraf kategorileri

- İki kök: **Araç masrafları** (araca bağlı, aracın maliyetine eklenir) ve **Genel giderler** (kira, fatura, personel).
- **Önceden liste doldurmak zorunlu değil.** Masraf girerken kategori alanına yazılır; varsa önerilir,
  yoksa "'Boya' kategorisi olarak ekle" ile o an oluşturulur.
- En sık kullanılan kategoriler masraf ekranının üstünde hızlı buton olarak çıkar.
- En fazla 3 seviye. İçinde kayıt olan kategori silinmez, arşivlenir.
- Rapor: dönem içinde kategori ve alt kategori bazında toplamlar; araç bazında masraf dökümü.

## 8. Çalışan bazında aylık alım-satım görünümü

Akıllı Randevum'daki haftalık görünümün aylık karşılığı:

- Ay seçilir (önceki/sonraki ay okları).
- Her çalışan için o ay **kaç araç aldığı** ve **kaç araç sattığı** gösterilir.
- Çalışana dokununca o ayın listesi açılır: alınanlar ve satılanlar ayrı, her satırda **marka-model ve plaka** (tarihle birlikte).
- **Tutar ve kâr gösterilmez.**
- `ADMIN` bütün çalışanları görür. `STAFF` sadece **kendi** aldığı ve sattığı araçları görür (backend'de filtrelenir). Başlangıçta çalışanlara uygulama verilmeyecek, ama yetki şimdiden böyle yazılır.
- Kaynağı: araçtaki `bought_by_id` + `purchase_date` ve `sold_by_id` + `sale_date`. Uç: `GET /api/reports/monthly-trades?year=&month=`.

### Alım ve satım ekranı (panel)

Ekranın üstünde kişi seçilir (Tüm galeri / çalışanlar / Belirtilmemiş; STAFF'ta sadece kendisi). Yıl seçilir.
Grafik: 12 ay, alınan (mavi) ve satılan (turuncu) adet; solda adet ekseni, çubukların üstünde o ayın
seçili kişinin yıllık işlemleri içindeki payı (%). Aya dokununca altta o ayın alınan/satılan araçları listelenir.
Kaynak: `GET /api/reports/yearly-trades?year=` (gruplama panelde).

## 8b. Ana ekran

- **ADMIN:** kasa + banka (dağılım şeridiyle), stok (adet, maliyet, ortalama gün), alacak (yeşil), verecek (kırmızı);
  sermaye ve ortaklar (halka grafik, sahip ilk sırada; her ortağın toplamdaki payı ve sahibin sermayesine oranı);
  alacak/verecek kişileri (en büyük 5); son 6 ay alım-satım adedi ve satış kârı; altta stoktaki araç kartları (tıklayınca detay).
  Kaynak: `GET /api/dashboard`.
- **STAFF:** tutar yok. Stok sayısı, ortalama stok günü, bu ay kendi aldığı/sattığı; araç kartları.
- Hesaplar: ortağın sermayesi = CAPITAL hesabının eksi bakiyesi; kişinin carisi (BALANCE) artıysa **alacak**, eksiyse **verecek**.
- Dükkân sahibi `contact.is_owner` ile işaretlenir (galeri başına en fazla bir).
- Görsel kurallar: `design/README.md`.

## 9. Ödeme günü bildirimleri

Galeri sahibine, vadesi gelen ödemeler için telefonuna bildirim gider (Akıllı Randevum'daki PWA web push altyapısı aynen kullanılır: tarayıcı/telefon "bildirimlere izin ver", VAPID anahtarları `.env`'de).

- Her sabah 09.00'da (galerinin saat dilimine göre; galericiyle kararlaştırıldı) bir zamanlanmış görev o gün vadesi gelen `installment` satırlarını bulur.
- **Tahsilat** (bize ödenecek): "Bugün Ali Yılmaz'dan 25.000 TL senet tahsilatı var (34 ABC 123)".
- **Ödeme** (bizim ödeyeceğimiz, alıştaki taksit/senetler): "Bugün Mehmet Demir'e 40.000 TL senet ödemesi var".
- Aynı gün birden fazla varsa tek bildirimde toplanır ("Bugün 3 tahsilat, 1 ödeme"), dokununca ilgili liste açılır.
- Vadesi geçmiş ve hâlâ ödenmemiş olanlar için ertesi günlerde de hatırlatma (en fazla 3 gün) gider.
- Bildirimler sadece `ADMIN` kullanıcılara gider (tutar içerdikleri için).
- Bildirim gönderimi bir kayıt tablosuna yazılır; aynı taksit için aynı gün iki kez bildirim gitmez.

## 10. Yetki

| Rol | Görür |
|---|---|
| `ADMIN` (galeri sahibi) | Her şey |
| `STAFF` (çalışan) | Araç teknik bilgileri, hasar şeması ve aylık alım-satım görünümünde sadece kendi kayıtları. **Hiçbir tutar yok** (alış, satış, masraf, cari, kasa, sermaye). |

Bu kısıt **backend'de** uygulanır: STAFF'a dönen DTO'larda para alanları hiç yer almaz,
para uç noktaları STAFF için 403 döner. Sadece arayüzde gizlemek yetmez.

## 11. Veri modeli (özet)

| Tablo | Önemli alanlar |
|---|---|
| `contact` | name, phone, type (PERSON/ORGANIZATION), notes, is_owner (dükkân sahibi) |
| `account` | type, contact_id (BALANCE/CAPITAL için), vehicle_id (VEHICLE için), name |
| `ledger_entry` | entry_date, type, source_account_id, target_account_id, amount_cents, vehicle_id, expense_category_id, description, corrected_entry_id, created_by_id |
| `expense_category` | parent_id, name, root (VEHICLE/GENERAL), archived |
| `vehicle` | plate, make, model, model_year, fuel, transmission, mileage_km, color, tramer_cents, notes, ownership, consignor_id, commission_rate / commission_cents, purchase_date, seller_id, bought_by_id, actual_purchase_cents, official_purchase_cents, sale_date, buyer_id, sold_by_id, sale_price_cents, status |
| `vehicle_part` | vehicle_id, part (BodyPart), condition (PartCondition) |
| `vehicle_share` | vehicle_id, contact_id, share_percent (sadece CO_OWNED) |
| `payment_plan` | vehicle_id, direction (SALE/PURCHASE), contact_id, total_cents, created_by_id; unique (vehicle_id, direction) |
| `installment` | plan_id, seq, due_date, amount_cents, method, note_no, payer_contact_id, paid_entry_id, paid_at; index (tenant_id, due_date) ödenmemişler için |
| `push_subscription` | panel_user_id, endpoint, anahtarlar (Akıllı Randevum'daki gibi) |
| `notification_log` | installment_id, date, sent_at (aynı gün tekrarını önler) |

Hepsi `tenant_id` taşır ve tenant'a göre filtrelenir. Tablo/kolon adları İngilizce (bkz. §14 sözlük);
henüz yazılmamış tabloların (`vehicle_share`, `push_subscription` ...) adları plandır, migration'ı yazılırken kesinleşir.

## 12. Yol haritası

Her adım ayrı bir PR; her biri bittiğinde galericiye gösterilebilir.

1. **Kişiler ve araçlar:** kişi kaydı, araç kaydı, teknik bilgiler, hasar şeması, alan/satan çalışan, araç listesi ve detayı. STAFF/ADMIN ayrımı.
   - Çalışan bazında aylık alım-satım görünümü (para içermediği için bu adımda yapılabilir).
2. **Para defteri:** hesaplar, hareket tablosu, kasa/banka bakiyesi, araç alışı (peşin), masraf girişi (kategori yazarken oluşur), araç maliyeti.
3. **Satış ve tahsilat:** satış kaydı, ödeme planı (peşin, taksit, senet, Evim, kredi), tahsilat, geciken ödemeler, müşteri carisi.
   - Ödeme günü bildirimleri (web push).
4. **Konsinye araçlar:** sahiplik türü, komisyon, sahibin carisi.
5. **Sermaye:** sermaye hareketleri, paylar, dönem kârı, "kâr payını sermayeye ekle".
6. **Raporlar ve ana ekran:** kategori bazında giderler, araç kârlılığı, stok yaşı; ana ekran özeti.

## 13. Açık sorular

- Konsinye araçlardan alınan komisyon sermaye havuzunun kârına giriyor mu? (Galerici "sonra konuşalım" dedi. Şimdilik INCOME'a yazılır ve dönem kârına dahil gösterilir; değişirse sadece rapor değişir.)
- Takas yapılıyor mu? (Sorulmadı. Gerekirse: alıcının verdiği araç yeni bir IN_STOCK araç olur, değeri alıcının carisinden düşülür.)
- Ana ekranda ne görünecek? (İlk hali yapıldı, bkz. §8b; galericiye gösterilip geri bildirimle şekillenecek.)

## 14. İsimlendirme sözlüğü

Kod, veritabanı, API ve JSON İngilizce; arayüz metinleri, yorumlar, loglar ve test adları Türkçe.
Her yerde aynı terim kullanılır:

| Türkçe | İngilizce |
|---|---|
| kişi (müşteri, satıcı, kurum) | `contact`; tür: `PERSON`, `ORGANIZATION` |
| araç | `vehicle` |
| araç parçası, hasar şeması | `vehicle_part`; enum `BodyPart`, `PartCondition` |
| plaka / marka / model yılı | `plate` / `make` / `model_year` |
| yakıt: benzin, dizel, LPG, benzin+LPG, hibrit, elektrik | `fuel`: `GASOLINE`, `DIESEL`, `LPG`, `GASOLINE_LPG`, `HYBRID`, `ELECTRIC` |
| vites: manuel, otomatik, yarı otomatik | `transmission`: `MANUAL`, `AUTOMATIC`, `SEMI_AUTOMATIC` |
| km / renk / notlar | `mileage_km` / `color` / `notes` |
| tramer | `tramer` (özel ad) |
| sahiplik: galeri, konsinye, özel ortaklı | `ownership`: `DEALER`, `CONSIGNMENT`, `CO_OWNED` |
| durum: stokta, satıldı, kapandı | `status`: `IN_STOCK`, `SOLD`, `CLOSED` |
| parça durumu: orijinal, lokal boyalı, boyalı, değişen | `ORIGINAL`, `LOCALLY_PAINTED`, `PAINTED`, `REPLACED` |
| parçalar: ön tampon, motor kaputu, tavan, bagaj kapağı, arka tampon, sol/sağ ön/arka çamurluk ve kapı | `FRONT_BUMPER`, `HOOD`, `ROOF`, `TRUNK_LID`, `REAR_BUMPER`, `LEFT/RIGHT_FRONT/REAR_FENDER`, `LEFT/RIGHT_FRONT/REAR_DOOR` |
| alış tarihi / satıcı / alan çalışan | `purchase_date` / `seller_id` / `bought_by_id` |
| gerçek / resmi alış | `actual_purchase_cents` / `official_purchase_cents` |
| satış tarihi / alıcı / satan çalışan / satış bedeli | `sale_date` / `buyer_id` / `sold_by_id` / `sale_price_cents` |
| tutar (kuruş) | `*_cents` soneki, BIGINT |
| stokta gün | `daysInStock` |
| çalışan | `staff` (`/api/staff`) |
| rapor, aylık alım-satım | `report`, `monthlyTrades` (`/api/reports/monthly-trades`); alınanlar `bought`, satılanlar `sold` |
| hesap / hareket | `account` / `ledger_entry` |
| kasa / banka | `CASH` / `BANK` |
| gider / gelir (hesap türü) | `EXPENSE` / `INCOME` |
| masraf / masraf kategorisi (kök, üst) | `expense` / `expense_category` (`root`: `VEHICLE`, `GENERAL`; `parent_id`) |
| hareket türü: araç alışı, araç masrafı, genel gider, sermaye, araç satışı, tahsilat, ödeme | `VEHICLE_PURCHASE`, `VEHICLE_EXPENSE`, `GENERAL_EXPENSE`, `CAPITAL`, `VEHICLE_SALE`, `COLLECTION`, `PAYMENT` |
| alacak / verecek | `receivable` / `payable` |
| dükkân sahibi | `owner` (`contact.is_owner`) |
| ana ekran | `dashboard` (`/api/dashboard`) |
| düzeltme (ters hareket) | `correct`, `corrected_entry_id` |
| sermaye / ortak / pay | `capital` / `partner` / `share` |
| cari | `balance` (hesap türü `BALANCE`; hesap özeti: `statement`) |
| ödeme planı / taksit / senet / tahsilat | `payment_plan` / `installment` / `promissory_note` / `collection` |
| konsinye / komisyon | `consignment` / `commission` |

API: `/api/vehicles` (`/{id}/parts`, `/{id}/sale`, `/{id}/cost`), `/api/contacts`, `/api/staff`,
`/api/reports/monthly-trades`, `/api/reports/yearly-trades`, `/api/dashboard`, `/api/accounts`, `/api/ledger-entries` (`/{id}/correct`), `/api/expenses`,
`/api/expense-categories?root=`.
