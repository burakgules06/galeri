# Galeri API — Bruno collection

Spring Boot backend endpoint'leri için örnek istek koleksiyonu. Controller kaynaklarından hazırlanmıştır; tüm istekler `{{baseUrl}}` ve `{{token}}` ortam değişkenlerini kullanır.

## Açma

Bruno'da **Open Collection** seçip bu klasörü aç: `bruno/galeri-api`. Üst menüden **Local** ortamını seç. Backend yerelde farklı portta çalışıyorsa `environments/local.bru` içindeki `baseUrl` değerini değiştir.

## İlk giriş

1. `00 - Auth / Login` isteğindeki telefon ve şifreyi environment değişkenlerine yaz. Koleksiyonda gerçek kimlik bilgisi tutulmaz.
2. Login isteğini gönder. Dönen token'ı Bruno'da **Local > Configure** bölümündeki `token` değişkenine yapıştır.
3. Diğer isteklerdeki örnek UUID'leri, kendi veritabanından veya endpoint yanıtlarından aldığın gerçek ID'lerle değiştir.

## Yetki ve örnek veriler

- Login ve push public key dışındaki istekler Bearer token ile gönderilir.
- Yönetim işlemlerinin çoğu ADMIN rolü ister. STAFF kullanıcıları yalnızca yetkili oldukları endpoint'leri çağırabilir.
- Örnek POST/PUT/PATCH/DELETE istekleri gerçek veri değiştirebilir. İlk olarak geliştirme veritabanında dene; ID ve tutarları isteğine göre düzenle.
- Para alanları kuruş cinsindendir: 1.000 TL için `100000` gönder.
- Endpoint'ler Spring Controller'larından listelendi. Yeni backend değişikliklerinde koleksiyonu da güncelle.

## Klasörler

Auth, dashboard, vehicles, contacts, installments, ledger, capital, reports, net worth, catalog, users, staff, settings, push.

Başarılı yanıtlar çoğunlukla `{ success, code, message, data, requestId }` zarfındadır.
