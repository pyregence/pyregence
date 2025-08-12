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
    ADD COLUMN IF NOT EXISTS user_role user_role DEFAULT 'member' NOT NULL,
    ADD COLUMN IF NOT EXISTS org_membership_status org_membership_status DEFAULT 'none' NOT NULL,
    ADD COLUMN IF NOT EXISTS organization_rid INTEGER REFERENCES organizations(organization_uid),
    RENAME COLUMN verified TO email_verified;

--------------------------------------------------------------------------------
-- Step 3: Backfill data from organization_users and roles TODO this needs to be fixed to account for users who are part of multiple organizations
--------------------------------------------------------------------------------
-- Map org membership
UPDATE users u
SET organization_rid = ou.organization_rid
FROM organization_users ou
WHERE u.user_uid = ou.user_rid;

-- Map roles
UPDATE users u
SET user_role = CASE
    WHEN u.super_admin = TRUE THEN 'super_admin'
    WHEN ou.role_rid = 1 THEN 'organization_admin'
    WHEN ou.role_rid = 2 THEN 'organization_member'
    WHEN ou.role_rid = 3 THEN 'member' -- previously "pending" role
    ELSE 'member'
END
FROM organization_users ou
WHERE u.user_uid = ou.user_rid;

UPDATE users
SET org_membership_status = 'accepted'
WHERE organization_rid IS NOT NULL;

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
    -- Only allow elevated org roles if status is 'accepted'
    (user_role IN ('organization_admin', 'organization_member') AND org_membership_status = 'accepted')
    OR (user_role = 'member')
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
    user_role = 'member',
    org_membership_status = 'none'
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
