-- NAMESPACE: match
-- REQUIRES: clear

-- Retrieve the match job based on the runway_job_uid
CREATE OR REPLACE FUNCTION get_match_job_runway(_runway_job_id text)
 RETURNS TABLE (
    match_job_id        integer,
    runway_job_id       text,
    user_id             integer,
    created_at          timestamp,
    updated_at          timestamp,
    md_status           integer,
    display_name        varchar,
    message             text,
    job_log             text,
    elmfire_done        boolean,
    gridfire_done       boolean,
    request             text,
    geoserver_workspace text
 ) AS $$

    SELECT match_job_uid,
        runway_job_uid,
        user_rid,
        created_at,
        updated_at,
        md_status,
        display_name,
        message,
        job_log,
        elmfire_done,
        gridfire_done,
        request::text,
        geoserver_workspace
    FROM match_jobs
    WHERE runway_job_uid = _runway_job_id

$$ LANGUAGE SQL;

-- Retrieve the match job based on the match_job_uid
CREATE OR REPLACE FUNCTION get_match_job(_match_job_id integer)
 RETURNS TABLE (
    match_job_id        integer,
    runway_job_id       text,
    user_id             integer,
    created_at          timestamp,
    updated_at          timestamp,
    md_status           integer,
    display_name        varchar,
    message             text,
    job_log             text,
    elmfire_done        boolean,
    gridfire_done       boolean,
    request             text,
    geoserver_workspace text
 ) AS $$

    SELECT match_job_uid,
        runway_job_uid,
        user_rid,
        created_at,
        updated_at,
        md_status,
        display_name,
        message,
        job_log,
        elmfire_done,
        gridfire_done,
        request::text,
        geoserver_workspace
    FROM match_jobs
    WHERE match_job_uid = _match_job_id

$$ LANGUAGE SQL;

-- Retrieve all match drop jobs associated with user_rid
CREATE OR REPLACE FUNCTION get_user_match_jobs(_user_id integer)
 RETURNS TABLE (
    match_job_id        integer,
    runway_job_id       text,
    user_id             integer,
    created_at          timestamp,
    updated_at          timestamp,
    md_status           integer,
    display_name        varchar,
    message             text,
    job_log             text,
    elmfire_done        boolean,
    gridfire_done       boolean,
    request             text,
    geoserver_workspace text
 ) AS $$

    SELECT match_job_uid,
        runway_job_uid,
        user_rid,
        created_at,
        updated_at,
        md_status,
        display_name,
        message,
        job_log,
        elmfire_done,
        gridfire_done,
        request::text,
        geoserver_workspace
    FROM match_jobs
    WHERE user_rid = _user_id

$$ LANGUAGE SQL;

-- Retrieve count of running match drop jobs associated with user_rid
CREATE OR REPLACE FUNCTION count_running_user_match_jobs(_user_id integer)
 RETURNS integer AS $$

    SELECT count(*)::integer
    FROM match_jobs
    WHERE user_rid = _user_id
        AND md_status = 2
        AND updated_at > now() - interval '1 hour'

$$ LANGUAGE SQL;

-- Retrieve count of all running match drop jobs
CREATE OR REPLACE FUNCTION count_all_running_match_jobs()
 RETURNS integer AS $$

    SELECT count(*)::integer
    FROM match_jobs
    WHERE md_status = 2
        AND updated_at > now() - interval '1 hour'

$$ LANGUAGE SQL;

-- Inserts a new match-drop job
CREATE OR REPLACE FUNCTION initialize_match_job(_user_id integer)
 RETURNS integer AS $$

    INSERT INTO match_jobs
        (user_rid)
    VALUES
        (_user_id)
    RETURNING match_job_uid

$$ LANGUAGE SQL;

-- Update job message
CREATE OR REPLACE FUNCTION update_match_job(
    _match_job_id        integer,
    _runway_job_id       text,
    _md_status           integer,
    _display_name        varchar,
    _message             text,
    _elmfire_done        boolean,
    _gridfire_done       boolean,
    _request             text,
    _geoserver_workspace text
 ) RETURNS void AS $$

    UPDATE match_jobs
    SET runway_job_uid = coalesce(_runway_job_id, runway_job_uid),
        md_status = coalesce(_md_status, md_status),
        display_name = coalesce(_display_name, display_name),
        message = coalesce(_message, message),
        job_log = coalesce(job_log || to_char(now(), 'YYYY-MM-DD HH:MI.SS') || ': ' || _message || '\n', job_log),
        elmfire_done = coalesce(_elmfire_done, elmfire_done),
        gridfire_done = coalesce(_gridfire_done, gridfire_done),
        request = coalesce(_request::jsonb, request),
        updated_at = now(),
        geoserver_workspace = coalesce(_geoserver_workspace, geoserver_workspace)
    WHERE match_job_uid = _match_job_id

$$ LANGUAGE SQL;

-- Retrieve the display names for user's completed match drops
CREATE OR REPLACE FUNCTION get_user_match_names(_user_rid integer)
 RETURNS TABLE (
    match_job_id    integer,
    display_name    varchar
 ) AS $$

    SELECT match_job_uid, display_name
    FROM match_jobs
    WHERE md_status = 0
        AND user_rid = _user_rid

$$ LANGUAGE SQL;

-- Delete the selected match job from the match_jobs table
CREATE OR REPLACE FUNCTION delete_match_job(_match_job_rid integer)
 RETURNS void AS $$

   DELETE
   FROM match_jobs
   WHERE match_job_uid = _match_job_rid

$$ LANGUAGE SQL;
