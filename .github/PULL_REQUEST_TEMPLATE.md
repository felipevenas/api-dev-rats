# Descrição do Pull Request

Por favor, inclua um resumo das alterações feitas e quais problemas ou funcionalidades foram abordados.

---

## Tipo de Alteração

- [ ] 🚀 Nova funcionalidade (`feature`)
- [ ] 🐞 Correção de bug (`bug fix`)
- [ ] 🔧 Refatoração de código sem alteração funcional (`refactor`)
- [ ] 📝 Documentação (`docs`)
- [ ] 🎨 Estilo / Formatação (`style`)
- [ ] ⚙️ Infraestrutura, CI/CD, dependências ou migrations (`chore`, `ci/cd`)

## Como foi testado?

Descreva as validações executadas para verificar suas alterações:

- [ ] **Build e testes:** Executei `.\mvnw.cmd verify` e todos os testes passaram.
- [ ] **Testcontainers:** Os testes de integração rodaram com Docker em execução.
- [ ] **ArchUnit:** Nenhuma violação de camada ou de convenção foi introduzida.
- [ ] **Migration Flyway:** Criei e apliquei a migration necessária (se aplicável).
- [ ] **Swagger / Contrato:** Validei os endpoints alterados em `http://localhost:8080/swagger-ui.html`.
- [ ] Outros testes executados:

## Evidência Visual / Logs

*Se aplicável, cole logs relevantes, capturas do Swagger, do ranking ou do fluxo validado.*

## Checklist de Qualidade e Segurança

- [ ] Meu código segue [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md) e as camadas do projeto.
- [ ] Realizei uma auto-revisão detalhada do meu próprio código.
- [ ] Adicionei ou atualizei testes para cobrir o comportamento alterado.
- [ ] Atualizei o [`CHANGELOG.md`](CHANGELOG.md) quando a mudança é relevante ao usuário.
- [ ] Não versionei segredos, tokens, `.env` ou dados de repositórios privados.
- [ ] Validei autorização, idempotência e concorrência quando aplicável.
- [ ] Atualizei a documentação e o contrato da API quando necessário.
