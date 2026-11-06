# E-mails transacionais — Resend

## Papel

O Resend entrega os e-mails de redefinição de senha do DevRats. Ele **não é fonte de verdade**:
o token vive no PostgreSQL, tem hash, validade e uso único. O e-mail é apenas o canal que
leva o link até o usuário.

| E-mail | Gatilho | Destino | Idempotência |
| --- | --- | --- | --- |
| Redefinição de senha | `POST /api/v1/auth/password-reset/request` | E-mail da conta | `password-reset-<tokenId>` |

## Arquitetura

```text
PasswordResetService
  ├─ cria o token no PostgreSQL (transação curta, com lock da conta)
  ├─ grava auditoria
  └─ publica PasswordResetRequestedEvent
              │
              │ AFTER_COMMIT
              ▼
PasswordResetEmailListener (@Async, executor limitado)
              ▼
EmailService (off | test | live)
              ▼
ResendEmailClient → POST https://api.resend.com/emails
```

Decisões:

- **Depois do commit.** Se a transação reverter, nenhum e-mail é enviado.
- **Assíncrono.** O `202` não espera o provedor; uma falha do Resend não desfaz o token
  nem revela se a conta existe.
- **Sem outbox nesta versão.** Se o processo morrer entre o commit e o envio, aquele e-mail
  específico se perde; o usuário pode solicitar outro link. Para entrega garantida, veja
  “Evolução”.
- **Sem lock de banco durante HTTP.** O lock da conta cobre apenas a criação do token.
- **Token no fragmento.** O link é `.../reset-password#token=...`; o fragmento não é enviado
  ao servidor, não aparece em logs de proxy/CDN e não vaza pelo `Referer`.

## Configuração

| Variável | Padrão | Obrigatória | Observação |
| --- | --- | --- | --- |
| `RESEND_API_KEY` | — | em `test`/`live` | Sem ela, o envio fica desligado |
| `EMAIL_MODE` | `off` | não | `off`, `test` ou `live` |
| `EMAIL_FROM` | `DevRats <onboarding@resend.dev>` | em `test`/`live` | Use um endereço do domínio verificado |
| `EMAIL_REPLY_TO` | vazio | não | Resposta opcional |
| `EMAIL_TEST_RECIPIENT` | vazio | em `test` | Único destinatário permitido no modo de teste |
| `EMAIL_TIMEOUT` | `PT10S` | não | Timeout de leitura do Resend |
| `RESEND_BASE_URL` | `https://api.resend.com` | não | Útil para testes/proxy |
| `APP_AUTH_RESET_TOKEN_TTL` | `PT30M` | não | Validade do link |
| `APP_AUTH_RESET_REQUEST_COOLDOWN` | `PT1M` | não | Intervalo mínimo entre pedidos da mesma conta |
| `APP_FRONTEND_URL` | `http://localhost:5173` | sim | Base do link do e-mail |

## Modos

- **`off`** — nenhum envio, apenas log. É o padrão seguro.
- **`test`** — envia **tudo** para `EMAIL_TEST_RECIPIENT`, adiciona `[TESTE]` ao assunto
  e uma faixa no HTML com o destinatário original. Use antes de validar o domínio ou em
  ambientes de homologação.
- **`live`** — envia ao usuário real. Exige domínio verificado e `EMAIL_FROM` desse domínio.

## Domínio e DNS

O domínio **`eufelipedesign.cloud`** está verificado no Resend (região `sa-east-1`).

Registros atuais no painel:

| Tipo | Nome | Valor | Status |
| --- | --- | --- | --- |
| `TXT` | `resend._domainkey` | `p=MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCugSZMSmixnZFP2+gPdxZMduf1gN6hrkGn9LPR2g6B0TJHDpgpXgmpT+dQpYw4ZDVgWBJKPsqQD7CNBYOu1QqNf3tR7iI7o/kP4FoJZcD85FIxJT9Fb7izmtjftapovCu3bh5SC0LjfBwcqbhvn6dHK7lLRhZlBQoKDlygBRW8XwIDAQAB` | Verificado |
| `CNAME` | `rsend` | `rsend-sae1.forge.rmta.net` | Verificado |
| `CNAME` | `send` | `send.forge.rmta.net` | Verificado |

> Não altere MX, SPF ou DKIM de outros serviços que usem o mesmo domínio. O Resend usa
> CNAMEs próprios em `send`/`rsend` e um seletor DKIM separado.

Após a verificação, um DMARC mínimo é recomendado:

| Tipo | Nome | Valor |
| --- | --- | --- |
| `TXT` | `_dmarc` | `v=DMARC1; p=none; rua=mailto:<seu-email>;` |

Só endureça para `quarantine`/`reject` depois de alguns dias de relatórios.

## Teste local

1. No `.env` da API:
   ```properties
   EMAIL_MODE=test
   EMAIL_FROM=DevRats <nao-responda@eufelipedesign.cloud>
   EMAIL_REPLY_TO=<seu-email>
   EMAIL_TEST_RECIPIENT=<seu-email>
   ```
2. Suba o banco e a API:
   ```powershell
   docker compose up -d postgres
   .\run-local.ps1
   ```
3. Abra `http://localhost:5174/forgot-password`, informe o e-mail de uma conta e envie.
4. O e-mail chega em `EMAIL_TEST_RECIPIENT`. Como `app.auth.expose-reset-token=true` em
   `local`, a própria tela também mostra o token de desenvolvimento.
5. Abra o link, defina a nova senha e confirme o login.

## Produção

Configure `/opt/devstreak/.env`:

```properties
RESEND_API_KEY=re_...
EMAIL_MODE=live
EMAIL_FROM=DevRats <nao-responda@eufelipedesign.cloud>
EMAIL_REPLY_TO=<seu-email>
EMAIL_TIMEOUT=PT10S
APP_AUTH_RESET_TOKEN_TTL=PT30M
APP_AUTH_RESET_REQUEST_COOLDOWN=PT1M
```

Recrie o container e confira:

```bash
docker exec devstreak_api printenv EMAIL_MODE EMAIL_FROM
curl -s https://devrats.app/api/v1/auth/password-reset/request \
  -H 'Content-Type: application/json' \
  -d '{"email":"<conta-de-teste>"}'
```

A resposta deve ser `202` com a mensagem genérica. O e-mail chega em instantes.

## Verificação

1. Domínio verificado no Resend:
   ```bash
   curl -s https://api.resend.com/domains \
     -H "Authorization: Bearer $RESEND_API_KEY"
   ```
2. Painel do Resend → **Emails** → último envio deve estar `delivered`.
3. Logs da API:
   ```bash
   docker logs devstreak_api --tail 100 | grep -i "redefini"
   ```
   O log mostra `userId`, `deliveryId`, `mode` e código de erro — nunca o token ou o e-mail.
4. Métrica Prometheus:
   ```text
   email_send_total{type="password_reset",result="sent"}
   ```

## Troubleshooting

| Sintoma | Causa provável | Ação |
| --- | --- | --- |
| `EMAIL_MODE=off` no log | Variável ausente | Definir `test` ou `live` |
| `EMAIL_NOT_CONFIGURED` | Chave ausente | Preencher `RESEND_API_KEY` |
| `validation_error` (422) | `EMAIL_FROM` fora do domínio verificado | Corrigir o remetente |
| `rate_limit_exceeded` (429) | Limite do Resend | Aguardar e reenviar |
| E-mail não chega | Modo `test` com destinatário errado, spam ou domínio recém-verificado | Conferir `EMAIL_TEST_RECIPIENT`, spam e painel do Resend |
| Link inválido | Token usado ou expirado | Solicitar novo link |
| Sem e-mail, mas token na tela | `app.auth.expose-reset-token=true` (local) | Comportamento esperado em desenvolvimento |

## Segurança

- Token de 32 bytes, hash SHA-256 no banco, uso único e TTL configurável.
- Todos os refresh tokens são revogados após a troca.
- Todos os tokens ativos da conta são invalidados quando um novo é emitido ou usado.
- Mesma resposta para e-mail inexistente, conta inativa e conta existente.
- Cooldown por conta + rate limit por IP em `/api/v1/auth/**`.
- `Cache-Control: no-store` em `/api/v1/auth`.
- `Referrer-Policy: no-referrer` no frontend.
- Nome do usuário escapado no HTML; token nunca em log, assunto ou URL de query.
- Segredo apenas em `.env`/ambiente; `.env` não é versionado.

## Evolução

Se entrega garantida passar a ser requisito, o próximo passo é um **outbox no PostgreSQL**:

```text
email_outbox (PENDING → SENT | FAILED, attempts, next_attempt_at)
  └─ scheduler com FOR UPDATE SKIP LOCKED + retry/backoff
```

Isso sobrevive a restart e funciona com múltiplas instâncias. RabbitMQ/SQS só entra depois,
se houver volume ou fan-out que justifiquem um broker.

## Definition of Done

- [x] Envio via Resend em `test` e `live`.
- [x] Link com token no fragmento e validade de 30 minutos.
- [x] Token de uso único, hash no banco e sessões revogadas.
- [x] Mesma resposta para conta inexistente/inativa.
- [x] Cooldown por conta e rate limit por IP.
- [x] Auditoria e métricas sem PII.
- [x] Testes unitários do cliente, template, serviço e reset.
- [x] Teste de integração com Testcontainers.
- [ ] Validar entrega real em produção após o deploy.
