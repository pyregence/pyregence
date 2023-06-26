-- Add the runway_job_uid column to match_jobs
ALTER TABLE match_jobs
ADD COLUMN runway_job_uid text;

-- Rename the job_uid column to match_job_uid
ALTER TABLE match_jobs
RENAME COLUMN job_uid TO match_job_uid;
