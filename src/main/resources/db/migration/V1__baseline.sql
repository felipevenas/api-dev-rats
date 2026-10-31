-- DevRats MVP baseline. Flyway owns the schema; Hibernate only validates it.
-- Instants are stored as timestamptz; activity dates are local dates in the challenge timezone.

CREATE TABLE users (
    id UUID PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    username VARCHAR(30) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    timezone VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    notify_daily_goal BOOLEAN NOT NULL DEFAULT TRUE,
    notify_milestones BOOLEAN NOT NULL DEFAULT TRUE,
    notify_challenge_updates BOOLEAN NOT NULL DEFAULT TRUE,
    notify_integration BOOLEAN NOT NULL DEFAULT TRUE,
    terms_accepted_at TIMESTAMPTZ,
    terms_version VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_username UNIQUE (username)
);

CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));
CREATE INDEX idx_users_status ON users (status);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    user_agent VARCHAR(255),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_tokens_expires ON password_reset_tokens (expires_at);

CREATE TABLE github_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    github_user_id BIGINT NOT NULL,
    github_username VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    connection_status VARCHAR(16) NOT NULL CHECK (connection_status IN ('CONNECTED', 'REVOKED', 'EXPIRED')),
    encrypted_access_token BYTEA,
    access_token_expires_at TIMESTAMPTZ,
    encrypted_refresh_token BYTEA,
    refresh_token_expires_at TIMESTAMPTZ,
    token_key_version INT,
    connected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    disconnected_at TIMESTAMPTZ,
    last_synced_at TIMESTAMPTZ,
    CONSTRAINT uq_github_accounts_user UNIQUE (user_id),
    CONSTRAINT uq_github_accounts_github_user UNIQUE (github_user_id)
);

CREATE INDEX idx_github_accounts_status ON github_accounts (connection_status);

CREATE TABLE github_installations (
    id UUID PRIMARY KEY,
    github_account_id UUID NOT NULL REFERENCES github_accounts(id) ON DELETE CASCADE,
    installation_id BIGINT NOT NULL,
    account_login VARCHAR(100) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    suspended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_github_installations_installation UNIQUE (installation_id)
);

CREATE INDEX idx_github_installations_account ON github_installations (github_account_id);

CREATE TABLE github_repositories (
    id UUID PRIMARY KEY,
    github_repository_id BIGINT NOT NULL,
    owner_github_user_id BIGINT,
    owner_login VARCHAR(100) NOT NULL,
    owner_type VARCHAR(20) NOT NULL CHECK (owner_type IN ('USER', 'ORGANIZATION')),
    repository_name VARCHAR(200) NOT NULL,
    full_name VARCHAR(300) NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    is_fork BOOLEAN NOT NULL DEFAULT FALSE,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    installation_id BIGINT,
    access_status VARCHAR(20) NOT NULL CHECK (access_status IN ('ACCESSIBLE', 'NOT_ACCESSIBLE', 'REVOKED')),
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_github_repositories_github_id UNIQUE (github_repository_id)
);

CREATE INDEX idx_github_repositories_owner ON github_repositories (owner_github_user_id);
CREATE INDEX idx_github_repositories_full_name ON github_repositories (full_name);

CREATE TABLE groups (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    creator_user_id UUID NOT NULL REFERENCES users(id),
    timezone VARCHAR(64) NOT NULL,
    max_members INT NOT NULL DEFAULT 20 CHECK (max_members BETWEEN 2 AND 200),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_groups_creator ON groups (creator_user_id);
CREATE INDEX idx_groups_status ON groups (status);

CREATE TABLE group_members (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(16) NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'LEFT', 'REMOVED')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at TIMESTAMPTZ,
    CONSTRAINT uq_group_members_group_user UNIQUE (group_id, user_id)
);

CREATE INDEX idx_group_members_user ON group_members (user_id);
CREATE UNIQUE INDEX uq_group_members_owner ON group_members (group_id) WHERE role = 'OWNER' AND status = 'ACTIVE';

CREATE TABLE group_invites (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    max_uses INT CHECK (max_uses IS NULL OR max_uses > 0),
    uses INT NOT NULL DEFAULT 0 CHECK (uses >= 0),
    CONSTRAINT uq_group_invites_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_group_invites_group ON group_invites (group_id);
CREATE INDEX idx_group_invites_expires ON group_invites (expires_at);

CREATE TABLE challenges (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    sequence_no INT NOT NULL,
    verification_mode VARCHAR(32) NOT NULL CHECK (verification_mode IN ('SELECTED_REPOSITORIES', 'OWN_REPOSITORIES')),
    target_valid_days INT NOT NULL CHECK (target_valid_days BETWEEN 1 AND 365),
    timezone VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    starts_at TIMESTAMPTZ NOT NULL,
    admission_ends_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    allow_forks BOOLEAN NOT NULL DEFAULT FALSE,
    tiebreak_rule VARCHAR(120) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_challenges_group_sequence UNIQUE (group_id, sequence_no),
    CONSTRAINT ck_challenges_window CHECK (ends_at IS NULL OR ends_at > starts_at)
);

CREATE UNIQUE INDEX uq_challenges_group_open ON challenges (group_id) WHERE status IN ('DRAFT', 'ACTIVE');
CREATE INDEX idx_challenges_group_sequence ON challenges (group_id, sequence_no DESC);
CREATE INDEX idx_challenges_status_starts ON challenges (status, starts_at);

CREATE TABLE challenge_participants (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    group_member_id UUID NOT NULL REFERENCES group_members(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'LEFT', 'REMOVED')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at TIMESTAMPTZ,
    valid_days INT NOT NULL DEFAULT 0 CHECK (valid_days >= 0),
    current_streak INT NOT NULL DEFAULT 0 CHECK (current_streak >= 0),
    best_streak INT NOT NULL DEFAULT 0 CHECK (best_streak >= 0),
    last_valid_date DATE,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_participants_challenge_user UNIQUE (challenge_id, user_id)
);

CREATE INDEX idx_participants_challenge_progress ON challenge_participants (challenge_id, valid_days DESC, current_streak DESC);
CREATE INDEX idx_participants_user ON challenge_participants (user_id);

CREATE TABLE challenge_repositories (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    github_repository_id UUID NOT NULL REFERENCES github_repositories(id) ON DELETE CASCADE,
    slot INT NOT NULL CHECK (slot BETWEEN 1 AND 3),
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_challenge_repositories_slot UNIQUE (challenge_id, slot),
    CONSTRAINT uq_challenge_repositories_repo UNIQUE (challenge_id, github_repository_id)
);

CREATE INDEX idx_challenge_repositories_repo ON challenge_repositories (github_repository_id);

CREATE TABLE github_events (
    id UUID PRIMARY KEY,
    delivery_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    action VARCHAR(64),
    installation_id BIGINT,
    github_repository_id BIGINT,
    payload JSONB,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED', 'IGNORED')),
    attempts INT NOT NULL DEFAULT 0,
    error_code VARCHAR(80),
    last_error VARCHAR(500),
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    CONSTRAINT uq_github_events_delivery UNIQUE (delivery_id)
);

CREATE INDEX idx_github_events_pending ON github_events (received_at) WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX idx_github_events_repo ON github_events (github_repository_id);

CREATE TABLE eligible_commits (
    id UUID PRIMARY KEY,
    github_repository_id UUID NOT NULL REFERENCES github_repositories(id) ON DELETE CASCADE,
    commit_sha VARCHAR(64) NOT NULL,
    github_author_id BIGINT,
    github_committer_id BIGINT,
    author_login VARCHAR(100),
    committed_at TIMESTAMPTZ NOT NULL,
    first_observed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_automated BOOLEAN NOT NULL DEFAULT FALSE,
    eligibility_status VARCHAR(16) NOT NULL CHECK (eligibility_status IN ('ELIGIBLE', 'INELIGIBLE')),
    eligibility_reason VARCHAR(120),
    observation_source VARCHAR(20) NOT NULL CHECK (observation_source IN ('WEBHOOK', 'RECONCILIATION')),
    CONSTRAINT uq_eligible_commits_repo_sha UNIQUE (github_repository_id, commit_sha)
);

CREATE INDEX idx_eligible_commits_author ON eligible_commits (github_author_id, committed_at);

CREATE TABLE member_daily_activity (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES challenge_participants(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    activity_date DATE NOT NULL,
    first_eligible_commit_at TIMESTAMPTZ NOT NULL,
    valid_commit_count INT NOT NULL DEFAULT 1 CHECK (valid_commit_count > 0),
    validation_status VARCHAR(16) NOT NULL CHECK (validation_status IN ('VALID', 'CORRECTED', 'REVOKED')),
    validated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    correction_reason VARCHAR(255),
    CONSTRAINT uq_daily_activity_participant_date UNIQUE (challenge_id, participant_id, activity_date)
);

CREATE INDEX idx_daily_activity_challenge_date ON member_daily_activity (challenge_id, activity_date);
CREATE INDEX idx_daily_activity_user ON member_daily_activity (user_id);

CREATE TABLE challenge_results (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    winner_user_id UUID NOT NULL REFERENCES users(id),
    winner_participant_id UUID NOT NULL REFERENCES challenge_participants(id),
    achieved_at TIMESTAMPTZ NOT NULL,
    winning_activity_date DATE NOT NULL,
    valid_days_at_win INT NOT NULL CHECK (valid_days_at_win > 0),
    result_status VARCHAR(16) NOT NULL CHECK (result_status IN ('PROVISIONAL', 'FINAL', 'SUPERSEDED')),
    finalized_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    superseded_by_result_id UUID,
    CONSTRAINT uq_challenge_results_challenge UNIQUE (challenge_id)
);

CREATE INDEX idx_challenge_results_winner ON challenge_results (winner_user_id);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    challenge_id UUID,
    payload JSONB,
    dedupe_key VARCHAR(120) NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notifications_user_dedupe UNIQUE (user_id, dedupe_key)
);

CREATE INDEX idx_notifications_user_unread ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID,
    entity_type VARCHAR(40) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX idx_audit_actor ON audit_logs (actor_user_id, created_at DESC);

CREATE TABLE reconciliation_runs (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    error_code VARCHAR(80),
    commits_found INT NOT NULL DEFAULT 0,
    commits_eligible INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_reconciliation_runs_challenge ON reconciliation_runs (challenge_id, started_at DESC);
