-- Existing users have no created or updated times, but moving forward new users will.

-- Existing
ALTER TABLE users
  ADD created_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
  ADD updated_at TIMESTAMP WITH TIME ZONE DEFAULT NULL;

-- New
ALTER TABLE users
  ALTER created_at SET DEFAULT NOW(),
  ALTER updated_at SET DEFAULT NOW();
