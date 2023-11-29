UPDATE organization_layers
SET layer_path = '[:fire-weather :params :model :options :nve]'
WHERE org_layer_uid = 26;

UPDATE organization_layers
SET layer_config = '{:opt-label "ADS WRF", :filter "nve", :geoserver-key :psps, :disabled-for #{:apcptot :apcp01 :vpd :smoke :tcdc}}'
WHERE org_layer_uid = 26;
