# Deploy de produção (Vercel + VPS Azure) — devrats.app

Layout com domínio próprio. O frontend fica em `devrats.app` (Vercel) e a API em `api.devrats.app` (VPS Azure).
O `sslip.io` deixa de ser usado em produção.

| Peça | Onde | URL |
| --- | --- | --- |
| Frontend (SPA) | Vercel · projeto `devrats-web` | https://devrats.app |
| Frontend alternativo | Vercel · redirect 308 para o apex | https://www.devrats.app |
| API (Spring Boot) | VPS Azure `venasvps` | https://api.devrats.app |
| WebSocket/STOMP | VPS Azure `venasvps` | wss://api.devrats.app/ws |
| Postgres | VPS, database `devstreak` no container `evolution_postgres` | interno |

> **Identificadores legados (proposital)**: o app agora se chama **DevRats**, mas alguns nomes de infraestrutura
> continuam com `devstreak` para não quebrar dados e integrações já provisionadas — database/role `devstreak`,
> diretório `/opt/devstreak`, container `devstreak_api`, volume `devstreak_pgdata`, chave SSH `devstreak_vps`
> e o slug da GitHub App `devstreak-local-felipe`. Renomear qualquer um deles exige migração de dados ou recriação
> da App; o código, o domínio e a marca já usam `devrats`.

- **VM**: `venasvps`, RG `VENASVPS_GROUP`, `Standard_B2pts_v2` (2 vCPU ARM64, ~1 GB), `westus2`, IP `172.179.233.109`
- **NSG**: apenas 80, 443 e 22. Nenhuma porta de aplicação publicada.
- **Domínio**: `devrats.app` registrado no Name.com (promoção estudante GitHub).
- **DNS**: Name.com (ou nameservers da Vercel, se você preferir centralizar).
- **TLS**: Vercel emite o certificado do frontend; Caddy emite o da API via Let's Encrypt.

## DNS no Name.com

Registre estes records em **Name.com → My Domains → devrats.app → DNS Records**.
Use TTL `300` durante a migração e `3600` depois.

| Tipo | Nome | Valor | Para quê |
| --- | --- | --- | --- |
| A | `@` | `216.198.79.1` | Apex `devrats.app` na Vercel |
| A | `@` | `64.29.17.1` | Apex `devrats.app` na Vercel (segundo IP) |
| CNAME | `www` | `049563e597390b02.vercel-dns-017.com` | `www.devrats.app` na Vercel |
| A | `api` | `172.179.233.109` | API na VPS Azure |

> Esses são os valores que a Vercel retornou para este projeto em 04/11/2026. Eles podem mudar; rode
> `vercel domains verify devrats.app --scope felipevenas-projects` para ver os records recomendados atuais.
> O `CNAME cname.vercel-dns.com.` também é aceito como fallback.
> Remova o `A 91.195.240.94` (parking do Name.com) e qualquer outro `A`/`CNAME` antigo de `@`/`www`.
>
> Se você colocar Cloudflare na frente do DNS, deixe o record `api` como **DNS only** (nuvem cinza) para o Caddy
> conseguir emitir o certificado via HTTP-01. Proxy laranja exigiria certificado de origem ou DNS-01.

**Alternativa:** trocar os nameservers do Name.com para `ns1.vercel-dns.com` e `ns2.vercel-dns.com` e criar
todos os records no painel da Vercel. É mais simples para o frontend, mas exige recriar o `A api` lá também.
Só faça isso se não houver e-mail ou outros serviços usando o DNS atual.

### Passo a passo

1. No Name.com, remova o `A 91.195.240.94` e adicione os records acima.
2. `devrats.app` e `www.devrats.app` já foram adicionados ao projeto `devrats-web` via CLI. Confirme em
   **Settings → Domains** e defina `devrats.app` como primário.
3. Rode a verificação:
   ```bash
   vercel domains verify devrats.app --scope felipevenas-projects
   vercel domains verify www.devrats.app --scope felipevenas-projects
   ```
4. Aguarde a verificação e o certificado TLS da Vercel.
5. Confirme a propagação:
   ```bash
   nslookup devrats.app
   nslookup api.devrats.app
   curl -I https://devrats.app
   curl -s https://api.devrats.app/actuator/health
   ```
6. Só depois de `api.devrats.app` resolver para a VPS, adicione o bloco no Caddy para emitir o certificado da API.

> ⚠️ O IP público da VPS precisa continuar estático no Azure (Public IP → Configuration → Static). Se ele mudar,
> atualize o record `A api` e o `GITHUB_CALLBACK_URL`/webhook.

## Ordem de migração

1. **DNS**: crie `A @`, `CNAME www`, `A api` e o `TXT _vercel` no Name.com. Aguarde propagar.
2. **Vercel**: adicione `devrats.app` e `www.devrats.app` ao projeto e espere o TLS.
3. **Caddy**: adicione o bloco `api.devrats.app` mantendo o `sslip.io`; recarregue e confirme
   `curl -s https://api.devrats.app/actuator/health`.
4. **API**: atualize `APP_FRONTEND_URL`, `APP_CORS_ALLOWED_ORIGINS`, `GITHUB_CALLBACK_URL` e `GITHUB_SETUP_URL`
   no `/opt/devstreak/.env`; recrie o container (há um restart breve de uma única instância).
5. **Vercel env**: defina `VITE_WS_URL=wss://api.devrats.app/ws` e faça o deploy de produção.
6. **GitHub App**: atualize Homepage, Callback, Setup e Webhook para o novo domínio.
7. **Validação**: login, refresh, upload de avatar, OAuth, webhook, chat REST e chat WebSocket.
8. **Limpeza**: remova o bloco `sslip.io` do Caddy e os records antigos.

> Faça o deploy do `vercel.json` novo **depois** que `api.devrats.app` estiver respondendo. Se a Vercel apontar
> para um domínio que ainda não existe, o REST quebra mesmo com o frontend no ar.

## Como as chamadas funcionam

O SPA chama `/api/v1/...` no **próprio domínio** (`https://devrats.app`). O `vercel.json` faz rewrite server-side
para a API:

```json
{ "source": "/api/:path*", "destination": "https://api.devrats.app/api/:path*" }
```

Isso mantém o REST *first-party* para o browser: o cookie `ds_refresh` (`SameSite=Strict`, `Secure`, `HttpOnly`,
`Path=/api/v1/auth`) é gravado em `devrats.app` e enviado normalmente no refresh. Sem rewrite, o REST seria
cross-origin para `api.devrats.app`; como os dois hosts são do mesmo site (`devrats.app`), o `SameSite=Strict`
ainda permitiria o cookie, mas exigiria CORS com credenciais e aumentaria a superfície de configuração.

O **WebSocket não passa pelo rewrite da Vercel**. O browser conecta direto em `wss://api.devrats.app/ws`; o token
vai no header do frame STOMP `CONNECT`, então não há dependência de cookie nem de same-origin. Por isso a variável
de produção do frontend é:

```env
VITE_API_BASE_URL=
VITE_WS_URL=wss://api.devrats.app/ws
```

O OAuth do GitHub **não** passa pelo rewrite: a Callback URL aponta direto para a API, e o backend redireciona o
usuário para `APP_FRONTEND_URL=https://devrats.app`.

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

Durante a migração, mantenha o bloco antigo e adicione o novo:

```caddyfile
172-179-233-109.sslip.io {
	encode gzip
	reverse_proxy devstreak_api:8080
}

api.devrats.app {
	encode gzip
	reverse_proxy devstreak_api:8080
}
```

O `reverse_proxy` do Caddy já encaminha o upgrade de WebSocket automaticamente. O certificado é emitido via
Let's Encrypt quando `api.devrats.app` aponta para a VPS e as portas 80/443 estão abertas. Depois de validar o novo
domínio, remova o bloco `sslip.io` e recarregue o Caddy.

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
GITHUB_CALLBACK_URL=https://api.devrats.app/api/v1/integrations/github/callback
GITHUB_SETUP_URL=https://api.devrats.app/api/v1/integrations/github/setup
APP_FRONTEND_URL=https://devrats.app
# Mantenha o domínio antigo durante a migração; remova depois do cutover.
APP_CORS_ALLOWED_ORIGINS=https://devrats.app,https://www.devrats.app,https://devrats-web.vercel.app
RATE_LIMIT_TRUST_FORWARDED_HEADERS=true
SPRINGDOC_ENABLED=false
# Chat: bucket privado de anexos (opcional; sem ele o texto funciona)
APP_CHAT_STORAGE_BUCKET=
APP_CHAT_STORAGE_KEY_PREFIX=chat
APP_CHAT_ATTACHMENT_URL_TTL=PT15M
```

`RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` depende de o proxy preservar o IP real do cliente na primeira posição
do `X-Forwarded-For`. Confirme isso no Caddy/Vercel antes de manter habilitado; caso contrário, o header é
spoofável e o rate limit por IP perde valor.

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

`vercel.json` define `outputDirectory: dist`, o rewrite de `/api` e o fallback de SPA. **Remova**
`VITE_API_BASE_URL` do ambiente de Production da Vercel: o padrão do app é `/api/v1` (mesma origem). Defina
**somente**:

```env
VITE_WS_URL=wss://api.devrats.app/ws
```

O `VITE_WS_URL` é obrigatório em produção porque o WebSocket não passa pelo rewrite da Vercel. Sem ele, o app
tentaria `wss://devrats.app/ws` e a conexão falharia.

> **Preview deployments:** o REST funciona via rewrite, mas o WebSocket só conecta se a origem
> `https://<preview>.vercel.app` estiver em `APP_CORS_ALLOWED_ORIGINS`. Para testar chat em preview, adicione a URL
> específica do deployment (evite wildcard em produção).

## Resource limits

A VM tem ~1 GB e a stack da Evolution ocupa ~610 MB:

- `mem_limit: 640m` / `memswap_limit: 1200m` — se o Spring estourar, só ele reinicia.
- `JAVA_TOOL_OPTIONS=-Xms192m -Xmx320m -XX:MaxMetaspaceSize=224m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError`
- 2 GB de swap em `/swapfile` (no `/etc/fstab`)

> **Metaspace**: não desça de ~200 MB. O fluxo OAuth (`/integrations/github/callback`) carrega BouncyCastle e
> Nimbus para assinar o JWT da GitHub App; com 112 MB o app morria com `OutOfMemoryError: Metaspace` no callback.

Para folga real: SKU ARM com 4 GB (família `B*pls_v2`) e `-Xmx` em ~1 GB.

## E-mail (opcional)

O frontend usa `contato@devrats.app` nas páginas legais, mas registrar o domínio não cria caixa postal. Para
receber mensagens:

- **Name.com Email Forwarding** (se disponível no plano): encaminhe `contato@devrats.app` para seu e-mail pessoal.
- **ImprovMX** (plano gratuito) ou **Google Workspace**: crie os registros `MX`, `SPF`, `DKIM` e `DMARC` indicados
  pelo provedor.

Sem isso, o link `mailto:` abre o cliente de e-mail do usuário, mas a mensagem não chega a uma caixa do domínio.

## GitHub App

*Settings → Developer settings → GitHub Apps → devstreak-local-felipe*:

| Campo | Valor |
| --- | --- |
| Homepage URL | `https://devrats.app` |
| Callback URL | `https://api.devrats.app/api/v1/integrations/github/callback` |
| Setup URL | `https://api.devrats.app/api/v1/integrations/github/setup` |
| Webhook URL | `https://api.devrats.app/api/v1/webhooks/github` |
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
