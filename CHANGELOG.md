# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

### 🚀 Added

- **Study photo mode**: `STUDY_PHOTO` challenge mode where one study photo per participant per day validates the day, stored in a private S3 bucket with short-lived signed URLs, optional title/description and repository/commit context, one-photo-per-day uniqueness, deletion with day recalculation, LGPD export, and account-deletion cleanup.
- **Cloudflare Turnstile**: server-side verification of the `X-Turnstile-Token` header on register, login, and password-reset request, with fail-closed production behavior and `fail-open` only for local development.
- **Feature flags**: `GET /api/v1/features` exposes `studyPhotos` so the UI only offers the mode when the private bucket is configured.
- Individual notification deletion, scoped to the authenticated user.
- Group deletion by the creator, with notification to active participants and an audit record.
- Default timezone of Brasilia for new accounts and challenges.
- Login with e-mail or username, while remaining compatible with the previous payload.
- Profile fields: bio, location, website, experience level, and primary stack.
- Endpoint for eligible commits that validated each challenge day.
- Profile picture upload and removal with S3-compatible storage and GitHub fallback.
- Full pagination for GitHub App installation and repository listings.
- **Friendship system**: username search, public profile, requests with accept, decline, and cancel, auto-accept for crossed requests, and friendship removal.
- **Presence**: authenticated heartbeat with `ONLINE`/`AWAY`/`OFFLINE` states, visible only to friends.
- **Social notifications**: `FRIEND_REQUEST` and `FRIEND_REQUEST_ACCEPTED` with inline actions in the alert center.
- **Privacy**: account deletion removes the social graph and presence; LGPD export includes minimal friendships and the owner's last heartbeat.
- **Social rate limiting**: per-IP and per-user limits on search and friend requests, with `Retry-After` on every 429 response.
- **Real-time chat**: private conversations between friends and automatic chat for active groups, with cursor-paginated history, unread counter, read cursor, and soft deletion of messages.
- **WebSocket/STOMP**: `/ws` endpoint with JWT authentication in the `CONNECT` frame, event delivery through `/user/queue/chat`, client reconnection, heartbeats, session limits, and server-side close when the token expires.
- **Messages**: text, image, voice message, and document; `clientMessageId` guarantees retry idempotency, and events are published only after commit.
- **Private attachments**: separate S3 bucket for chat, multipart upload validated by magic bytes, images re-encoded to JPEG without EXIF, per-type limits, and short-lived presigned download URLs.
- **Chat security**: authorization derived from `ACCEPTED` friendship/`ACTIVE` membership, 404 for non-participants, per-user rate limiting, `Cache-Control: no-store`, and logs without content or file names.
- **LGPD**: export includes only messages sent by the owner; account deletion closes WebSocket sessions and anonymizes the author without deleting other participants' history.
- **Operations**: session and message metrics, upload/S3 timeouts, and documentation of the scaling path with Redis/RabbitMQ.
- **E-mail password reset**: delivery through Resend with `off`/`test`/`live` modes, link with token in the fragment, 30-minute validity, single use, SHA-256 hash, revocation of all sessions, per-account cooldown, auditing, and daily token cleanup.
- **Asynchronous send after commit**: `@TransactionalEventListener` + bounded executor, with Resend idempotency and the `email.send` metric.

### 🔧 Fixed

- **Study photos**: feed entries now show "Foto de estudo publicada/removida" instead of the generic update message.

- GitHub callback and setup always redirect to the frontend with `github=connected|installed|error`, without exposing error JSON.
- Disconnecting GitHub revokes authorization in the GitHub App in addition to clearing local tokens.
- Organization installations can be shared between DevRats accounts without improper reassignment.
- Installation-removed and repository-added/removed webhooks keep local access up to date.
- Revoked repositories no longer count in `repositoryCount`, and disconnected accounts stop validating commits.
- Repository synchronization marks as revoked only the links from the affected installation.
- Outbound GitHub HTTP calls remain outside database transactions in the integration flow.
- `/api/v1/auth/**` responses now use `Cache-Control: no-store`.
- Reset tokens now expire exactly at the validity boundary, without accepting the expiration instant.

### 📝 Documentation

- Standardized English README with architecture, technical decisions, complete environment reference, API surface, governance, and license.
- English contribution guide, security policy, code of conduct, changelog, and GitHub collaboration templates.
- Direct cross-links between the backend and frontend repositories.
- GitHub App setup documents redirect URLs and callback error codes.
- Migration `V4` models installation sharing between accounts.
- `docs/PR-CHAT.md` consolidates the PR body, decisions, and improvement points for the feature.
- `docs/DEPLOY-VPS.md` updated for the `devrats.app` domain: DNS on Name.com, API at `api.devrats.app`, Caddy with automatic TLS, Vercel rewrite, and `VITE_WS_URL` pointing directly to the WebSocket.
- Frontend `vercel.json` rewrites `/api` to `https://api.devrats.app`; `index.html` uses canonical/OG at `https://devrats.app`.
- `docs/EMAIL.md` documents Resend, delivery modes, DNS for `eufelipedesign.cloud`, local testing, and the production runbook.
- README and `docs/DEPLOY-VPS.md` include the e-mail variables, verified sender, and validation checklist.

### ⚠️ Known Limitations

- E-mail sending is asynchronous and has no outbox: if the process dies between the commit and the send, that e-mail is lost. The user can request another link; the evolution path with an outbox is in `docs/EMAIL.md`.
- The simple STOMP broker lives in a single instance's memory. Multiple replicas require RabbitMQ (STOMP relay) or a Redis Pub/Sub bridge.
- Rate limiting and presence remain local to the instance; migration to Redis is documented as an evolution.
- Chat has no end-to-end encryption, push notification, message editing/search, reactions, or content moderation.
- v1 accepts one attachment per message; virus scanning and audio transcoding are out of scope.
- HEIC is not accepted because the JDK does not decode the format natively.

## [0.1.0] - 2026-10-28

### 🚀 Added

- **Authentication and profile**: registration, login, rotating refresh token in an `HttpOnly` cookie, logout, password recovery, account editing and deletion, and user data export.
- **GitHub App integration**: OAuth flow, installation, repository synchronization, callback, setup, and webhook with HMAC signature validation.
- **Groups and invites**: creation, editing, member listing, leaving, token-based invites, and joining by invite.
- **Challenges**: draft, start, cancel, selection of up to three repositories, `SELECTED_REPOSITORIES` and `OWN_REPOSITORIES` modes, configurable admission, and fixed challenge timezone.
- **Progress and result**: valid days, streaks, ranking, activity feed, notifications, and atomic winner resolution with tie-breaking.
- **Commit eligibility**: authorship by numeric GitHub ID, exclusion of bots, empty commits and merge commits, configurable age limit, and idempotency by `repository`/`sha`.
- **Reconciliation**: periodic, idempotent recovery of lost GitHub events.
- **Observability**: Actuator, Prometheus metrics, trace ID in error responses, and structured logs.
- **Security**: HS256 JWT, Bucket4j rate limiting, configurable CORS, AES-256-GCM encryption for GitHub tokens, and RFC 9457 errors.
- **Documentation**: README with setup, configuration, endpoints, and limitations; PRD in [`docs/PRD-MVD.md`](docs/PRD-MVD.md) and conventions in [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md).
- **Quality**: 36 tests across unit, Testcontainers integration, WireMock, concurrency, and ArchUnit architecture; JaCoCo coverage.
- **Governance**: MIT license, contribution guide, security policy, code of conduct, issue and pull request templates, automated labels, and GitHub Actions CI.

### 🔐 Security

- Development secrets are not committed; `.env.example` contains example values only.
- The `local` profile must be explicitly enabled, preventing accidental use of development settings in production.
- GitHub tokens are stored encrypted and never returned to the client.

### ⚠️ Known Limitations

- Password-reset e-mail delivery was not integrated; in `local`, the token is exposed in the response.
- Full GitHub App validation depends on credentials and a public webhook URL.
- Scheduled jobs and reconciliation assume a single API instance.
- Retroactive result corrections require an audited administrative procedure.
