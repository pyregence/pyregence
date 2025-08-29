-- NAMESPACE: totp
-- REQUIRES: user

--------------------------------------------------------------------------------
-- TOTP Triggers
--------------------------------------------------------------------------------
-- Trigger function for automatic updated_at timestamp maintenance
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to user_totp table
CREATE TRIGGER user_totp_updated_at_trigger
BEFORE UPDATE ON user_totp
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

--------------------------------------------------------------------------------
-- TOTP Functions
--------------------------------------------------------------------------------
-- Create a new TOTP setup for a user
CREATE OR REPLACE FUNCTION create_totp_setup(_user_id integer, _secret text)
 RETURNS void AS $$

    INSERT INTO user_totp (user_id, secret, verified)
    VALUES (_user_id, _secret, FALSE)
    ON CONFLICT (user_id) DO UPDATE
    SET secret = EXCLUDED.secret,
        verified = FALSE

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
    user_id               integer,
    user_email            text,
    match_drop_access     boolean,
    secret                text,
    user_role             user_role,
    org_membership_status org_membership_status,
    organization_rid      integer
 ) AS $$

    SELECT u.user_uid,
           u.email,
           u.match_drop_access,
           t.secret,
           u.user_role,
           u.org_membership_status,
           u.organization_rid
    FROM user_totp t
    JOIN users u ON u.user_uid = t.user_id
    WHERE t.user_id = _user_id
        AND t.verified = TRUE
        AND u.email_verified = TRUE

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
    SET verified = TRUE
    WHERE user_id = _user_id
        AND verified = FALSE

$$ LANGUAGE SQL;

--------------------------------------------------------------------------------
---  Backup Code Functions
--------------------------------------------------------------------------------
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

--  Cleanup all TOTP-related data for a user
CREATE OR REPLACE FUNCTION cleanup_totp_data(_user_id integer)
 RETURNS void AS $$

BEGIN
    PERFORM delete_totp_setup(_user_id);
    PERFORM delete_backup_codes(_user_id);
END;

$$ LANGUAGE plpgsql;

-- Setup TOTP with backup codes
CREATE OR REPLACE FUNCTION begin_totp_setup(_user_id integer, _secret text, _backup_codes text[])
 RETURNS void AS $$

BEGIN
    PERFORM delete_backup_codes(_user_id);
    PERFORM create_totp_setup(_user_id, _secret);
    PERFORM create_backup_codes(_user_id, _backup_codes);
END;

$$ LANGUAGE plpgsql;

-- Regenerate backup codes
CREATE OR REPLACE FUNCTION regenerate_backup_codes(_user_id integer, _backup_codes text[])
 RETURNS void AS $$

BEGIN
    PERFORM delete_backup_codes(_user_id);
    PERFORM create_backup_codes(_user_id, _backup_codes);
END;

$$ LANGUAGE plpgsql;
