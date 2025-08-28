-- NAMESPACE: organization
-- REQUIRES: user

--------------------------------------------------------------------------------
---  Organizations
--------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION get_all_organizations()
 RETURNS TABLE (
  org_id                integer,
  org_name              text,
  org_unique_id         text,
  geoserver_credentials text,
  email_domains         text,
  auto_add              boolean,
  auto_accept           boolean,
  archived              boolean,
  created_date          date,
  archived_date         date
 ) AS $$

  SELECT
    o.organization_uid               AS org_id,
    o.org_name                       AS org_name,
    o.org_unique_id                  AS org_unique_id,
    o.geoserver_credentials::text    AS geoserver_credentials,
    o.email_domains::text            AS email_domains,
    o.auto_add                       AS auto_add,
    o.auto_accept                    AS auto_accept,
    o.archived                       AS archived,
    o.created_date::date             AS created_date,
    o.archived_date::date            AS archived_date
  FROM organizations o
  ORDER BY o.organization_uid;

$$ LANGUAGE SQL;

-- Returns the organization for a user if they are an org admin or org member
-- TODO in the future we should get rid of this b/c the organization a user belongs to
-- is simply in the u.organization_rid column. this was kept in order to not have
-- to overhaul the front end code that calls this.
CREATE OR REPLACE FUNCTION get_user_organization(_user_id integer)
 RETURNS TABLE (
    org_id                integer,
    org_name              text,
    org_unique_id         text,
    geoserver_credentials text,
    user_role             user_role,
    email_domains         text,
    auto_add              boolean,
    auto_accept           boolean
 ) AS $$

    SELECT
        o.organization_uid,
        o.org_name,
        o.org_unique_id,
        o.geoserver_credentials,
        u.user_role,
        o.email_domains,
        o.auto_add,
        o.auto_accept
    FROM users u
    JOIN organizations o ON u.organization_rid = o.organization_uid
    WHERE u.user_uid = _user_id
      AND u.user_role IN ('organization_admin', 'organization_member')
      AND u.org_membership_status = 'accepted'
    ORDER BY o.org_unique_id

$$ LANGUAGE SQL;

-- Returns all organizations that have PSPS data (denoted by presence of geoserver_credentials)
CREATE OR REPLACE FUNCTION get_psps_organizations()
RETURNS TABLE (org_unique_id text) AS $$

    SELECT org_unique_id
    FROM organizations
    WHERE geoserver_credentials IS NOT NULL;

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION update_org_info(
    _org_id           integer,
    _org_name         text,
    _email_domains    text,
    _auto_add         boolean,
    _auto_accept      boolean
 ) RETURNS void AS $$

    UPDATE organizations
    SET org_name = _org_name,
        email_domains = _email_domains,
        auto_add = _auto_add,
        auto_accept = _auto_accept
    WHERE
        organization_uid = _org_id

$$ LANGUAGE SQL;

-- Allows switching between 'pending' and 'accepted' (and 'none' for users with no org).
-- DB constraints in user_tables.sql will enforce invalid combos.
CREATE OR REPLACE FUNCTION update_org_membership_status(
    _user_id     integer,
    _status_text text
) RETURNS void AS $$
    UPDATE users
    SET org_membership_status = _status_text::org_membership_status
    WHERE user_uid = _user_id;
$$ LANGUAGE SQL;

--------------------------------------------------------------------------------
---  Organization Memberss
--------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION get_org_member_users(_org_id integer)
 RETURNS TABLE (
    user_id               integer,
    full_name             text,
    email                 text,
    user_role             user_role,
    org_membership_status org_membership_status
 ) AS $$

    SELECT
        user_uid,
        name AS full_name,
        email,
        user_role,
        org_membership_status
    FROM users
    WHERE organization_rid = _org_id

$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION remove_org_user(_user_id integer)
RETURNS void AS $$
    UPDATE users
    SET organization_rid = NULL,
        user_role = 'member',
        org_membership_status = 'none'
    WHERE user_uid = _user_id;
$$ LANGUAGE SQL;

CREATE OR REPLACE FUNCTION update_org_user_role(
    _user_id        integer,
    _user_role_text text
) RETURNS void AS $$

    UPDATE users
    SET user_role = _user_role_text::user_role
    WHERE user_uid = _user_id;

$$ LANGUAGE SQL;

--------------------------------------------------------------------------------
---  Organization Layers
--------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION get_user_layers_list(_user_id integer)
 RETURNS TABLE (
    org_layer_id integer,
    layer_path   text,
    layer_config text
 ) AS $$
    SELECT
        ol.org_layer_uid AS org_layer_id,
        ol.layer_path,
        ol.layer_config
    FROM users u
    CROSS JOIN organization_layers ol
    WHERE u.user_uid = _user_id
      AND (
            -- super_admin sees ALL organization_layers
            u.user_role = 'super_admin'
            -- org admins/members see only their own org's layers
            OR (u.user_role IN ('organization_admin', 'organization_member')
                AND ol.organization_rid = u.organization_rid)
          );
$$ LANGUAGE SQL;

--------------------------------------------------------------------------------
---  Helper functions (unused on front-end)
--------------------------------------------------------------------------------
-- This simplifies adding a new layer even though it's not hooked in the UI
CREATE OR REPLACE FUNCTION add_org_layer(
    _org_id       integer,
    _layer_path   text,
    _layer_config text
 ) RETURNS void AS $$

    INSERT INTO organization_layers
        (organization_rid, layer_path, layer_config)
    VALUES
        (_org_id, _layer_path, _layer_config)

$$ LANGUAGE SQL;
