# Galeri

Oto galeriler için araç stoku ve müşteri yönetimi (çok kiracılı SaaS).
Akıllı Randevum ve Düğün Salonu Asistanı ile aynı mimariden türetildi; WhatsApp entegrasyonu yok.
Proje başlangıç aşamasında: şu an sadece iskelet var (galeri/tenant, panel kullanıcısı, giriş).

## Teknoloji

- Backend: Java 21, Spring Boot 4.1, Maven (`./mvnw`), PostgreSQL 16, Flyway,
  Spring Data JPA (`ddl-auto=validate`), Spring Security + JWT, Lombok, Actuator
- Panel (`panel/`): React 19 + TypeScript + Vite + Tailwind

## Komutlar

```bash
docker compose up -d                                  # lokal Postgres (5434)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run     # backend, 8082; demo giris: 5550000000 / demo1234
./mvnw -B verify                                      # derleme + testler (Postgres ve JWT_SECRET gerekir)

cd panel
npm ci && npm run dev                                 # 5173; /api istekleri 8082'ye proxy'lenir
npm run lint && npm run build
```

`dev` profili (`application-dev.properties`) lokal DB adresini, sabit bir JWT anahtarını ve
`db/dev/R__dev_seed.sql` ile demo galeri + kullanıcıyı getirir. Prod'da bu profil asla açılmaz;
prod değerleri `/opt/galeri/.env`'den gelir (`.env.example`'a bak). `jwt.secret`'ın varsayılanı yoktur.

`AppApplicationTests` gerçek Postgres'e bağlanır. CI'da ve agent ortamında `DB_URL`, `DB_USER`,
`DB_PASSWORD`, `JWT_SECRET` ortam değişkenleriyle sağlanır.

## Mimari

Alan modeli, para kuralları ve yol haritası: **[docs/MIMARI.md](docs/MIMARI.md)**. Yeni bir özelliğe başlamadan önce oku; oradaki kavramlara ve kurallara (hareket defteri, kuruş cinsinden tutar, silinmeyen hareketler, STAFF'a para alanı dönmemesi) uy.


```
com.galeri.app
├── controller/        REST uçları (/api/auth/login açık, diğer /api/** JWT ister)
├── service/ + impl/   İş mantığı (arayüz + Impl); GenericResponseService cevap zarfını üretir
├── repository/        Spring Data JPA
├── model/entity       BaseModel (created_at/updated_at), Tenant, PanelUser
├── model/response     API cevap DTO'ları (record)
├── security/          JWT, CurrentUser, istek loglama (requestId/tenantId MDC)
├── exception/         GlobalExceptionHandler + runtime/ altında iş kuralı hataları
├── constant/          ErrorMessageConstant (panel bu kodlara göre mesaj gösterir)
└── config/ util/
panel/src/
├── api/client.ts      apiFetch: token ekler, GenericResponse zarfını açar, 401'de oturumu kapatır
├── auth/              AuthProvider, useAuth, LoginPage
└── layout/Dashboard   Modüller buraya eklenecek
```

Her cevap `GenericResponse` zarfıyla döner: `{ success, code, message, data, requestId }`.
Panel tarafında `apiFetch<T>()` doğrudan `data`'yı verir; hata olursa `ApiError` fırlatır.

## Kurallar (agent'lar dahil herkes için)

### Veritabanı
- **Mevcut bir `V*__*.sql` dosyasını asla değiştirme.** Yeni migration bir sonraki numarayla (`V2__arac_tablosu.sql`).
- Her yeni iş tablosu `tenant_id UUID NOT NULL REFERENCES tenant(id)` taşır ve `tenant_id` üzerinde index'lenir.
- Entity değişikliği ve migration aynı PR'da olmalı (Hibernate `validate`).
- Veri silen ya da kolon daraltan migration için PR açıklamasında açıkça uyar; PR'a `migration` etiketi ekle.
- `db/dev/` sadece lokal örnek veri içindir; prod'a gidecek hiçbir şey oraya yazılmaz.

### Çok kiracılılık
- Her sorgu tenant'a göre filtrelenmeli. Tenant her zaman `CurrentUser.tenantId()`'den alınır, istek gövdesinden değil.
- Başka tenant'a ait bir kayıt istenirse `SourceNotBelongToTenantException` fırlat (dışarıya "bulunamadı" der).

### Zaman, gizli bilgiler, stil
- Şu anki zaman için enjekte edilen `Clock` kullanılır (`ClockConfig`). `LocalDateTime.now()` / `Instant.now()` doğrudan kullanma.
- Gizli bilgi koda ya da `application.properties` varsayılanına yazılmaz. Yeni ayar `${ENV_ADI}` olarak eklenir,
  `.env.example`'a da yazılır ve PR açıklamasında belirtilir.
- Servis arayüzü + `impl/`, constructor injection (Lombok `@RequiredArgsConstructor`).
- Yorumlar ve log mesajları Türkçe ASCII ("Basarili giris"); kullanıcıya giden metinler düzgün Türkçe.
- İş kuralı hataları `ApplicationRuntimeException`'dan türer; her birine `ErrorMessageConstant`'ta yeni bir kod verilir.

### Testler
- Yeni iş mantığı test olmadan PR'a gitmez. Desen: Mockito + sabit `Clock` (`JwtServiceTest`, `AuthControllerTest`).
- Test adları Türkçe ve davranışı anlatır: `yanlisSifre_401Doner`.

### Panel
- `npm run lint` ve `npm run build` temiz geçmeli (CI'da ikisi de zorunlu).
- Component dosyası sadece component export eder; context/hook ayrı `.ts` dosyasında (`auth/useAuth.ts` gibi).
  Yeni dosya adı mevcut bir dosyayla sadece harf büyüklüğünde ayrışmasın (Windows).
- API çağrıları `apiFetch` üzerinden; adres `VITE_API_BASE`'den gelir, sabit URL yazma.
- Arayüz metinleri Türkçe.

### Dokunma
- `.github/workflows/deploy.yml` ve sunucu ayarları: sadece Burak değiştirir.

## İş akışı (agent'lar için)

1. Issue'yu oku. Belirsizlik varsa varsayımını issue'ya yorum olarak yaz, sonra devam et.
2. Değişikliği yap, testleri ekle, `./mvnw -B verify` (panel değiştiyse `npm run lint && npm run build`) çalıştır.
3. Commit mesajları İngilizce, kısa ve emir kipinde.
4. Branch'i push et; PR'ı Burak açar. Bitirdiğinde issue yorumuna Türkçe bir PR özeti yaz:
   ne/neden (`Closes #N`), nasıl test edildi, migration ya da yeni `.env` ayarı var mı, riskler.
5. Prod'a deploy etme. Deploy her zaman Burak'ın onayıyla, elle tetiklenir.
