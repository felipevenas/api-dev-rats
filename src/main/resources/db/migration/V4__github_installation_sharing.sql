-- Uma instalação de organização pode ser visível para várias contas DevRats.
-- A unicidade passa a ser por conta + instalação, e a instalação sozinha vira índice de busca.

ALTER TABLE github_installations
    DROP CONSTRAINT IF EXISTS uq_github_installations_installation;

ALTER TABLE github_installations
    ADD CONSTRAINT uq_github_installations_account_installation
        UNIQUE (github_account_id, installation_id);

CREATE INDEX idx_github_installations_installation ON github_installations (installation_id);
