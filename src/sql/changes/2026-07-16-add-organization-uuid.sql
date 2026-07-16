-- Adds organization_uuid, an unpredictable public identifier for organizations, so
-- the browser no longer needs the sequential organization_uid (PYR1-1512 enumeration
-- hardening -- the integer PK stays internal). The existing org_unique_id is a
-- human-readable slug and so is predictable; it cannot serve as the public handle.
-- gen_random_uuid() is a volatile default, so Postgres evaluates it per row and
-- backfills existing rows with distinct values.
ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS organization_uuid uuid NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX IF NOT EXISTS organizations_organization_uuid_key ON organizations (organization_uuid);
