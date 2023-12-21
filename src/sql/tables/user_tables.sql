-- NAMESPACE: user

-- Stores text values for roles
CREATE TABLE roles (
    role_uid    integer PRIMARY KEY,
    title       text NOT NULL
);

-- Stores information about users
CREATE TABLE users (
    user_uid          SERIAL PRIMARY KEY,
    email             text NOT NULL UNIQUE,
    name              text NOT NULL,
    password          varchar(72) NOT NULL,
    settings          text,
    super_admin       boolean DEFAULT FALSE,
    verified          boolean DEFAULT FALSE,
    reset_key         text DEFAULT NULL,
    match_drop_access boolean DEFAULT FALSE,
    psps_org          text default NULL
);

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
