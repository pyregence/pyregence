-- Emailed 2FA codes get their own columns, so mailing one leaves a pending link alone.
ALTER TABLE users ADD COLUMN emailed_code text DEFAULT NULL,
                  ADD COLUMN code_expiration TIMESTAMP WITH TIME ZONE DEFAULT NULL;

-- Codes mailed before this carry an expiry in the link column, and nothing would clear them now.
UPDATE users SET verification_token = NULL, token_expiration = NULL WHERE token_expiration IS NOT NULL;
