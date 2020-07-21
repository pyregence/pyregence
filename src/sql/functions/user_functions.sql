-- NAMESPACE: user

CREATE OR REPLACE FUNCTION pyre.trim_space(_str text)
 RETURNS text as $$

    SELECT trim(regexp_replace(_str, '\s+', ' ', 'g'))

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION pyre.lower_trim(_str text)
 RETURNS text as $$

    SELECT lower(pyre.trim_space(_str))

$$ LANGUAGE SQL;

-- Returns user info user name and password match
CREATE OR REPLACE FUNCTION pyre.verify_user_login(_email text, _password text)
 RETURNS TABLE (user_id integer, org_id integer) AS $$

    SELECT user_uid, 1
    FROM pyre.users
    WHERE email = pyre.lower_trim(_email)
        AND password = crypt(_password, password)
        AND verified = true

$$ LANGUAGE SQL;

-- Returns true if user information is taken, excludes user_id
CREATE OR REPLACE FUNCTION pyre.user_email_exists(_email text, _user_id integer)
 RETURNS boolean AS $$

    SELECT EXISTS (
        SELECT 1
        FROM pyre.users
        WHERE user_uid != _user_id
            AND email = pyre.lower_trim(_email)
    )

$$ LANGUAGE SQL;

-- Inserts a new user with its info
CREATE OR REPLACE FUNCTION pyre.insert_user(
    _email       text,
    _name        text,
    _password    text,
    _settings    text
 ) RETURNS integer AS $$

    INSERT INTO pyre.users
        (email, name, settings, password)
    VALUES (
        pyre.lower_trim(_email),
        _name,
        crypt(_password, gen_salt('bf')),
        _settings
    )

    RETURNING user_uid

$$ LANGUAGE SQL;

-- Adds a password reset key to the user
CREATE OR REPLACE FUNCTION pyre.set_reset_key(_email text, _reset_key text)
 RETURNS void AS $$

    UPDATE pyre.users
    SET reset_key = _reset_key
    WHERE email = pyre.lower_trim(_email)

$$ LANGUAGE SQL;

-- Sets the password for a user, if the reset key is valid
CREATE OR REPLACE FUNCTION pyre.set_user_password(_email text, _password text, _reset_key text)
 RETURNS TABLE (user_id integer, org_id integer) AS $$

    WITH user_ids AS (
        UPDATE pyre.users
        SET password = crypt(_password, gen_salt('bf')),
            verified = true,
            reset_key = NULL
        WHERE email = pyre.lower_trim(_email)
            AND reset_key = _reset_key
        RETURNING user_uid
    )

    SELECT user_uid, 1
    FROM pyre.users
    WHERE email = pyre.lower_trim(_email)
        AND verified = true

$$ LANGUAGE SQL;

-- Sets verified to true, if the reset key is valid
CREATE OR REPLACE FUNCTION pyre.verify_user_email(_email text, _reset_key text)
 RETURNS TABLE (user_id integer, org_id integer) AS $$

    WITH user_ids AS (
        UPDATE pyre.users
        SET verified = true,
            reset_key = NULL
        WHERE email = pyre.lower_trim(_email)
            AND reset_key = _reset_key
        RETURNING user_uid
    )

    SELECT user_uid, 1
    FROM pyre.users
    WHERE email = pyre.lower_trim(_email)
        AND verified = true

$$ LANGUAGE SQL;
