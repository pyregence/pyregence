-- NAMESPACE: match
-- REQUIRES: clear

-- Retrieve match job with job_id
CREATE OR REPLACE FUNCTION get_match_job(_job_id integer)
 RETURNS TABLE (
    job_id        integer,
    user_id       integer,
    md_status     integer,
    message       text,
    job_log       text,
    elmfire_done  boolean,
    gridfire_done boolean,
    request       jsonb
 ) AS $$

    SELECT job_uid,
        user_rid,
        md_status,
        message,
        job_log,
        elmfire_done,
        gridfire_done,
        request
    FROM match_jobs
    WHERE job_uid = _job_id

$$ LANGUAGE SQL;

-- Retrieve all match drop jobs associated with user_rid
CREATE OR REPLACE FUNCTION get_user_match_jobs(_user_id integer)
 RETURNS TABLE (
    job_id        integer,
    user_id       integer,
    md_status     integer,
    message       text,
    job_log       text,
    elmfire_done  boolean,
    gridfire_done boolean,
    request       jsonb
 ) AS $$

    SELECT job_uid,
        user_rid,
        md_status,
        message,
        job_log,
        elmfire_done,
        gridfire_done,
        request
    FROM match_jobs
    WHERE user_rid = _user_id

$$ LANGUAGE SQL;

-- Inserts a new match-drop job
CREATE OR REPLACE FUNCTION initialize_match_job(_user_id integer)
 RETURNS integer AS $$

    INSERT INTO match_jobs
        (user_rid, updated_at)
    VALUES
        (_user_id, now())
    RETURNING job_uid

$$ LANGUAGE SQL;

-- Update job message
CREATE OR REPLACE FUNCTION update_match_job(
    _job_id        integer,
    _md_status     integer,
    _message       text,
    _elmfire_done  boolean,
    _gridfire_done boolean,
    _request       jsonb
 ) RETURNS void AS $$

    UPDATE match_jobs
    SET md_status = COALESCE(_md_status, md_status),
        message = COALESCE(_message, message),
        job_log = COALESCE(job_log || now() || ': ' || _message || '\n', job_log),
        elmfire_done = COALESCE(_elmfire_done, elmfire_done),
        gridfire_done = COALESCE(_gridfire_done, gridfire_done),
        request = COALESCE(_request, request),
        updated_at = NOW()
    WHERE job_uid = _job_id

$$ LANGUAGE SQL;
