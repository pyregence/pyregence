-- Drops user_email_taken: its only caller was the /clj/user-email-taken endpoint,
-- removed as an account-enumeration vector. The users.email UNIQUE constraint
-- still enforces uniqueness on insert.
DROP FUNCTION IF EXISTS user_email_taken(text, integer);
