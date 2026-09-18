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

-- Returns user's settings for a given email
CREATE OR REPLACE FUNCTION get_user_settings_by_email(_email text)
RETURNS text AS $$

    SELECT settings
    FROM users
    WHERE email = lower_trim(_email)

$$ LANGUAGE SQL;

-- Returns user id for a given email
CREATE OR REPLACE FUNCTION get_user_id_by_email(_email text)
 RETURNS integer AS $$

    SELECT user_uid
    FROM users
    WHERE email = lower_trim(_email)

$$ LANGUAGE SQL;

-- Returns user's name for a given email
CREATE OR REPLACE FUNCTION get_user_name_by_email(_email text)
RETURNS text AS $$

    SELECT name
    FROM users
    WHERE email = lower_trim(_email)

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
        FALSE,                        -- by default a user is pending email verification
        'member'::user_role,          -- by default a user becomes a member
        'none'::org_membership_status -- by default a user doesn't belong to an organization
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
        WHERE auto_add = TRUE
          AND email_domains LIKE '%' || _email_domain || '%'
        LIMIT 1
    )
    UPDATE users u
    SET organization_rid = m.organization_uid,
        user_role = 'organization_member'::user_role,
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
   user_name             text,
   user_email            text,
   match_drop_access     boolean,
   user_role             user_role,
   org_membership_status org_membership_status,
   organization_rid      integer,
   subscription_tier     subscription_tier,
   marketplace_status    marketplace_status
   ) AS $$

    SELECT
      u.user_uid              AS user_id,
      u.name                  AS user_name,
      u.email                 AS user_email,
      u.match_drop_access     AS match_drop_access,
      u.user_role             AS user_role,
      u.org_membership_status AS org_membership_status,
      u.organization_rid      AS organization_rid,
      COALESCE(o.subscription_tier, 'tier1_free_registered'::subscription_tier) AS subscription_tier,
      COALESCE(o.marketplace_status, 'none'::marketplace_status) AS marketplace_status
    FROM users u
    LEFT JOIN organizations o
      ON o.organization_uid = u.organization_rid
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
    user_name             text,
    match_drop_access     boolean,
    user_role             user_role,
    org_membership_status org_membership_status,
    organization_rid      integer,
    subscription_tier     subscription_tier,
    marketplace_status    marketplace_status
) AS $$
  WITH updated AS (
    UPDATE users
    SET password = crypt(_password, gen_salt('bf')),
        email_verified = TRUE,
        verification_token = NULL,
        token_expiration = NULL,
        password_set_date = NOW()
    WHERE email = lower_trim(_email)
        AND verification_token = _token
        AND verification_token IS NOT NULL
        AND (token_expiration IS NULL OR token_expiration > NOW())
    RETURNING user_uid, email, name, match_drop_access, user_role, org_membership_status, organization_rid
  )
  SELECT
    u.user_uid            AS user_id,
    u.email               AS user_email,
    u.name                AS user_name,
    u.match_drop_access   AS match_drop_access,
    u.user_role           AS user_role,
    u.org_membership_status,
    u.organization_rid,
    COALESCE(o.subscription_tier, 'tier1_free_registered'::subscription_tier) AS subscription_tier,
    COALESCE(o.marketplace_status, 'none'::marketplace_status) AS marketplace_status
  FROM updated u
  LEFT JOIN organizations o
    ON o.organization_uid = u.organization_rid;
$$ LANGUAGE SQL;

-- Sets email_verified to true, if the verification token is valid
CREATE OR REPLACE FUNCTION verify_user_email(_email text, _token text)
 RETURNS TABLE (
    user_id               integer,
    user_email            text,
    user_name             text,
    match_drop_access     boolean,
    user_role             user_role,
    org_membership_status org_membership_status,
    organization_rid      integer,
    subscription_tier     subscription_tier,
    marketplace_status    marketplace_status
) AS $$
  WITH updated AS (
    UPDATE users
    SET email_verified = TRUE,
        verification_token = NULL,
        token_expiration = NULL
    WHERE email = lower_trim(_email)
        AND verification_token = _token
        AND verification_token IS NOT NULL
        AND (token_expiration IS NULL OR token_expiration > NOW())
    RETURNING user_uid, email, name, match_drop_access, user_role, org_membership_status, organization_rid
  )
  SELECT
    u.user_uid            AS user_id,
    u.email               AS user_email,
    u.name                AS user_name,
    u.match_drop_access   AS match_drop_access,
    u.user_role           AS user_role,
    u.org_membership_status,
    u.organization_rid,
    COALESCE(o.subscription_tier, 'tier1_free_registered'::subscription_tier) AS subscription_tier,
    COALESCE(o.marketplace_status, 'none'::marketplace_status) AS marketplace_status
  FROM updated u
  LEFT JOIN organizations o
    ON o.organization_uid = u.organization_rid;
$$ LANGUAGE SQL;

-- Verifies a 2FA token without changing verified status
CREATE OR REPLACE FUNCTION verify_user_2fa(_email text, _token text)
 RETURNS TABLE (
    user_id               integer,
    user_email            text,
    user_name             text,
    match_drop_access     boolean,
    user_role             user_role,
    org_membership_status org_membership_status,
    organization_rid      integer,
    subscription_tier     subscription_tier,
    marketplace_status    marketplace_status
) AS $$
  WITH updated AS (
    UPDATE users
    SET verification_token = NULL,
        token_expiration = NULL
    WHERE email = lower_trim(_email)
        AND verification_token = _token
        AND verification_token IS NOT NULL
        AND token_expiration > NOW()
        AND email_verified = TRUE
    RETURNING user_uid, email, name, match_drop_access, user_role, org_membership_status, organization_rid
  )
  SELECT
    u.user_uid            AS user_id,
    u.email               AS user_email,
    u.name                AS user_name,
    u.match_drop_access   AS match_drop_access,
    u.user_role           AS user_role,
    u.org_membership_status,
    u.organization_rid,
    COALESCE(o.subscription_tier, 'tier1_free_registered'::subscription_tier) AS subscription_tier,
    COALESCE(o.marketplace_status, 'none'::marketplace_status) AS marketplace_status
  FROM updated u
  LEFT JOIN organizations o
    ON o.organization_uid = u.organization_rid;
$$ LANGUAGE SQL;

--------------------------------------------------------------------------------
-- Misc functions
--------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION get_all_users()
 RETURNS TABLE (
  email                 text,
  name                  text,
  settings              text,
  match_drop_access     boolean,
  email_verified        boolean,
  last_login_date       timestamptz,
  user_role             user_role,
  org_membership_status org_membership_status,
  organization_name     text
 ) AS $$
    -- The sequential user_uid PK is intentionally not selected: nothing needs it
    -- client-side (user actions key off email) and it must not reach the browser
    -- (PYR1-1512 enumeration hardening).
    SELECT
      u.email,
      u.name,
      u.settings,
      u.match_drop_access,
      u.email_verified,
      u.last_login_date,
      u.user_role,
      u.org_membership_status,
      o.org_name
    FROM users u
    LEFT JOIN organizations o
      ON u.organization_rid = o.organization_uid

$$ LANGUAGE SQL;

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

CREATE OR REPLACE FUNCTION update_user_name(
    _user_id integer,
    _name    text
 ) RETURNS void AS $$

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

CREATE OR REPLACE FUNCTION update_users_roles_by_email(_requesting_user_id integer, _user_role text, _org_name text, _users_to_be_updated text[])
RETURNS void AS $$
BEGIN
  -- if user isn't an org admin, account mananger or super_admin then fail fast
  IF NOT EXISTS (
    SELECT 1
    FROM users
    WHERE user_uid = _requesting_user_id
      AND user_role IN ('super_admin', 'account_manager', 'organization_admin')
  ) THEN
    RAISE EXCEPTION 'User % isnt an admin', _requesting_user_id;
  END IF;

  -- if requester is an org admin, require same org for all target users
  IF EXISTS (
    SELECT 1
    FROM users
    WHERE user_uid = _requesting_user_id
      AND user_role = 'organization_admin'
  ) THEN
    IF NOT (
      (SELECT organization_rid
       FROM users
       WHERE user_uid = _requesting_user_id)
      = ALL (
          SELECT organization_rid
          FROM users
          WHERE email = ANY(_users_to_be_updated)
        )
    ) THEN
      RAISE EXCEPTION 'User % isnt an Organization Admin of some of these users.', _requesting_user_id;
    END IF;
  END IF;

  UPDATE users
  SET user_role = _user_role::user_role,
      organization_rid      = CASE WHEN _user_role IN ('organization_member', 'organization_admin') THEN (SELECT organization_uid FROM organizations o WHERE o.org_name = _org_name) ELSE NULL END,
      org_membership_status = CASE WHEN _user_role IN ('organization_member', 'organization_admin') THEN 'pending'::org_membership_status ELSE 'none'::org_membership_status END
  WHERE email = ANY(_users_to_be_updated);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_users_status_by_email(
  _requesting_user_id integer,
  _status text,
  _org_name text,
  _users_to_be_updated text[]
)
RETURNS void
AS $$
BEGIN
  -- if user isn't an org admin, account mananger or super_admin then fail fast
  IF NOT EXISTS (
    SELECT 1
    FROM users
    WHERE user_uid = _requesting_user_id
      AND user_role IN ('super_admin', 'account_manager', 'organization_admin')
  ) THEN
    RAISE EXCEPTION 'User % isnt an admin', _requesting_user_id;
  END IF;

  -- if requester is an org admin, require same org for all target users
  IF EXISTS (
    SELECT 1
    FROM users
    WHERE user_uid = _requesting_user_id
      AND user_role = 'organization_admin'
  ) THEN
    IF NOT (
      (SELECT organization_rid
       FROM users
       WHERE user_uid = _requesting_user_id)
      = ALL (
          SELECT organization_rid
          FROM users
          WHERE email = ANY(_users_to_be_updated)
        )
    ) THEN
      RAISE EXCEPTION 'User % isnt an Organization Admin of some of these users.', _requesting_user_id;
    END IF;
  END IF;

  -- update runs after checks pass
  UPDATE users
  SET org_membership_status = _status::org_membership_status,
      organization_rid      = CASE
                                WHEN _status IN ('pending', 'accepted')
                                THEN (SELECT organization_uid
                                      FROM organizations o
                                      WHERE o.org_name = _org_name)
                                ELSE NULL
                              END,
      user_role             = CASE
                                WHEN _status IN ('pending', 'accepted')
                                THEN
                                  CASE
                                    WHEN user_role IN ('organization_admin', 'organization_member')
                                    THEN user_role
                                    ELSE 'organization_member'::user_role
                                END
                                ELSE 'member'::user_role
                              END
  WHERE email = ANY(_users_to_be_updated);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION delete_users(_user_email text, _users_to_be_removed text[])
RETURNS boolean
LANGUAGE plpgsql
AS $$
BEGIN
  IF EXISTS(SELECT 1 FROM users WHERE _user_email = email AND user_role = 'super_admin' FOR SHARE)
  THEN
    DELETE FROM users where email = ANY(_users_to_be_removed);
    RETURN true;
  ELSE
    RETURN false;
  END IF;
END;
$$;

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

  -- Returns the date the password was last reset.
CREATE OR REPLACE FUNCTION get_password_set_date(_user_id integer)
RETURNS timestamptz AS $$
    SELECT password_set_date
    FROM users
    WHERE user_uid = _user_id;
$$ LANGUAGE SQL;

-- Mark all of a user's sessions invalidated as of _ts (epoch ms).
CREATE OR REPLACE FUNCTION set_user_session_invalidated_at(_user_id integer, _ts bigint)
RETURNS void AS $$
    UPDATE users
    SET session_invalidated_at = _ts
    WHERE user_uid = _user_id;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION get_user_session_invalidated_at(_user_id integer)
RETURNS bigint AS $$
    SELECT session_invalidated_at
    FROM users
    WHERE user_uid = _user_id;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION get_active_user_session(_user_id integer)
RETURNS TABLE (
    session_generation uuid,
    device_id uuid,
    session_epoch bigint,
    created_at bigint,
    last_active_at bigint,
    revoked_at bigint
) AS $$
    SELECT s.session_generation, s.device_id, s.session_epoch,
           s.created_at, s.last_active_at, s.revoked_at
      FROM active_user_sessions s
     WHERE s.user_rid = _user_id;
$$ LANGUAGE SQL;

-- Start a session after either an uncontested login or an explicit takeover.
-- The registry and legacy cutoff move atomically.
CREATE OR REPLACE FUNCTION begin_active_user_session(
    _user_id integer,
    _observed_generation text,
    _observed_epoch bigint,
    _generation text,
    _device_id text,
    _now bigint)
RETURNS TABLE (
    user_rid integer,
    session_generation uuid,
    device_id uuid,
    session_epoch bigint,
    created_at bigint,
    last_active_at bigint,
    revoked_at bigint
) AS $$
    WITH started AS (
        INSERT INTO active_user_sessions AS current_session
                    (user_rid, session_generation, device_id, session_epoch,
                     created_at, last_active_at, revoked_at)
             VALUES (_user_id, _generation::uuid, _device_id::uuid, 1,
                     _now + 1, _now + 1, NULL)
        ON CONFLICT (user_rid) DO UPDATE
                SET session_generation = EXCLUDED.session_generation,
                    device_id          = EXCLUDED.device_id,
                    session_epoch      = current_session.session_epoch + 1,
                    created_at         = EXCLUDED.created_at,
                    last_active_at     = EXCLUDED.last_active_at,
                    revoked_at         = NULL
              WHERE _observed_generation IS NOT NULL
                AND current_session.session_generation = _observed_generation::uuid
                AND current_session.session_epoch = _observed_epoch
        RETURNING current_session.*
    ), advanced_cutoff AS (
        UPDATE users
           SET session_invalidated_at = _now
         WHERE user_uid = _user_id
           AND EXISTS (SELECT 1 FROM started)
    )
    SELECT started.user_rid, started.session_generation, started.device_id,
           started.session_epoch, started.created_at, started.last_active_at,
           started.revoked_at
      FROM started;
$$ LANGUAGE SQL;

-- Confirm the exact account session named by the prompt. A delayed confirmation
-- cannot evict an unseen successor.
CREATE OR REPLACE FUNCTION replace_active_user_session(
    _user_id integer,
    _observed_generation text,
    _observed_epoch bigint,
    _new_generation text,
    _device_id text,
    _now bigint)
RETURNS TABLE (
    user_rid integer,
    session_generation uuid,
    device_id uuid,
    session_epoch bigint,
    created_at bigint,
    last_active_at bigint,
    revoked_at bigint
) AS $$
    WITH replaced AS (
        UPDATE active_user_sessions
           SET session_generation = _new_generation::uuid,
               device_id = _device_id::uuid,
               session_epoch = session_epoch + 1,
               created_at = _now + 1,
               last_active_at = _now + 1,
               revoked_at = NULL
         WHERE user_rid = _user_id
           AND session_generation = _observed_generation::uuid
           AND session_epoch = _observed_epoch
           AND revoked_at IS NULL
        RETURNING active_user_sessions.*
    ), advanced_cutoff AS (
        UPDATE users
           SET session_invalidated_at = _now
         WHERE user_uid = _user_id
           AND EXISTS (SELECT 1 FROM replaced)
    )
    SELECT replaced.user_rid, replaced.session_generation, replaced.device_id,
           replaced.session_epoch, replaced.created_at, replaced.last_active_at,
           replaced.revoked_at
      FROM replaced;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION active_device_session_matches(
    _user_id integer, _generation text, _device_id text)
RETURNS boolean AS $$
    SELECT EXISTS (
        SELECT 1 FROM active_user_sessions
         WHERE user_rid = _user_id
           AND session_generation = _generation::uuid
           AND device_id = _device_id::uuid
           AND revoked_at IS NULL);
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION note_active_device_activity(
    _user_id integer, _generation text, _device_id text, _now bigint)
RETURNS bigint AS $$
    WITH touched AS (
        UPDATE active_user_sessions
           SET last_active_at = GREATEST(last_active_at, _now)
         WHERE user_rid = _user_id
           AND session_generation = _generation::uuid
           AND device_id = _device_id::uuid
           AND revoked_at IS NULL
        RETURNING last_active_at)
    SELECT last_active_at FROM touched;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION revoke_active_device_session(
    _user_id integer, _generation text, _device_id text, _now bigint)
RETURNS boolean AS $$
DECLARE revoked_count integer;
BEGIN
    UPDATE active_user_sessions SET revoked_at = _now
     WHERE user_rid = _user_id
       AND session_generation = _generation::uuid
       AND device_id = _device_id::uuid
       AND revoked_at IS NULL;
    GET DIAGNOSTICS revoked_count = ROW_COUNT;
    IF revoked_count = 1 THEN
        UPDATE users SET session_invalidated_at = _now WHERE user_uid = _user_id;
    END IF;
    RETURN revoked_count = 1;
END;
$$ LANGUAGE plpgsql;

-- An idle decision made by one tab may arrive after a sibling tab reported
-- activity. Revoke only the exact activity version the idle tab observed.
CREATE OR REPLACE FUNCTION revoke_idle_device_session(
    _user_id integer, _generation text, _device_id text,
    _expected_last_active bigint, _now bigint)
RETURNS boolean AS $$
DECLARE revoked_count integer;
BEGIN
    UPDATE active_user_sessions SET revoked_at = _now
     WHERE user_rid = _user_id
       AND session_generation = _generation::uuid
       AND device_id = _device_id::uuid
       AND last_active_at = _expected_last_active
       AND revoked_at IS NULL;
    GET DIAGNOSTICS revoked_count = ROW_COUNT;
    IF revoked_count = 1 THEN
        UPDATE users SET session_invalidated_at = _now WHERE user_uid = _user_id;
    END IF;
    RETURN revoked_count = 1;
END;
$$ LANGUAGE plpgsql;

--------------------------------------------------------------------------------
-- Marketplace Provisioning
--------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION get_user_with_org_info(_email text)
RETURNS TABLE (
    user_id                integer,
    user_name              text,
    user_email             text,
    organization_rid       integer,
    procurement_account_id text,
    google_user_identity   text
) AS $$

    SELECT user_uid, name, email, organization_rid, procurement_account_id, google_user_identity
    FROM users
    WHERE email = lower_trim(_email)

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION link_user_to_marketplace_org(
    _user_id                integer,
    _org_id                 integer,
    _procurement_account_id text,
    _google_user_identity   text
) RETURNS void AS $$

    UPDATE users
    SET organization_rid       = _org_id,
        user_role              = 'organization_admin'::user_role,
        org_membership_status  = 'accepted'::org_membership_status,
        procurement_account_id = _procurement_account_id,
        google_user_identity   = _google_user_identity
    WHERE user_uid = _user_id

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION link_existing_user_to_marketplace(
    _user_id                integer,
    _procurement_account_id text,
    _google_user_identity   text
) RETURNS void AS $$

    UPDATE users
    SET procurement_account_id = _procurement_account_id,
        google_user_identity   = _google_user_identity
    WHERE user_uid = _user_id

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION marketplace_gaia_user_exists(_google_user_identity text)
RETURNS boolean AS $$

    SELECT EXISTS(
        SELECT 1 FROM users
        WHERE google_user_identity = _google_user_identity
          AND email_verified = TRUE
    )

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION get_user_by_gaia_identity(_google_user_identity text)
RETURNS TABLE (
    user_id               integer,
    user_name             text,
    user_email            text,
    match_drop_access     boolean,
    user_role             user_role,
    org_membership_status org_membership_status,
    organization_rid      integer,
    subscription_tier     subscription_tier,
    marketplace_status    marketplace_status
) AS $$

    SELECT
      u.user_uid              AS user_id,
      u.name                  AS user_name,
      u.email                 AS user_email,
      u.match_drop_access     AS match_drop_access,
      u.user_role             AS user_role,
      u.org_membership_status AS org_membership_status,
      u.organization_rid      AS organization_rid,
      COALESCE(o.subscription_tier, 'tier1_free_registered'::subscription_tier)  AS subscription_tier,
      COALESCE(o.marketplace_status, 'none'::marketplace_status)                 AS marketplace_status
    FROM users u
    LEFT JOIN organizations o ON o.organization_uid = u.organization_rid
    WHERE u.google_user_identity = _google_user_identity
      AND u.email_verified = TRUE

$$ LANGUAGE SQL;
