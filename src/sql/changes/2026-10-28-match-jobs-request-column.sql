-- PYR1-1317: one request column per match job instead of one per model.
-- Only dps_request ever held data; elmfire_request and geosync_request were
-- written as '{}' and gridfire_request/gridfire_done are left from GridFire.
ALTER TABLE match_jobs ADD COLUMN request jsonb;

-- Delete reads the GeoSync host and port from the request, so copy it over.
UPDATE match_jobs SET request = dps_request;

ALTER TABLE match_jobs
    DROP COLUMN dps_request,
    DROP COLUMN elmfire_request,
    DROP COLUMN gridfire_request,
    DROP COLUMN geosync_request,
    DROP COLUMN gridfire_done;

ALTER TABLE match_jobs ADD COLUMN pyretechnics_done boolean DEFAULT FALSE;

-- Reload the functions after this (`bb functions`): the job readers and
-- update_match_job change signature, and `clear_functions.sql` drops the old ones first.
