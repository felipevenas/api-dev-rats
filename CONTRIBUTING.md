# Guia de Contribuição 🤝

Obrigado pelo interesse em contribuir com a **DevRats API**! Este documento orienta o processo de desenvolvimento para manter o backend consistente, seguro e sustentável.

> **Regra de ouro:** o DevRats comprova atividade elegível, não aprendizado. Nenhuma contribuição pode expor código-fonte, mensagens privadas de commit, tokens ou dados de repositórios de terceiros. A API é a fonte de verdade para autorização, progresso e resultado dos desafios.

---

## 🛠️ Ambiente de Desenvolvimento

### Pré-requisitos

- **JDK 25** (Temurin recomendado).
- **Docker Desktop** em execução (PostgreSQL local e Testcontainers).
- **Git** e **Maven Wrapper** (já versionado no repositório).

### Instalação

```powershell
git clone https://github.com/felipevenas/api-dev-rats.git
cd api-dev-rats
Copy-Item .env.example .env
docker compose up -d postgres
.\run-local.ps1
```

A API sobe em **http://localhost:8080**.

- Health: `GET /actuator/health`
- OpenAPI: `GET /v3/api-docs`
- Swagger UI: `GET /swagger-ui.html`

> Sem `SPRING_PROFILES_ACTIVE=local` a aplicação não sobe. Isso evita que segredos de desenvolvimento sejam usados por acidente em produção. O script `run-local.ps1` define o profile e o `JAVA_HOME` automaticamente.

Detalhes completos de setup, configuração e endpoints estão no [`README.md`](README.md).

---

## 🌿 Branches e Commits

Adotamos **Trunk-Based Development** com branches curtas a partir de `main`, mescladas por Pull Request.

### Nomes de branches

- `feat/nome-da-feature`
- `bug/nome-do-bug`
- `chore/nome-da-tarefa`
- `docs/nome-do-documento`
- `style/nome-do-ajuste-visual`
- `refactor/nome-da-refatoracao`
- `test/nome-do-teste`

Exemplos: `feat/ranking-por-fuso`, `bug/reconciliacao-duplicada`, `chore/migration-avatar`.

### Commits

Mensagens em português com prefixos convencionais:

| Prefixo     | Uso                                                        |
| ----------- | ---------------------------------------------------------- |
| `feat:`     | Nova funcionalidade visível ao usuário                     |
| `fix:`      | Correção de comportamento ou de contrato da API            |
| `refactor:` | Refatoração sem alteração funcional                        |
| `docs:`     | Documentação, README ou comentários relevantes             |
| `test:`     | Testes unitários, de integração ou de arquitetura          |
| `style:`    | Ajuste puramente visual ou de formatação                   |
| `chore:`    | Build, dependências, CI/CD, migrations ou configuração     |

Exemplo: `feat: adiciona desempate por horário do último commit elegível`.

---

## 🏛️ Diretrizes Arquiteturais

A organização em camadas e as regras de nomenclatura são **obrigatórias** e estão detalhadas em [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md).

```
controllers -> services -> repositories -> entities
controllers -> dto
services    -> services/github, services/activity, utils
security    -> services (token), repositories (leitura de usuário)
```

Regras que não podem ser quebradas:

1. **Controller não acessa repository nem entidade.** Toda regra de negócio passa por `services`.
2. **Service não conhece HTTP.** Nada de `ResponseEntity`, `HttpStatus` ou `ProblemDetail` fora de `controllers`.
3. **Entidade não conhece DTO, controller ou service.** Mapeamentos ficam em `dto` ou `utils`.
4. **Transações apenas em `services/impl`.** Escritas com `@Transactional`; leituras com `@Transactional(readOnly = true)`.
5. **Idempotência é garantida por constraints**, não por `if (exists)`. Webhooks, commits e dias válidos têm constraints únicas.
6. **Nunca segurar lock de banco durante chamada HTTP ao GitHub.** Recalcule valores absolutos com `FOR UPDATE` quando houver concorrência.
7. **`Clock` e `ZoneId` entram por parâmetro.** Nunca use `LocalDate.now()` escondido em regra de domínio.
8. **Contratos são versionados em `/api/v1`.** Erros seguem RFC 9457 (`application/problem+json`) com `code`, `detail` e `traceId`.

---

## 🧪 Gates de Qualidade

Execute antes de abrir um Pull Request:

```powershell
.\mvnw.cmd verify
```

O `verify` executa compilação, testes unitários, testes de integração com Testcontainers, testes de arquitetura (ArchUnit) e gera o relatório de cobertura em `target/site/jacoco/index.html`.

Checklist mínimo:

- [ ] Testes novos cobrem o comportamento adicionado ou corrigido.
- [ ] Testes seguem o padrão `shouldDoX_whenCondition`.
- [ ] Constraint de banco acompanha a regra de idempotência.
- [ ] Nenhum novo aviso de compilação ou violação de ArchUnit.
- [ ] Cobertura não regride de forma significativa.

### Migrations (Flyway)

Migrations ficam em `src/main/resources/db/migration` e seguem o padrão `V{n}__descricao_em_snake_case.sql`.

1. Crie uma nova migration versionada; **nunca edite uma migration já aplicada**.
2. Aplique localmente subindo a aplicação com `.\run-local.ps1` ou executando `.\mvnw.cmd flyway:migrate`.
3. Inclua a migration no mesmo commit da alteração de entidade ou regra de negócio.
4. Para mudanças destrutivas, planeje a compatibilidade com a versão anterior da API.

---

## 🔐 Segurança e Privacidade

- Nunca versione `.env`, chaves, tokens ou credenciais. Use apenas `.env.example` com valores de exemplo.
- Tokens do GitHub são armazenados cifrados com AES-256-GCM; nunca os registre em logs, traces ou respostas.
- Não registre payloads de autenticação, cookies, headers de webhook ou dados pessoais em logs.
- Valide a assinatura HMAC (`X-Hub-Signature-256`) de todo webhook antes de processar o evento.
- Permissões e autorização são validadas **na API**, não apenas na interface.
- Dependências novas exigem justificativa, licença compatível e revisão de vulnerabilidades.
- Vulnerabilidades devem seguir [`SECURITY.md`](SECURITY.md), nunca uma issue pública.

---

## 📬 Submetendo um Pull Request

1. Crie uma branch curta a partir de `main`.
2. Mantenha `.\mvnw.cmd verify` verde.
3. Atualize o [`CHANGELOG.md`](CHANGELOG.md) quando a mudança for relevante ao usuário.
4. Preencha o template de PR com evidências e passos de teste.
5. Para mudanças de contrato da API, atualize o README, o OpenAPI e os testes de integração.
6. Aguarde a CI e a revisão antes do merge (squash).

Alterações em autenticação, integração GitHub, webhooks, permissões, convites ou privacidade exigem atenção explícita à segurança no PR.

---

## ✅ Definição de Pronto

- Comportamento descrito, implementado e testado.
- Contrato da API e documentação atualizados quando aplicável.
- Estados de erro, idempotência e concorrência tratados.
- Migrations versionadas e compatíveis.
- Nenhum segredo ou dado sensível versionado.
- CI verde e PR aprovado.
