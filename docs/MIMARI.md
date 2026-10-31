# Galeri: alan modeli ve mimari

Bu belge galericiyle yapılan görüşmelerden çıkan kararları tutar. Kod yazarken buradaki
kavramlara ve kurallara uy; bir karar değişirse önce bu belgeyi güncelle.

## 0. Kullanıcılar ve kişiler (Kasım 2026 kararı, uygulanıyor)

Bu bölüm aşağıdaki eski kurallarla çelişirse **bu bölüm geçerlidir**. Eski bölümler (kişi altında ortak, `is_owner`, kişiye bağlı
sermaye) uygulama bitene kadar mevcut kodu anlatır.

**Ayrım:** Kullanıcılar dükkânın içindekilerdir (parası ve/veya yetkisi olanlar). Kişiler dışarıdaki taraflardır (müşteri, araç
aldığımız kişi, tedarikçi, kurum). Bir insan ikisi birden olmaz: sermayedar ve dükkân sahibi artık kişi listesinde yer almaz.

### Kullanıcı rolleri

| Rol | Kim | Görür / yapar |
|---|---|---|
| `ADMIN` (dükkân sahibi) | Şimdilik tek kişi (ileride birden fazla olabilir) | Her şey. Fiziksel kasa ve bankaları yönetir. Kullanıcılar ekranında herkesin alacak/verecek ve işlem geçmişini görür. Sermayesi = havuz − sermayedarların sermayesi. |
| `INVESTOR` (sermayedar) | Dükkâna para koyan | Sadece kendi sermayesi, kendi para hareketleri, alacak/borcu. Kendi ve ortak olduğu araçları fiyatlarıyla; diğer araçları fiyatsız (çalışan gibi). Araç alımı ve satışı girer, kendi araçlarına masraf ekler. Kasa ve banka bakiyesini, başkalarının parasını görmez. |
| `STAFF` (çalışan) | Çalışan | Hiçbir tutar görmez, sadece araç bilgileri. `can_trade` işaretliyse **peşin** araç alımı / satımı girebilir (girdiği tutarı sonra göremez). |

- Panele giriş isteğe bağlı: `can_login = false` olan kullanıcı (ör. telefonu olmayan sermayedar) sadece kayıt olarak tutulur.
- Kısıtlar backend'de uygulanır; para alanları yetkisi olmayana DTO'da hiç dönmez.

### Sermayedarın parası

- Sermayedar araç alırken para **kendi sermayesinden** düşer; kasa seçmez. Ortaklı alımda her ortağın payı kendi sermayesinden otomatik düşer.
  Dükkân sahibinin payı kasa/bankadan çıkar (fiziksel kasayı o yönetir).
- Sermayedar kendi aracının tahsilatını yaparken iki seçenek: **Sermayeme ekle** (para dükkâna girer, sermayesi artar) ya da
  **Nakit aldım** (para cebine; alacak kapanır, sermaye değişmez).
- Sermayedara ait çek / senet tahsil edilene kadar onun kartında alacak; risk onda (V18 + #37 kuralı korunur, sahibi kişi değil kullanıcı olur).
- **Araç sahipliği:** Bir araç bir ya da birkaç kullanıcıya aittir (paylar toplamı %100). **Havuz sahip değildir**; havuz herkesin
  parasının durduğu yer (kasa, bankalar, araçlar, alacaklar) ve dükkân sahibinindir. Dükkân sahibinin %100 aracı = galeri aracı.
- **Ortaklı araç** (birden çok sahip): mevcut ekrandaki gibi her sahibin sahiplik payı ve kâr payı oranı girilir. Sahiplik payı sadece
  kimin ne kadar para koyduğudur; kâr, kâr payı oranlarına göre bölünür.
- **Galeri sahibine komisyon (ortaklı araç):** sahiplerden en az biri galeri sahibi değilse alışta "Galeri sahibine komisyon verilecek mi?"
  sorulur; evetse % (kârın yüzdesi) ya da tutar girilir. Araç satılınca yazılır; galeri sahibi dışındaki ortaklar öder (galeri sahibi
  ödemez). % girilirse her ortak **kendi kâr payının** yüzdesini öder. Ortaklar için masraf, galeri sahibi için gelir.
  Örnek: Furkan %50, Burak %30, Ali %20 kâr payı; kâr 1.000.000; komisyon %10 → Burak 30.000, Ali 20.000 öder;
  Furkan 550.000, Burak 270.000, Ali 180.000.
- **Tek sahipli sermayedar aracı = konsinye:** mevcut konsinye ekranı ve komisyonu aynen kalır; sadece sahibi kişi değil kullanıcıdır.
- **Masraf:** ortaklı araca masraf sahiplik paylarına göre bölünür; herkesin payı kendi sermayesinden düşer (dükkân sahibininki kasadan).
- **Zarar:** şimdilik tanımsız, sonra konuşulacak.
- **Sermayedarlar arası araç devri:** alan kullanıcı satışta "satmak için al" işaretler. Satanın sermayesine satış bedeli (maliyet + kâr)
  eklenir, araç onun araçlarından düşer; alanın sermayesinden bedel düşer, araç onun araçlarına eklenir. Kasa değişmez.

### Kişiler

- **Ana tür (kalıcı, kayıtta seçilir):** `CUSTOMER` (müşteri), `SELLER` (araç aldığımız kişi), `SUPPLIER` (tedarikçi: usta, fotoğrafçı...).
- **Biçim:** `PERSON` (bireysel) / `ORGANIZATION` (tüzel; Evim gibi kurumlar da burada).
- **Etiketler (otomatik):** "Araç sattık", "Araç aldık" işlemlerden eklenir; ana tür değişmez.
- Masrafa tedarikçi bağlama şimdilik **yok** (sadelik).
- Araç sahipleri sadece **kullanıcılardır**; kişi araç sahibi olmaz.
- Sermayedar satış girerken dükkân sahibiyle aynı ödeme seçeneklerini kullanır (peşin, taksit, senet, çek, açık hesap); kendi senet ve
  açık hesaplarını kendi ekranından takip eder.

### Veri değişikliği (özet, migration'larda kesinleşir)

- `panel_user`: `role` ADMIN (dükkân sahibi) / INVESTOR / STAFF, `can_trade`, `can_login`, `capital_base`; telefon ve şifre `can_login` false ise boş olabilir. Havuzdan kalan, en önce açılan aktif ADMIN'in (birden çok dükkân sahibinde paylaşım sonra tanımlanacak).
- Sermaye hesabı (`account` CAPITAL), `vehicle_share`, evrak sahibi (`payment_plan.holder_*`): kişi yerine kullanıcıya bağlanır.
- `vehicle_share`: Havuz satırı (contact null) kalkar, dükkân sahibi de kullanıcı satırı olur; satırlar kullanıcı + sahiplik payı + kâr payı.
  Dükkân payı (`shop_cut_*`) kalkar. Konsinye sahibi (`consignor_id`) kullanıcıya bağlanır; komisyon alanları kalır.
- `contact`: `kind` (CUSTOMER / SELLER / SUPPLIER), etiketler; `is_owner` ve `capital_base` kullanıcıya taşınır / kalkar.
- Prod verisi: hem kişi hem kullanıcı olan kayıtlar (Furkan, Burak Şahin) birleştirilir; tutarlar ve hareket geçmişi korunur.
  Kişi kayıtlarında telefon boş, ad soyad kullanıcıyla birebir aynı: eşleme ad soyadla yapılır.

### Uygulama sırası (her biri ayrı PR)

1. Kullanıcı rolleri ve veri taşıma: sermaye, araç payları, konsinye ve evrak sahibi kullanıcıya; Furkan / Burak birleştirme.
   **Yapıldı** (`V22__users_own_capital.sql`). Sermayedarın dükkândan sermayesiyle araç alması (V19) kaldırıldı; yerini 5. madde alacak.
2. Kişi türleri ve etiketler; kişi listesinden kullanıcıların çıkması.
3. Yetkiler: sermayedar ve çalışan için backend kısıtları ve panel ekranları; Kullanıcılar ekranında alacak/verecek ve geçmiş.
4. Sermayedarın alımı (sermayeden düşme) ve tahsilat seçenekleri (Sermayeme ekle / Nakit aldım).
5. Sermayedarlar arası araç devri ("satmak için al").
6. `can_trade` çalışanın peşin alım / satımı.

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
| **Kullanıcı** | Dükkânın içindekiler: dükkân sahibi, sermayedar, çalışan (§0). Sermaye ve araç sahipliği kullanıcıdadır. |
| **Kişi** | Dışarıdaki taraf: müşteri, araç aldığımız kişi, tedarikçi, kurum (§0). Ortak ya da araç sahibi olmaz. |
| **Sermaye ortağı** | Galeriye sermaye koymuş kişi. Genel dönem kârı önerisindeki ağırlığı, araçlarda bağlı olmayan nakit sermayesidir. |
| **Konsinye araç** | Aracın sahibi galeri değil bir kişidir (örn. Burak'ın kendi aracı). Galeride satılır, galeri satıştan komisyon alır. Galerinin bu araca maliyeti yoktur, kârı sadece komisyondur. |
| **Ortak araç** | Aracın maliyeti Havuz (galerinin ortak parası) ve/veya sermaye ortaklarınca karşılanır. Her sahibin katkı ve kâr payı hesaplanır. |
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
| `OPENING` (açılış) | 1 | Açılış bakiyelerinin karşı hesabı (sisteme geçiş) |

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
| Ortaklar kâr payını sermayeye eklemeye karar verdi | CAPITAL(Burak) → INCOME X (elle girilir, bkz. 5; sermaye girişiyle aynı yön: sermaye hesabından çıkış = sermaye artar) |
| Kasadan bankaya para yatırıldı (virman) | CASH → BANK 100.000 (toplam nakit değişmez) |
| Sisteme geçişte kasada 120.000 TL vardı | OPENING → CASH 120.000 (hesap başına bir kez) |

## 4. Araç

### Teknik bilgiler
Plaka, marka, model, model yılı, yakıt tipi, vites, kilometre, renk, tramer (hasar kaydı) tutarı, notlar.

### Marka / model kataloğu
- Ortak katalog `src/main/resources/catalog/vehicles.json` (Türkiye pazarı, 50 marka); galeri eklemeleri `catalog_entry` tablosunda.
- `vehicle.model` "temel model + paket" taşır ("Passat 1.5 TSI Business"); kayıtta temel model katalogla eşleşmeli
  (büyük/küçük harf ve boşluk farkı yok sayılır, katalogdaki yazım kaydedilir). Eşleşmezse 1041.
- "Listede yok": `addToCatalog = true` + `catalogModel` ile marka/temel model önce galerinin kataloğuna eklenir. Galerici kapalı
  liste isterse panelden bu seçenek kaldırılır, backend kuralı aynı kalır.
- Katalog öncesi kayıtlar: marka ve model değişmedikçe doğrulanmaz, aynen kalır.

### Hasar şeması (Sahibinden'deki gibi)
Aracın çizimi üzerinde 13 parça, her biri dört durumdan biri:

`ORIGINAL` (orijinal) · `LOCALLY_PAINTED` (lokal boyalı) · `PAINTED` (boyalı) · `REPLACED` (değişen)

Parçalar: ön tampon, motor kaputu, tavan, bagaj kapağı, arka tampon, sol ön çamurluk, sol ön kapı,
sol arka kapı, sol arka çamurluk, sağ ön çamurluk, sağ ön kapı, sağ arka kapı, sağ arka çamurluk.

### Sahiplik
- `DEALER` (galeri): varsayılan. Maliyeti VEHICLE hesabında birikir, kârı galerinin kârıdır.
- `CONSIGNMENT` (konsinye): sahibi bir kişidir, komisyon oranı ya da sabit tutarı tutulur. VEHICLE hesabına maliyet yazılmaz.
- `CO_OWNED` (ortak): Havuz (kişi hesabı olmayan galeri payı) ve/veya kişiler `vehicle_share` satırlarında sahip olur. Sahiplik payları
  toplamı 100%; ortakların katkısı ve kâr payı oranı ayrı tutulur. Havuz payı dönem kârına, ortak payı satışta ortağın sermayesine yazılır.

Uygulama (Adım 4B):
- Komisyon: `RATE` (baz puan, %3 = 300; satış × oran, kuruşa yarım yukarı) ya da `FIXED` (kuruş). Konsinye araçta alış bedeli,
  satıcı ve alış ödemesi tutulmaz; araç stok maliyetine girmez.
- Konsinye satış: `VEHICLE_SALE` INCOME → BALANCE(alıcı) (her satışta olduğu gibi) + `CONSIGNMENT_SETTLEMENT` BALANCE(sahibi) → INCOME
  (satış − komisyon). Sahibine ödeme `PAYMENT` CASH/BANK → BALANCE(sahibi), kısmi olabilir.
- Konsinye masraf: sahibine yansıtılırsa CASH/BANK → BALANCE(sahibi), değilse CASH/BANK → EXPENSE; ikisi de `VEHICLE_EXPENSE` ve araca bağlı.
  Araç kârı = komisyon − galerinin üstlendiği masraf. Dönem kârında üstlenilen masraf genel giderlerin içinde sayılır (çift sayılmaz).
- Ortak araç: defter `DEALER` ile aynı; sahiplik ve kâr payı oranları aşağıdaki PR 3 kurallarıyla hesaplanır.

Uygulama (Revizyon PR 3, ortak finansmanı):
- Katkı = gerçek alış bedeli × sahiplik payı; araç masrafları katkıya eklenmez. Katkı bakiyesi saklanmaz, alış ödemelerinden hesaplanır.
  Peşinat ve her senet ödemesi bütün sahipler arasında sahiplik paylarına göre bölünür; kuruş farkı Havuz'a (Havuz yoksa en büyük paya) gider.
- Ortağın katkısı `CAPITAL` (nakitteki sermayeyi araca bağlar), `POCKET` (aynı transaction'da otomatik sermaye girişi) veya `HYBRID`
  (cepten girilen tutar önce, kalanı sermayeden) olabilir. Sermayeden gelen kısım ortağın nakitteki sermayesini negatife düşüremez.
- Ortağın toplam sermayesi CAPITAL hareketlerinden; araçta bağlı sermayesi stoktaki ortak araçların ödenmiş katkılarından hesaplanır.
  Nakit sermaye = toplam sermaye − bağlı sermaye. Dönem kârı önerisinin ağırlığı sadece nakit sermayedir.
- Kâr/zarar = satış − gerçek alış − araca bağlı masraflar. `profit_share_bp` oranlarıyla bölünür; bütün kuruşlar korunur.
  İsteğe bağlı dükkân payı yalnız pozitif ortak kârından alınır ve Havuz'a eklenir. Ortakların payı `VEHICLE_PROFIT_SHARE` ile
  satışta sermayeye yazılır; zarar önce sermayeden katkısı kadar düşer, aşan kısım ortağın BALANCE hesabına borç yazılır.
- Satıştaki ortak dönüşü alıcı ödeme planından bağımsız olarak aynı transaction'da hesaplanır: sermayede bırak, tüm alacak için nakit
  öde veya hibrit tutarı nakit öde. Nakit ödeme sermaye çekiş hareketidir. Satış bu çekişler terslenmeden geri alınamaz.
- Zamanlama `CoOwnerSettlementPolicy` ile belirlenir. Bugünkü tek politika `AT_SALE`; `ON_COLLECTION` ileride eklenebilir.
- Araç sahiplerinin farklı alış ödeme planı seçmesi (ör. Havuz peşin, ortak taksitli) kapsam dışıdır.

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

Sistem genel dönem kârını **kendiliğinden dağıtmaz**. Kâr payı sermayeye ekleme işlemi manuel kalır. Ortak araç için PR 3'te tanımlanan satış
anındaki otomatik kâr/zarar payı bu kuralın araç bazlı özel halidir.

- Her ortağın toplam sermayesi ve payı (%), nakitteki ve araçlarda bağlı sermayesi
- Seçilen dönemin Havuz kârı: galeri araçlarının kârı + ortak araçlardaki Havuz ve dükkân payı + komisyonlar − genel giderler.
  Ortak araçta ortaklara sermayelerine yazılan kâr bu nete girmez.
- Bilgi amaçlı: "Bu oranlarla Burak'ın payı X TL olur"

Ortaklar kendi aralarında konuşup karar verince, kâr payını sermayeye ekleme işlemini
**elle** girerler (tek ekran: dönem, kime, ne kadar). Bu işlem bir harekettir ve payları otomatik günceller.

Uygulama (Adım 5):
- Ortağın sermayesi = CAPITAL hesabından çıkan − giren (giriş CAPITAL → kasa, çekiş kasa → CAPITAL, kâr payı CAPITAL → INCOME).
- Dönem kârı iki tarih dahil: satılan galeri araçlarının kârı + ortak araçlarda Havuz/dükkân payı + konsinye komisyonu
  − genel giderler (EXPENSE hesabına net giriş; galerinin üstlendiği konsinye masrafı dahil). Ortaklara sermayelerine yazılan paylar nete girmez.
- Önerilen pay = Havuz net kârı × (dönem sonu nakit sermaye − bu dönemin kârından eklenen) / toplam nakit sermaye; kuruş kalanı en büyük paya.
- Virman TRANSFER; açılış bakiyesi OPENING_BALANCE (hesap başına bir kez, düzeltilirse yeniden).

### Varlıklar (Revizyon PR 4)

- `GET /api/net-worth` sadece ADMIN içindir. Tek PostgreSQL `REPEATABLE_READ` okuma transaction'ı aynı anın hesaplarını verir.
- Toplam mal = kasa + banka + stoktaki VEHICLE hesaplarının maliyeti + pozitif cariler − negatif cariler. Konsinye stok dışarıda kalır.
- Dağıtılmamış kâr = INCOME eksi bakiyesi − EXPENSE bakiyesi − stokta olmayan araçların VEHICLE maliyeti. Sermayeye eklenen kâr
  zaten INCOME hareketlerine yansıdığı için yeniden düşülmez.
- Denge: toplam mal = sermaye + dağıtılmamış kâr + açılıştan gelen varlık. OPENING karşı hesabı, sermaye sayılmadan ayrı gösterilir.
- Ortak araçta brüt mal maliyetin sahiplik payıdır; borç ödenmemiş alış taksitlerinin sahiplik payıdır. Her bölmede kuruş kalanı
  Havuz'a gider (Havuz yoksa en büyük paya). Masraflar brüt mala dahildir; bağlı sermaye yalnız ödenmiş alış katkısıdır, masraf içermez.
  Taksitlerin ayrı ayrı yuvarlanması nedeniyle brüt net payla ödenmiş katkı arasında kuruş farkı olabilir; ikisi ayrı gösterilir.
- Kasa > Varlıklar: toplam mal, ortak sermayeleri, nakit/araç dağılımı, araç başına mal/borç/net ve denge farkı. Nakitteki sermaye
  fiziksel kasa bakiyesiyle aynı değildir; alacak/verecek, Havuz araçları, kâr ve açılış nedeniyle fark oluşabilir.

## 6. Ödeme planı (alışta ve satışta)

Her alış ve satışın bir ödeme planı vardır:

```
payment_plan: vehicle_id | direction (SALE/PURCHASE) | contact_id (karşı taraf) | total_cents | created_by_id
              unique (vehicle_id, direction): aracın her yönde en fazla bir planı
installment:  plan_id | seq | due_date | amount_cents | method (CASH/TRANSFER/PROMISSORY_NOTE/INSTITUTION/CHEQUE)
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

Alış kuralları (3B ile kesinleşti):
- Araç formunda ödeme **Peşin** (kasa/banka → VEHICLE, `syncVehiclePurchase`) ya da **Senetli / taksitli** (`direction = PURCHASE`).
- Planlı alışta satıcı zorunlu (1025); plan toplamı (ödenmişler dahil) gerçek alış bedeline eşit (1024).
- Deftere ödenecek kişi başına `VEHICLE_PURCHASE` BALANCE(satıcı) → VEHICLE, alış tarihinde. "Şimdi ödendi" satırlar
  aynı işlemde `PAYMENT` CASH/BANK → BALANCE(satıcı) ile kapanır; sonraki taksitler ödemeler ekranında "Öde" ile.
- Düzenleme: istek sadece ödenmemiş satırları taşır. Değişiklik yoksa hiçbir şey yazılmaz; sadece vade/senet no değiştiyse
  ödenmemiş taksitler yeniden yazılır, defter aynı kalır; bedel ya da satıcı değiştiyse alış hareketleri terslenip yeniden yazılır.
- Ödenmiş taksit varken bedel, satıcı ya da plandan peşine geçiş değiştirilemez (1026); önce ödemeler geri alınır.
- Planlı alış hareketi kasa ekranından düzeltilmez (1023); araç formundaki "Ödeme nereden" sadece peşin alışta dolar.

Durum (saklanmaz, bugüne göre hesaplanır): `PAID`
- Araçsız geçmiş cari kayıtları senet/çek veya `OPEN_ACCOUNT` (sözlü borç) olabilir; açık hesap taksitlerinde isteğe bağlı açıklama tutulur.
  Açıklama ödeme hareketinden bağımsızdır; tahsil edilmiş açık hesap satırında da düzenlenebilir, vade ve defter hareketleri değişmez.
  Ödenmemiş geçmiş satır tek tek silinirse eski açılış
  hareketi terslenir ve kalan satırların toplamıyla aynı tarihle yeniden cariye yazılır. Ödenmiş satır önce geri alınmalıdır; araçlı planlardaki
  satırları silmek yerine araç satış/alış akışı düzeltilir.

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

- Görev her dakika Europe/Istanbul saatine göre çalışır. Galerinin `reminder_time` saati geldiyse ve `reminder_sent_on` bugün değilse
  o gün vadesi gelen `installment` satırlarını bulur. Varsayılan saat 09:00; ADMIN Hesabım > Bildirimler'den değiştirir.
- **Tahsilat** (bize ödenecek): "Bugün Ali Yılmaz'dan 25.000 TL senet tahsilatı var (34 ABC 123)".
- **Ödeme** (bizim ödeyeceğimiz, alıştaki taksit/senetler): "Bugün Mehmet Demir'e 40.000 TL senet ödemesi var".
- Aynı gün birden fazla varsa tek bildirimde toplanır ("Bugün 3 tahsilat, 1 ödeme"), dokununca ilgili liste açılır.
- Vadesi geçmiş ve hâlâ ödenmemiş olanlar için ertesi günlerde de hatırlatma (en fazla 3 gün) gider.
- Bildirimler sadece `ADMIN` kullanıcılara gider (tutar içerdikleri için).
- Bildirim gönderimi bir kayıt tablosuna yazılır; aynı taksit için aynı gün iki kez bildirim gitmez.

Uygulama (Adım 3C):
- Tek kayıtta metin: tahsilatta ayrılma eki ("Ali Yılmaz'dan"), ödemede yönelme eki ("Mehmet Demir'e"), yöntem kelimesi
  (senet / kurum / havale, nakitte yok). Gecikmişte: "Ayşe Demir'den 395.000 TL havale tahsilatı 1 gün gecikti (34 ABC 123)".
- Birden fazlada: "Bugün 3 tahsilat, 1 ödeme (1 gecikmiş)". Dokununca `/#/payments`.
- Hiç ADMIN abonesi yoksa ya da hiçbir gönderim başarılı olmazsa `notification_log` yazılmaz.
- Gönderim denemesinden sonra `reminder_sent_on` bugün olur; taksit, abone veya VAPID yoksa da galeri o gün işlenmiş sayılır.
  Sunucu seçilen saatte kapalıysa aynı gün açıldığında gönderir. Saat değişince işaret temizlenir; `notification_log` aynı taksitin
  aynı gün yeniden bildirilmesini engellemeye devam eder. Günlük işleme ve saat değişikliği galeri satırını kilitler.
- ADMIN uçları: `GET/PUT /api/settings/reminder` (`{ time: "HH:mm" }`), `POST /api/push/test`. Test sadece çağıranın kayıtlı
  cihazlarına gider; günlük işareti ve taksit bildirim logunu değiştirmez. Hata 1046: cihaz/VAPID yok; 1047: gönderim başarısız.
- Abonelik endpoint'e göre tekildir: aynı cihazda başka ADMIN izin verirse ona geçer; çıkışta panel aboneliği siler.

## 10. Yetki

| Rol | Görür |
|---|---|
| `ADMIN` (galeri sahibi) | Her şey |
| `STAFF` (çalışan) | Araç teknik bilgileri, hasar şeması ve aylık alım-satım görünümünde sadece kendi kayıtları. **Hiçbir tutar yok** (alış, satış, masraf, cari, kasa, sermaye). |

Bu kısıt **backend'de** uygulanır: STAFF'a dönen DTO'larda para alanları hiç yer almaz,
para uç noktaları STAFF için 403 döner. Sadece arayüzde gizlemek yetmez.

Kullanıcılar (Adım 4A): ADMIN çalışan ve yönetici ekler, rolünü değiştirir, pasifleştirir, şifresini sıfırlar; herkes kendi şifresini
değiştirir. Kullanıcı silinmez (araçtaki alan/satan çalışan kaydı bozulmasın). Galeride her zaman en az bir aktif ADMIN kalır,
ADMIN kendini pasifleştiremez. Rol ve aktiflik her istekte DB'den okunur; pasifleştirme açık oturumu hemen kapatır.

## 11. Veri modeli (özet)

| Tablo | Önemli alanlar |
|---|---|
| `contact` | name, phone, type (PERSON/ORGANIZATION), notes, is_owner (dükkân sahibi) |
| `account` | type, contact_id (BALANCE/CAPITAL için), vehicle_id (VEHICLE için), name |
| `ledger_entry` | entry_date, type, source_account_id, target_account_id, amount_cents, vehicle_id, expense_category_id, description, corrected_entry_id, created_by_id |
| `expense_category` | parent_id, name, root (VEHICLE/GENERAL), archived |
| `vehicle` | plate, make, model, model_year, fuel, transmission, mileage_km, color, tramer_cents, notes, ownership, consignor_id, commission_type / commission_rate_bp / commission_cents, shop_cut_type / shop_cut_rate_bp / shop_cut_cents, purchase_date, seller_id, bought_by_id, actual_purchase_cents, official_purchase_cents, sale_date, buyer_id, sold_by_id, sale_price_cents, status |
| `vehicle_part` | vehicle_id, part (BodyPart), condition (PartCondition) |
| `catalog_entry` | make, model (galerinin kataloğa eklediği temel model); unique (tenant_id, lower(make), lower(model)) |
| `vehicle_share` | vehicle_id, nullable contact_id (null = Havuz), share_bp, profit_share_bp, contribution_source (CAPITAL/POCKET/HYBRID), pocket_cents |
| `payment_plan` | vehicle_id, direction (SALE/PURCHASE), contact_id, total_cents, created_by_id; unique (vehicle_id, direction) |
| `installment` | plan_id, seq, due_date, amount_cents, method, note_no, bank_name, drawer_name, payer_contact_id, paid_entry_id, paid_at, endorsed_to_contact_id, endorse_entry_id, bounced_at; index (tenant_id, due_date) ödenmemişler için |
| `push_subscription` | panel_user_id, endpoint (unique), p256dh, auth |
| `notification_log` | installment_id, notify_date, sent_at; unique (installment_id, notify_date) |

Hepsi `tenant_id` taşır ve tenant'a göre filtrelenir. Tablo/kolon adları İngilizce (bkz. §14 sözlük);
yeni tabloların adları migration'ı yazılırken kesinleşir.

## 12. Yol haritası

Her adım ayrı bir PR; her biri bittiğinde galericiye gösterilebilir.

1. **Kişiler ve araçlar:** kişi kaydı, araç kaydı, teknik bilgiler, hasar şeması, alan/satan çalışan, araç listesi ve detayı. STAFF/ADMIN ayrımı.
   - Çalışan bazında aylık alım-satım görünümü (para içermediği için bu adımda yapılabilir).
2. **Para defteri:** hesaplar, hareket tablosu, kasa/banka bakiyesi, araç alışı (peşin), masraf girişi (kategori yazarken oluşur), araç maliyeti.
3. **Satış ve tahsilat:** satış kaydı, ödeme planı (peşin, taksit, senet, Evim, kredi), tahsilat, geciken ödemeler, müşteri carisi.
   - Ödeme günü bildirimleri (web push). (Yapıldı, Adım 3C.)
4. **Konsinye araçlar:** sahiplik türü, komisyon, sahibin carisi.
5. **Sermaye:** sermaye hareketleri, paylar, dönem kârı, "kâr payını sermayeye ekle".
6. **Raporlar ve ana ekran:** kategori bazında giderler, araç kârlılığı, stok yaşı; ana ekran özeti.

## 13. Açık sorular

- §0 zarar: ortaklı ya da konsinye araç zararına satılırsa sermayedarın payı (şimdilik tanımsız).

- Konsinye araçlardan alınan komisyon sermaye havuzunun kârına giriyor mu? (Galerici "sonra konuşalım" dedi. Şimdilik INCOME'a yazılır ve dönem kârına dahil gösterilir; değişirse sadece rapor değişir. Ana ekranda ve raporlarda komisyon
  araç kârından ayrı satırda gösterilir.)
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
| marka / model kataloğu, galeri eklemesi | `catalog` (`/api/catalog/makes`), `catalog_entry`; "kataloğa ekle" `addToCatalog` |
| yakıt: benzin, dizel, LPG, benzin+LPG, hibrit, elektrik | `fuel`: `GASOLINE`, `DIESEL`, `LPG`, `GASOLINE_LPG`, `HYBRID`, `ELECTRIC` |
| vites: manuel, otomatik, yarı otomatik | `transmission`: `MANUAL`, `AUTOMATIC`, `SEMI_AUTOMATIC` |
| km / renk / notlar | `mileage_km` / `color` / `notes` |
| tramer | `tramer` (özel ad) |
| sahiplik: galeri, konsinye, ortak | `ownership`: `DEALER`, `CONSIGNMENT`, `CO_OWNED` |
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
| hareket türü: araç alışı, araç masrafı, genel gider, sermaye, araç satışı, tahsilat, ödeme, ortak araç kâr/zararı | `VEHICLE_PURCHASE`, `VEHICLE_EXPENSE`, `GENERAL_EXPENSE`, `CAPITAL`, `VEHICLE_SALE`, `COLLECTION`, `PAYMENT`, `VEHICLE_PROFIT_SHARE` |
| alacak / verecek | `receivable` / `payable` |
| dükkân sahibi | `owner` (`contact.is_owner`) |
| ana ekran | `dashboard` (`/api/dashboard`) |
| düzeltme (ters hareket) | `correct`, `corrected_entry_id` |
| sermaye / ortak / pay | `capital` / `partner` / `share` |
| cari | `balance` (hesap türü `BALANCE`; hesap özeti: `statement`) |
| ödeme planı / taksit / senet / tahsilat | `payment_plan` / `installment` / `promissory_note` / `collection` |
| konsinye / komisyon | `consignment` / `commission` |
| Havuz / ortak araç sahip satırı / sahiplik payı / kâr payı oranı | `pool` / `vehicle_share` / `share_bp` / `profit_share_bp` |
| katkı kaynağı / cepten tutar / dükkân payı | `contribution_source` (`CAPITAL`, `POCKET`, `HYBRID`) / `pocket_cents` / `shop_cut_*` |
| ortağa dönüş / satış anında ödeme politikası | `owner return` / `CoOwnerSettlementPolicy` (`AT_SALE`) |

API: `/api/vehicles` (`/{id}/parts`, `/{id}/sale`, `/{id}/cost`), `/api/contacts`, `/api/staff`,
`/api/reports/monthly-trades`, `/api/reports/yearly-trades`, `/api/dashboard`, `/api/accounts`, `/api/ledger-entries` (`/{id}/correct`), `/api/expenses`,
`/api/expense-categories?root=`.


## Ortak alıcının araç finansmanı

Ortak alıcılı araç satışında ödeme planı satırı, sermaye kaynaklı tutarı capital_cents ile saklar. Nakit kaynaklı kalan kısım tahsilatta seçilen kasa/banka hesabına, sermaye kısmı ise BALANCE(ortak) -> CAPITAL(ortak) olarak PARTNER_SALE_SETTLEMENT hareketiyle yazılır. Her iki hareket de aynı installment kaydına bağlıdır; geri alma ikisini de tersler. Hibrit taksit kısmi tahsil edilirse sermaye payı önce kapatılır; kalan taksit sermaye ve nakit paylarının kalanını taşır. Sermaye taahhüdü, nakitteki kullanılabilir sermayeyi ve açık plan rezervlerini aşamaz.
