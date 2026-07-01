-- Adds match_job_uuid, an unpredictable public identifier for match jobs, so the
-- browser no longer needs the sequential match_job_uid (PYR1-1512 enumeration
-- hardening -- the integer PK stays internal). gen_random_uuid() is a volatile
-- default, so Postgres evaluates it per row and backfills existing rows with
-- distinct values.
ALTER TABLE match_jobs
    ADD COLUMN IF NOT EXISTS match_job_uuid uuid NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX IF NOT EXISTS match_jobs_match_job_uuid_key ON match_jobs (match_job_uuid);
