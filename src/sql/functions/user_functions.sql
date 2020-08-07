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
 RETURNS TABLE (user_id integer) AS $$

    SELECT user_uid
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

-- Returns true if user information is taken, excludes user_id
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

-- Adds a password reset key to the user
CREATE OR REPLACE FUNCTION set_reset_key(_email text, _reset_key text)
 RETURNS void AS $$

    UPDATE users
    SET reset_key = _reset_key
    WHERE email = lower_trim(_email)

$$ LANGUAGE SQL;

-- Sets the password for a user, if the reset key is valid
CREATE OR REPLACE FUNCTION set_user_password(_email text, _password text, _reset_key text)
 RETURNS TABLE (user_id integer) AS $$

    UPDATE users
    SET password = crypt(_password, gen_salt('bf')),
        verified = TRUE,
        reset_key = NULL
    WHERE email = lower_trim(_email)
        AND reset_key = _reset_key
        AND reset_key IS NOT NULL;

    SELECT user_uid
    FROM users
    WHERE email = lower_trim(_email)
        AND verified = TRUE;

$$ LANGUAGE SQL;

-- Sets verified to true, if the reset key is valid
CREATE OR REPLACE FUNCTION verify_user_email(_email text, _reset_key text)
 RETURNS TABLE (user_id integer) AS $$

    UPDATE users
    SET verified = TRUE,
        reset_key = NULL
    WHERE email = lower_trim(_email)
        AND reset_key = _reset_key
        AND reset_key IS NOT NULL;

    SELECT user_uid
    FROM users
    WHERE email = lower_trim(_email)
        AND verified = TRUE;

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION get_user_info(_user_id integer)
 RETURNS TABLE (settings text) AS $$

    SELECT settings
    FROM users
    WHERE user_uid = _user_id

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION update_user_info(
    _user_id     integer,
    _settings    text
 ) RETURNS void AS $$

    UPDATE users
    SET settings = _settings
    WHERE user_uid = _user_id

$$ LANGUAGE SQL;

---
---  Organizations
---

CREATE OR REPLACE FUNCTION get_org_list(_user_id integer)
 RETURNS TABLE (
    org_id           integer,
    org_name         text,
    email_domains    text,
    auto_add         boolean,
    auto_accept      boolean
 ) AS $$

    SELECT organization_uid,
        org_name,
        email_domains,
        auto_add,
        auto_accept
    FROM organizations, organization_users
    WHERE organization_uid = organization_rid
        AND user_rid = _user_id
        AND role_rid = 1
    ORDER BY org_name

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
---  Organization Users
---

CREATE OR REPLACE FUNCTION get_org_users_list(_org_id integer)
 RETURNS TABLE (
    org_user_id    integer,
    name           text,
    email          text,
    role_id        integer
 ) AS $$

    SELECT org_user_uid, name, email, role_rid
    FROM users, organization_users, organizations
    WHERE organization_uid = _org_id
        AND organization_rid = organization_uid
        AND user_uid = user_rid

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
