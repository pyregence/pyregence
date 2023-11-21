WITH liberty AS (SELECT organization_uid FROM organizations WHERE org_unique_id = 'liberty')
INSERT INTO organization_layers
    (organization_rid, layer_path, layer_config)
VALUES
    ((SELECT organization_uid FROM liberty), '[:fire-weather :params :model :options :ecmwf]', '{:opt-label "Euro", :filter "ecmwf", :geoserver-key :psps, :disabled-for #{:hdw :apcptot :apcp01 :vpd :smoke :tcdc}}');

WITH nve AS (SELECT organization_uid FROM organizations WHERE org_unique_id = 'nve')
INSERT INTO organization_layers
    (organization_rid, layer_path, layer_config)
VALUES
    ((SELECT organization_uid FROM nve), '[:fire-weather :params :model :options :ecmwf]', '{:opt-label "Euro", :filter "ecmwf", :geoserver-key :psps, :disabled-for #{:hdw :apcptot :apcp01 :vpd :smoke :tcdc}}'),
    ((SELECT organization_uid FROM nve), '[:fire-weather :params :model :options :adswrf]', '{:opt-label "ADS WRF", :filter "adswrf", :geoserver-key :psps, :disabled-for #{:apcptot :apcp01 :vpd :smoke :tcdc}}');
