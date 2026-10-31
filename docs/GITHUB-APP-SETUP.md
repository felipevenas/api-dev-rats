# Configurando a GitHub App (ambiente local)

A integração do DevRats com o GitHub usa uma **GitHub App** (e não um OAuth App comum), porque só a App gera
*installation tokens* para ler commits dos repositórios escolhidos pelo usuário.

Sem as 5 credenciais abaixo o backend responde `appConfigured: false`, a tela
`/app/settings/integrations` mostra "GitHub App não configurada" e o botão **Conectar GitHub** fica desabilitado.

```
GITHUB_APP_ID
GITHUB_CLIENT_ID
GITHUB_CLIENT_SECRET
GITHUB_PRIVATE_KEY
GITHUB_WEBHOOK_SECRET
```

---

## 1. Criar a App

Acesse **https://github.com/settings/apps/new** (conta pessoal) — ou `/organizations/<org>/settings/apps/new`
se a App for da organização.

Preencha exatamente assim:

| Campo | Valor |
| --- | --- |
| **GitHub App name** | `devrats-local-<seu-usuario>` (precisa ser único no GitHub) |
| **Description** | `DevRats local` (opcional) |
| **Homepage URL** | `http://localhost:5174` |
| **Callback URL** (em *Identifying and authorizing users*) | `http://localhost:8080/api/v1/integrations/github/callback` |
| **Expire user authorization tokens** | **mantenha marcado** (padrão) |
| **Request user authorization (OAuth) during installation** | desmarcado |
| **Setup URL** (em *Post installation*) | `http://localhost:8080/api/v1/integrations/github/setup` |
| **Redirect on update** | desmarcado (o setup também é chamado na atualização, se marcado) |
| **Webhook → Active** | **desmarcado** em desenvolvimento (o GitHub não alcança `localhost`) |
| **Webhook URL** | **deixe em branco** — o GitHub recusa URLs não públicas; configure depois via túnel |
| **Webhook secret** | gere um valor, ex.: `dev-webhook-secret` — você vai usar o mesmo no `.env` |
| **Repository permissions → Contents** | `Read-only` |
| **Repository permissions → Metadata** | `Read-only` (obrigatório) |
| **Subscribe to events** | `Push` |
| **Where can this GitHub App be installed?** | `Only on this account` |

Clique em **Create GitHub App**.

> Só essas duas permissões são necessárias: o backend lê metadados e commits (`/repos/{owner}/{repo}`,
> `/repos/{owner}/{repo}/commits`). Nada de escrita.

## 2. Coletar as credenciais

Na página da App criada:

1. **App ID** — número no topo da página.
2. **Client ID** — visível na seção *About*.
3. **Client secret** — clique em **Generate a new client secret** e copie (só aparece uma vez).
4. **Private key** — role até *Private keys* → **Generate a private key**. Baixa um `.pem`
   (ex.: `devrats-local-felipe.2026-10-30.private-key.pem`). Salve em `api-dev-rats/`.
5. **Slug** — está na URL: `https://github.com/settings/apps/<slug>`. Use em `GITHUB_APP_SLUG`.
6. **Webhook secret** — o valor que você definiu no passo 1.

## 3. Preencher o `.env`

Arquivo: `api-dev-rats/.env`.

```properties
GITHUB_APP_ID=123456
GITHUB_CLIENT_ID=Iv1.abc123def456
GITHUB_CLIENT_SECRET=...
GITHUB_APP_SLUG=devrats-local-felipe
GITHUB_WEBHOOK_SECRET=dev-webhook-secret
GITHUB_PRIVATE_KEY=-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END RSA PRIVATE KEY-----\n
```

> A App já provisionada para este projeto mantém o slug **`devstreak-local-felipe`** (nome externo, criado antes do
> rebrand). Renomeá-la exige criar outra App e trocar todas as credenciais no `.env` da VPS — por isso o slug atual
> não acompanha a mudança de marca. Para um ambiente novo, use `devrats-local-<seu-usuario>`.

### Formato da chave privada

O `.env` é lido como *properties*: valor em **uma única linha**. Use `\n` literal (o backend converte em
quebra de linha real):

```powershell
$pem = Get-Content .\devrats-local-felipe.private-key.pem -Raw
$oneLine = $pem.Trim() -replace "`r", "" -replace "`n", '\n'
$env = Get-Content .\.env -Raw
$env -replace '(?m)^GITHUB_PRIVATE_KEY=.*$', "GITHUB_PRIVATE_KEY=$oneLine" | Set-Content .\.env -NoNewline
```

O backend também aceita **base64 do PEM** ou **base64 do DER PKCS#8**, útil em secret managers:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\devrats-local-felipe.private-key.pem"))
```

## 4. Reiniciar a API

```powershell
.\run-local.ps1
```

O `.env` é carregado por `spring.config.import=optional:file:.env[.properties]`, então basta reiniciar.

## 5. Validar

1. `GET http://localhost:8080/actuator/health` → `UP`.
2. Na webapp (http://localhost:5174) entre e abra **Configurações → Integração GitHub**.
   O aviso "GitHub App não configurada" deve desaparecer e **Conectar GitHub** fica habilitado.
3. Clique em **Conectar GitHub** → autorize a App no GitHub → você volta para
   `http://localhost:5174/?github=connected` e o toast "GitHub conectado" aparece.
4. Clique em **Gerenciar instalação** → escolha os repositórios → o GitHub redireciona para a *Setup URL*,
   o backend sincroniza as instalações e a lista de repositórios aparece na tela.

## 6. Webhooks (opcional em local)

Com o webhook inativo, os commits são reconciliados pelo job periódico (`app.reconciliation.interval`,
10 min). Para entrega em tempo real no ambiente local, exponha a API com um túnel e ajuste a Webhook URL:

```powershell
cloudflared tunnel --url http://localhost:8080
```

Depois atualize em **Webhook URL** para `https://<subdominio>.trycloudflare.com/api/v1/webhooks/github`,
marque **Active** e mantenha o mesmo secret do `.env`.

## Problemas comuns

| Sintoma | Causa |
| --- | --- |
| `appConfigured: false` mesmo com `.env` preenchido | API não reiniciada, ou `.env` fora do diretório de execução |
| `Invalid GitHub App configuration` / `Hook url is not supported because it isn't reachable over the public Internet` | URL de webhook `localhost`/privada. Crie a App **sem webhook** e adicione depois via túnel |
| `403 Estado OAuth inválido ou expirado` | state expirou (cache Caffeine) ou a App usa outra callback URL |
| `400 redirect_uri_mismatch` no GitHub | **Callback URL** da App diferente de `GITHUB_CALLBACK_URL` |
| Toast de conectado, mas lista vazia | falta instalar a App (*Gerenciar instalação*) ou nenhum repositório selecionado |
| Instalação não abre | `GITHUB_APP_SLUG` diferente do slug real da App |
| Webhook retorna `401` | `GITHUB_WEBHOOK_SECRET` diferente do secret configurado no GitHub |
