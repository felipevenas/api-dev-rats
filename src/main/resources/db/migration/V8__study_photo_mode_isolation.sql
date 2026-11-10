-- Isolamento do modo "foto de estudo".
-- A partir desta versão, STUDY_PHOTO valida dias exclusivamente por foto.
-- Commits observados antes da correção não podem permanecer no ledger.

-- 1. Desafios de foto não mantêm vínculos de repositórios selecionados.
DELETE FROM challenge_repositories cr
USING challenges c
WHERE cr.challenge_id = c.id
  AND c.verification_mode = 'STUDY_PHOTO';

-- 2. Remove dias validados apenas por commit (sem foto).
DELETE FROM member_daily_activity a
USING challenges c
WHERE a.challenge_id = c.id
  AND c.verification_mode = 'STUDY_PHOTO'
  AND a.validation_status = 'VALID'
  AND a.photo_count = 0;

-- 3. Em dias híbridos (foto + commit), remove a evidência de commit.
--    O dia continua válido pela foto e o desempate passa a usar first_photo_at.
UPDATE member_daily_activity a
SET valid_commit_count = 0,
    first_eligible_commit_at = NULL
FROM challenges c
WHERE a.challenge_id = c.id
  AND c.verification_mode = 'STUDY_PHOTO'
  AND a.photo_count > 0
  AND a.valid_commit_count > 0;

-- 4. Recalcula progresso dos participantes de desafios de foto.
--    valid_days/best_streak refletem apenas as datas válidas restantes após a limpeza.
--    Datas consecutivas são agrupadas por (data - row_number). O streak atual
--    segue a regra de StreakHelper: a sequência continua viva se a última data
--    for hoje ou ontem (reference_date - 1).
WITH valid AS (
    SELECT a.participant_id,
           a.challenge_id,
           a.activity_date,
           a.activity_date - (ROW_NUMBER() OVER (
               PARTITION BY a.participant_id, a.challenge_id ORDER BY a.activity_date
           ))::int AS streak_group
    FROM member_daily_activity a
    JOIN challenges c ON c.id = a.challenge_id
    WHERE c.verification_mode = 'STUDY_PHOTO'
      AND a.validation_status = 'VALID'
),
streaks AS (
    SELECT participant_id, challenge_id, streak_group, COUNT(*) AS length, MAX(activity_date) AS end_date
    FROM valid
    GROUP BY participant_id, challenge_id, streak_group
),
totals AS (
    SELECT participant_id, challenge_id, COUNT(*) AS valid_days, MAX(activity_date) AS last_valid_date
    FROM valid
    GROUP BY participant_id, challenge_id
),
best AS (
    SELECT participant_id, challenge_id, MAX(length) AS best_streak
    FROM streaks
    GROUP BY participant_id, challenge_id
),
challenge_reference AS (
    SELECT cp.id AS participant_id,
           cp.challenge_id,
           CASE
               WHEN c.completed_at IS NOT NULL
                   THEN (c.completed_at AT TIME ZONE c.timezone)::date
               ELSE LEAST(
                   (now() AT TIME ZONE c.timezone)::date,
                   COALESCE((c.ends_at AT TIME ZONE c.timezone)::date,
                            (now() AT TIME ZONE c.timezone)::date)
               )
           END AS reference_date
    FROM challenge_participants cp
    JOIN challenges c ON c.id = cp.challenge_id
    WHERE c.verification_mode = 'STUDY_PHOTO'
),
current_streak AS (
    SELECT s.participant_id, s.challenge_id, s.length
    FROM streaks s
    JOIN challenge_reference r
      ON r.participant_id = s.participant_id
     AND r.challenge_id = s.challenge_id
     AND s.end_date >= r.reference_date - 1
)
UPDATE challenge_participants p
SET valid_days = COALESCE(t.valid_days, 0),
    last_valid_date = t.last_valid_date,
    best_streak = COALESCE(b.best_streak, 0),
    current_streak = COALESCE(cs.length, 0)
FROM challenge_reference r
LEFT JOIN totals t ON t.participant_id = r.participant_id AND t.challenge_id = r.challenge_id
LEFT JOIN best b ON b.participant_id = r.participant_id AND b.challenge_id = r.challenge_id
LEFT JOIN current_streak cs ON cs.participant_id = r.participant_id AND cs.challenge_id = r.challenge_id
WHERE p.id = r.participant_id AND p.challenge_id = r.challenge_id;

-- 5. Desafios de foto já concluídos não são revertidos automaticamente.
--    Consulte o runbook pós-deploy para identificar vencedores abaixo da meta.
--    A migração deve rodar com uma única instância da API ativa.
