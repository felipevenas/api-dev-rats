-- Origem e chave de armazenamento do avatar do usuario.
-- avatar_source indica de onde a imagem veio (NONE sem avatar, GITHUB sincronizado,
-- CUSTOM enviado pelo usuario). avatar_object_key guarda a chave do objeto no storage
-- apenas quando avatar_source = 'CUSTOM'.

ALTER TABLE users ADD COLUMN avatar_source VARCHAR(16) NOT NULL DEFAULT 'NONE';
ALTER TABLE users ADD CONSTRAINT ck_users_avatar_source CHECK (avatar_source IN ('NONE', 'GITHUB', 'CUSTOM'));
ALTER TABLE users ADD COLUMN avatar_object_key VARCHAR(300);

-- Corrige o bug em que o avatar do GitHub nunca era copiado para users no momento da conexao.
-- Copia apenas para usuarios ainda sem avatar, mantendo avatares ja definidos.
UPDATE users u
SET avatar_url = ga.avatar_url,
    avatar_source = 'GITHUB'
FROM github_accounts ga
WHERE ga.user_id = u.id
  AND ga.connection_status = 'CONNECTED'
  AND ga.avatar_url IS NOT NULL
  AND u.avatar_url IS NULL
  AND u.status <> 'DELETED';
