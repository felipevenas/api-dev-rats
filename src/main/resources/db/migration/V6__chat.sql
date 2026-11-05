-- Chat privado e de grupo. Texto fica no PostgreSQL; anexos vivem em bucket
-- S3 privado e nunca expõem a object key para o cliente.
--
-- DIRECT usa direct_key canônico (UUIDs ordenados como string) para que a
-- unicidade do par não dependa da ordenação de UUID do Java/Postgres.
-- GROUP usa group_id único; o grupo é a fonte de verdade da participação.

CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    type VARCHAR(16) NOT NULL CHECK (type IN ('DIRECT', 'GROUP')),
    group_id UUID REFERENCES groups(id) ON DELETE CASCADE,
    user_a_id UUID REFERENCES users(id),
    user_b_id UUID REFERENCES users(id),
    direct_key VARCHAR(73),
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_conversations_shape CHECK (
        (type = 'DIRECT'
            AND group_id IS NULL
            AND user_a_id IS NOT NULL
            AND user_b_id IS NOT NULL
            AND direct_key IS NOT NULL
            AND user_a_id <> user_b_id)
        OR
        (type = 'GROUP'
            AND group_id IS NOT NULL
            AND user_a_id IS NULL
            AND user_b_id IS NULL
            AND direct_key IS NULL)
    )
);

CREATE UNIQUE INDEX uq_conversations_direct_key
    ON conversations (direct_key) WHERE type = 'DIRECT';
CREATE UNIQUE INDEX uq_conversations_group
    ON conversations (group_id) WHERE type = 'GROUP';
CREATE INDEX idx_conversations_user_a ON conversations (user_a_id, last_message_at DESC);
CREATE INDEX idx_conversations_user_b ON conversations (user_b_id, last_message_at DESC);
CREATE INDEX idx_conversations_last_message ON conversations (last_message_at DESC);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(16) NOT NULL CHECK (type IN ('TEXT', 'IMAGE', 'VOICE', 'FILE')),
    body VARCHAR(2000),
    client_message_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_chat_messages_client
        UNIQUE (conversation_id, sender_id, client_message_id),
    CONSTRAINT ck_chat_messages_body
        CHECK (body IS NULL OR char_length(body) <= 2000)
);

CREATE INDEX idx_chat_messages_cursor
    ON chat_messages (conversation_id, created_at DESC, id DESC);
CREATE INDEX idx_chat_messages_sender
    ON chat_messages (sender_id, created_at DESC);

CREATE TABLE chat_message_attachments (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('IMAGE', 'VOICE', 'FILE')),
    object_key VARCHAR(500) NOT NULL,
    original_name VARCHAR(255),
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    width INT,
    height INT,
    duration_seconds INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_attachments_message ON chat_message_attachments (message_id);

CREATE TABLE conversation_reads (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_read_at TIMESTAMPTZ NOT NULL,
    last_read_message_id UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_conversation_reads_user UNIQUE (conversation_id, user_id)
);

CREATE INDEX idx_conversation_reads_user ON conversation_reads (user_id);

-- Backfill: todo grupo existente ganha uma conversa. Os membros ativos começam
-- com o histórico já lido para não receberem um badge gigante na primeira abertura.
INSERT INTO conversations (id, type, group_id, last_message_at, created_at, updated_at)
SELECT gen_random_uuid(), 'GROUP', g.id, g.created_at, g.created_at, now()
FROM groups g;

INSERT INTO conversation_reads (id, conversation_id, user_id, last_read_at, updated_at)
SELECT gen_random_uuid(), c.id, gm.user_id, now(), now()
FROM conversations c
JOIN group_members gm ON gm.group_id = c.group_id AND gm.status = 'ACTIVE'
WHERE c.type = 'GROUP';
