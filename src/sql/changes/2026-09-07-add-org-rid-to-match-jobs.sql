-- Records which organization a Match Drop belongs to, so its compute can be billed
-- (PYR1-1668). ON DELETE SET NULL because organizations are hard-deletable -- there is
-- an AFTER DELETE trigger on organizations that demotes their users -- and a restricting
-- reference here would block that delete. Nullable anyway: free-tier users have no org.
ALTER TABLE match_jobs
    ADD COLUMN IF NOT EXISTS org_rid integer
    REFERENCES organizations (organization_uid) ON DELETE SET NULL;

-- Not CONCURRENTLY: triangulum runs each migration inside a transaction and Postgres
-- rejects a concurrent build there. match_jobs holds one row per Match Drop, so the
-- brief lock is cheap.
CREATE INDEX IF NOT EXISTS match_jobs_org_rid_index ON match_jobs (org_rid);
