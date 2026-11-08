-- Modo "foto de estudo": uma foto por participante/dia vale um dia válido.
-- A foto vive em bucket S3 privado e o dia é registrado no mesmo ledger
-- (member_daily_activity) usado por commits, calendário, streak e ranking.

-- 1. Novo valor de modalidade sem tocar nos valores existentes.
DO $$
DECLARE constraint_name text;
BEGIN
    SELECT con.conname INTO constraint_name
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'challenges'
      AND con.contype = 'c'
      AND pg_get_constraintdef(con.oid) LIKE '%verification_mode%';
    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE challenges DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

ALTER TABLE challenges
    ADD CONSTRAINT ck_challenges_verification_mode
    CHECK (verification_mode IN ('SELECTED_REPOSITORIES', 'OWN_REPOSITORIES', 'STUDY_PHOTO'));

-- 2. Um dia pode existir só com foto: sem commit, o timestamp do commit é nulo.
DO $$
DECLARE constraint_name text;
BEGIN
    SELECT con.conname INTO constraint_name
    FROM pg_constraint con
    JOIN pg_class rel ON rel.oid = con.conrelid
    WHERE rel.relname = 'member_daily_activity'
      AND con.contype = 'c'
      AND pg_get_constraintdef(con.oid) LIKE '%valid_commit_count%';
    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE member_daily_activity DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

ALTER TABLE member_daily_activity
    ALTER COLUMN first_eligible_commit_at DROP NOT NULL;

ALTER TABLE member_daily_activity
    ADD CONSTRAINT ck_daily_activity_valid_commit_count CHECK (valid_commit_count >= 0);

ALTER TABLE member_daily_activity
    ADD COLUMN photo_count INT NOT NULL DEFAULT 0;

ALTER TABLE member_daily_activity
    ADD COLUMN first_photo_at TIMESTAMPTZ;

ALTER TABLE member_daily_activity
    ADD COLUMN points NUMERIC(3, 1) NOT NULL DEFAULT 1.0;

ALTER TABLE member_daily_activity
    ADD CONSTRAINT ck_daily_activity_evidence CHECK (valid_commit_count > 0 OR photo_count > 0);

-- 3. Foto publicada. A unique key é a garantia de "uma foto por dia".
CREATE TABLE study_photos (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES challenge_participants(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    activity_date DATE NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    width INT,
    height INT,
    title VARCHAR(120),
    description VARCHAR(500),
    github_repository_id UUID REFERENCES github_repositories(id) ON DELETE SET NULL,
    commit_url VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_study_photos_participant_date
        UNIQUE (challenge_id, participant_id, activity_date)
);

CREATE INDEX idx_study_photos_challenge_date ON study_photos (challenge_id, activity_date DESC);
CREATE INDEX idx_study_photos_user ON study_photos (user_id);
