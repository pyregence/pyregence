-- NAMESPACE: user
-- REQUIRES: clear

CREATE OR REPLACE FUNCTION trim_space(_str text)
 RETURNS text as $$

    SELECT trim(regexp_replace(_str, '\s+', ' ', 'g'))

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION lower_trim(_str text)
 RETURNS text as $$

    SELECT lower(trim_space(_str))

$$ LANGUAGE SQL;

-- Returns user info user name and password match
CREATE OR REPLACE FUNCTION verify_user_login(_email text, _password text)
 RETURNS TABLE (
   user_id integer,
   user_email text,
   match_drop_access boolean,
   super_admin boolean
 ) AS $$

    SELECT user_uid, email, match_drop_access, super_admin
    FROM users
    WHERE email = lower_trim(_email)
        AND password = crypt(_password, password)
        AND verified = TRUE

$$ LANGUAGE SQL;

-- Returns true if user information is taken, excludes user_id
CREATE OR REPLACE FUNCTION user_email_taken(_email text, _user_id_to_ignore integer)
 RETURNS boolean AS $$

    SELECT count(1) > 0
    FROM users
    WHERE user_uid != _user_id_to_ignore
        AND email = lower_trim(_email)

$$ LANGUAGE SQL;

-- Returns user id for a given email
CREATE OR REPLACE FUNCTION get_user_id_by_email(_email text)
 RETURNS integer AS $$

    SELECT user_uid
    FROM users
    WHERE email = lower_trim(_email)

$$ LANGUAGE SQL;

-- Inserts a new user with its info
CREATE OR REPLACE FUNCTION add_new_user(
    _email       text,
    _name        text,
    _password    text,
    _settings    text
 ) RETURNS integer AS $$

    INSERT INTO users
        (email, name, password, settings)
    VALUES (
        lower_trim(_email),
        _name,
        crypt(_password, gen_salt('bf')),
        _settings
    )
    RETURNING user_uid

$$ LANGUAGE SQL;

-- Sets a verification token for a user (used for email verification and password reset)
CREATE OR REPLACE FUNCTION set_verification_token(_email text, _token text, _expiration TIMESTAMP WITH TIME ZONE DEFAULT NULL)
 RETURNS void AS $$

    UPDATE users
    SET verification_token = _token,
        token_expiration = _expiration
    WHERE email = lower_trim(_email)

$$ LANGUAGE SQL;

-- Sets the password for a user, if the verification token is valid
CREATE OR REPLACE FUNCTION set_user_password(_email text, _password text, _token text)
  RETURNS TABLE (
    user_id integer,
    user_email text,
    match_drop_access boolean,
    super_admin boolean
  ) AS $$

    UPDATE users
    SET password = crypt(_password, gen_salt('bf')),
        verified = TRUE,
        verification_token = NULL,
        token_expiration = NULL
    WHERE email = lower_trim(_email)
        AND verification_token = _token
        AND verification_token IS NOT NULL
        AND (token_expiration IS NULL OR token_expiration > NOW());

    SELECT user_uid, email, match_drop_access, super_admin
    FROM users
    WHERE email = lower_trim(_email)
        AND verified = TRUE;

$$ LANGUAGE SQL;

-- Sets verified to true, if the verification token is valid
CREATE OR REPLACE FUNCTION verify_user_email(_email text, _token text)
  RETURNS TABLE (
    user_id integer,
    user_email text,
    match_drop_access boolean,
    super_admin boolean
  ) AS $$

    UPDATE users
    SET verified = TRUE,
        verification_token = NULL,
        token_expiration = NULL
    WHERE email = lower_trim(_email)
        AND verification_token = _token
        AND verification_token IS NOT NULL
        AND (token_expiration IS NULL OR token_expiration > NOW())
    RETURNING user_uid;

    SELECT user_uid, email, match_drop_access, super_admin
    FROM users
    WHERE email = lower_trim(_email)
        AND verified = TRUE;

$$ LANGUAGE SQL;

-- Verifies a 2FA token without changing verified status
CREATE OR REPLACE FUNCTION verify_user_2fa(_email text, _token text)
 RETURNS TABLE (
    user_id integer,
    user_email text,
    match_drop_access boolean,
    super_admin boolean
 ) AS $$

    WITH updated AS (
        UPDATE users
        SET verification_token = NULL,
            token_expiration = NULL
        WHERE email = lower_trim(_email)
            AND verification_token = _token
            AND verification_token IS NOT NULL
            AND token_expiration > NOW()
            AND verified = TRUE
        RETURNING user_uid, email, match_drop_access, super_admin
    )
    SELECT * FROM updated;

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

---
---  Organizations
---

-- Returns all organizations for a user that they are an admin or member of
CREATE OR REPLACE FUNCTION get_organizations(_user_id integer)
 RETURNS TABLE (
    org_id                integer,
    org_name              text,
    org_unique_id         text,
    geoserver_credentials text,
    role_id               integer,
    email_domains         text,
    auto_add              boolean,
    auto_accept           boolean
 ) AS $$

    SELECT o.organization_uid,
        o.org_name,
        o.org_unique_id,
        o.geoserver_credentials,
        ou.role_rid,
        o.email_domains,
        o.auto_add,
        o.auto_accept
    FROM organizations AS o, organization_users AS ou
    WHERE (o.organization_uid = ou.organization_rid)
        AND (ou.user_rid = _user_id)
        AND (ou.role_rid = 1 OR ou.role_rid = 2)
    ORDER BY o.org_unique_id

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION get_user_admin_access(_user_id integer)
 RETURNS boolean AS $$

    SELECT CASE WHEN COUNT(o.organization_uid) > 0 THEN TRUE ELSE FALSE END
    FROM organizations AS o, organization_users AS ou
    WHERE (o.organization_uid = ou.organization_rid)
        AND (ou.user_rid = _user_id)
        AND (ou.role_rid = 1);

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION is_user_admin_of_org(_user_id integer, _org_id integer)
 RETURNS boolean AS $$
    SELECT EXISTS (
      SELECT 1
      FROM organization_users
      WHERE user_rid = _user_id
        AND organization_rid = _org_id
        AND role_rid = 1 -- admin role
    );
$$ LANGUAGE SQL;


-- Returns all organizations that have PSPS data (denoted by presence of geoserver_credentials)
CREATE OR REPLACE FUNCTION get_psps_organizations()
RETURNS TABLE (org_unique_id text) AS $$

    SELECT org_unique_id
    FROM organizations
    WHERE geoserver_credentials IS NOT NULL;

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION update_org_info(
    _org_id           integer,
    _org_name         text,
    _email_domains    text,
    _auto_add         boolean,
    _auto_accept      boolean
 ) RETURNS void AS $$

    UPDATE organizations
    SET org_name = _org_name,
        email_domains = _email_domains,
        auto_add = _auto_add,
        auto_accept = _auto_accept
    WHERE
        organization_uid = _org_id

$$ LANGUAGE SQL;

---
---  Organization Member Users
---

CREATE OR REPLACE FUNCTION get_org_member_users(_org_id integer)
 RETURNS TABLE (
    org_user_id    integer,
    full_name      text,
    email          text,
    role_id        integer
 ) AS $$

    SELECT org_user_uid, name AS full_name, email, role_rid
    FROM users, organization_users, organizations
    WHERE organization_uid = _org_id
        AND organization_rid = organization_uid
        AND user_uid = user_rid

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION get_org_non_member_users(_org_id integer)
  RETURNS TABLE (
    user_uid    integer,
    email       text,
    name        text
  ) AS $$

  SELECT user_uid, email, name
  FROM users
  WHERE users.user_uid
  NOT IN (SELECT user_rid
          FROM organization_users
          WHERE organization_rid = _org_id)

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION add_org_user(
    _org_id     integer,
    _user_id    integer
 ) RETURNS void AS $$

    INSERT INTO organization_users
        (organization_rid, user_rid, role_rid)
    VALUES
        (_org_id, _user_id, 2)

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION auto_add_org_user(
    _user_id    integer,
    _domain     text
 ) RETURNS void AS $$

    INSERT INTO organization_users
        (organization_rid, user_rid, role_rid)
    (SELECT organization_uid,
        _user_id,
        CASE WHEN auto_accept THEN 2 ELSE 3 END
    FROM organizations
    WHERE email_domains LIKE '%' || _domain || '%'
        AND auto_add = TRUE)

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION update_org_user_role(
    _org_user_id    integer,
    _role_id        integer
 ) RETURNS void AS $$

    UPDATE organization_users
    SET role_rid = _role_id
    WHERE org_user_uid = _org_user_id

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION remove_org_user(_org_user_id integer)
 RETURNS void AS $$

    DELETE
    FROM organization_users
    WHERE org_user_uid = _org_user_id

$$ LANGUAGE SQL;

---
---  Organization Layers
---

CREATE OR REPLACE FUNCTION get_user_layers_list(_user_id integer)
 RETURNS TABLE (
    org_layer_id    integer,
    layer_path      text,
    layer_config    text
 ) AS $$

    SELECT org_layer_uid, layer_path, layer_config
    FROM organization_layers ol, organization_users ou
    WHERE ol.organization_rid = ou.organization_rid
        AND ou.user_rid = _user_id
        AND role_rid < 3

$$ LANGUAGE SQL;

-- This simplifies adding a new layer even though it's not hooked in the UI
CREATE OR REPLACE FUNCTION add_org_layer(
    _org_id          integer,
    _layer_path      text,
    _layer_config    text
 ) RETURNS void AS $$

    INSERT INTO organization_layers
        (organization_rid, layer_path, layer_config)
    VALUES
        (_org_id, _layer_path, _layer_config)

$$ LANGUAGE SQL;

-- Simplifies adding and linking a new test user to the given organization
CREATE OR REPLACE PROCEDURE add_new_org_test_user(
    _org_name    text,
    _email       text,
    _name        text,
    _password    text,
    _verified    boolean,
    _role        integer
) LANGUAGE plpgsql AS $$
DECLARE
  _user_id    integer;
  _org_id     integer;
BEGIN
    INSERT INTO users
        (email, name, password, verified, settings)
    VALUES
        (_email, _name, _password, _verified, '{:timezone :utc}') RETURNING user_uid INTO _user_id;

    SELECT organization_uid INTO _org_id FROM organizations WHERE org_name = _org_name;

    INSERT INTO organization_users
        (organization_rid, user_rid, role_rid)
    VALUES
        (_org_id, _user_id, _role);
END $$;

-- Simplifies adding and linking an existing test user to the given organization
CREATE OR REPLACE PROCEDURE add_exist_org_test_user(
    _org_name    text,
    _email       text,
    _role        integer
) LANGUAGE plpgsql AS $$
DECLARE
  _user_id    integer;
  _org_id     integer;
BEGIN
    SELECT user_uid INTO _user_id FROM users WHERE email = _email;

    SELECT organization_uid INTO _org_id FROM organizations WHERE org_name = _org_name;

    INSERT INTO organization_users
        (organization_rid, user_rid, role_rid)
    VALUES
        (_org_id, _user_id, _role);
END $$;