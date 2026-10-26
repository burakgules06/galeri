# Galeri: altın senaryo (TC0-TC13)

Bu dosya, "Galeri: alım-satım, ödeme ve sermaye senaryoları" dokümanının test için gereken kısmıdır.
`revizyon-ortak-finansman.md` "Para doğruluğu > Altın senaryo" maddesindeki entegrasyon testinin kaynağı budur.
Repoda `docs/SENARYOLAR.md` olarak durur.

**Test nasıl kurulur**
- Boş veritabanı, adımlar sırayla. Her TC bir öncekinin üstüne kurulur. Beklenen değerler **birikimli**dir.
- Hepsi aynı gün (sabit `Clock`). TC4'teki "ilk vade dün" için tarih `Clock`'tan bir gün öncesidir. Dönem kârı = o ayın tamamı.
- Tutarlar TL. Testte kuruşa çevir (×100). TC10 ve TC11'deki kuruşlu değerler (669.722,96 gibi) birebir tutmalı.
- Her adımdan sonra `MoneyInvariantsIT`'teki denge kontrolleri de koşsun.
- "Kasa" = CASH, "Ziraat" = BANK, "Cari(X)" = X'in BALANCE hesabı (artı = X bize borçlu, eksi = biz X'e borçluyuz).

## Temel kurallar (beklenen değerler bunlara göre hesaplandı)

- Kâr = satış bedeli − (gerçek alış bedeli + araca masraflar). Ödeme şekli kârı değiştirmez.
- Kâr satış gününde tamamıyla yazılır (tahakkuk). Senetler sonra tahsil edilse de kâr satış ayına aittir.
- Kasa ve banka sadece gerçek para hareketiyle değişir.
- Senetli alış ve senetli satış önce cariye yazılır. Senet ödendikçe ya da tahsil edildikçe cari kapanır.
- Konsinye komisyon oranla hesaplanıyorsa kuruşa yarım yukarı yuvarlanır (`(satış × bp + 5000) / 10000`).
- Önerilen kâr payı = net kâr × ortak ağırlığı. Kuruş aşağı yuvarlanır, kalan kuruş en büyük ortağa gider (`MoneyMath.splitByWeights`).

## TC0. Hazırlık
- Banka hesabı aç: `Ziraat`.
- Kişiler: Furkan, Selin, Burak Şahin, Ali (satıcı), Hasan (satıcı), Veli (alıcı), Can (alıcı), Mehmet (konsinye sahibi), Ayşe (konsinye sahibi). `Eminevim` türü kurum (ORGANIZATION).
- Furkan, Selin ve Burak Şahin'i ortak yap. Furkan dükkân sahibi.
- Sermaye girişi: Furkan 40.000.000 (Kasa), Selin 8.000.000 (Ziraat), Burak Şahin 2.000.000 (Kasa).
- **Beklenen:** Kasa 42.000.000, Ziraat 8.000.000, toplam nakit 50.000.000. Paylar %80 / %16 / %4.

## TC1. Nakit alım, nakit satım
- `34 TC 001`: gerçek alış 1.000.000, peşin, Kasa. Masraf: Boya 20.000, Kasa. Araç maliyeti 1.020.000.
- Veli'ye 1.150.000 peşin satış (Kasa).
- **Beklenen:** kâr 130.000. Veli cari 0. Kasa **42.130.000**.

## TC2. Nakit alım, parçalı satım
- `34 TC 002`: 800.000 peşin, Kasa.
- Can'a 950.000: taksit/senet, peşinat 350.000 (Kasa), 3 senet, ilk vade bugün + 30 gün.
- **Beklenen:** senetler 3 × 200.000. Kâr 150.000. Can cari +600.000. Kasa **41.680.000**.
- İlk senedi tahsil et (Ziraat). **Beklenen:** Ziraat **8.200.000**, Can +400.000.

## TC3. Parçalı alım, nakit satım
- `34 TC 003`: satıcı Ali, gerçek alış 1.000.000, senetli: peşinat 400.000 (Kasa) + 2 senet × 300.000 (vade +15 ve +45 gün).
- **Beklenen:** Kasa 41.280.000. Ali cari −600.000. Ana ekranda verecek 600.000. Ödenecekler'de 2 senet.
- Veli'ye 1.120.000 peşin satış (Kasa). **Beklenen:** kâr 120.000 (senetler ödenmeden). Kasa 42.400.000.
- Ali'nin ilk senedini öde (Kasa). **Beklenen:** Kasa **42.100.000**, Ali −300.000.

## TC4. Parçalı alım, parçalı satım, gecikme
- `34 TC 004`: satıcı Hasan, 600.000: peşinat 200.000 (Kasa) + 1 senet 400.000. **Beklenen:** Kasa 41.900.000, Hasan −400.000.
- Can'a 750.000: peşinat 250.000 (Kasa) + 2 senet × 250.000, **ilk vade dün** (ikinci vade bir ay sonrası).
- **Beklenen:** kâr 150.000. Kasa 42.150.000. Can +900.000. Geciken listesinde Can 250.000 "1 gün gecikti". Ana ekranda geciken tahsilat 1 adet / 250.000.
- Hasan'ın senedini öde (Kasa). **Beklenen:** Kasa **41.750.000**, Hasan 0.

## TC5. Nakit alım, Evim satış
- `34 TC 005`: 1.500.000 peşin, Kasa.
- Veli'ye 1.700.000: Evim/kredi, peşinat 340.000 (Kasa), kalan 1.360.000 kurum Eminevim, vade +10 gün.
- **Beklenen:** Kasa 40.590.000. Veli 0. Eminevim +1.360.000. Kâr 200.000.
- Eminevim satırını tahsil et (Ziraat). **Beklenen:** Ziraat **9.560.000**, Eminevim 0.

## TC6. Konsinye, nakit satış
- `34 TC 006`: konsinye, sahibi Mehmet, oran %3 (300 bp). Masraf 10.000 galeri üstlenir (Kasa). Masraf 5.000 sahibine yansıt (Kasa).
- **Beklenen:** Kasa 40.575.000. Mehmet +5.000. Stok maliyeti değişmez. Ana ekranda "+1 konsinye araç".
- Veli'ye 2.000.000 peşin (Kasa). **Beklenen:** komisyon 60.000. Sahibine borç 1.940.000. Mehmet −1.935.000. Araç kârı 50.000 (60.000 − 10.000). Kasa 42.575.000.
- Mehmet'e öde 1.935.000 (Kasa). **Beklenen:** Mehmet 0, Kasa **40.640.000**.

## TC7. Konsinye, parçalı satış
- `34 TC 007`: konsinye, sahibi Ayşe, sabit komisyon 40.000.
- Can'a 1.000.000: peşinat 300.000 (Kasa) + 2 senet × 350.000.
- **Beklenen:** Kasa 40.940.000. Ayşe −960.000. Can +1.600.000.
- Ayşe'ye 260.000 öde (kısmi, Kasa). **Beklenen:** Ayşe −700.000, Kasa **40.680.000**.

## TC8. Ortaklı araç  ⚠ yeni modelle yeniden hesaplanacak
- `34 TC 008`: ortaklı, Furkan %60 + Selin %40. 500.000 peşin (Kasa). Veli'ye 600.000 peşin (Kasa).
- **Eski beklenen:** kâr 100.000, araç detayında Furkan 60.000 / Selin 40.000. Kasa **40.780.000**. Kimsenin sermayesi değişmez.
- **Yeni model (ortak finansmanı):** bu adımı revizyon dosyasındaki kurallarla yeniden yaz (sahipler, katkı kaynağı, kâr payı oranı, `VEHICLE_PROFIT_SHARE`, dönüş seçimi). Kasa 40.780.000 değişmemeli (para yine kasadan çıkıp kasaya giriyor). Değişen sermaye, pay ve dönem kârı değerlerini yeniden hesapla. Sonraki TC'lere (özellikle TC10 ve TC11) etkisini işle. PR açıklamasına eski/yeni değeri ve nedenini yaz.

## TC9. Burak Şahin modeli (konsinye yolu)
- Burak Şahin'den 2.000.001 çekiş dene. **Beklenen:** hata **1036** (sermayesinden fazla).
- Çekiş 1.200.000 (Kasa). **Beklenen:** Burak sermayesi 800.000, payı yaklaşık %1,64. Kasa 39.580.000.
- `34 TC 009`: konsinye, sahibi Burak Şahin, sabit komisyon 0. Masraf 30.000 sahibine yansıt (Kasa). Veli'ye 1.400.000 peşin (Kasa).
- **Beklenen:** Burak cari −1.370.000. Kasa 40.950.000.
- Burak'a 1.370.000 öde (Kasa), sonra sermaye girişi 1.370.000 (Kasa). **Beklenen:** Burak cari 0, sermaye **2.170.000**, Kasa **40.950.000**.
- Not: bu yol yeni modelde de geçerli kalır. Aynı işin ortak finansmanıyla yapılmış hali için revizyon dosyasındaki örnekleri (1.150.000 / 2.150.000) ayrı bir test olarak ekle.

## TC10. Ara kontrol: bakiyeler ve dönem kârı
- Kasa **40.950.000**, Ziraat **9.560.000**, toplam nakit **50.510.000**.
- Alacak: Can 1.600.000. Verecek: Ayşe 700.000, Ali 300.000 (toplam 1.000.000). Diğer herkes 0.
- Sermayeler: Furkan 40.000.000, Selin 8.000.000, Burak Şahin 2.170.000. Paylar %79,73 / %15,95 / %4,33.
- Dönem kârı (bu ay): araç kârı **750.000** (TC1-TC5), konsinye komisyonu **100.000** (60.000 + 40.000 + 0), genel giderler **10.000**, net **840.000**. Ortaklı araç kârı **100.000** ayrı gösterilir, nete girmez.
- Önerilen: Furkan **669.722,96**, Selin **133.944,58**, Burak Şahin **36.332,46** (toplam tam 840.000,00).
- Denge: nakit 50.510.000 + alacak 1.600.000 − verecek 1.000.000 = **51.110.000** = sermaye 50.170.000 + dağıtılmamış kâr 940.000 (840.000 + ortaklı 100.000).
- ⚠ TC8 yeni modelle değişirse bu bölümdeki sermaye, pay, net ve denge satırları da yeniden hesaplanır.

## TC11. Kâr payını sermayeye ekle ve çek
- Furkan'a 669.722,96 sermayeye ekle. **Beklenen:** Furkan sermayesi 40.669.722,96. Kasa değişmez. Sayfa yenilenince öneriler **aynı** kalır, Furkan'ın "Eklenen" sütunu dolar.
- Selin'e 133.944,58 ve Burak Şahin'e 36.332,46 da ekle. **Beklenen:** paylar yine yaklaşık %79,73 / %15,95 / %4,33.
- Selin'den 133.944,58 çekiş (Ziraat). **Beklenen:** Ziraat 9.426.055,42, Selin sermayesi 8.000.000.

## TC12. Geri alma zinciri
- TC4'teki gecikmiş senedi tahsil et (Kasa), sonra geri al. **Beklenen:** Kasa önce +250.000, sonra eski haline döner. Senet tekrar gecikmiş görünür. Kasa hareketlerinde "Düzeltme: Senet tahsilatı" satırı olur.
- `34 TC 001` satışını geri almayı dene. **Beklenen:** hata **1018** (ödenmiş taksit var).
- Ödeme planında peşin taksiti geri al (Kasa −1.150.000), sonra satışı geri al. **Beklenen:** araç stokta, Veli cari 0, dönem araç kârı 750.000 → 620.000.
- Kasa hareketlerinde satış hareketini ve konsinye borç hareketini "Düzelt" ile düzeltmeyi dene. **Beklenen:** hata **1023**.

## TC13. Virman
- Kasa → Ziraat 1.000.000 virman. **Beklenen:** Kasa −1.000.000, Ziraat +1.000.000, toplam nakit aynı. Kâr ve cariler değişmez.
- Kasa → Kasa virman dene. **Beklenen:** hata **1037**.
