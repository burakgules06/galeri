# Galeri

Oto galeriler için araç stoku ve müşteri yönetimi. Geliştirme rehberi ve kurallar: [CLAUDE.md](CLAUDE.md).
AI geliştirme hattı: [docs/ORCHESTRATOR.md](docs/ORCHESTRATOR.md).

## Hızlı başlangıç (Windows)

```powershell
docker compose up -d
$env:SPRING_PROFILES_ACTIVE="dev"; .\mvnw.cmd spring-boot:run
# başka bir terminalde
cd panel; npm ci; npm run dev
```

http://localhost:5173 → telefon `5550000000`, şifre `demo1234`.
