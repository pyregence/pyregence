-- NAMESPACE: organization

--------------------------------------------------------------------------------
-- Stores information about organizations
--------------------------------------------------------------------------------
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

--------------------------------------------------------------------------------
-- Stores information about layers available to an organization
--------------------------------------------------------------------------------
CREATE TABLE organization_layers (
    org_layer_uid    SERIAL PRIMARY KEY,
    organization_rid integer NOT NULL REFERENCES organizations (organization_uid) ON DELETE CASCADE ON UPDATE CASCADE,
    layer_path       text,
    layer_config     text
);
