--- NVE Transmission Lines
UPDATE organization_layers
SET layer_config = '{:opt-label "Transmission Lines (NVE)", :z-index 106, :filter-set #{"nve" "nve-trans" "psps-static"}, :geoserver-key :psps}'
WHERE org_layer_uid IN (4, 6, 8, 10, 12);

UPDATE organization_layers
SET layer_path = '[:fuels :underlays :nve-trans]'
WHERE org_layer_uid = 4;

UPDATE organization_layers
SET layer_path = '[:fire-weather :underlays :nve-trans]'
WHERE org_layer_uid = 6;

UPDATE organization_layers
SET layer_path = '[:fire-risk :underlays :nve-trans]'
WHERE org_layer_uid = 8;

UPDATE organization_layers
SET layer_path = '[:active-fire :underlays :nve-trans]'
WHERE org_layer_uid = 10;

UPDATE organization_layers
SET layer_path = '[:psps-zonal :underlays :nve-trans]'
WHERE org_layer_uid = 12;

--- NVE Distribution Lines
UPDATE organization_layers
SET layer_config = '{:opt-label "Distribution Lines (NVE)", :z-index 105, :filter-set #{"nve" "nve-dist" "psps-static"}, :geoserver-key :psps}'
WHERE org_layer_uid IN (5, 7, 9, 11, 13);

UPDATE organization_layers
SET layer_path = '[:fuels :underlays :nve-dist]'
WHERE org_layer_uid = 5;

UPDATE organization_layers
SET layer_path = '[:fire-weather :underlays :nve-dist]'
WHERE org_layer_uid = 7;

UPDATE organization_layers
SET layer_path = '[:fire-risk :underlays :nve-dist]'
WHERE org_layer_uid = 9;

UPDATE organization_layers
SET layer_path = '[:active-fire :underlays :nve-dist]'
WHERE org_layer_uid = 11;

UPDATE organization_layers
SET layer_path = '[:psps-zonal :underlays :nve-dist]'
WHERE org_layer_uid = 13;

--- Liberty Transmission Lines
UPDATE organization_layers
SET layer_config = '{:opt-label "Transmission Lines (Liberty)", :z-index 106, :filter-set #{"liberty" "liberty-trans" "psps-static"}, :geoserver-key :psps}'
WHERE org_layer_uid IN (14, 16, 18, 20, 22);

UPDATE organization_layers
SET layer_path = '[:fuels :underlays :liberty-trans]'
WHERE org_layer_uid = 14;

UPDATE organization_layers
SET layer_path = '[:fire-weather :underlays :liberty-trans]'
WHERE org_layer_uid = 16;

UPDATE organization_layers
SET layer_path = '[:fire-risk :underlays :liberty-trans]'
WHERE org_layer_uid = 18;

UPDATE organization_layers
SET layer_path = '[:active-fire :underlays :liberty-trans]'
WHERE org_layer_uid = 20;

UPDATE organization_layers
SET layer_path = '[:psps-zonal :underlays :liberty-trans]'
WHERE org_layer_uid = 22;

--- Liberty Distribution Lines
UPDATE organization_layers
SET layer_config = '{:opt-label "Distribution Lines (Liberty)", :z-index 105, :filter-set #{"liberty" "liberty-dist" "psps-static"}, :geoserver-key :psps}'
WHERE org_layer_uid IN (15, 17, 19, 21, 23);

UPDATE organization_layers
SET layer_path = '[:fuels :underlays :liberty-dist]'
WHERE org_layer_uid = 15;

UPDATE organization_layers
SET layer_path = '[:fire-weather :underlays :liberty-dist]'
WHERE org_layer_uid = 17;

UPDATE organization_layers
SET layer_path = '[:fire-risk :underlays :liberty-dist]'
WHERE org_layer_uid = 19;

UPDATE organization_layers
SET layer_path = '[:active-fire :underlays :liberty-dist]'
WHERE org_layer_uid = 21;

UPDATE organization_layers
SET layer_path = '[:psps-zonal :underlays :liberty-dist]'
WHERE org_layer_uid = 23;
