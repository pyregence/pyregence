-- NAMESPACE: user
-- REQUIRES: clear

--------------------------------------------------------------------------------
-- Triggers
--------------------------------------------------------------------------------
-- Demotes users if their organization is deleted
CREATE OR REPLACE FUNCTION demote_users_on_org_delete()
RETURNS TRIGGER AS $$
BEGIN
  UPDATE users
  SET
    organization_rid = NULL,
    user_role = 'member'::user_role,
    org_membership_status = 'none'::org_membership_status
  WHERE organization_rid = OLD.organization_uid;

  RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_demote_users_on_org_delete
AFTER DELETE ON organizations
FOR EACH ROW
EXECUTE FUNCTION demote_users_on_org_delete();

--------------------------------------------------------------------------------
-- Helper functions
--------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trim_space(_str text)
 RETURNS text as $$

    SELECT trim(regexp_replace(_str, '\s+', ' ', 'g'))

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION lower_trim(_str text)
 RETURNS text as $$

    SELECT lower(trim_space(_str))

$$ LANGUAGE SQL;

-- Returns user id for a given email
CREATE OR REPLACE FUNCTION get_user_id_by_email(_email text)
 RETURNS integer AS $$

    SELECT user_uid
    FROM users
    WHERE email = lower_trim(_email)

$$ LANGUAGE SQL;

-- Returns true if user information is taken, excludes user_id
CREATE OR REPLACE FUNCTION user_email_taken(_email text, _user_id_to_ignore integer)
 RETURNS boolean AS $$

    SELECT count(1) > 0
    FROM users
    WHERE user_uid != _user_id_to_ignore
        AND email = lower_trim(_email)

$$ LANGUAGE SQL;

--------------------------------------------------------------------------------
-- User Creation functions
--------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION add_new_user(
    _email    text,
    _name     text,
    _password text,
    _settings text
 ) RETURNS integer AS $$
    INSERT INTO users
        (email, name, password, settings, email_verified, user_role, org_membership_status)
    VALUES (
        lower_trim(_email),
        _name,
        crypt(_password, gen_salt('bf')),
        _settings,
        FALSE,    -- by default a user is pending email verification
        'member', -- by default a user becomes a member
        'none'    -- by default a user doesn't belong to an organization
    )
    RETURNING user_uid

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION add_org_user(
    _org_id     integer,
    _user_id    integer
) RETURNS void AS $$
    UPDATE users
    SET organization_rid = _org_id,
        user_role = 'organization_member'::user_role,
        org_membership_status = 'accepted'::org_membership_status
    WHERE user_uid = _user_id;
$$ LANGUAGE SQL;

-- TODO consider gracefully handling edge case for when multiple organizations have the same email_domains values
CREATE OR REPLACE FUNCTION auto_add_org_user(
    _user_id      integer,
    _email_domain text
) RETURNS void AS $$
    WITH matched_org AS (
        SELECT organization_uid, auto_accept
        FROM organizations
        WHERE email_domains LIKE '%' || _email_domain || '%'
        LIMIT 1
    )
    UPDATE users u
    SET organization_rid = m.organization_uid,
        user_role = CASE WHEN m.auto_accept THEN 'organization_member'::user_role ELSE 'member'::user_role END,
        org_membership_status = CASE WHEN m.auto_accept THEN 'accepted'::org_membership_status ELSE 'pending'::org_membership_status END
    FROM matched_org m
    WHERE u.user_uid = _user_id
$$ LANGUAGE SQL;

--------------------------------------------------------------------------------
-- Login functions
--------------------------------------------------------------------------------
-- Returns user info user name and password match
CREATE OR REPLACE FUNCTION verify_user_login(_email text, _password text)
 RETURNS TABLE (
   user_id               integer,
   user_email            text,
   match_drop_access     boolean,
   user_role             user_role,
   org_membership_status org_membership_status,
   organization_rid      integer
 ) AS $$

    SELECT user_uid, email, match_drop_access, user_role, org_membership_status, organization_rid
    FROM users
    WHERE email = lower_trim(_email)
        AND password = crypt(_password, password)
        AND email_verified = TRUE

$$ LANGUAGE SQL;

-- Sets a verification token for a user (used for email verification and password reset)
CREATE OR REPLACE FUNCTION set_verification_token(
  _email      text,
  _token      text,
  _expiration TIMESTAMP WITH TIME ZONE DEFAULT NULL
)
 RETURNS void AS $$

    UPDATE users
    SET verification_token = _token,
        token_expiration = _expiration
    WHERE email = lower_trim(_email)

$$ LANGUAGE SQL;

-- Sets the password for a user, if the verification token is valid
CREATE OR REPLACE FUNCTION set_user_password(
    _email    text,
    _password text,
    _token    text
)
RETURNS TABLE (
    user_id               integer,
    user_email            text,
    match_drop_access     boolean,
    user_role             user_role,
    org_membership_status org_membership_status,
    organization_rid      integer
) AS $$

    UPDATE users
    SET password = crypt(_password, gen_salt('bf')),
        email_verified = TRUE,
        verification_token = NULL,
        token_expiration = NULL
    WHERE email = lower_trim(_email)
        AND verification_token = _token
        AND verification_token IS NOT NULL
        AND (token_expiration IS NULL OR token_expiration > NOW());

    SELECT user_uid, email, match_drop_access, user_role, org_membership_status, organization_rid
    FROM users
    WHERE email = lower_trim(_email)
      AND email_verified = TRUE;

$$ LANGUAGE SQL;

-- Sets email_verified to true, if the verification token is valid
CREATE OR REPLACE FUNCTION verify_user_email(_email text, _token text)
RETURNS TABLE (
    user_id               integer,
    user_email            text,
    match_drop_access     boolean,
    user_role             user_role,
    org_membership_status org_membership_status,
    organization_rid      integer
) AS $$

    UPDATE users
    SET email_verified = TRUE,
        verification_token = NULL,
        token_expiration = NULL
    WHERE email = lower_trim(_email)
        AND verification_token = _token
        AND verification_token IS NOT NULL
        AND (token_expiration IS NULL OR token_expiration > NOW());

    SELECT user_uid, email, match_drop_access, user_role, org_membership_status, organization_rid
    FROM users
    WHERE email = lower_trim(_email)
      AND email_verified = TRUE;

$$ LANGUAGE SQL;

-- Verifies a 2FA token without changing verified status
CREATE OR REPLACE FUNCTION verify_user_2fa(_email text, _token text)
 RETURNS TABLE (
    user_id               integer,
    user_email            text,
    match_drop_access     boolean,
    user_role             user_role,
    org_membership_status org_membership_status,
    organization_rid      integer
 ) AS $$

    UPDATE users
    SET verification_token = NULL,
        token_expiration = NULL
    WHERE email = lower_trim(_email)
        AND verification_token = _token
        AND verification_token IS NOT NULL
        AND token_expiration > NOW()
        AND email_verified = TRUE
    RETURNING user_uid, email, match_drop_access, user_role, org_membership_status, organization_rid;

$$ LANGUAGE SQL;

--------------------------------------------------------------------------------
-- Misc functions
--------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION get_user_settings(_user_id integer)
 RETURNS TABLE (settings text) AS $$

    SELECT settings
    FROM users
    WHERE user_uid = _user_id

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION update_user_settings(
    _user_id     integer,
    _settings    text
 ) RETURNS void AS $$

    UPDATE users
    SET settings = _settings
    WHERE user_uid = _user_id

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION update_user_name(_user_id integer, _name text)
 RETURNS void AS $$

    UPDATE users
    SET name = _name
    WHERE user_uid = _user_id

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION update_user_match_drop_access(_user_id integer, _match_drop_access boolean)
 RETURNS void AS $$

    UPDATE users
    SET match_drop_access = _match_drop_access
    WHERE user_uid = _user_id

$$ LANGUAGE SQL;

-- Sets the given users last login date to now.
CREATE OR REPLACE FUNCTION set_users_last_login_date_to_now(_user_id integer)
  RETURNS void AS $$
  UPDATE users
  SET last_login_date = CURRENT_TIMESTAMP
  WHERE user_uid = _user_id

$$ LANGUAGE SQL;

-- Returns a table of all the users last login date and some metadata.
CREATE OR REPLACE FUNCTION get_all_users_last_login_dates()
RETURNS TABLE (
    email text,
    name text,
    organization text,
    role user_role,
    org_membership_status org_membership_status,
    last_login_date timestamptz
) AS $$
    SELECT
        u.email,
        u.name,
        o.org_name AS organization,
        u.user_role AS role,
        u.org_membership_status,
        u.last_login_date
    FROM users AS u
    LEFT JOIN organizations o
        ON u.organization_rid = o.organization_uid;
$$ LANGUAGE SQL;
