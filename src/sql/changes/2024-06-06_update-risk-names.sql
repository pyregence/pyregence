--- NVE
UPDATE organization_layers
SET layer_config = '{:opt-label "NV Energy lines", :filter "nve", :geoserver-key :psps, :disabled-for #{:crown-fire-area}}'
WHERE org_layer_uid = 3;

--- Liberty
UPDATE organization_layers
SET layer_config = '{:opt-label "Liberty lines", :filter "liberty", :geoserver-key :psps, :disabled-for #{:crown-fire-area}}'
WHERE org_layer_uid = 1;

--- NorthWestern
UPDATE organization_layers
SET layer_config = '{:opt-label "NorthWestern Energy lines", :filter "northwestern", :geoserver-key :psps, :disabled-for #{:crown-fire-area}}'
WHERE org_layer_uid = 27;

--- OTEC
UPDATE organization_layers
SET layer_config = '{:opt-label "OTEC lines", :filter "otec", :geoserver-key :psps, :disabled-for #{:crown-fire-area}}'
WHERE org_layer_uid = 39;
