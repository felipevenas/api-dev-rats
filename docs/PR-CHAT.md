# feat(api): chat privado e de grupo com STOMP/WebSocket e anexos S3

## Resumo

Adiciona o backend de chat do DevRats:

- conversa direta entre amigos (`friendships.status = ACCEPTED`);
- chat automático para grupos ativos (`group_members.status = ACTIVE`);
- texto, imagem, mensagem de voz e documento;
- histórico paginado por cursor, não lidas e cursor de leitura;
- entrega em tempo real por STOMP/WebSocket;
- anexos em bucket S3 privado com URL assinada.

## Motivação

O grafo social e os grupos já existiam, mas não havia canal de comunicação. O chat fecha esse ciclo sem criar um microsserviço nem duplicar autenticação: usa o mesmo JWT, o mesmo PostgreSQL e a mesma abstração de storage.

## Decisões de arquitetura

1. **REST para escrever, WebSocket para entregar.** O envio usa `POST` para reaproveitar validação, idempotência, multipart, rate limit e ProblemDetail. O WebSocket é o canal de fan-out em tempo real.
2. **STOMP sobre WebSocket nativo.** O token vai no header do frame `CONNECT`, porque o browser não permite `Authorization` no upgrade. O handshake HTTP é liberado no `SecurityConfig` e autenticado pelo `StompAuthChannelInterceptor`.
3. **Uma tabela `conversations` para DIRECT e GROUP.** `direct_key` canônico garante a unicidade do par sem depender da ordenação de UUID entre Java e Postgres.
4. **Broker simples na v1.** Uma única instância; o caminho de escala com RabbitMQ/Redis está documentado nas limitações.
5. **Bucket privado separado para chat.** Avatares continuam no bucket público; anexos usam presigned GET de curta duração.
6. **Eventos só depois do commit.** `@TransactionalEventListener(AFTER_COMMIT)` evita entregar mensagem de transação revertida.

## Banco de dados

Migration `V6__chat.sql`:

- `conversations` com índices únicos para par direto e grupo;
- `chat_messages` com índice `(conversation_id, created_at DESC, id DESC)` e unicidade `(conversation_id, sender_id, client_message_id)`;
- `chat_message_attachments` com metadados e object key privada;
- `conversation_reads` com cursor por `(last_read_at, last_read_message_id)`;
- backfill de conversas para grupos existentes.

## API

| Método | Rota | Função |
|---|---|---|
| GET | `/api/v1/chat/conversations` | Lista conversas, última mensagem e não lidas |
| POST | `/api/v1/chat/conversations` | Resolve/cria conversa DIRECT ou GROUP |
| GET | `/api/v1/chat/conversations/{id}` | Detalhe da conversa |
| GET | `/api/v1/chat/conversations/{id}/messages` | Histórico por cursor |
| POST | `/api/v1/chat/conversations/{id}/messages` | Envia texto |
| POST | `/api/v1/chat/conversations/{id}/messages/attachments` | Envia imagem/voz/documento |
| POST | `/api/v1/chat/conversations/{id}/read` | Atualiza cursor de leitura |
| DELETE | `/api/v1/chat/messages/{id}` | Exclusão lógica da própria mensagem |
| GET | `/api/v1/chat/unread-count` | Total para o badge |
| GET | `/api/v1/chat/attachments/{id}/url` | Renova a URL assinada |

## WebSocket

- Endpoint: `/ws`
- Destino de entrega: `/user/queue/chat`
- Destino de digitação: `/app/chat.typing`
- Eventos: `MESSAGE_CREATED`, `MESSAGE_DELETED`, `CONVERSATION_REVOKED`, `TYPING`
- JWT validado no `CONNECT`; sessão fechada com código `4001` quando o token expira.
- Limite de sessões por usuário e backpressure do broker.

## Segurança e privacidade

- Não participante recebe **404**, não 403.
- Remoção de amizade/revogação de grupo publica `CONVERSATION_REVOKED`.
- Rate limit por usuário para mensagens e uploads.
- Allowlist de extensões + magic bytes; SVG, HTML, executáveis e compactados bloqueados.
- Imagens reencodadas em JPEG, sem EXIF, com limite de dimensão.
- Logs sem corpo de mensagem, nome de arquivo ou URL assinada.
- `Cache-Control: no-store` em `/api/v1/chat/**`.
- Exportação LGPD inclui apenas mensagens enviadas pelo titular.
- Exclusão de conta encerra sessões WebSocket e anonimiza o autor.

## Testes

- Unitários: `ChatImageProcessorTest`, `ChatAttachmentPolicyTest`, `StompAuthChannelInterceptorTest`.
- Integração: `ChatIntegrationTest` cobre conversa direta, idempotência, histórico, não lidas, leitura, exclusão, 404 para não participante, grupo e upload de imagem.
- **Observação:** os testes de integração usam Testcontainers e exigem Docker. Neste ambiente o daemon não estava disponível; os testes unitários e a compilação de produção/testes passaram.

## Configuração

Novas variáveis em `.env.example`:

```env
APP_CHAT_STORAGE_BUCKET=
APP_CHAT_STORAGE_KEY_PREFIX=chat
APP_CHAT_ATTACHMENT_URL_TTL=PT15M
APP_CHAT_MAX_TEXT_CHARS=2000
APP_CHAT_MAX_IMAGE_BYTES=8388608
APP_CHAT_MAX_VOICE_BYTES=16777216
APP_CHAT_MAX_FILE_BYTES=20971520
APP_CHAT_MAX_VOICE_SECONDS=300
APP_CHAT_MESSAGES_PER_MINUTE=30
APP_CHAT_UPLOADS_PER_MINUTE=10
APP_CHAT_TYPING_THROTTLE=PT2S
APP_CHAT_MAX_SESSIONS_PER_USER=5
```

O multipart global passa para 25 MB/30 MB; o avatar continua limitado a 2 MB no serviço.

## Deploy

- Em produção: frontend `https://devrats.app`, API `https://api.devrats.app` e WebSocket `wss://api.devrats.app/ws`.
- O proxy reverso (Caddy) precisa encaminhar o upgrade em `/ws`; o Caddy já faz isso no `reverse_proxy`.
- `APP_CORS_ALLOWED_ORIGINS` deve incluir `https://devrats.app` e `https://www.devrats.app`.
- O frontend usa rewrite da Vercel para `/api` e `VITE_WS_URL` direto para a API.
- Sem `APP_CHAT_STORAGE_BUCKET`, o chat de texto funciona e uploads retornam 503.
- O bucket de chat deve ser privado; não reutilizar o bucket público de avatares.

## Pontos de melhoria documentados

- Migrar rate limit/presença/cache para Redis quando houver mais de uma instância.
- Trocar o broker simples por RabbitMQ STOMP relay ou ponte Redis Pub/Sub para fan-out entre réplicas.
- Adicionar criptografia ponta a ponta, push notification e moderação/denúncia.
- Evoluir para presigned PUT em arquivos grandes, múltiplos anexos por mensagem, thumbnails e virus scanning.
- Adicionar busca de mensagens, edição, reações e replies.
- Criar job de limpeza de objetos órfãos do S3.

## Checklist

- [x] Migration Flyway
- [x] Entidades/repositórios
- [x] Serviços e regras de autorização
- [x] REST + ProblemDetail
- [x] STOMP/WebSocket com JWT
- [x] Storage S3 privado
- [x] Rate limit
- [x] Testes unitários
- [x] Integração (requer Docker)
- [x] `.env.example`
- [x] CHANGELOG
- [x] Documentação de limitações e escala
