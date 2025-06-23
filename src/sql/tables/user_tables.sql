-- NAMESPACE: user

-- Stores text values for roles
CREATE TABLE roles (
    role_uid    integer PRIMARY KEY,
    title       text NOT NULL
);

-- Stores information about users
CREATE TABLE users (
    user_uid           SERIAL PRIMARY KEY,
    email              text NOT NULL UNIQUE,
    name               text NOT NULL,
    password           varchar(72) NOT NULL,
    settings           text,
    super_admin        boolean DEFAULT FALSE,
    verified           boolean DEFAULT FALSE,
    verification_token text DEFAULT NULL,
    token_expiration   TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    match_drop_access  boolean DEFAULT FALSE
);

-- Stores TOTP secrets with verification state
CREATE TABLE user_totp (
    user_id INTEGER PRIMARY KEY REFERENCES users(user_uid) ON DELETE CASCADE,
    secret TEXT NOT NULL,
    verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Stores backup codes (8-character alphanumeric)
CREATE TABLE user_backup_codes (
    user_backup_code_uid SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(user_uid) ON DELETE CASCADE,
    code TEXT NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for backup code lookups
CREATE INDEX idx_backup_codes_user ON user_backup_codes(user_id);

-- Stores information about organizations
CREATE TABLE organizations (
    organization_uid      SERIAL PRIMARY KEY,
    org_name              text NOT NULL,
    org_unique_id         text NOT NULL UNIQUE,
    geoserver_credentials text,
    email_domains         text,
    auto_add              boolean,
    auto_accept           boolean,
    archived              boolean DEFAULT FALSE,
    created_date          date DEFAULT NOW(),
    archived_date         date
);

-- Creates a relationship between users and organizations
-- organizations -> many organization_users <- users
CREATE TABLE organization_users (
    org_user_uid        SERIAL PRIMARY KEY,
    organization_rid    integer NOT NULL REFERENCES organizations (organization_uid) ON DELETE CASCADE ON UPDATE CASCADE,
    user_rid            integer NOT NULL REFERENCES users (user_uid) ON DELETE CASCADE ON UPDATE CASCADE,
    role_rid            integer NOT NULL REFERENCES roles (role_uid),
    CONSTRAINT per_organization_per_user UNIQUE(organization_rid, user_rid)
);

-- Stores information about layers available to an organization
CREATE TABLE organization_layers (
    org_layer_uid       SERIAL PRIMARY KEY,
    organization_rid    integer NOT NULL REFERENCES organizations (organization_uid) ON DELETE CASCADE ON UPDATE CASCADE,
    layer_path          text,
    layer_config        text
);

-- Update trigger function to keep track of `updated_at`
CREATE OR REPLACE FUNCTION update_updated_at_column() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for TOTP table
CREATE TRIGGER user_totp_updated_at_trigger
BEFORE UPDATE ON user_totp
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
