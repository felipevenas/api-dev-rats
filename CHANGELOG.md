# Changelog

Todas as mudanças notáveis neste projeto são documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/)
e este projeto adere ao [Semantic Versioning](https://semver.org/lang/pt-BR/).

---

## [Não publicado]

### 🚀 Adicionado

- Fuso horário padrão em Brasília para novos cadastros e desafios.

### 📝 Documentação

- README padronizado com badges, arquitetura, configuração, API, governança e licença.
- Descrição e topics do repositório alinhados ao ecossistema DevRats.

## [0.1.0] - 2026-10-28

### 🚀 Adicionado

- **Autenticação e perfil**: cadastro, login, refresh token rotativo em cookie `HttpOnly`, logout, recuperação de senha, edição e exclusão de conta, além de exportação dos dados do usuário.
- **Integração com GitHub App**: fluxo OAuth, instalação, sincronização de repositórios, callback, setup e webhook com validação de assinatura HMAC.
- **Grupos e convites**: criação, edição, listagem de membros, saída, convites com token e ingresso por convite.
- **Desafios**: rascunho, início, cancelamento, seleção de até três repositórios, modalidades `SELECTED_REPOSITORIES` e `OWN_REPOSITORIES`, admissão configurável e fuso fixo do desafio.
- **Progresso e resultado**: dias válidos, sequências, ranking, feed de atividade, notificações e definição atômica de vencedor com desempate.
- **Elegibilidade de commits**: autoria pelo ID numérico do GitHub, exclusão de bots, commits vazios e merge commits, limite de idade configurável e idempotência por `repository`/`sha`.
- **Reconciliação**: recuperação periódica e idempotente de eventos perdidos do GitHub.
- **Observabilidade**: Actuator, métricas Prometheus, trace ID nas respostas de erro e logs estruturados.
- **Segurança**: JWT HS256, rate limit com Bucket4j, CORS configurável, criptografia AES-256-GCM para tokens do GitHub e erros no padrão RFC 9457.
- **Documentação**: README com setup, configuração, endpoints e limitações; PRD em [`docs/PRD-MVD.md`](docs/PRD-MVD.md) e convenções em [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md).
- **Qualidade**: 36 testes entre unitários, integração com Testcontainers, WireMock, concorrência e arquitetura com ArchUnit; cobertura com JaCoCo.
- **Governança**: licença MIT, guia de contribuição, política de segurança, código de conduta, templates de issue e pull request, labels automatizadas e CI no GitHub Actions.

### 🔐 Segurança

- Segredos de desenvolvimento não são versionados; `.env.example` contém apenas valores de exemplo.
- O profile `local` precisa ser ativado explicitamente, evitando o uso acidental de configurações de desenvolvimento em produção.
- Tokens do GitHub são armazenados cifrados e nunca retornados ao cliente.

### ⚠️ Limitações conhecidas

- Envio de e-mail de recuperação de senha não integrado; em `local` o token é exposto na resposta.
- A validação completa da GitHub App depende de credenciais e de uma URL pública de webhook.
- Jobs agendados e reconciliação assumem uma única instância da API.
- Correções retroativas de resultado exigem procedimento administrativo auditado.
