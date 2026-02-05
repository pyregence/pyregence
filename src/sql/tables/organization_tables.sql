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
    max_seats             integer NOT NULL default 1
);

ALTER TABLE organizations
ADD CONSTRAINT org_tier_seat_rules CHECK (
  (subscription_tier IN ('tier1_free_registered', 'tier1_basic_paid') AND max_seats = 1)
  OR
  (subscription_tier IN ('tier2_pro', 'tier3_enterprise') AND max_seats >= 1)
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
