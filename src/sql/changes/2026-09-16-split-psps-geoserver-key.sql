-- The single utility GeoServer became three: /geoserver04 (risk forecast),
-- /geoserver05 (weather) and /geoserver06 (PSPS static, PSPS zonal, risk
-- planning). Repoint every stored layer config at the one that now serves it.
-- Match on layer_path, not on the layer label: only the path says which family
-- a row belongs to.

-- Risk planning first: its path also matches the forecast pattern below.
UPDATE organization_layers
   SET layer_config = replace(layer_config, ':geoserver-key :psps', ':geoserver-key :parrot06')
 WHERE layer_config LIKE '%:geoserver-key :psps%'
   AND layer_path LIKE '[:fire-risk :params :pattern :options :%-planning]';

UPDATE organization_layers
   SET layer_config = replace(layer_config, ':geoserver-key :psps', ':geoserver-key :parrot04')
 WHERE layer_config LIKE '%:geoserver-key :psps%'
   AND layer_path LIKE '[:fire-risk :params :pattern :options :%';

UPDATE organization_layers
   SET layer_config = replace(layer_config, ':geoserver-key :psps', ':geoserver-key :parrot05')
 WHERE layer_config LIKE '%:geoserver-key :psps%'
   AND layer_path LIKE '[:fire-weather :params :model :options :%';

-- Everything left is a psps-static underlay, whichever tab it hangs off.
UPDATE organization_layers
   SET layer_config = replace(layer_config, ':geoserver-key :psps', ':geoserver-key :parrot06')
 WHERE layer_config LIKE '%:geoserver-key :psps%';
