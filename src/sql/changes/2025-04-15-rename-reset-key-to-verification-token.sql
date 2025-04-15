-- Rename reset_key column to verification_token for better clarity
ALTER TABLE users RENAME COLUMN reset_key TO verification_token;