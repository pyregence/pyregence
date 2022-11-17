-- Add the geoserver_workspace column to match_jobs
ALTER TABLE match_jobs
ADD COLUMN geoserver_workspace text;
