-- Add the elmfire_request, gridfire_request, and geosync_request columns to match_jobs
ALTER TABLE match_jobs
ADD COLUMN elmfire_request jsonb,
ADD COLUMN gridfire_request jsonb,
ADD COLUMN geosync_request jsonb;

-- Rename the request column to dps_request
ALTER TABLE match_jobs
RENAME COLUMN request TO dps_request;
