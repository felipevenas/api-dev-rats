# Convenções de código — DevRats API

Este documento é obrigatório para qualquer pessoa ou agente que altere o backend.

## 1. Nomes de métodos

Um verbo tem um único significado em todos os domínios.

| Verbo | Contrato |
|---|---|
| `getX` | Retorna o recurso e lança `XNotFoundException` quando ausente. |
| `findX` | Retorna `Optional<X>` quando a ausência é esperada. |
| `listX` | Retorna coleção ou página. |
| `createX`, `updateX`, `deleteX` | Comandos CRUD. |
| `startX`, `cancelX`, `acceptX`, `revokeX`, `joinX`, `leaveX` | Transições de estado. |
| `existsX`, `isX`, `hasX`, `canX` | Predicados booleanos. |
| `validateX` | Valida e lança exceção; não retorna boolean. |
| `calculateX`, `resolveX`, `parseX` | Cálculo ou transformação pura. |
| `toX`, `fromX` | Mapeamento DTO/entidade. |
| `processX`, `handleX` | Processamento de evento. |
| `reconcileX`, `expireX` | Jobs agendados. |
| `issueX`, `rotateX` | Tokens e credenciais. |

Proibido usar `fetch`, `retrieve`, `load` ou `save` como sinônimos de `get`, `create` ou `update`.
Proibidas abreviações (`usr`, `chlg`) e nomes genéricos (`handleData`, `doProcess`, `manage`).

Repositórios seguem o Spring Data: `findBy...`, `findAllBy...`, `existsBy...`, `countBy...`, `deleteBy...`.
DTOs usam `XRequest`, `XResponse`, `XSummary` e `XFilter`.
Testes usam `shouldDoX_whenCondition`.

## 2. Comentários

- Javadoc apenas em contratos públicos de serviço, políticas de negócio e algoritmos não óbvios.
- Comentário inline explica **por que**, invariantes, concorrência, idempotência ou particularidades externas.
- Uma ideia por comentário, no máximo duas linhas.
- Não comentar o que o código já diz.
- Não deixar código comentado.
- `TODO` exige justificativa e referência: `// TODO(#42): ...`.
- Comentários em português; identificadores, endpoints e OpenAPI em inglês.

## 3. Helpers

- Helpers ficam em `utils/` e têm responsabilidade explícita no nome (`ActivityDateHelper`, `StreakHelper`, `SignatureHelper`, `PageHelper`).
- Helper é folha: não acessa repositório nem outro service.
- Função pura e estática quando não há dependência; `@Component` com injeção por construtor quando precisa de `Clock` ou configuração.
- `Clock` e `ZoneId` entram por parâmetro nas funções puras. Nunca usar `LocalDate.now()` escondido.
- Extrair para helper quando o trecho for reutilizado, passar de ~10 linhas, contiver regra de negócio ou for difícil de testar.
- Método privado só para cola trivial, usada uma vez e sem regra de negócio.
- Regras de domínio recebem nome próprio (`CommitEligibilityPolicy`, `TieBreakPolicy`, `StreakCalculator`), não `Helper` genérico.
- Proibido criar `Utils`, `CommonHelper` ou `Misc`.

## 4. Camadas

```
controllers -> services -> repositories -> entities
controllers -> dto
services    -> services/github, services/activity, utils
security    -> services (token), repositories (leitura de usuário)
```

- Controller não acessa repository nem entidade.
- Service não conhece `ResponseEntity`, `HttpStatus` ou `ProblemDetail`.
- Entidade não conhece DTO, controller ou service.
- `services/github` e `services/activity` são folhas de integração/domínio; não importam controllers.

## 5. Transações e concorrência

- `@Transactional` do Spring apenas em métodos de escrita de `services/impl`.
- Leituras usam `@Transactional(readOnly = true)`.
- Nunca segurar lock de banco durante chamada HTTP ao GitHub.
- Progresso e vitória usam `FOR UPDATE` e valores absolutos recalculados; não usar `valid_days = valid_days + 1`.
- Idempotência é garantida por constraints, não por `if (exists)`.
