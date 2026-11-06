# feat(auth): redefinição de senha por e-mail via Resend

## Resumo

Fecha o fluxo de recuperação de senha do DevRats:

- envia o link por e-mail usando o Resend;
- mantém o token no PostgreSQL com hash SHA-256, validade e uso único;
- responde igual para conta existente, inexistente ou inativa;
- envia depois do commit, de forma assíncrona;
- revoga todas as sessões após a troca;
- tem cooldown por conta, auditoria, métrica e limpeza diária.

## Motivação

Os endpoints e o token já existiam, mas o e-mail nunca era enviado. Em produção, o usuário
recebia `202` e não conseguia redefinir a senha.

## Decisões de arquitetura

1. **Resend via HTTP**, sem SDK e sem SMTP. O `ResendEmailClient` segue o padrão do
   `GitHubApiClient`: timeouts curtos, erro classificado e nenhuma dependência extra.
2. **Evento depois do commit.** `@TransactionalEventListener(AFTER_COMMIT)` evita enviar
   e-mail de uma transação revertida.
3. **Assíncrono com executor limitado.** O `202` não espera o provedor; falha do Resend não
   muda a resposta nem revela existência da conta.
4. **Sem outbox nesta versão.** Se o processo cair entre o commit e o envio, aquele e-mail
   se perde; o usuário pode solicitar outro. A evolução com outbox está em `docs/EMAIL.md`.
5. **Token no fragmento.** `#token=...` não vai ao servidor, não aparece em logs de proxy/CDN
   e não vaza pelo `Referer`.
6. **Lock da conta, não da conexão.** O `PESSIMISTIC_WRITE` cobre só a criação do token;
   nenhuma chamada HTTP acontece dentro da transação.
7. **Configuração fail-closed.** `EMAIL_MODE=off` por padrão; produção exige `live` explícito.

## Banco de dados

Nenhuma migration nesta versão. A tabela `password_reset_tokens` já existe com:

- `token_hash` único;
- `expires_at` indexado;
- `used_at` para uso único;
- FK com `ON DELETE CASCADE`.

A única mudança de schema-adjacent é o uso dos índices existentes para a limpeza diária.

## API

| Método | Rota | Resposta |
| --- | --- | --- |
| POST | `/api/v1/auth/password-reset/request` | `202` com mensagem genérica; `resetToken` só em local/test |
| POST | `/api/v1/auth/password-reset/confirm` | `204`; `400 VALIDATION_ERROR` para token inválido/expirado |

O contrato de sucesso não mudou. A mensagem ficou mais precisa:
“Se houver uma conta com este e-mail, enviaremos um link para redefinir a senha.”

## Fluxo

1. Normaliza o e-mail e busca a conta com `PESSIMISTIC_WRITE`.
2. Se não existir ou estiver inativa, retorna o mesmo `202`.
3. Respeita o cooldown configurado por conta.
4. Marca tokens anteriores como usados e cria o novo token (hash no banco).
5. Grava auditoria e publica `PasswordResetRequestedEvent`.
6. Depois do commit, o listener assíncrono chama `EmailService`.
7. `EmailService` aplica `off`/`test`/`live`, monta o template e chama o Resend.
8. Na confirmação: valida senha, token, expiração e uso; troca o hash; invalida todos os
   tokens ativos; revoga refresh tokens; audita.

## Configuração

| Variável | Padrão |
| --- | --- |
| `RESEND_API_KEY` | — |
| `EMAIL_MODE` | `off` |
| `EMAIL_FROM` | `DevRats <onboarding@resend.dev>` |
| `EMAIL_REPLY_TO` | vazio |
| `EMAIL_TEST_RECIPIENT` | vazio |
| `EMAIL_TIMEOUT` | `PT10S` |
| `APP_AUTH_RESET_TOKEN_TTL` | `PT30M` |
| `APP_AUTH_RESET_REQUEST_COOLDOWN` | `PT1M` |

## Segurança

- Token de 32 bytes, SHA-256 no banco, uso único e TTL.
- Mesma resposta para conta inexistente, inativa e existente.
- Cooldown por conta + rate limit por IP em `/api/v1/auth/**`.
- `Cache-Control: no-store` em `/api/v1/auth`.
- `Referrer-Policy: no-referrer` no frontend.
- Nome escapado no HTML; token nunca em log, assunto ou query string.
- Segredo apenas em `.env`/ambiente.

## Frontend

Em `../webapp-dev-rats`:

- `/forgot-password` mostra confirmação, destino informado, validade, spam e opção de trocar
  o e-mail.
- `/reset-password` lê `#token=`, aceita `?token=` como fallback e mostra erro inline para
  link inválido/expirado.
- O token de desenvolvimento só aparece em `import.meta.env.DEV`.
- `ScrollToTop` passou a usar `getElementById`, porque `#token=...` não é seletor CSS válido.

## Testes

- `ResendEmailClientTest` (WireMock): payload, autorização, idempotência, 429/422, chave ausente.
- `PasswordResetEmailTemplateTest`: escaping, link, texto puro, banner de teste.
- `EmailServiceImplTest`: `off`, `test`, `live`, falha absorvida e métrica.
- `PasswordResetServiceImplTest`: conta desconhecida/inativa, cooldown, evento, expiração,
  uso único, revogação de sessões e limpeza.
- `PasswordResetIntegrationTest`: fluxo completo, token de uso único, senha antiga rejeitada,
  token expirado e `Cache-Control`.
- `PrivateCacheControlFilterTest`: `/api/v1/auth` com `no-store`.
- Frontend: schemas de forgot/reset, lint, typecheck e build.

**Gates:** `.\mvnw.cmd verify` passou com **220 testes, 0 falhas**, incluindo Testcontainers,
ArchUnit e JaCoCo.

## Deploy

1. Adicionar as variáveis de e-mail no `/opt/devstreak/.env`.
2. Recriar o container.
3. Validar uma solicitação real e a entrega no painel do Resend.
4. Só então manter `EMAIL_MODE=live`.

Runbook completo: [`EMAIL.md`](EMAIL.md).
