-- Add TOTP authentication tables

-- TOTP secrets with verification state
CREATE TABLE user_totp (
    user_id INTEGER PRIMARY KEY REFERENCES users(user_uid) ON DELETE CASCADE,
    secret TEXT NOT NULL,
    verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Backup codes (8-character alphanumeric)
CREATE TABLE user_backup_codes (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(user_uid) ON DELETE CASCADE,
    code TEXT NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Simple index for user lookups
CREATE INDEX idx_backup_codes_user ON user_backup_codes(user_id);

-- Update trigger function to keep track of `updated_at`
CREATE OR REPLACE FUNCTION update_updated_at_column() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for TOTP table
CREATE TRIGGER user_totp_updated_at_trigger
BEFORE UPDATE ON user_totp
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
