-- NAMESPACE: match
-- REQUIRES: user

-- Stores information about organizations
CREATE TABLE match_jobs (
    job_uid        SERIAL PRIMARY KEY,
    user_rid       integer NOT NULL REFERENCES users (user_uid) ON DELETE CASCADE ON UPDATE CASCADE,
    created_at     timestamp DEFAULT now(),
    updated_at     timestamp DEFAULT now(),
    md_status      integer DEFAULT 2, -- 0: Completed, 1: Error, 2: In-Progress
    message        text,
    job_log        text DEFAULT '',
    elmfire_done   boolean DEFAULT FALSE,
    gridfire_done  boolean DEFAULT FALSE,
    request        jsonb
);

CREATE INDEX CONCURRENTLY match_jobs_user_rid_index ON match_jobs (user_rid);
