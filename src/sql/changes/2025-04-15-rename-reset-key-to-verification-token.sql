-- Rename reset_key column to verification_token for better clarity
ALTER TABLE users RENAME COLUMN reset_key TO verification_token;

-- Add token_expiration column to support time-based tokens
ALTER TABLE users ADD COLUMN token_expiration TIMESTAMP WITH TIME ZONE DEFAULT NULL;