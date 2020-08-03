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
CREATE OR REPLACE FUNCTION user_email_exists(_email text, _user_id integer)
 RETURNS boolean AS $$

    SELECT EXISTS (
        SELECT 1
        FROM users
        WHERE user_uid != _user_id
            AND email = lower_trim(_email)
    )

$$ LANGUAGE SQL;

-- Inserts a new user with its info
CREATE OR REPLACE FUNCTION insert_user(
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

$$ LANGUAGE SQL;

-- This simplifies adding a new layer even though its not hooked in the UI
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
