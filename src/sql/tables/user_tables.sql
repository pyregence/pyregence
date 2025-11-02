-- NAMESPACE: user
-- REQUIRES: organization

--------------------------------------------------------------------------------
-- Enum types
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
-- Stores information about users
--------------------------------------------------------------------------------
CREATE TABLE users (
    user_uid              SERIAL PRIMARY KEY,
    email                 text NOT NULL UNIQUE,
    name                  text NOT NULL,
    password              varchar(72) NOT NULL,
    password_set_date     TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    settings              text,
    match_drop_access     BOOLEAN DEFAULT FALSE,
    email_verified        BOOLEAN DEFAULT FALSE,
    verification_token    text DEFAULT NULL, -- used for both initial email verification and 2FA
    token_expiration      TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    last_login_date       TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    user_role             user_role NOT NULL DEFAULT 'member',
    org_membership_status org_membership_status NOT NULL DEFAULT 'none',
    organization_rid      integer references organizations(organization_uid)
);

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
-- Stores TOTP secrets with verification state
--------------------------------------------------------------------------------
CREATE TABLE user_totp (
    user_id    INTEGER PRIMARY KEY REFERENCES users(user_uid) ON DELETE CASCADE,
    secret     TEXT NOT NULL,
    verified   BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

--------------------------------------------------------------------------------
-- Stores backup codes (8-character alphanumeric)
--------------------------------------------------------------------------------
CREATE TABLE user_backup_codes (
    user_backup_code_uid SERIAL PRIMARY KEY,
    user_id              INTEGER REFERENCES users(user_uid) ON DELETE CASCADE,
    code                 TEXT NOT NULL,
    used                 BOOLEAN DEFAULT FALSE,
    used_at              TIMESTAMP WITH TIME ZONE NULL,
    created_at           TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for backup code lookups
CREATE INDEX idx_backup_codes_user ON user_backup_codes(user_id);
