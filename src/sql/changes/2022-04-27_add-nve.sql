INSERT INTO organizations
    (org_name, email_domains, auto_add, auto_accept)
VALUES
    ('NV Energy', '@nvenergy.com', TRUE, TRUE);


INSERT INTO users
    (email, name, password, verified, settings)
VALUES
    ('demo@nvenergy.com', 'NV Energy Demo Account', crypt('#nve2022#', gen_salt('bf')), TRUE, '{:timezone :utc}');


WITH nve AS (SELECT organization_uid FROM organizations WHERE org_name = 'NV Energy')
INSERT INTO organization_layers
    (organization_rid, layer_path, layer_config)
VALUES
    ((SELECT organization_uid FROM nve), '[:fire-risk :params :pattern :options :nve]', '{:opt-label "NV Energy overhead lines", :filter "nve", :geoserver-key :psps}'),
    ((SELECT organization_uid FROM nve), '[:fuels :underlays :trans-nve]', '{:opt-label "Transmission Lines (NVE)", :z-index 106, :filter-set #{"nve-trans" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM nve), '[:fuels :underlays :dist-nve]', '{:opt-label "Distribution Lines (NVE)", :z-index 105, :filter-set #{"nve-dist" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM nve), '[:fire-weather :underlays :trans-nve]', '{:opt-label "Transmission Lines (NVE)", :z-index 106, :filter-set #{"nve-trans" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM nve), '[:fire-weather :underlays :dist-nve]', '{:opt-label "Distribution Lines (NVE)", :z-index 105, :filter-set #{"nve-dist" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM nve), '[:fire-risk :underlays :trans-nve]', '{:opt-label "Transmission Lines (NVE)", :z-index 106, :filter-set #{"nve-trans" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM nve), '[:fire-risk :underlays :dist-nve]', '{:opt-label "Distribution Lines (NVE)", :z-index 105, :filter-set #{"nve-dist" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM nve), '[:active-fire :underlays :trans-nve]', '{:opt-label "Transmission Lines (NVE)", :z-index 106, :filter-set #{"nve-trans" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM nve), '[:active-fire :underlays :dist-nve]', '{:opt-label "Distribution Lines (NVE)", :z-index 105, :filter-set #{"nve-dist" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM nve), '[:psps-zonal :underlays :trans-nve]', '{:opt-label "Transmission Lines (NVE)", :z-index 106, :filter-set #{"nve-trans" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM nve), '[:psps-zonal :underlays :dist-nve]', '{:opt-label "Distribution Lines (NVE)", :z-index 105, :filter-set #{"nve-dist" "psps-static"}, :geoserver-key :psps}');


WITH nve_org AS (SELECT organization_uid FROM organizations WHERE org_name = 'NV Energy'),
     nve_user AS (SELECT user_uid FROM users WHERE email = 'demo@nvenergy.com')
INSERT INTO organization_users
    (organization_rid, user_rid, role_rid)
VALUES
    ((SELECT organization_uid FROM nve_org), (SELECT user_uid FROM nve_user), 1);
