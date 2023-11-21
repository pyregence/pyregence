WITH liberty AS (SELECT organization_uid FROM organizations WHERE org_unique_id = 'liberty')
INSERT INTO organization_layers
    (organization_rid, layer_path, layer_config)
VALUES
    ((SELECT organization_uid FROM liberty), '[:fuels :underlays :trans-liberty]', '{:opt-label "Transmission Lines (Liberty)", :z-index 106, :filter-set #{"liberty-trans" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM liberty), '[:fuels :underlays :dist-liberty]', '{:opt-label "Distribution Lines (Liberty)", :z-index 105, :filter-set #{"liberty-dist" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM liberty), '[:fire-weather :underlays :trans-liberty]', '{:opt-label "Transmission Lines (Liberty)", :z-index 106, :filter-set #{"liberty-trans" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM liberty), '[:fire-weather :underlays :dist-liberty]', '{:opt-label "Distribution Lines (Liberty)", :z-index 105, :filter-set #{"liberty-dist" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM liberty), '[:fire-risk :underlays :trans-liberty]', '{:opt-label "Transmission Lines (Liberty)", :z-index 106, :filter-set #{"liberty-trans" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM liberty), '[:fire-risk :underlays :dist-liberty]', '{:opt-label "Distribution Lines (Liberty)", :z-index 105, :filter-set #{"liberty-dist" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM liberty), '[:active-fire :underlays :trans-liberty]', '{:opt-label "Transmission Lines (Liberty)", :z-index 106, :filter-set #{"liberty-trans" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM liberty), '[:active-fire :underlays :dist-liberty]', '{:opt-label "Distribution Lines (Liberty)", :z-index 105, :filter-set #{"liberty-dist" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM liberty), '[:psps-zonal :underlays :trans-liberty]', '{:opt-label "Transmission Lines (Liberty)", :z-index 106, :filter-set #{"liberty-trans" "psps-static"}, :geoserver-key :psps}'),
    ((SELECT organization_uid FROM liberty), '[:psps-zonal :underlays :dist-liberty]', '{:opt-label "Distribution Lines (Liberty)", :z-index 105, :filter-set #{"liberty-dist" "psps-static"}, :geoserver-key :psps}');

UPDATE organization_layers
SET layer_config = '{:opt-label "Liberty Distribution Lines", :filter "liberty", :geoserver-key :psps, :disabled-for #{:crown-fire-area}}'
WHERE org_layer_uid = 1;

UPDATE organization_layers
SET layer_config = '{:opt-label "NV Energy Distribution Lines", :filter "nve", :geoserver-key :psps, :disabled-for #{:crown-fire-area}}'
WHERE org_layer_uid = 3;
