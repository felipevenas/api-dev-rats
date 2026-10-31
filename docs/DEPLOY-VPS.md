# Deploy de produção (Vercel + VPS Azure) — sem domínio próprio

Layout em uso desde 30/10/2026. O projeto **não usa domínio registrado**: o frontend fica na URL padrão da
Vercel e a API usa um hostname `sslip.io`, que resolve para o IP embutido no próprio nome.

| Peça | Onde | URL |
| --- | --- | --- |
| Frontend (SPA) | Vercel · projeto `devstreak` | https://devstreak-ten.vercel.app |
| API (Spring Boot) | VPS Azure `venasvps` | https://172-179-233-109.sslip.io |
| Postgres | VPS, database `devstreak` no container `evolution_postgres` | interno |

- **VM**: `venasvps`, RG `VENASVPS_GROUP`, `Standard_B2pts_v2` (2 vCPU ARM64, ~1 GB), `westus2`, IP `172.179.233.109`
- **NSG**: apenas 80, 443 e 22. Nenhuma porta de aplicação publicada.
- **Domínio**: nenhum. `172-179-233-109.sslip.io` → A implícito para `172.179.233.109` (DNS de terceiros, grátis).

> ⚠️ O hostname da API contém o IP. Se o IP público mudar, é preciso atualizar o Caddyfile, o `vercel.json` e a
> GitHub App. **Deixe o IP público como estático no portal Azure** (Public IP → Configuration → Static).

## Como as chamadas funcionam

O SPA chama `/api/v1/...` no **próprio domínio da Vercel**. O `vercel.json` faz rewrite server-side para a API:

```json
{ "source": "/api/:path*", "destination": "https://172-179-233-109.sslip.io/api/:path*" }
```

Isso mantém tudo *first-party* para o browser: o cookie `ds_refresh` (`SameSite=Strict`, `Secure`, `HttpOnly`,
`Path=/api/v1/auth`) é gravado no host da Vercel e enviado normalmente no refresh. Sem rewrite seria cross-site e
o `SameSite=Strict` bloquearia o cookie.

O OAuth do GitHub **não** passa pelo rewrite: a Callback URL aponta direto para a API, e o backend redireciona o
usuário para `APP_FRONTEND_URL`.

## Estrutura na VPS

```
/opt/devstreak/
├── .env                  # segredos de produção (chmod 600)
├── docker-compose.yml    # apenas o serviço api
├── Dockerfile
└── pom.xml / mvnw / .mvn / src
```

O container entra na rede externa `evolution_evolution` e **não publica portas** (`expose: 8080`). O Caddy da
stack da Evolution faz o reverse proxy.

Banco: database `devstreak` (role `devstreak`) dentro do `evolution_postgres` — sem container Postgres próprio
por causa da RAM.

## Caddy

`/opt/evolution/Caddyfile` (backup: `Caddyfile.bak.2026-10-30`):

```caddyfile
172-179-233-109.sslip.io {
	encode gzip
	reverse_proxy devstreak_api:8080
}
```

Aplicar:

```bash
docker exec evolution_caddy caddy validate --config /etc/caddy/Caddyfile
docker exec evolution_caddy caddy reload  --config /etc/caddy/Caddyfile
```

> Não sobrescreva `X-Forwarded-For`: quando a origem é a Vercel, é ela quem entrega o IP real do cliente na
> primeira posição do header, e o rate limit por IP depende disso.

## Variáveis da API (`/opt/devstreak/.env`)

```properties
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://evolution_postgres:5432/devstreak
DB_USER=devstreak
DB_PASS=...
JWT_SECRET=...                 # base64, >= 32 bytes
TOKEN_ENCRYPTION_KEY=...       # base64, 32 bytes (AES-GCM dos tokens GitHub)
GITHUB_APP_ID=5546729
GITHUB_CLIENT_ID=Iv23liA2nnSj0Dp90FHP
GITHUB_CLIENT_SECRET=...
GITHUB_APP_SLUG=devstreak-local-felipe
GITHUB_PRIVATE_KEY=...         # PEM em linha única com \n
GITHUB_WEBHOOK_SECRET=dev-webhook-secret
GITHUB_CALLBACK_URL=https://172-179-233-109.sslip.io/api/v1/integrations/github/callback
GITHUB_SETUP_URL=https://172-179-233-109.sslip.io/api/v1/integrations/github/setup
APP_FRONTEND_URL=https://devstreak-ten.vercel.app
APP_CORS_ALLOWED_ORIGINS=https://devstreak-ten.vercel.app,https://devstreak-felipevenas-projects.vercel.app
RATE_LIMIT_TRUST_FORWARDED_HEADERS=true
SPRINGDOC_ENABLED=false
```

`RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` é obrigatório atrás de proxy.

> Cuidado ao rodar comandos em pipeline: `docker compose up -d --force-recreate | tail -2` pode morrer por SIGPIPE
> e **não** recriar o container. Rode sem pipe e confira com
> `docker exec devstreak_api printenv APP_CORS_ALLOWED_ORIGINS`.

## Atualizar a API

```bash
tar -czf api-src.tar.gz -C api-dev-rats Dockerfile pom.xml mvnw .mvn src
scp -i ~/.ssh/devstreak_vps api-src.tar.gz <usuario>@172.179.233.109:/tmp/
ssh -i ~/.ssh/devstreak_vps <usuario>@172.179.233.109 \
  "cd /opt/devstreak && tar -xzf /tmp/api-src.tar.gz && docker compose build && docker compose up -d"
```

## Atualizar o frontend

```bash
cd webapp-dev-rats
vercel --prod --scope felipevenas-projects
```

`vercel.json` define `outputDirectory: dist`, o rewrite de `/api` e o fallback de SPA. **Não** defina
`VITE_API_BASE_URL`: o padrão do app é `/api/v1` (mesma origem).

## Resource limits

A VM tem ~1 GB e a stack da Evolution ocupa ~610 MB:

- `mem_limit: 640m` / `memswap_limit: 1200m` — se o Spring estourar, só ele reinicia.
- `JAVA_TOOL_OPTIONS=-Xms192m -Xmx320m -XX:MaxMetaspaceSize=224m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError`
- 2 GB de swap em `/swapfile` (no `/etc/fstab`)

> **Metaspace**: não desça de ~200 MB. O fluxo OAuth (`/integrations/github/callback`) carrega BouncyCastle e
> Nimbus para assinar o JWT da GitHub App; com 112 MB o app morria com `OutOfMemoryError: Metaspace` no callback.

Para folga real: SKU ARM com 4 GB (família `B*pls_v2`) e `-Xmx` em ~1 GB.

## GitHub App

*Settings → Developer settings → GitHub Apps → devstreak-local-felipe*:

| Campo | Valor |
| --- | --- |
| Homepage URL | `https://devstreak-ten.vercel.app` |
| Callback URL | `https://172-179-233-109.sslip.io/api/v1/integrations/github/callback` |
| Setup URL | `https://172-179-233-109.sslip.io/api/v1/integrations/github/setup` |
| Webhook URL | `https://172-179-233-109.sslip.io/api/v1/webhooks/github` |
| Content type | `application/json` |
| Secret | igual a `GITHUB_WEBHOOK_SECRET` |
| Events | `Push` |

> A Callback URL é única por App. Para testar OAuth local, troque temporariamente para
> `http://localhost:8080/api/v1/integrations/github/callback`.

## Rollback

```bash
sudo cp /opt/evolution/Caddyfile.bak.2026-10-30 /opt/evolution/Caddyfile
docker exec evolution_caddy caddy reload --config /etc/caddy/Caddyfile
cd /opt/devstreak && docker compose down
```
