-- NAMESPACE: organization

--------------------------------------------------------------------------------
-- Enum types
--------------------------------------------------------------------------------

CREATE TYPE subscription_tier AS ENUM (
  'tier1_free_registered',
  'tier1_basic_paid',
  'tier2_pro',
  'tier3_enterprise'
);

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
    archived_date         date,
    system_assets         boolean,
    subscription_tier     subscription_tier NOT NULL DEFAULT 'tier1_free_registered',
    is_shell_org          boolean NOT NULL DEFAULT FALSE
);

ALTER TABLE organizations
ADD CONSTRAINT org_shell_matches_tier CHECK (
  (subscription_tier IN ('tier1_free_registered', 'tier1_basic_paid') AND is_shell_org = TRUE)
  OR
  (subscription_tier IN ('tier2_pro', 'tier3_enterprise') AND is_shell_org = FALSE)
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
