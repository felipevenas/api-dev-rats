# Política de Segurança 🔒

A **DevStreak API** lê metadados de commits no GitHub para validar constância em desafios privados. A segurança e a privacidade são pilares do produto: nenhum código-fonte, mensagem privada de commit, token do GitHub ou dado de repositório de terceiros deve ser exposto.

Se você acredita ter encontrado uma vulnerabilidade, agradecemos a divulgação responsável.

---

## Versões Suportadas

Apenas a versão mais recente em `main` recebe atualizações ativas de segurança:

| Versão  | Suportada          |
| ------- | ------------------ |
| 0.1.x   | :white_check_mark: |
| < 0.1.0 | :x:                |

---

## Relatando uma Vulnerabilidade

**Por favor, não relate vulnerabilidades de segurança publicamente em Issues abertas.**

1. Abra um relatório confidencial pelo recurso de **[Private Vulnerability Reporting](https://github.com/felipevenas/api-devstreak/security/advisories/new)**.
2. Se o recurso não estiver disponível, entre em contato diretamente com o mantenedor pelo perfil [@felipevenas](https://github.com/felipevenas), sem abrir uma Issue pública.
3. Descreva os detalhes, passos para reprodução, impacto potencial e qualquer prova de conceito (PoC).
4. Responderemos em até **48 horas úteis** com avaliação e plano de mitigação.

---

## Escopo de Segurança do Backend

São consideradas vulnerabilidades de alta prioridade:

- **Exposição de token ou segredo**: token do GitHub, `JWT_SECRET`, `TOKEN_ENCRYPTION_KEY`, chave privada da GitHub App ou `GITHUB_WEBHOOK_SECRET` em logs, respostas, traces, artefatos de build ou no repositório.
- **Falha de autenticação ou sessão**: JWT forjado, validação incorreta de `iss`/`aud`/expiração, refresh token reutilizável, ausência de rotação ou cookie de refresh sem `HttpOnly`/`SameSite`.
- **Falha de autorização**: acesso a grupos, convites, desafios, progresso ou dados de outro participante (IDOR), inclusive por troca de IDs ou de token de convite.
- **Bypass de webhook**: aceitar payload sem validar `X-Hub-Signature-256` ou processar entrega repetida de forma não idempotente.
- **Manipulação de progresso ou resultado**: commit inelegível contabilizado, dia válido duplicado, vencedor alterado fora do fluxo atômico ou ranking adulterado.
- **Injeção**: SQL/JPQL, template, header ou log injection a partir de parâmetros de entrada.
- **SSRF ou acesso indevido**: uso de URL fornecida pelo usuário para chamadas externas à API do GitHub.
- **Vazamento de dados**: exposição de código-fonte, mensagens de commit privadas, e-mails, metadados de repositórios privados ou dados pessoais.
- **Configuração insegura**: CORS permissivo, profile `local` ativo em produção, Actuator exposto sem autenticação, Swagger habilitado em produção ou rate limit desativado.
- **Dependências vulneráveis** com impacto no build ou no runtime.

---

## Processo de Resolução

1. Confirmamos o relatório e classificamos o impacto.
2. Preparamos a correção em ambiente controlado, com testes de regressão.
3. Publicamos a correção em `main` e registramos no [`CHANGELOG.md`](CHANGELOG.md).
4. Damos crédito ao pesquisador, se desejado, após a correção.

Obrigado por ajudar a manter o DevStreak seguro.
