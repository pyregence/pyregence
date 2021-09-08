
-- Add fire_name column to match_jobs
ALTER TABLE match_jobs
ADD COLUMN display_name varchar;

UPDATE match_jobs
SET display_name = 'Match Drop ' || job_uid;
