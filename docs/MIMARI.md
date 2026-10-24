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
hepsi `hareket` tablosundaki kayıtların toplamıdır.

Her hareket "hangi hesaptan → hangi hesaba, ne kadar" bilgisini taşır.

```
hareket: tarih | kaynak_hesap → hedef_hesap | tutar | arac (ops.) | masraf_kategorisi (ops.) | açıklama
```

Bir hesabın bakiyesi = hesaba giren hareketler − hesaptan çıkan hareketler.

Kurallar:
1. **Hareket silinmez ve değiştirilmez.** Yanlış girildiyse aynı tutarda ters yönde bir düzeltme hareketi atılır (`duzeltilen_hareket_id` ile bağlanır). Ekranda "düzelt" böyle çalışır.
2. **Tutarlar kuruş cinsinden tam sayı** (`BIGINT`). 1.250.000,50 TL = `125000050`. `double`/`float` asla kullanılmaz.
3. Her hareketi kimin, ne zaman girdiği tutulur.

### Hesap türleri

| Tür | Kaç tane | Ne tutar |
|---|---|---|
| `KASA` | Galeri başına 1 | Nakit |
| `BANKA` | Galeri başına 1 veya daha fazla | Banka hesapları |
| `CARI` | Kişi başına 1 | O kişiyle aramızdaki alacak/borç |
| `SERMAYE` | Ortak başına 1 | Ortağın galerideki sermayesi |
| `ARAC` | Araç başına 1 | Aracın maliyeti (alış + araç masrafları) |
| `GIDER` | 1 | Araca bağlı olmayan genel giderler |
| `GELIR` | 1 | Satış gelirleri ve komisyonlar |

Gösterimde işaret hesap türüne göre çevrilir: `SERMAYE` hesabından 2 milyon çıkmışsa ekranda
"Burak'ın sermayesi: 2.000.000 TL" yazar.

### Örnek akışlar

| Olay | Hareket(ler) |
|---|---|
| Burak 2.000.000 TL sermaye koydu | SERMAYE(Burak) → KASA 2.000.000 |
| Araç peşin alındı, 1.000.000 TL | KASA → ARAC(#12) 1.000.000 |
| Araç senetle alındı | CARI(satıcı) → ARAC(#12) 1.000.000; her senet ödendiğinde KASA → CARI(satıcı) |
| Araca boya masrafı, kasadan 5.000 TL | KASA → ARAC(#12) 5.000, kategori: Boya |
| Masrafı Furkan kendi cebinden ödedi | CARI(Furkan) → ARAC(#12) 5.000 (galeri Furkan'a borçlu) |
| Kira ödendi | KASA → GIDER 40.000, kategori: Kira |
| Araç 1.300.000 TL'ye satıldı | GELIR → CARI(alıcı) 1.300.000; araç "satıldı" olur |
| Alıcı 300.000 TL peşinat verdi | CARI(alıcı) → KASA 300.000 |
| Evim şirketi kalan 1.000.000 TL'yi ödedi | CARI(Evim şirketi) → BANKA 1.000.000 (satışta bu tutar alıcının değil şirketin carisine yazılır) |
| Konsinye araç satıldı, komisyon %3 | GELIR → CARI(alıcı) 1.300.000 ve CARI(araç sahibi) → GELIR 1.261.000 (galeri sahibine borçlu); GELIR'de net 39.000 komisyon kalır |
| Ortaklar kâr payını sermayeye eklemeye karar verdi | GELIR → SERMAYE(Burak) X (elle girilir, bkz. 5) |

## 4. Araç

### Teknik bilgiler
Plaka, marka, model, model yılı, yakıt tipi, vites, kilometre, renk, tramer (hasar kaydı) tutarı, notlar.

### Hasar şeması (Sahibinden'deki gibi)
Aracın çizimi üzerinde 13 parça, her biri dört durumdan biri:

`ORIJINAL` · `LOKAL_BOYALI` · `BOYALI` · `DEGISEN`

Parçalar: ön tampon, motor kaputu, tavan, bagaj kapağı, arka tampon, sol ön çamurluk, sol ön kapı,
sol arka kapı, sol arka çamurluk, sağ ön çamurluk, sağ ön kapı, sağ arka kapı, sağ arka çamurluk.

### Sahiplik
- `GALERI`: varsayılan. Maliyeti ARAC hesabında birikir, kârı galerinin kârıdır.
- `KONSINYE`: sahibi bir kişidir, komisyon oranı ya da sabit tutarı tutulur. ARAC hesabına maliyet yazılmaz.
- `OZEL_ORTAKLI`: havuz dışı; `arac_payi` tablosunda kişi + yüzde (toplam %100). Kârı bu oranlarla gösterilir.

### Alış bilgileri
Alış tarihi, kimden alındı, **aracı alan çalışan**, **gerçek alış bedeli** ve **resmi (beyan edilen) alış bedeli**, ödeme şekli.
Kâr hesapları her zaman gerçek bedelle yapılır.

### Satış bilgileri
Satış tarihi, alıcı, **aracı satan çalışan**, satış bedeli, ödeme şekli.

### Durum
`STOKTA` → `SATILDI` → `KAPANDI` (satışın bütün tahsilatı tamamlandığında).
Stokta kaç gündür beklediği alış tarihinden hesaplanır.

### Kâr
Kâr satış anında yazılır: satış bedeli − ARAC hesabının bakiyesi (alış + araç masrafları).
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
odeme_plani: arac | yon (ALIS/SATIS) | karsi_taraf (kişi) | toplam_tutar | sekil
taksit:      plan | vade_tarihi | tutar | tur (NAKIT/HAVALE/SENET/KURUM) | senet_no | odeyen (kişi) | odendi_hareket_id
```

- **Peşin:** tek satır, vade bugün.
- **Taksitli / senetli:** her taksit ya da senet bir satır.
- **Evim sistemi / banka kredisi:** satır türü `KURUM`, `odeyen` = Evim şirketi ya da banka.
- **Karma:** yukarıdakilerin karışımı.

Ödeme alındığında ilgili satır bir harekete bağlanır. Vadesi geçmiş ve ödenmemiş satırlar
"geciken ödemeler" listesini oluşturur.

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
- **Tutar ve kâr gösterilmez.** Bu yüzden ekran STAFF'a da açıktır.
- Kaynağı: araçtaki `alan_calisan_id` + `alis_tarihi` ve `satan_calisan_id` + `satis_tarihi`.

## 9. Ödeme günü bildirimleri

Galeri sahibine, vadesi gelen ödemeler için telefonuna bildirim gider (Akıllı Randevum'daki PWA web push altyapısı aynen kullanılır: tarayıcı/telefon "bildirimlere izin ver", VAPID anahtarları `.env`'de).

- Her sabah (galerinin saat dilimine göre 09.00) bir zamanlanmış görev o gün vadesi gelen `taksit` satırlarını bulur.
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
| `STAFF` (çalışan) | Araç teknik bilgileri, hasar şeması ve çalışan bazında aylık alım-satım görünümü. **Hiçbir tutar yok** (alış, satış, masraf, cari, kasa, sermaye). |

Bu kısıt **backend'de** uygulanır: STAFF'a dönen DTO'larda para alanları hiç yer almaz,
para uç noktaları STAFF için 403 döner. Sadece arayüzde gizlemek yetmez.

## 11. Veri modeli (özet)

| Tablo | Önemli alanlar |
|---|---|
| `kisi` | ad, telefon, tur (KISI/KURUM), not |
| `hesap` | tur, kisi_id (CARI/SERMAYE için), arac_id (ARAC için), ad |
| `hareket` | tarih, kaynak_hesap_id, hedef_hesap_id, tutar_kurus, arac_id, masraf_kategori_id, aciklama, duzeltilen_hareket_id, olusturan_kullanici_id |
| `masraf_kategori` | ust_id, ad, kok (ARAC/GENEL), arsivlendi |
| `arac` | plaka, marka, model, model_yili, yakit, vites, km, renk, tramer_kurus, sahiplik, konsinye_sahibi_id, komisyon_orani / komisyon_kurus, alis_tarihi, gercek_alis_kurus, resmi_alis_kurus, satis_tarihi, satis_kurus, alici_id, alan_calisan_id, satan_calisan_id, durum |
| `arac_parca` | arac_id, parca, durum |
| `arac_payi` | arac_id, kisi_id, yuzde (sadece OZEL_ORTAKLI) |
| `odeme_plani`, `taksit` | bkz. 6 |
| `push_abonelik` | panel_user_id, endpoint, anahtarlar (Akıllı Randevum'daki gibi) |
| `bildirim_kaydi` | taksit_id, tarih, gonderildi_at (aynı gün tekrarını önler) |

Hepsi `tenant_id` taşır ve tenant'a göre filtrelenir. Tablo/kolon adları Türkçe ASCII.

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

- Konsinye araçlardan alınan komisyon sermaye havuzunun kârına giriyor mu? (Galerici "sonra konuşalım" dedi. Şimdilik GELIR'e yazılır ve dönem kârına dahil gösterilir; değişirse sadece rapor değişir.)
- Takas yapılıyor mu? (Sorulmadı. Gerekirse: alıcının verdiği araç yeni bir STOKTA araç olur, değeri alıcının carisinden düşülür.)
- Ana ekranda ne görünecek? (Galerici henüz düşünmedi; 6. adımda karar verilecek.)
- Çalışan görünümünde STAFF herkesi mi, sadece kendini mi görsün? (Şimdilik herkesi; para içermiyor.)
- Bildirim saati sabah 09.00 uygun mu, galerici başka saat ister mi?
