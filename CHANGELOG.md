# Changelog

Todas as mudanças notáveis neste projeto são documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/)
e este projeto adere ao [Semantic Versioning](https://semver.org/lang/pt-BR/).

---

## [Não publicado]

### 🚀 Adicionado

- Exclusão individual de notificações, escopada ao usuário autenticado.
- Exclusão de grupo pelo criador, com notificação aos participantes ativos e registro de auditoria.
- Fuso horário padrão em Brasília para novos cadastros e desafios.
- Login com e-mail ou nome de usuário, mantendo compatibilidade com o payload antigo.
- Campos de perfil: bio, localização, site, nível de experiência e stack principal.
- Endpoint de commits elegíveis que validaram cada dia do desafio.
- Upload e remoção de foto de perfil com storage S3-compatível e fallback para o GitHub.
- Paginação completa nas listagens de instalações e repositórios da GitHub App.
- **Sistema de amizades**: busca por username, perfil público, solicitações com aceite, recusa e cancelamento, auto-aceite de solicitações cruzadas e remoção de amizade.
- **Presença**: heartbeat autenticado com estados `ONLINE`/`AWAY`/`OFFLINE`, visível apenas para amigos.
- **Notificações sociais**: `FRIEND_REQUEST` e `FRIEND_REQUEST_ACCEPTED` com ações inline na central de alertas.
- **Privacidade**: exclusão de conta remove o grafo social e a presença; exportação LGPD inclui as amizades mínimas e o último heartbeat do titular.
- **Rate limit social**: limites por IP e por usuário na busca e no envio de solicitações, com `Retry-After` em toda resposta 429.
- **Chat em tempo real**: conversas privadas entre amigos e chat automático para grupos ativos, com histórico paginado por cursor, contador de não lidas, cursor de leitura e exclusão lógica de mensagens.
- **WebSocket/STOMP**: endpoint `/ws` com autenticação JWT no frame `CONNECT`, entrega de eventos por `/user/queue/chat`, reconexão do cliente, heartbeats, limite de sessões e fechamento server-side quando o token expira.
- **Mensagens**: texto, imagem, mensagem de voz e documento; `clientMessageId` garante idempotência de retry e o evento é publicado apenas depois do commit.
- **Anexos privados**: bucket S3 separado para chat, upload multipart validado por magic bytes, imagens reencodadas em JPEG sem EXIF, limites por tipo e download por URL assinada de curta duração.
- **Segurança do chat**: autorização derivada de amizade `ACCEPTED`/membro `ACTIVE`, 404 para não participantes, rate limit por usuário, `Cache-Control: no-store` e logs sem conteúdo ou nome de arquivo.
- **LGPD**: exportação inclui apenas mensagens enviadas pelo titular; exclusão de conta encerra sessões WebSocket e anonimiza o autor sem apagar o histórico dos demais.
- **Operação**: métricas de sessões e mensagens, timeouts de upload/S3 e documentação do caminho de escala com Redis/RabbitMQ.
- **Redefinição de senha por e-mail**: envio via Resend com modos `off`/`test`/`live`, link com token no fragmento, validade de 30 minutos, uso único, hash SHA-256, revogação de todas as sessões, cooldown por conta, auditoria e limpeza diária de tokens.
- **Envio assíncrono após commit**: `@TransactionalEventListener` + executor limitado, com idempotência no Resend e métrica `email.send`.

### 🔧 Corrigido

- Callback e setup do GitHub sempre redirecionam para o frontend com `github=connected|installed|error`, sem expor JSON de erro.
- Desconectar o GitHub revoga a autorização na GitHub App, além de limpar os tokens locais.
- Instalações de organização podem ser compartilhadas entre contas DevRats sem reatribuição indevida.
- Webhooks de instalação removida e de repositórios adicionados/removidos mantêm o acesso local atualizado.
- Repositórios revogados deixam de contar em `repositoryCount` e contas desconectadas param de validar commits.
- Sincronização de repositórios marca como revogados apenas os vínculos da instalação afetada.
- Chamadas HTTP ao GitHub ficam fora das transações de banco no fluxo de integração.
- Respostas de `/api/v1/auth/**` passam a usar `Cache-Control: no-store`.
- Token de redefinição passa a expirar exatamente no limite da validade, sem aceitar o instante de expiração.

### 📝 Documentação

- README padronizado com badges, arquitetura, configuração, API, governança e licença.
- Descrição e topics do repositório alinhados ao ecossistema DevRats.
- Setup da GitHub App documenta as URLs de redirecionamento e os códigos de erro do callback.
- Migration `V4` modela o compartilhamento de instalações entre contas.
- `docs/PR-CHAT.md` consolida o corpo do PR, decisões e pontos de melhoria da feature.
- `docs/DEPLOY-VPS.md` atualizado para o domínio `devrats.app`: DNS no Name.com, API em `api.devrats.app`, Caddy com TLS automático, rewrite da Vercel e `VITE_WS_URL` direto para o WebSocket.
- `vercel.json` do frontend aponta o rewrite de `/api` para `https://api.devrats.app`; `index.html` usa canonical/OG em `https://devrats.app`.
- `docs/EMAIL.md` documenta o Resend, os modos de envio, o DNS de `eufelipedesign.cloud`, o teste local e o runbook de produção.
- README e `docs/DEPLOY-VPS.md` incluem as variáveis de e-mail, o remetente verificado e o checklist de validação.

### ⚠️ Limitações conhecidas

- O envio de e-mail é assíncrono e não tem outbox: se o processo cair entre o commit e o envio, aquele e-mail se perde. O usuário pode solicitar outro link; o caminho de evolução com outbox está em `docs/EMAIL.md`.
- O broker STOMP simples vive na memória de uma instância. Múltiplas réplicas exigem RabbitMQ (relay STOMP) ou uma ponte Pub/Sub com Redis.
- Rate limit e presença continuam locais à instância; a migração para Redis está documentada como evolução.
- Chat não tem criptografia ponta a ponta, push notification, edição/busca de mensagens, reações ou moderação de conteúdo.
- A v1 aceita um anexo por mensagem; vírus scanning e transcodificação de áudio ficam fora do escopo.
- HEIC não é aceito porque o JDK não decodifica o formato nativamente.

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
