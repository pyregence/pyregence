-- Add planning ignition pattern for all orgs (excluding public & consortium)
WITH orgs AS (
  SELECT
    o.organization_uid,
    o.org_unique_id,
    o.org_name,
    -- Build EDN keyword path: :<org_unique_id>-planning
    '[:fire-risk :params :pattern :options :' || o.org_unique_id || '-planning]' AS layer_path,
    -- Use org_name for label text
    '{:opt-label "' ||
      o.org_name ||
      ' overhead lines Planning", ' ||
      ':filter "' || o.org_unique_id || '", ' ||
      ':geoserver-key :psps}'
      AS layer_config
  FROM organizations o
  WHERE o.org_unique_id NOT IN ('public', 'pyregence-consortium')
)
INSERT INTO organization_layers (organization_rid, layer_path, layer_config)
SELECT
  organization_uid,
  layer_path,
  layer_config
FROM orgs o
-- Make sure row doesn't already exist
WHERE NOT EXISTS (
  SELECT 1
  FROM organization_layers ol
  WHERE ol.organization_rid = o.organization_uid
    AND ol.layer_path = o.layer_path
);
