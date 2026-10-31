# Descrição do Pull Request

Inclua um resumo das alterações, o problema ou funcionalidade abordada e o impacto esperado no contrato da API, na segurança e nos dados.

---

## Tipo de Alteração

- [ ] 🚀 Nova funcionalidade (`feature`)
- [ ] 🐞 Correção de bug (`bug fix`)
- [ ] 🔧 Refatoração sem alteração funcional (`refactor`)
- [ ] 📝 Documentação (`docs`)
- [ ] 🎨 Estilo / formatação (`style`)
- [ ] ⚙️ Infraestrutura, CI/CD, dependências ou migrations (`chore`)
- [ ] 🔒 Segurança ou privacidade (`security`)

## Como foi testado?

Descreva as validações executadas e como reproduzir no ambiente local.

- [ ] **Build e testes:** executei `.\mvnw.cmd verify` e todos os testes passaram.
- [ ] **Testcontainers:** os testes de integração rodaram com Docker em execução.
- [ ] **ArchUnit:** nenhuma violação de camada ou convenção foi introduzida.
- [ ] **Migration Flyway:** criei e apliquei a migration necessária (quando aplicável).
- [ ] **Swagger / Contrato:** validei os endpoints alterados em `http://localhost:8080/swagger-ui.html`.
- [ ] **Autorização:** validei que o recurso exige autenticação e as permissões corretas.
- [ ] **Concorrência / idempotência:** validei os fluxos afetados quando aplicável.
- [ ] Outros testes executados:

## Evidência Visual / Logs

*Se aplicável, cole logs relevantes, capturas do Swagger ou do fluxo validado.*

## Checklist

- [ ] Meu código segue [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md) e as camadas do projeto.
- [ ] Fiz auto-revisão e removi código morto ou experimental.
- [ ] Adicionei ou atualizei testes para cobrir o comportamento alterado.
- [ ] Atualizei o [`CHANGELOG.md`](CHANGELOG.md) quando a mudança for relevante ao usuário.
- [ ] Não versionei segredos, tokens, `.env` ou dados de repositórios privados.
- [ ] Atualizei a documentação e o contrato da API quando necessário.
