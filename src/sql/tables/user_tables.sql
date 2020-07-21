-- NAMESPACE: user

-- Stores information about users
CREATE TABLE pyre.users (
    user_uid       SERIAL PRIMARY KEY,
    email          text NOT NULL UNIQUE,
    name           text NOT NULL,
    password       varchar(72) NOT NULL,
    settings       text,
    super_admin    boolean DEFAULT FALSE,
    verified       boolean DEFAULT false,
    reset_key      text DEFAULT NULL
);

-- Stores information about organizations
CREATE TABLE pyre.organizations (
    organization_uid    SERIAL PRIMARY KEY,
    org_name            text NOT NULL,
    email_domain        text NOT NULL,
    archived            boolean DEFAULT FALSE,
    created_date        date DEFAULT NOW(),
    archived_date       date
);

-- Stores text values for roles
CREATE TABLE pyre.roles (
    role_uid    SERIAL PRIMARY KEY,
    title       text NOT NULL
);

-- Creates a relationship between users and organizations
-- organizations -> many organization_users <- users
CREATE TABLE pyre.organization_users (
    org_user_uid       SERIAL PRIMARY KEY,
    organization_rid    integer NOT NULL REFERENCES pyre.organizations (organization_uid) ON DELETE CASCADE ON UPDATE CASCADE,
    user_rid            integer NOT NULL REFERENCES pyre.users (user_uid) ON DELETE CASCADE ON UPDATE CASCADE,
    role_rid            integer NOT NULL REFERENCES pyre.roles (role_uid),
    CONSTRAINT per_organization_per_user UNIQUE(organization_rid, user_rid)
);
