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
 RETURNS TABLE (user_id integer, org_id integer) AS $$

    SELECT user_uid, 1
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
 RETURNS TABLE (user_id integer, org_id integer) AS $$

    UPDATE users
    SET password = crypt(_password, gen_salt('bf')),
        verified = TRUE,
        reset_key = NULL
    WHERE email = lower_trim(_email)
        AND reset_key = _reset_key
        AND reset_key IS NOT NULL;

    SELECT user_uid, 1
    FROM users
    WHERE email = lower_trim(_email)
        AND verified = TRUE;

$$ LANGUAGE SQL;

-- Sets verified to true, if the reset key is valid
CREATE OR REPLACE FUNCTION verify_user_email(_email text, _reset_key text)
 RETURNS TABLE (user_id integer, org_id integer) AS $$

    UPDATE users
    SET verified = TRUE,
        reset_key = NULL
    WHERE email = lower_trim(_email)
        AND reset_key = _reset_key
        AND reset_key IS NOT NULL;

    SELECT user_uid, 1
    FROM users
    WHERE email = lower_trim(_email)
        AND verified = TRUE;

$$ LANGUAGE SQL;
