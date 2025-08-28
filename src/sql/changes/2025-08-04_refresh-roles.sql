-- DESCRIPTION: migrates to a single-role, single-organization model

--------------------------------------------------------------------------------
-- Step 1: Create enums for roles and status
--------------------------------------------------------------------------------
-- Roles describe capabilities
CREATE TYPE user_role AS ENUM (
    'super_admin',
    'organization_admin',
    'organization_member',
    'account_manager',
    'member'
);

-- Describes account lifecycle state
CREATE TYPE org_membership_status AS ENUM (
    'none',    -- not in any org
    'pending', -- waiting for approval
    'accepted' -- approved
);

--------------------------------------------------------------------------------
-- Step 2: Add new columns to users table
--------------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS user_role             user_role             NOT NULL DEFAULT 'member',
    ADD COLUMN IF NOT EXISTS org_membership_status org_membership_status NOT NULL DEFAULT 'none',
    ADD COLUMN IF NOT EXISTS organization_rid      INTEGER REFERENCES organizations(organization_uid);

ALTER TABLE users
    RENAME COLUMN verified TO email_verified;

--------------------------------------------------------------------------------
-- Step 3: Backfill data from organization_users using heuristics
--------------------------------------------------------------------------------
-- (1) Users in >1 orgs => super_admin, detach from orgs
WITH multi_org AS (
  SELECT user_rid
  FROM organization_users
  GROUP BY user_rid
  HAVING COUNT(DISTINCT organization_rid) > 1
)
UPDATE users u
SET user_role             = 'super_admin'::user_role,
    org_membership_status = 'none'::org_membership_status,
    organization_rid      = NULL
FROM multi_org m
WHERE u.user_uid = m.user_rid;

-- (2) Users in exactly 1 org => attach that org and set role/status
WITH one_org AS (
  SELECT user_rid
  FROM organization_users
  GROUP BY user_rid
  HAVING COUNT(DISTINCT organization_rid) = 1
),
one_org_role AS (
  -- Resolve multiple rows to the highest-privilege role within that single org
  SELECT
    ou.user_rid,
    ou.organization_rid,
    MIN(ou.role_rid) AS role_rid  -- 1 admin, 2 member, 3 pending
  FROM organization_users ou
  JOIN one_org oo ON oo.user_rid = ou.user_rid
  GROUP BY ou.user_rid, ou.organization_rid
),
multi_org AS (
  SELECT user_rid
  FROM organization_users
  GROUP BY user_rid
  HAVING COUNT(DISTINCT organization_rid) > 1
)
UPDATE users u
SET organization_rid = o.organization_rid,
    user_role = CASE o.role_rid
                  WHEN 1 THEN 'organization_admin'::user_role
                  WHEN 2 THEN 'organization_member'::user_role
                  WHEN 3 THEN 'organization_member'::user_role
                END,
    org_membership_status = CASE o.role_rid
                              WHEN 3 THEN 'pending'::org_membership_status
                              ELSE 'accepted'::org_membership_status
                            END
FROM one_org_role o
LEFT JOIN multi_org mo ON mo.user_rid = o.user_rid
WHERE mo.user_rid IS NULL
  AND u.user_uid = o.user_rid;

-- (3) Users with 0 orgs => normalize to member/none/NULL
-- (i.e., not in organization_users at all)
UPDATE users u
SET user_role             = 'member'::user_role,
    org_membership_status = 'none'::org_membership_status,
    organization_rid      = NULL
WHERE NOT EXISTS (
        SELECT 1
        FROM organization_users ou
        WHERE ou.user_rid = u.user_uid
      );

--------------------------------------------------------------------------------
-- Step 4: Drop old columns no longer needed
--------------------------------------------------------------------------------
ALTER TABLE users
    DROP COLUMN IF EXISTS super_admin,
    DROP COLUMN IF EXISTS analyst;

--------------------------------------------------------------------------------
-- Step 5: Add constraints to users table
--------------------------------------------------------------------------------
ALTER TABLE users
ADD CONSTRAINT valid_org_role_and_status
CHECK (
    -- If user is in an org, membership status must not be 'none' (it should either be 'pending' or 'accepted')
    (organization_rid IS NOT NULL AND org_membership_status != 'none')
    -- If user is not in an org, status must be 'none'
    OR (organization_rid IS NULL AND org_membership_status = 'none')
);

ALTER TABLE users
ADD CONSTRAINT valid_role_for_membership
CHECK (
  CASE
    WHEN org_membership_status = 'accepted' THEN
      user_role IN ('organization_member', 'organization_admin')
    WHEN org_membership_status = 'pending' THEN
      user_role IN ('organization_member', 'organization_admin')
    WHEN org_membership_status = 'none' THEN
      user_role IN ('super_admin' ,'account_manager', 'member')
    ELSE
      FALSE
  END
);

--------------------------------------------------------------------------------
-- Add users table trigger
--------------------------------------------------------------------------------
-- Demotes users if their organization is deleted
CREATE OR REPLACE FUNCTION demote_users_on_org_delete()
RETURNS TRIGGER AS $$
BEGIN
  UPDATE users
  SET
    organization_rid = NULL,
    user_role = 'member'::user_role,
    org_membership_status = 'none'::org_membership_status
  WHERE organization_rid = OLD.organization_uid;

  RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_demote_users_on_org_delete
AFTER DELETE ON organizations
FOR EACH ROW
EXECUTE FUNCTION demote_users_on_org_delete();

--------------------------------------------------------------------------------
-- Step 6: Drop unused tables
---------------------------------------------------------------------------
-- organization_users table is not needed anymore
-- a user's organization is now stored directly in the users.organization_id column.
DROP TABLE IF EXISTS organization_users CASCADE;

-- the roles table is not needed anymore
-- a user's role is now stored in the users.user_role enum column
DROP TABLE IF EXISTS roles CASCADE;
