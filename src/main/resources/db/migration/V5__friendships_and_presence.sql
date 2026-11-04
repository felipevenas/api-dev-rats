-- Grafo social do DevRats: amizades (com solicitacoes) e presenca por usuario.
-- O par de usuarios e unico independente da direcao, por isso o indice unico usa
-- LEAST/GREATEST; o status guarda a maquina de estados da solicitacao.
-- A presenca fica em tabela separada para nao tocar users.updated_at a cada heartbeat.

CREATE TABLE friendships (
    id UUID PRIMARY KEY,
    requester_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    addressee_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    CONSTRAINT ck_friendships_not_self CHECK (requester_id <> addressee_id)
);

CREATE UNIQUE INDEX uq_friendships_pair
    ON friendships (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));

CREATE INDEX idx_friendships_requester ON friendships (requester_id, status);
CREATE INDEX idx_friendships_addressee ON friendships (addressee_id, status);

-- Ultimo heartbeat de cada usuario; o status ONLINE/AWAY/OFFLINE e calculado na aplicacao.
CREATE TABLE user_presence (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    last_seen_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_user_presence_last_seen ON user_presence (last_seen_at);

-- Busca por prefixo de username (text_pattern_ops evita depender do collation do banco).
CREATE INDEX idx_users_username_prefix ON users (username text_pattern_ops)
    WHERE status = 'ACTIVE';
