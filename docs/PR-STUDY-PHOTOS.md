# feat(api): modo foto de estudo e Cloudflare Turnstile na autenticação

## Resumo

Adiciona ao DevRats um novo modo de desafio em que o dia válido é conquistado com uma foto de estudo, e protege o cadastro/login/recuperação de senha com Cloudflare Turnstile.

- Novo `VerificationMode.STUDY_PHOTO`.
- Uma foto por participante por dia; o dia vale 1 dia válido.
- Título e descrição opcionais; vínculo opcional com repositório e URL de commit (contexto, não pontua).
- Foto em bucket S3 privado, com URL assinada de curta duração.
- Galeria do grupo, fotos no calendário, exclusão com recálculo do dia.
- Turnstile validado no servidor via header `X-Turnstile-Token`.

## Motivação

O produto já validava commits, mas não cobria quem estuda sem necessariamente commitar. O modo foto reduz a barreira de entrada e mantém a mesma mecânica de dias válidos, ranking, streak e vitória. O Turnstile fecha a porta de cadastro/login automatizado sem exigir mudança de DNS ou proxy da API.

## Decisões de arquitetura

1. **Reutilizar o ledger diário.** A foto grava em `member_daily_activity`, a mesma tabela usada por commits, calendário, streak e ranking. Assim a vitória e o progresso continuam baseados em `valid_days`.
2. **Uma foto por dia no banco.** A unique key `(challenge_id, participant_id, activity_date)` em `study_photos` é a garantia, não um `if (exists)`.
3. **Bucket privado separado.** Fotos de estudo não compartilham bucket com avatares públicos nem com anexos do chat. A API não faz proxy dos bytes; devolve URL assinada.
4. **Upload antes do INSERT.** A object key é gerada antes do flush para respeitar `object_key NOT NULL`; se a transação falhar, o objeto é removido no rollback.
5. **Pontuação desacoplada do vínculo.** Repositório e URL de commit são apenas contexto visual. A pontuação do modo foto é a própria foto.
6. **Turnstile no header.** O token viaja em `X-Turnstile-Token`, evitando alterar os DTOs de registro/login/reset e quebrar clientes antigos.
7. **Produção falha fechado.** `fail-open` existe apenas para desenvolvimento local; em produção, indisponibilidade da Cloudflare retorna 503.
8. **Modo estrito.** `STUDY_PHOTO` nunca valida dia por commit. A regra fica centralizada em `VerificationMode.countsCommits()` e é aplicada no webhook, na reconciliação e na listagem de evidências; a reconciliação nem consulta o GitHub para desafios de foto.

## Banco de dados

Migration `V7__study_photos.sql`:

- estende o `CHECK` de `challenges.verification_mode` com `STUDY_PHOTO`;
- permite dia sem commit em `member_daily_activity` (`first_eligible_commit_at` nullable, `valid_commit_count >= 0`);
- adiciona `photo_count`, `first_photo_at` e `points` ao ledger;
- cria `study_photos` com unique key por participante/dia e índices de consulta;
- mantém os modos existentes intactos.

Migration `V8__study_photo_mode_isolation.sql`:

- remove vínculos de repositórios selecionados de desafios de foto;
- remove dias de desafios de foto validados apenas por commit;
- zera a evidência de commit em dias híbridos, preservando o dia válido pela foto;
- recalcula `valid_days`, `current_streak`, `best_streak` e `last_valid_date` dos participantes de desafios de foto, seguindo a mesma regra de `StreakHelper` (sequência viva se a última data for hoje ou ontem);
- não reverte automaticamente desafios de foto já concluídos; o runbook lista os casos para correção manual.

## API

| Método | Rota | Função |
|---|---|---|
| POST | `/api/v1/challenges/{id}/photos` | Publica a foto do dia (multipart) |
| GET | `/api/v1/challenges/{id}/photos` | Galeria paginada, com filtros de participante/data |
| GET | `/api/v1/challenges/{id}/photos/{photoId}/url` | Renova a URL assinada |
| DELETE | `/api/v1/challenges/{id}/photos/{photoId}` | Autor ou dono do grupo exclui e recalcula o dia |
| GET | `/api/v1/features` | Expõe `studyPhotos` para o front |

Regras: membro ativo do desafio; participante `ACTIVE`; desafio `ACTIVE`; data dentro da janela; não membro recebe **404**; exclusão só com desafio ativo.

## Segurança e privacidade

- Bucket privado com bloqueio de acesso público.
- URLs assinadas de 15 minutos; a object key nunca é exposta.
- `Cache-Control: no-store` em `/api/v1/challenges/**`.
- Rate limit por IP para upload de foto.
- Imagem validada por magic bytes, reencodada em JPEG sem EXIF e limitada a 2048 px/8 MB.
- Turnstile no registro, login e solicitação de reset; token de uso único; segredo só no servidor.
- Export LGPD inclui metadados das fotos do titular; exclusão de conta remove as fotos e os objetos S3.

## Configuração

```env
APP_STUDY_PHOTOS_ENABLED=true
APP_STUDY_PHOTOS_STORAGE_BUCKET=devrats-study-photos
APP_STUDY_PHOTOS_STORAGE_KEY_PREFIX=study-photos
APP_STUDY_PHOTOS_MAX_IMAGE_BYTES=8388608
APP_STUDY_PHOTOS_URL_TTL=PT15M
APP_RATE_LIMIT_PHOTO_UPLOAD_CAPACITY=20
APP_RATE_LIMIT_PHOTO_UPLOAD_REFILL=PT1H

APP_TURNSTILE_ENABLED=true
CLOUDFLARE_TURNSTILE_SITE_KEY=
CLOUDFLARE_TURNSTILE_SECRET_KEY=
CLOUDFLARE_TURNSTILE_VERIFY_URL=https://challenges.cloudflare.com/turnstile/v0/siteverify
CLOUDFLARE_TURNSTILE_TIMEOUT=PT5S
CLOUDFLARE_TURNSTILE_FAIL_OPEN=false
```

## Testes

- Unitários: `TurnstileVerifierTest` (sucesso, token ausente, rejeição, indisponibilidade, fail-open).
- Integração: `StudyPhotoIntegrationTest` cobre publicação, dia válido no progresso, segunda foto no mesmo dia, listagem, exclusão com recálculo e 404 para não membro.
- Integração: `StudyPhotoModeIsolationIntegrationTest` cobre webhook, commit reconciliado, reconciliação ignorada, evidência de commit oculta e limpeza de vínculos ao trocar para foto.
- Suíte completa: 243 testes, 0 falhas.
- Frontend: 149 testes, `npm run build` OK.

## Runbook pós-deploy (migration V8)

A V8 roda no startup do container. Faça o deploy com a instância antiga parada (o `docker compose up -d` substitui o container anterior), para que nenhum webhook antigo grave um dia de commit após a limpeza.

```sql
-- 1. Não devem restar dias validados só por commit em desafios de foto.
SELECT count(*) AS dias_so_commit
FROM member_daily_activity a
JOIN challenges c ON c.id = a.challenge_id
WHERE c.verification_mode = 'STUDY_PHOTO'
  AND a.validation_status = 'VALID'
  AND a.photo_count = 0;

-- 2. Desafios de foto concluídos cujo vencedor ficou abaixo da meta.
--    A migration não reverte resultados; corrija manualmente se houver linhas.
SELECT c.id AS challenge_id, c.group_id, c.target_valid_days,
       r.winner_user_id, p.valid_days
FROM challenges c
JOIN challenge_results r ON r.challenge_id = c.id
JOIN challenge_participants p ON p.id = r.winner_participant_id
WHERE c.verification_mode = 'STUDY_PHOTO'
  AND c.status = 'COMPLETED'
  AND p.valid_days < c.target_valid_days;
```

## Checklist

- [x] Migration Flyway
- [x] Entidades/repositórios
- [x] Serviço e regras de autorização
- [x] Storage S3 privado
- [x] REST + ProblemDetail
- [x] Turnstile server-side
- [x] Rate limit e cache privado
- [x] Export LGPD e exclusão de conta
- [x] Testes unitários e de integração
- [x] `.env.example` e `CHANGELOG`
