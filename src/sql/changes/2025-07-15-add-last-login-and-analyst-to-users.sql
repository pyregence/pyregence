ALTER TABLE users
  ADD COLUMN IF NOT EXISTS last_login_date timestamptz,
  ADD COLUMN IF NOT EXISTS analyst boolean DEFAULT FALSE;
