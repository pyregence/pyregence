-- NAMESPACE: match
-- REQUIRES: user

-- Stores information about organizations
CREATE TABLE match_jobs (
    match_job_uid       SERIAL PRIMARY KEY,
    runway_job_uid      text,
    user_rid            integer NOT NULL REFERENCES users (user_uid) ON DELETE CASCADE ON UPDATE CASCADE,
    created_at          timestamp DEFAULT now(),
    updated_at          timestamp DEFAULT now(),
    display_name        varchar,
    md_status           integer DEFAULT 2, -- 0: Completed, 1: Error, 2: In-Progress, 3: Primed for deletion
    message             text,
    job_log             text DEFAULT '',
    elmfire_done        boolean DEFAULT FALSE,
    gridfire_done       boolean DEFAULT FALSE,
    dps_request         jsonb,
    elmfire_request     jsonb,
    gridfire_request    jsonb,
    geosync_request     jsonb,
    geoserver_workspace text
);

CREATE INDEX CONCURRENTLY match_jobs_user_rid_index ON match_jobs (user_rid);
