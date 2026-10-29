<div align="center">

# DevStreak API ⚙️

**API Java/Spring Boot do DevStreak — desafios privados de constância para desenvolvedores, com verificação automática de commits no GitHub, grupos, ranking, sequências e definição atômica de vencedor.**

[![CI](https://github.com/felipevenas/api-devstreak/actions/workflows/ci.yml/badge.svg)](https://github.com/felipevenas/api-devstreak/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-migrations-CC0200?style=for-the-badge&logo=flyway&logoColor=white)](https://flywaydb.org/)
[![Maven](https://img.shields.io/badge/Maven-Wrapper-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![JUnit 5](https://img.shields.io/badge/JUnit-5-25A162?style=for-the-badge&logo=junit5&logoColor=white)](https://junit.org/)
[![License](https://img.shields.io/badge/License-MIT-purple?style=for-the-badge)](LICENSE)

<br />

<p align="center">
  <a href="#stack"><b>Stack</b></a> •
  <a href="#arquitetura"><b>Arquitetura</b></a> •
  <a href="#como-rodar"><b>Executar Local</b></a> •
  <a href="#configuração"><b>Configuração</b></a> •
  <a href="#api"><b>API</b></a> •
  <a href="#testes"><b>Testes</b></a> •
  <a href="#-governança-e-comunidade"><b>Governança</b></a>
</p>

</div>

---

API Java/Spring Boot do **DevStreak**: desafios de constância para desenvolvedores, com verificação automática de commits no GitHub, grupos privados, ranking, sequências e definição atômica de vencedor.

Este repositório contém **somente o backend**. O frontend React está em [`../webapp-dev-rats`](../webapp-dev-rats).

## Stack

- Java 25 LTS
- Spring Boot 4.1.2 (Web MVC, Security, Data JPA, Validation, Actuator)
- PostgreSQL 17 + Flyway
- Maven Wrapper
- Spring Security JWT (HS256) + refresh token rotativo em cookie HttpOnly
- Caffeine, Bucket4j, springdoc OpenAPI
- JUnit 5, AssertJ, Mockito, Testcontainers, WireMock, ArchUnit e JaCoCo

## Arquitetura

O projeto segue a organização em camadas do projeto de referência `money-flow`:

```text
src/main/java/com/devstreak/
├── config/           configuração de segurança, propriedades, cache, OpenAPI
├── controllers/      endpoints HTTP e tratamento global de erros
├── dto/              requests, responses e paginação
├── entities/         entidades JPA
├── enums/            estados e tipos do domínio
├── exceptions/       exceções e códigos de erro
├── repositories/     Spring Data JPA
├── security/         filtros de trace e rate limit, escrita de erros de segurança
├── services/         contratos de caso de uso
├── services/impl/    implementações transacionais
├── services/github/  cliente e autenticação da GitHub App
├── services/activity/ elegibilidade, processamento de webhook e reconciliação
└── utils/            helpers puros e utilitários de segurança
```

As convenções de nomes, comentários e helpers estão em [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md).

### Principais decisões

- **`groups` e `challenges` são entidades separadas.** O grupo guarda identidade e participantes; o desafio guarda modalidade, meta, fuso, estado e resultado. Isso preserva o histórico quando um novo desafio é criado.
- **Um desafio aberto por grupo** é garantido por índice único parcial (`DRAFT`/`ACTIVE`).
- **Um dia válido por participante/desafio/data** é garantido por constraint única. Vários commits no mesmo dia incrementam apenas o contador interno, nunca os dias válidos.
- **Vitória atômica:** a linha do desafio é travada com `FOR UPDATE`, o vencedor é escolhido entre todos os participantes que atingiram a meta e `challenge_results.challenge_id` é único.
- **Idempotência em três camadas:** `github_events.delivery_id`, `eligible_commits (repository, sha)` e `member_daily_activity (challenge, participant, date)`.
- **Fuso do desafio é fixo.** `committed_at` é armazenado em UTC e convertido para o fuso do desafio ao calcular o dia.
- **Elegibilidade rigorosa:** autoria pelo ID numérico do GitHub, sem bots, commits vazios ou merge commits; forks não contam por padrão; no modo `OWN_REPOSITORIES` o repositório precisa pertencer à conta pessoal do participante.
- **Segredos e tokens:** tokens do GitHub são armazenados cifrados com AES-256-GCM e nunca retornados ao cliente.

## Como rodar

### Pré-requisitos

- JDK 25 (Temurin recomendado)
- Docker Desktop
- Git

Instalação do JDK no Windows:

```powershell
winget install --id EclipseAdoptium.Temurin.25.JDK --exact
```

### Banco local

```powershell
docker compose up -d postgres
```

O PostgreSQL fica exposto em `localhost:55432` (porta escolhida para evitar conflito com instâncias locais).

### Aplicação

Copie `.env.example` para `.env` (o Spring lê esse arquivo) e rode:

```powershell
.\run-local.ps1
```

Ou, manualmente:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot'
$env:SPRING_PROFILES_ACTIVE='local'
.\mvnw.cmd spring-boot:run
```

Sem `SPRING_PROFILES_ACTIVE` a aplicação não sobe: isso evita que segredos de desenvolvimento sejam usados por acidente em produção.

A API sobe em `http://localhost:8080`.

- Health: `GET /actuator/health`
- OpenAPI: `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui.html`

### Build e testes

```powershell
.\mvnw.cmd verify
```

Os testes de integração usam Testcontainers; o Docker precisa estar rodando. O relatório de cobertura fica em `target/site/jacoco/index.html`.

### Docker

```powershell
docker compose --profile full up --build
```

## Configuração

Copie `.env.example` para `.env` e preencha os segredos. As principais variáveis:

| Variável | Uso |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASS` | Conexão PostgreSQL |
| `JWT_SECRET` | Chave HMAC de 32+ bytes em base64 |
| `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL` | Duração do access token e do refresh token |
| `TOKEN_ENCRYPTION_KEY` | Chave AES-256-GCM de 32 bytes em base64 |
| `GITHUB_APP_ID` | ID numérico da GitHub App |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | OAuth da GitHub App |
| `GITHUB_PRIVATE_KEY` | PEM PKCS#1/PKCS#8 da GitHub App ou PKCS#8 em base64 |
| `GITHUB_WEBHOOK_SECRET` | Segredo do webhook |
| `GITHUB_APP_SLUG` | Slug usado na URL de instalação |
| `APP_CORS_ALLOWED_ORIGINS` | Origens permitidas, separadas por vírgula |
| `APP_FRONTEND_URL` | URL usada nos redirects OAuth |

Segredos nunca são versionados. O profile `local` precisa ser ativado explicitamente e `.env.example` contém apenas valores de exemplo.

## GitHub App

1. Crie uma GitHub App em `Settings > Developer settings > GitHub Apps`.
2. Permissões de repositório: **Metadata: read** e **Contents: read**.
3. Eventos: `push`, `installation`, `installation_repositories`, `repository`, `installation_suspend`, `installation_unsuspend`.
4. Webhook URL: `/api/v1/webhooks/github`.
5. Callback URL: `/api/v1/integrations/github/callback`.
6. Setup URL: `/api/v1/integrations/github/setup`.
7. Gere a chave privada e preencha as variáveis acima.

Para desenvolvimento local, exponha a porta 8080 com `smee.io` ou `ngrok` e use a URL pública no webhook da GitHub App.

## API

Todas as rotas são versionadas em `/api/v1`.

### Autenticação e perfil

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `POST /auth/password-reset/request`
- `POST /auth/password-reset/confirm`
- `GET /me`
- `PATCH /me`
- `GET /me/export`
- `DELETE /me`

### GitHub

- `GET /integrations/github`
- `GET /integrations/github/authorize-url`
- `GET /integrations/github/install-url`
- `GET /integrations/github/callback`
- `GET /integrations/github/setup`
- `POST /integrations/github/sync`
- `DELETE /integrations/github`
- `GET /github/repositories`
- `POST /webhooks/github`

### Grupos, convites e desafios

- `POST /groups`
- `GET /groups`
- `GET /groups/{groupId}`
- `PATCH /groups/{groupId}`
- `GET /groups/{groupId}/members`
- `DELETE /groups/{groupId}/members/{userId}`
- `POST /groups/{groupId}/leave`
- `POST /groups/{groupId}/invites`
- `GET /groups/{groupId}/invites`
- `DELETE /groups/{groupId}/invites/{inviteId}`
- `GET /invites/{token}`
- `POST /invites/{token}/accept`
- `POST /groups/{groupId}/challenges`
- `GET /groups/{groupId}/challenges`
- `GET /challenges/{challengeId}`
- `PATCH /challenges/{challengeId}`
- `POST /challenges/{challengeId}/repositories`
- `DELETE /challenges/{challengeId}/repositories/{challengeRepositoryId}`
- `POST /challenges/{challengeId}/start`
- `POST /challenges/{challengeId}/cancel`

### Progresso e leitura

- `GET /challenges/{challengeId}/progress`
- `GET /challenges/{challengeId}/leaderboard`
- `GET /challenges/{challengeId}/activity`
- `GET /challenges/{challengeId}/result`
- `GET /challenges/{challengeId}/feed`
- `GET /dashboard`
- `GET /notifications`
- `PATCH /notifications/{id}/read`
- `POST /notifications/read-all`

Erros seguem RFC 9457 (`application/problem+json`) com `code`, `detail` e `traceId`.

## Regras de atividade

- Um commit elegível gera no máximo um dia válido por data no fuso do desafio.
- Commits anteriores ao início, depois do encerramento, anteriores à entrada do participante ou com mais de 48h de idade são rejeitados (limite configurável em `APP_CHALLENGES_MAX_COMMIT_AGE`).
- A admissão pode continuar aberta após o início, conforme `admissionEndsAt` definido pelo criador.
- Autoria é verificada pelo ID numérico do GitHub, não por nome ou e-mail do commit.
- Bots, commits vazios e merge commits não contam.
- No modo `SELECTED_REPOSITORIES`, o repositório precisa estar vinculado ao desafio (máximo 3).
- No modo `OWN_REPOSITORIES`, o repositório precisa pertencer à conta pessoal do participante.
- Webhooks repetidos são reconhecidos por `X-GitHub-Delivery`.
- Eventos perdidos são recuperados por uma reconciliação periódica idempotente.

## Testes

- Unitários: elegibilidade, streaks, fuso, assinatura HMAC e política de senha.
- Integração: autenticação, webhook idempotente, fluxo de grupo/convite/início, limite de 3 repositórios e processamento de commits com Testcontainers.
- Concorrência: dois participantes atingindo a meta simultaneamente produzem exatamente um vencedor.
- Arquitetura: fronteiras de camada e convenções validadas com ArchUnit.

O CI executa `./mvnw -B verify` em JDK 25 e publica o relatório do JaCoCo como artefato.

## Limitações conhecidas do MVP

- O envio de e-mail de recuperação de senha não está integrado. Em `local`, o token é devolvido na resposta (`app.auth.expose-reset-token=true`); em produção, conecte um provedor de e-mail antes de habilitar o fluxo.
- A validação real da GitHub App depende das credenciais e de uma URL pública de webhook (`smee.io`/`ngrok` em desenvolvimento). Sem elas, o status é `appConfigured=false`.
- A reconciliação e os jobs agendados assumem uma única instância da API. Para múltiplas réplicas, adicione um lock distribuído (ShedLock/Redis) antes de escalar horizontalmente.
- Correções retroativas de resultado exigem procedimento administrativo auditado; não há endpoint público para alterar um vencedor já finalizado.

---

## 🤝 Governança e Comunidade

Este repositório segue uma governança formal, espelhando a dos demais projetos do autor:

| Documento | Finalidade |
|---|---|
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Fluxo de branches/commits, diretrizes arquiteturais, migrations e gates de qualidade. |
| [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) | Código de conduta da comunidade. |
| [`SECURITY.md`](SECURITY.md) | Política de divulgação responsável e escopo de segurança do backend. |
| [`CHANGELOG.md`](CHANGELOG.md) | Histórico de versões ([Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) + SemVer). |
| [`LICENSE`](LICENSE) | Licença MIT. |
| [`.github/`](.github) | Templates de Issue/PR e workflows de **CI** (Maven verify) e **labels**. |
| [`.editorconfig`](.editorconfig) · [`.gitattributes`](.gitattributes) | Padronização de estilo, finais de linha LF e normalização de binários. |

---

## Documentação relacionada

- [`docs/PRD-MVD.md`](docs/PRD-MVD.md) — PRD do produto.
- [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md) — convenções de código, camadas e testes.
- [`../webapp-dev-rats`](../webapp-dev-rats) — frontend React que consome esta API.

---

## 📄 Licença

Distribuído sob a licença **MIT**. Consulte o arquivo [`LICENSE`](LICENSE) para mais informações.

<div align="center">
Desenvolvido com excelência por <b><a href="https://github.com/felipevenas">Felipe Venas</a></b>
</div>
