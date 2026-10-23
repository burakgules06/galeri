# AI geliştirme hattı

```
Issue (ai-task) ──► Claude agent ──► branch + özet ──► PR (sen açarsın) ──► CI ──► merge ──► Deploy (prod, elle)
                                                         ▲
                                         "@claude review" / "@claude şunu düzelt"
```

## Günlük kullanım

| Ne istiyorsun | Ne yaparsın |
|---|---|
| Yeni özellik / hata düzeltme | **Issues → New issue → AI görevi**. Kaydedince agent başlar. |
| Agent bitirdi | Issue'daki yorumda **Create PR** bağlantısına tıkla, özeti PR açıklamasına yapıştır. |
| PR'ı inceletmek | PR'a `@claude review` yaz. |
| PR'da düzeltme | PR'a `@claude <ne değişsin>` yaz, agent aynı branch'e commit atar. |
| Prod'a çıkmak | Merge sonrası **Actions → Deploy (prod) → Run workflow**. |

## Güvenlik kuralları

- Agent'lar prod'a erişemez; deploy sadece elle tetiklenir ve sadece repo sahibi tetikleyebilir.
- Deploy sağlık kontrolünden geçmezse sunucu otomatik olarak önceki jar'a döner.
- `migration` etiketli PR'ları merge etmeden önce SQL'i kendin oku.

## Gerekli secret'lar

| Secret | Nereden |
|---|---|
| `CLAUDE_CODE_OAUTH_TOKEN` | Bilgisayarında `claude setup-token` |
| `DEPLOY_HOST`, `DEPLOY_KNOWN_HOSTS`, `DEPLOY_SSH_KEY` | Sunucuda `setup-deploy-user.sh` çıktısı |
