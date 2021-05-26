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
    request       text
 ) AS $$

    SELECT
        job_pid,
        user_rid,
        md_status,
        message,
        job_log,
        elmfire_done,
        gridfire_done,
        request::text
    FROM match_jobs
    WHERE job_pid = _job_id

$$ LANGUAGE SQL;

-- Retrieve all match drop jobs associated with user_rid
CREATE OR REPLACE FUNCTION get_user_match_jobs(_user_rid integer)
 RETURNS TABLE (
    job_id        integer,
    user_id       integer,
    md_status     integer,
    message       text,
    job_log       text,
    elmfire_done  boolean,
    gridfire_done boolean,
    request       text
 ) AS $$

    SELECT
      job_pid,
      user_rid,
      md_status,
      message,
      job_log,
      elmfire_done,
      gridfire_done,
      request::text
    FROM match_jobs
    WHERE user_rid = _user_rid

$$ LANGUAGE SQL;

-- Inserts a new match-drop job
CREATE OR REPLACE FUNCTION add_match_job(_user_id integer)
 RETURNS integer AS $$

    INSERT INTO match_jobs
        (user_rid, updated_at)
    VALUES
        (_user_id, NOW())
    RETURNING job_pid

$$ LANGUAGE SQL;

-- Update job message
CREATE OR REPLACE FUNCTION update_match_job(
    _job_id        integer,
    _user_id       integer,
    _md_status     integer,
    _message       text,
    _job_log       text,
    _elmfire_done  boolean,
    _gridfire_done boolean
 ) RETURNS TABLE (
    job_id        integer,
    user_id       integer,
    md_status     integer,
    message       text,
    job_log       text,
    elmfire_done  boolean,
    gridfire_done boolean
 ) AS $$

    UPDATE match_jobs
    SET md_status = _md_status,
        message = _message,
        job_log = _job_log,
        elmfire_done = _elmfire_done,
        gridfire_done = _gridfire_done,
        updated_at = NOW()
    WHERE job_pid = _job_id
        AND user_rid = _user_id
    RETURNING job_pid, user_rid, md_status, message, job_log, elmfire_done, gridfire_done

$$ LANGUAGE SQL;

-- Partially update job message
CREATE OR REPLACE FUNCTION upsert_match_job(
    _job_id        integer,
    _user_id       integer,
    _md_status     integer,
    _message       text,
    _job_log       text,
    _elmfire_done  boolean,
    _gridfire_done boolean,
    _request       text
 ) RETURNS integer AS $$

    INSERT INTO match_jobs
      (job_pid, user_rid, md_status, message, job_log, elmfire_done, gridfire_done, request, updated_at)
    VALUES
      (_job_id, _user_id, _md_status, _message, _job_log, _elmfire_done, _gridfire_done, _request::jsonb, NOW())
    ON CONFLICT(job_pid) DO UPDATE
    SET
      md_status = COALESCE(excluded.md_status, match_jobs.md_status),
      message = COALESCE(excluded.message, match_jobs.message),
      job_log = COALESCE(excluded.job_log, match_jobs.job_log),
      elmfire_done = COALESCE(excluded.elmfire_done, match_jobs.elmfire_done),
      gridfire_done = COALESCE(excluded.gridfire_done, match_jobs.gridfire_done),
      request = COALESCE(_request::jsonb, match_jobs.request),
      updated_at = NOW()
    WHERE match_jobs.job_pid = _job_id AND match_jobs.user_rid = _user_id
    RETURNING job_pid

$$ LANGUAGE SQL;
