# Galeri tasarım kaynağı

Panelin görsel dili. Token'ların kendisi `panel/src/index.css` içindeki `@theme`'de; burası **nasıl kullanılacağını** anlatır.
Yeni ekran yaparken önce bunu oku. "Yapay zekâ varsayılanı" görünümden kaçın: lavanta, jenerik gradyan,
her şeye aynı yuvarlak kart, renksiz düz liste yok.

## Kimlik

- **İmza öğe: Türk plakası** (`Plate` bileşeni). Araç her yerde plakasıyla tanıtılır.
- **Ana renk: plaka mavisi** (`plaka` #0B3B9E, açığı `plaka-acik`, koyusu `plaka-koyu`). Birincil buton, seçili durum, odak halkası.
- **Menü: gece mavisi** (`gece`, `gece-2`, `gece-cizgi`, `gece-yazi`). Sol menü ve telefondaki üst çubuk koyu; içerik açık zeminde.
- Zemin `zemin` (açık gri), kartlar `yuzey` (beyaz) + `cizgi` kenar + tek ince gölge (`.kart`).

## Tipografi (Barlow + Barlow Condensed)

| Rol | Stil |
|---|---|
| Sayfa başlığı | Barlow Condensed 700, 30-34px, sıkı aralık. Üstünde `.etiket` (ör. "RAPOR", "PARA DEFTERİ"). |
| Bölüm/kart etiketi | `.etiket`: 11.5px, 600, BÜYÜK HARF, 0.08em aralık, `murekkep-3`. |
| KPI / büyük sayı | `.sayi` (Condensed, tabular), 700, 28-40px. |
| Değer (form/detay) | 16px 600 `murekkep`; sayısal değerler `.sayi` 21px. |
| İkincil metin | 13-14px `murekkep-2`; pasif/ipucu `murekkep-3`. |
| Araç adı | Marka **kalın**, model normal/ince (`font-medium`, `murekkep-2`). |

Kalınlık, incelik ve renk birlikte hiyerarşi kurar: etiket soluk ve küçük, değer koyu ve büyük. Aynı satırda iki kalın öğe olmasın.

## Renk anlamları (her ürün aynı)

- Gelen / olumlu / alacak: **yeşil** (`basari`, çubukta `basari-canli`)
- Giden / sorunlu / verecek: **kırmızı** (`tehlike`, çubukta `tehlike-canli`)
- Bekleyen / uyarı (30+ gün stok): **turuncu** (`uyari`, `boyali`)
- Onaylı / stokta / seçili: **mavi** (`plaka`)
- İptal / kapandı / pasif: **gri**
- Durum rengi asla tek başına anlam taşımaz: yanında etiket ya da işaret (+/−) olur.

## Grafikler

- Kütüphane yok: `panel/src/shared/charts/` (BarChart, Donut, SplitBar). SVG gerçek piksel genişliğinde çizilir (`useWidth`), yazılar esnemez.
- **Seri renkleri sabit sırayla**: `seri-1` mavi, `seri-2` turuncu, `seri-3` deniz yeşili, `seri-4` hardal, `seri-5` frambuaz, `seri-6` mor.
  Döngüye girmez; 6'dan fazla seri "Diğer"e katlanır. Renk varlığı takip eder, sırayı değil (kişi süzülünce renkler değişmez).
  Bu sıra dataviz doğrulayıcısından geçti (CVD ve parlaklık); `seri-3`/`seri-4` beyazda 3:1 altında, bu yüzden değer etiketi ve lejant hep görünür.
- Sabit eşleşmeler: **alınan = seri-1 (mavi), satılan = seri-2 (turuncu)**, kâr = `basari-canli`, alacak/verecek = yeşil/kırmızı.
- Tek eksen; iki ölçü aynı grafiğe konmaz (ör. adet ve kâr ayrı grafik).
- İnce işaretler: çubuk en fazla 22px, üst köşeler 4px yuvarlak, çubuklar arası 2px boşluk; ızgara `izgara`, taban `eksen`.
- Her grafikte hover/dokunma ipucu; seçilebilir grafikte seçili grup açık mavi zeminle işaretlenir, diğerleri soluklaşmaz (sadece hover'da).
- Adet grafiklerinde eksen en az 4'e gider (`minMax`): 1 araçlık ay tavana vurmasın.
- 2+ seride lejant var; tek seride başlık seriyi adlandırır.

## Bileşen kalıpları

- **KPI karesi** (`StatTile`): renkli nokta + etiket, büyük sayı, alt satır; isteğe bağlı mini SplitBar.
- **Araç kartı** (`VehicleCard`): üstte 3px stok yaşı şeridi (gri < 30 gün, turuncu 30-59, kırmızı 60+), plaka + gün rozeti, marka/model, yıl · km · yakıt, hasar özeti.
- **Sermaye kartı** (`CapitalCard`): halka + ortak listesi; sahip ilk sırada "Dükkân sahibi" rozetiyle; her ortakta toplamdaki pay ve sahibe oranı. Liste satırına gelince halkada o dilim öne çıkar.
- **Alacak/verecek kartı** (`BalancesCard`): iki büyük tutar, oran şeridi, en büyük 5 kişi (çubuk + telefon), net durum.
- Kişi seçimi: yatay kayan yuvarlak çipler (baş harf avatarı). Telefonda taşarsa kayar, satır kırılmaz.

## Yerleşim ve mobil

- İçerik en fazla 1240px, ortalı. Izgaralar `minmax(0, …)` ile kurulur (grafik sütunu taşmasın).
- Sol menü varsayılan **dar** (76px, sadece ikon + ipucu); alttaki düğmeyle açılır, tercih `localStorage`'da (`galeri_menu_open`).
- Telefon: koyu üst çubuk + 5'li alt sekme; KPI'lar 2×2; 12 aylık grafik yatay kaydırılır (min 600px).
- Dokunma hedefleri en az 40px.
