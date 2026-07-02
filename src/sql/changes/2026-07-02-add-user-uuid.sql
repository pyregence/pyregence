-- Adds user_uuid, an unpredictable public identifier for users, so the browser no
-- longer needs the sequential user_uid (PYR1-1512 enumeration hardening -- the
-- integer PK stays internal). gen_random_uuid() is a volatile default, so Postgres
-- evaluates it per row and backfills existing rows with distinct values.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS user_uuid uuid NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX IF NOT EXISTS users_user_uuid_key ON users (user_uuid);
