-- Optional profile fields for the onboarding/registration flow.
-- All columns are nullable so existing users keep working; primary_stack holds a CSV list of up to 5 items.

ALTER TABLE users
    ADD COLUMN bio VARCHAR(280),
    ADD COLUMN location VARCHAR(120),
    ADD COLUMN website_url VARCHAR(300),
    ADD COLUMN experience_level VARCHAR(20),
    ADD COLUMN primary_stack VARCHAR(200);

ALTER TABLE users
    ADD CONSTRAINT ck_users_experience_level
        CHECK (experience_level IS NULL OR experience_level IN ('JUNIOR', 'PLENO', 'SENIOR', 'ESPECIALISTA'));
