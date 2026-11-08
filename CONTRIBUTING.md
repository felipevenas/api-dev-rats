# Contributing Guide 🤝

Thank you for your interest in contributing to the **DevRats API**! This document explains the development process so the backend stays consistent, secure, and sustainable.

> **Golden rule:** DevRats verifies eligible activity, not learning. No contribution may expose source code, private commit messages, tokens, or data from third-party repositories. The API is the source of truth for authorization, progress, and challenge results.

---

## 🛠️ Development Environment

### Prerequisites

- **JDK 25** (Temurin recommended).
- **Docker Desktop** running (local PostgreSQL and Testcontainers).
- **Git** and the **Maven Wrapper** (already committed to the repository).

### Installation

```powershell
git clone https://github.com/felipevenas/api-dev-rats.git
cd api-dev-rats
Copy-Item .env.example .env
docker compose up -d postgres
.\run-local.ps1
```

The API starts at **http://localhost:8080**.

- Health: `GET /actuator/health`
- OpenAPI: `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui.html`

> The application will not boot without `SPRING_PROFILES_ACTIVE=local`. This prevents development secrets from being used in production by accident. `run-local.ps1` sets the profile and `JAVA_HOME` automatically.

Complete setup, configuration, and endpoint documentation are in [`README.md`](README.md).

---

## 🌿 Branches and Commits

We use **Trunk-Based Development** with short-lived branches created from `main` and merged through pull requests.

### Branch names

- `feat/feature-name`
- `bug/bug-name`
- `chore/task-name`
- `docs/document-name`
- `style/visual-adjustment`
- `refactor/refactoring-name`
- `test/test-name`

Examples: `feat/timezone-ranking`, `bug/duplicate-reconciliation`, `chore/avatar-migration`.

### Commits

Commit messages are written in **English** with conventional prefixes:

| Prefix | Use |
| --- | --- |
| `feat:` | New user-visible functionality |
| `fix:` | Behavior or API contract fix |
| `refactor:` | Refactoring with no functional change |
| `docs:` | Documentation, README, or relevant comments |
| `test:` | Unit, integration, or architecture tests |
| `style:` | Formatting or purely visual adjustment |
| `chore:` | Build, dependencies, CI/CD, migrations, or configuration |

Example: `feat: add tie-break by last eligible commit time`.

---

## 🏛️ Architectural Guidelines

The layered organization and naming rules are **mandatory** and are detailed in [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md).

```
controllers -> services -> repositories -> entities
controllers -> dto
services    -> services/github, services/activity, utils
security    -> services (token), repositories (read user)
```

Rules that must not be broken:

1. **Controllers do not access repositories or entities.** Every business rule goes through `services`.
2. **Services do not know HTTP.** No `ResponseEntity`, `HttpStatus`, or `ProblemDetail` outside `controllers`.
3. **Entities do not know DTOs, controllers, or services.** Mapping belongs in `dto` or `utils`.
4. **Transactions live only in `services/impl`.** Writes use `@Transactional`; reads use `@Transactional(readOnly = true)`.
5. **Idempotency is guaranteed by constraints**, not by `if (exists)`. Webhooks, commits, and valid days have unique constraints.
6. **Never hold a database lock during an outbound HTTP call to GitHub.** Recompute absolute values with `FOR UPDATE` when concurrency is involved.
7. **`Clock` and `ZoneId` are injected as parameters.** Never hide `LocalDate.now()` inside a domain rule.
8. **Contracts are versioned under `/api/v1`.** Errors follow RFC 9457 (`application/problem+json`) with `code`, `detail`, and `traceId`.

---

## 🧪 Quality Gates

Run this before opening a pull request:

```powershell
.\mvnw.cmd verify
```

`verify` compiles the project, runs unit tests, integration tests with Testcontainers, and architecture tests with ArchUnit, then generates the coverage report at `target/site/jacoco/index.html`.

Minimum checklist:

- [ ] New tests cover the added or fixed behavior.
- [ ] Tests follow the `shouldDoX_whenCondition` naming pattern.
- [ ] A database constraint accompanies every idempotency rule.
- [ ] No new compiler warnings or ArchUnit violations.
- [ ] Coverage does not regress significantly.

### Migrations (Flyway)

Migrations live in `src/main/resources/db/migration` and follow `V{n}__snake_case_description.sql`.

1. Create a new versioned migration; **never edit an already-applied migration**.
2. Apply it locally by starting the application with `.\run-local.ps1` or running `.\mvnw.cmd flyway:migrate`.
3. Include the migration in the same commit as the entity or business-rule change.
4. For destructive changes, plan compatibility with the previous API version.

---

## 🔐 Security and Privacy

- Never commit `.env`, keys, tokens, or credentials. Use only `.env.example` with placeholder values.
- GitHub tokens are stored encrypted with AES-256-GCM; never log them or return them in responses.
- Do not log authentication payloads, cookies, webhook headers, or personal data.
- Validate the HMAC signature (`X-Hub-Signature-256`) of every webhook before processing the event.
- Permissions and authorization are validated **in the API**, not only in the interface.
- New dependencies require justification, a compatible license, and a vulnerability review.
- Vulnerabilities must follow [`SECURITY.md`](SECURITY.md), never a public issue.

---

## 📬 Submitting a Pull Request

1. Create a short-lived branch from `main`.
2. Keep `.\mvnw.cmd verify` green.
3. Update [`CHANGELOG.md`](CHANGELOG.md) when the change is relevant to users.
4. Fill in the PR template with evidence and test steps.
5. For API contract changes, update the README, OpenAPI, and integration tests.
6. Wait for CI and review before merging (squash).

Changes to authentication, GitHub integration, webhooks, permissions, invites, or privacy require explicit security attention in the PR.

---

## ✅ Definition of Done

- Behavior is described, implemented, and tested.
- API contract and documentation are updated when applicable.
- Error states, idempotency, and concurrency are handled.
- Migrations are versioned and compatible.
- No secrets or sensitive data are committed.
- CI is green and the PR is approved.
