-- NAMESPACE: totp
-- REQUIRES: user

---
---  TOTP Functions
---

-- Create a new TOTP setup for a user
CREATE OR REPLACE FUNCTION create_totp_setup(_user_id integer, _secret text)
 RETURNS void AS $$

    INSERT INTO user_totp (user_id, secret, verified)
    VALUES (_user_id, _secret, FALSE)
    ON CONFLICT (user_id) DO UPDATE
    SET secret = EXCLUDED.secret,
        verified = FALSE,
        created_at = NOW(),
        updated_at = NOW()

$$ LANGUAGE SQL;

-- Delete old unverified TOTP setups (older than 3 days)
CREATE OR REPLACE FUNCTION delete_old_unverified_totp_setups(_user_id integer)
 RETURNS void AS $$

    DELETE FROM user_totp
    WHERE user_id = _user_id
        AND verified = FALSE
        AND created_at < NOW() - INTERVAL '3 days'

$$ LANGUAGE SQL;

-- Delete TOTP setup for a user
CREATE OR REPLACE FUNCTION delete_totp_setup(_user_id integer)
 RETURNS void AS $$

    DELETE FROM user_totp
    WHERE user_id = _user_id

$$ LANGUAGE SQL;

-- Get TOTP setup for a user (verified or unverified)
CREATE OR REPLACE FUNCTION get_totp_setup(_user_id integer)
 RETURNS TABLE (
    secret text,
    verified boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone
 ) AS $$

    SELECT secret, verified, created_at, updated_at
    FROM user_totp
    WHERE user_id = _user_id

$$ LANGUAGE SQL;

-- Get user with TOTP secret for authenticated session creation
CREATE OR REPLACE FUNCTION get_user_with_totp(_user_id integer)
 RETURNS TABLE (
    user_id integer,
    user_email text,
    match_drop_access boolean,
    super_admin boolean,
    secret text
 ) AS $$

    SELECT u.user_uid,
           u.email,
           u.match_drop_access,
           u.super_admin,
           t.secret
    FROM user_totp t
    JOIN users u ON u.user_uid = t.user_id
    WHERE t.user_id = _user_id
        AND t.verified = TRUE
        AND u.verified = TRUE

$$ LANGUAGE SQL;

-- Check if user has verified TOTP
CREATE OR REPLACE FUNCTION has_verified_totp(_user_id integer)
 RETURNS boolean AS $$

    SELECT count(1) > 0
    FROM user_totp
    WHERE user_id = _user_id
        AND verified = TRUE

$$ LANGUAGE SQL;

-- Mark TOTP setup as verified (activate it for use)
CREATE OR REPLACE FUNCTION mark_totp_verified(_user_id integer)
 RETURNS void AS $$

    UPDATE user_totp
    SET verified = TRUE,
        updated_at = NOW()
    WHERE user_id = _user_id
        AND verified = FALSE

$$ LANGUAGE SQL;

---
---  Backup Code Functions
---

-- Create backup codes for a user
CREATE OR REPLACE FUNCTION create_backup_codes(_user_id integer, _codes text[])
 RETURNS void AS $$

    INSERT INTO user_backup_codes (user_id, code)
    SELECT _user_id, unnest(_codes)

$$ LANGUAGE SQL;

-- Delete all backup codes for a user
CREATE OR REPLACE FUNCTION delete_backup_codes(_user_id integer)
 RETURNS void AS $$

    DELETE FROM user_backup_codes
    WHERE user_id = _user_id

$$ LANGUAGE SQL;

-- Get all backup codes for a user
CREATE OR REPLACE FUNCTION get_backup_codes(_user_id integer)
 RETURNS TABLE (
    code text,
    used boolean,
    created_at timestamp with time zone
 ) AS $$

    SELECT code, used, created_at
    FROM user_backup_codes
    WHERE user_id = _user_id
    ORDER BY created_at

$$ LANGUAGE SQL;

-- Mark a backup code as used (consume it)
CREATE OR REPLACE FUNCTION use_backup_code(_user_id integer, _code text)
 RETURNS boolean AS $$

    WITH updated AS (
        UPDATE user_backup_codes
        SET used = TRUE,
            used_at = NOW()
        WHERE user_id = _user_id
            AND UPPER(code) = UPPER(_code)
            AND used = FALSE
        RETURNING 1
    )
    SELECT count(1) > 0 FROM updated

$$ LANGUAGE SQL;
