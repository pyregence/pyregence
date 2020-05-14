(ns pyregence.config)

;; WMS Options

(def get-legend-url       (str "https://californiafireforecast.com:8443/geoserver/demo/wms"
                               "?SERVICE=WMS"
                               "&VERSION=1.3.0"
                               "&REQUEST=GetLegendGraphic"
                               "&FORMAT=application/json"
                               "&LAYER=%s"))
(def get-capabilities-url (str "https://californiafireforecast.com:8443/geoserver/demo/wms"
                               "?SERVICE=WMS"
                               "&VERSION=1.3.0"
                               "&REQUEST=GetCapabilities"
                               "&NAMESPACE=demo"))
(def point-info-url       (str "https://californiafireforecast.com:8443/geoserver/demo/wms"
                               "?SERVICE=WMS"
                               "&VERSION=1.3.0"
                               "&REQUEST=GetFeatureInfo"
                               "&INFO_FORMAT=application/json"
                               "&LAYERS=demo:lg-%s"
                               "&QUERY_LAYERS=demo:lg-%s"
                               "&FEATURE_COUNT=1000"
                               "&TILED=true"
                               "&I=0"
                               "&J=0"
                               "&WIDTH=1"
                               "&HEIGHT=1"
                               "&CRS=EPSG:3857"
                               "&STYLES="
                               "&BBOX=%s"))

;; Layer options

(def layer-types [{:opt-id 0
                   :opt-label "Fire Area"
                   :filter "fire-area"
                   :units "Acres"}
                  {:opt-id 1
                   :opt-label "Fire Volume"
                   :filter "fire-volume"
                   :units "Acre-ft"}
                  {:opt-id 2
                   :opt-label "Impacted Structures"
                   :filter "impacted-structures"
                   :units "Structures"}
                  {:opt-id 3
                   :opt-label "Times Burned"
                   :filter "times-burned"
                   :units "Times"}])

;; Scroll speeds for time slider

(def speeds [{:opt-id 0 :opt-label ".5x" :delay 2000}
             {:opt-id 1 :opt-label "1x"  :delay 1000}
             {:opt-id 2 :opt-label "2x"  :delay 500}
             {:opt-id 3 :opt-label "5x"  :delay 200}])

;; Base Map Options

(def BingMaps        js/ol.source.BingMaps)
(def OSM             js/ol.source.OSM)
(def TileWMS         js/ol.source.TileWMS)
(def TileJSON        js/ol.source.TileJSON)
(def XYZ             js/ol.source.XYZ)
(def base-map-options [{:opt-id    0
                        :opt-label "*OpenStreetMaps"
                        :source    (OSM.)}
                       {:opt-id    1
                        :opt-label "MapTiler Topographique"
                        :source    (TileJSON.
                                    #js {:url "https://api.maptiler.com/maps/topographique/tiles.json?key=aTxMH7uEmrp1p92D9slS"
                                         :crossOrigin "anonymous"})}
                       {:opt-id    2
                        :opt-label "MapTiler Satellite"
                        :source    (TileJSON.
                                    #js {:url "https://api.maptiler.com/maps/hybrid/tiles.json?key=aTxMH7uEmrp1p92D9slS"
                                         :crossOrigin "anonymous"})}
                       {:opt-id    3
                        :opt-label "Thunderforest Landscape"
                        :source    (XYZ.
                                    #js {:url "https://tile.thunderforest.com/landscape/{z}/{x}/{y}.png?apikey=9dccbe884aab4483823c7cb6ab9b5f4d"})}
                       {:opt-id    4
                        :opt-label "BingMaps Street"
                        :source    (BingMaps.
                                    #js {:key "AjMtoHQV7tiC8tKyktN3OFi_qXUv_0i5TvtZFic3vkIOzR2iTFmYxVOPJKSZwjHq"
                                         :imagerySet "RoadOnDemand"})}
                       {:opt-id    5
                        :opt-label "BingMaps Aerial"
                        :source    (BingMaps.
                                    #js {:key "AjMtoHQV7tiC8tKyktN3OFi_qXUv_0i5TvtZFic3vkIOzR2iTFmYxVOPJKSZwjHq"
                                         :imagerySet "Aerial"})}
                       {:opt-id    6
                        :opt-label "BingMaps Aerial with Streets"
                        :source    (BingMaps.
                                    #js {:key "AjMtoHQV7tiC8tKyktN3OFi_qXUv_0i5TvtZFic3vkIOzR2iTFmYxVOPJKSZwjHq"
                                         :imagerySet "AerialWithLabelsOnDemand"})}
                       {:opt-id    7
                        :opt-label "*USGS Street Topo"
                        :source    (TileWMS.
                                    #js {:url         "https://basemap.nationalmap.gov/arcgis/services/USGSTopo/MapServer/WMSServer"
                                         :params      #js {"LAYERS" "0"}
                                         :crossOrigin "anonymous"
                                         :serverType  "geoserver"})}
                       {:opt-id    8
                        :opt-label "*USGS Satellite"
                        :source    (TileWMS.
                                    #js {:url         "https://basemap.nationalmap.gov/arcgis/services/USGSImageryOnly/MapServer/WMSServer"
                                         :params      #js {"LAYERS" "0"}
                                         :crossOrigin "anonymous"
                                         :serverType  "geoserver"})}
                       {:opt-id    9
                        :opt-label "*USGS Satellite Topo"
                        :source    (TileWMS.
                                    #js {:url         "https://basemap.nationalmap.gov/arcgis/services/USGSImageryTopo/MapServer/WMSServer"
                                         :params      #js {"LAYERS" "0"}
                                         :crossOrigin "anonymous"
                                         :serverType  "geoserver"})}
                       {:opt-id    10
                        :opt-label "Google Terrain"
                        :source    (XYZ.
                                    #js {:url "https://mt0.google.com/vt/lyrs=p&hl=en&x={x}&y={y}&z={z}&s=Ga"})}
                       {:opt-id    11
                        :opt-label "Google Satellite"
                        :source    (XYZ.
                                    #js {:url "https://mt0.google.com/vt/lyrs=y&hl=en&x={x}&y={y}&z={z}&s=Ga"})}])
