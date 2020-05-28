(ns pyregence.config)

;; WMS options

(def wms-url "https://californiafireforecast.com:8443/geoserver/wms")

(def capabilities-url (str wms-url
                           "?SERVICE=WMS"
                           "&VERSION=1.3.0"
                           "&REQUEST=GetCapabilities"))

(defn legend-url [layer]
  (str wms-url
       "?SERVICE=WMS"
       "&VERSION=1.3.0"
       "&REQUEST=GetLegendGraphic"
       "&FORMAT=application/json"
       "&LAYER=" layer))

(defn point-info-url [layer-group bbox]
  (str wms-url
       "?SERVICE=WMS"
       "&VERSION=1.3.0"
       "&REQUEST=GetFeatureInfo"
       "&INFO_FORMAT=application/json"
       "&LAYERS=" layer-group
       "&QUERY_LAYERS=" layer-group
       "&FEATURE_COUNT=1000"
       "&TILED=true"
       "&I=0"
       "&J=0"
       "&WIDTH=1"
       "&HEIGHT=1"
       "&CRS=EPSG:3857"
       "&STYLES="
       "&BBOX=" bbox))

;; Layer options

(def forecast-options [{:opt-label   "Active Fire Forecast"
                        :filter      "fire-spread-forecast"
                        :auto-zoom?  true
                        :block-info? true
                        :params      [{:opt-label "Fire Name"
                                       :options   []}
                                      {:opt-label "Model"
                                       :options   [{:opt-label "ELMFIRE"
                                                    :filter    "elmfire"}]}
                                      {:opt-label "Model Time"
                                       :options   []}
                                      {:opt-label "Fuel"
                                       :options   [{:opt-label "LANDFIRE"
                                                    :filter    "landfire"}]}
                                      {:opt-label "Burn Probability"
                                       :options   [{:opt-label "90"
                                                    :filter    "90"}
                                                   {:opt-label "80"
                                                    :filter    "80"}
                                                   {:opt-label "70"
                                                    :filter    "70"}
                                                   {:opt-label "60"
                                                    :filter    "60"}
                                                   {:opt-label "50"
                                                    :filter    "50"}
                                                   {:opt-label "40"
                                                    :filter    "40"}
                                                   {:opt-label "30"
                                                    :filter    "30"}
                                                   {:opt-label "20"
                                                    :filter    "20"}
                                                   {:opt-label "10"
                                                    :filter    "10"}]}
                                      {:opt-label "Output"
                                       :options   [{:opt-label "Burned Area"
                                                    :filter    "burned-area"
                                                    :units     "Hours"}]}]}
                       {:opt-label       "Risk Forecast"
                        :filter          "fire-risk-forecast"
                        :reverse-legend? true
                        :params          [{:opt-label "Model"
                                           :options   [{:opt-label "ELMFIRE"
                                                        :filter    "elmfire"}]}
                                          {:opt-label "Model Time"
                                           :options   []}
                                          {:opt-label "Fuel"
                                           :options   [{:opt-label "CA Forest Observatory"
                                                        :filter    "cfo"}
                                                       {:opt-label "LANDFIRE"
                                                        :filter    "landfire"}]}
                                          {:opt-label "Ignition Pattern"
                                           :options   [{:opt-label    "All"
                                                        :filter       "all"}
                                                       {:opt-label    "Transmission Lines"
                                                        :filter       "tlines"
                                                        :clear-point? true}]}
                                          {:opt-label "Output"
                                           :options   [{:opt-label "Fire Area"
                                                        :filter    "fire-area"
                                                        :units     "Acres"}
                                                       {:opt-label "Fire Volume"
                                                        :filter    "fire-volume"
                                                        :units     "Acre-ft"}
                                                       {:opt-label "Impacted Structures"
                                                        :filter    "impacted-structures"
                                                        :units     "Structures"}
                                                       {:opt-label "Times Burned"
                                                        :filter    "times-burned"
                                                        :units     "Times"}]}]}])

;; Scroll speeds for time slider

(def speeds [{:opt-label ".5x" :delay 2000}
             {:opt-label "1x"  :delay 1000}
             {:opt-label "2x"  :delay 500}
             {:opt-label "5x"  :delay 200}])

;; Basemap options

(def BingMaps js/ol.source.BingMaps)
(def TileJSON js/ol.source.TileJSON)
(def XYZ      js/ol.source.XYZ)

(defn get-map-box-static-url [map-id]
  (str "https://api.mapbox.com/styles/v1/mspencer-sig/"
       map-id
       "/tiles/256/{z}/{x}/{y}"
       "?access_token=pk.eyJ1IjoibXNwZW5jZXItc2lnIiwiYSI6ImNrYThsbHN4dTAzcGMyeG14MWY0d3U3dncifQ.TB_ZdQPDkyzHHAZ1FfYahw"))

(defn get-map-box-raster-url [layer-name]
  (str "https://api.mapbox.com/v4/"
       layer-name
       "/{z}/{x}/{y}.jpg90"
       "?access_token=pk.eyJ1IjoibXNwZW5jZXItc2lnIiwiYSI6ImNrYThsbHN4dTAzcGMyeG14MWY0d3U3dncifQ.TB_ZdQPDkyzHHAZ1FfYahw"))

(def base-map-options [{:opt-label "MapBox Street Topo"
                        :source    (XYZ.
                                    #js {:url (get-map-box-static-url "cka8jaky90i9m1iphwh79wr04")})}
                       {:opt-label "MapBox Satellite"
                        :source    (XYZ.
                                    #js {:url (get-map-box-raster-url "mapbox.satellite")})}
                       {:opt-label "MapBox Satellite Street"
                        :source    (XYZ.
                                    #js {:url (get-map-box-static-url "cka8hoo5v0gpy1iphg08hz7oj")})}])
