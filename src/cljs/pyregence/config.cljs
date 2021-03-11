(ns pyregence.config
  (:require [clojure.string :as str]))

;; Layer options

(def forecast-options
  {:fire-weather {:opt-label       "Fire Weather"
                  :filter          "fire-weather-forecast"
                  :reverse-legend? true
                  :hover-text      "8-day forecast of key parameters affecting wildfire behavior obtained from operational National Weather Service forecast models."
                  :params          {:band       {:opt-label  "Weather Parameter"
                                                 :hover-text "Options include:\n
                                                              Fosberg Fire Weather Index - Meteorological filter that combines wind speed, relative humidity, and temperature into a fuel-independent measure of how quickly fires will spread.\n
                                                              Fine dead fuel moisture - Moisture content of fine dead fuels such as cured grasses, needle litter, and small-diameter twigs.\n
                                                              Relative Humidity - Amount of moisture in air relative to the amount of moisture the air can hold before condensation occurs.\n
                                                              Other common weather metrics."
                                                 :options    (array-map
                                                              :ffwi   {:opt-label "Fosberg Fire Weather Index"
                                                                       :filter    "ffwi"
                                                                       :units     ""}
                                                              :tmpf   {:opt-label "Temperature (F)"
                                                                       :filter    "tmpf"
                                                                       :units     "deg F"}
                                                              :rh     {:opt-label "Relative humidity (%)"
                                                                       :filter    "rh"
                                                                       :units     "%"}
                                                              :ws     {:opt-label "Sustained wind speed (mph)"
                                                                       :filter    "ws"
                                                                       :units     "mph"}
                                                              :wg     {:opt-label "Wind gust (mph)"
                                                                       :filter    "wg"
                                                                       :units     "mph"}
                                                              :apcp01 {:opt-label "1-hour precipitation (in)"
                                                                       :filter    "apcp01"
                                                                       :units     "inches"}
                                                              :meq    {:opt-label "Fine dead fuel moisture (%)"
                                                                       :filter    "meq"
                                                                       :units     "%"}
                                                              :vpd    {:opt-label "Vapor pressure deficit (hPa)"
                                                                       :filter    "vpd"
                                                                       :units     "hPa"}
                                                              :hdw    {:opt-label "Hot-Dry-Windy Index (hPa*m/s)"
                                                                       :filter    "hdw"
                                                                       :units     "hPa*m/s"})}
                                    :model-init {:opt-label  "Forecast Start Time"
                                                 :hover-text "Start time for forecast cycle, new data comes every 6 hours."
                                                 :options    {:loading {:opt-label "Loading..."}}}}}
   :active-fire {:opt-label   "Active Fire Forecast"
                 :filter      "fire-spread-forecast"
                 :block-info? true
                 :hover-text  "3-day forecasts of active fires with burning areas established from satellite-based heat detection."
                 :params      {:fire-name  {:opt-label      "Fire Name"
                                            :auto-zoom?     true
                                            :sort?          true
                                            :hover-text     "This section contains active fires across the state, as named in CALFIRE incident reports. Select a specific fire from the dropdown menu to view a simulation.\n
                                                             View all active fires by selecting one of two options with an asterisk at the beginning. We offer data from CALFIRE, as well as the National Interagency Fire Center."
                                            :underlays      {:nifs-perimeters   {:opt-label  "NIFS Perimeters"
                                                                                 :z-index    10
                                                                                 :filter-set #{"fire-detections" "nifs-perimeters"}}
                                                             :viirs-hotspots    {:opt-label  "VIIRS Hotspots"
                                                                                 :z-index    9
                                                                                 :filter-set #{"fire-detections" "viirs-timestamped"}}
                                                             :modis-hotspots    {:opt-label  "MODIS Hotspots"
                                                                                 :z-index    8
                                                                                 :filter-set #{"fire-detections" "modis-timestamped"}}}
                                            :default-option :calfire-incidents
                                            :options        {:calfire-incidents {:opt-label  "*CALFIRE Incidents"
                                                                                 :style-fn   :default
                                                                                 :filter-set #{"fire-detections" "calfire-incidents"}}
                                                             :nifc-large-fires  {:opt-label  "*NIFC Large Fires"
                                                                                 :style-fn   :default
                                                                                 :filter-set #{"fire-detections" "nifc-large-fires"}}}}
                               :output     {:opt-label  "Output"
                                            :hover-text "This shows the areas where our models forecast the fire to spread over 3 days. Time can be advanced with the slider below, and the different colors on the map provide information about when an area is forecast to burn."
                                            :options    {:burned {:opt-label "Forecasted fire location"
                                                                  :filter    "burned-area"
                                                                  :units     "Hours"}}}
                               :burn-pct   {:opt-label      "Burn Probability"
                                            :default-option :50
                                            :hover-text     "To develop an active fire forecast, we run 1,000 simulations, inputting a variety of factors for consideration. 'Burn Probability' shows the percentage of time the simulations followed the same path. To see the path taken in 90% of our simulations, for example, select 90% from the dropdown menu."
                                            :options        {:90 {:opt-label "90%"
                                                                  :filter    "90"}
                                                             :70 {:opt-label "70%"
                                                                  :filter    "70"}
                                                             :50 {:opt-label "50%"
                                                                  :filter    "50"}
                                                             :30 {:opt-label "30%"
                                                                  :filter    "30"}
                                                             :10 {:opt-label "10%"
                                                                  :filter    "10"}}}
                               :fuel       {:opt-label  "Fuel"
                                            :hover-text "Source of surface and canopy fuel inputs:\n
                                                         LANDFIRE data (https://landfire.gov) at 30-m resolution customized by Pyrologix (http://pyrologix.com) for the United States Forest Service, Pacific Southwest Region (https://www.fs.usda.gov/r5)."
                                            :options    {:landfire {:opt-label "Custom LANDFIRE"
                                                                    :filter    "landfire"}}}
                               :model      {:opt-label  "Model"
                                            :hover-text "ELMFIRE is a new predictive model developed by Chris Lautenberger of Reax Engineering. It is the artificial intelligence used to generate the active fire forecast in this tool."
                                            :options    {:elmfire {:opt-label "ELMFIRE"
                                                                   :filter    "elmfire"}}}
                               :model-init {:opt-label  "Forecast Start Time"
                                            :hover-text "This shows the date and time (24 hour time) from which the prediction starts. To view a different start time, select one from the dropdown menu. This data is automatically updated when active fires are sensed by satellites."
                                            :options    {:loading {:opt-label "Loading..."}}}}}
   :fire-risk {:opt-label       "Risk Forecast"
               :filter          "fire-risk-forecast"
               :reverse-legend? true
               :hover-text      "5-day forecast of fire consequence maps. Every day over 500 million hypothetical fires are ignited across California to evaluate potential fire risk.\n"
               :params          {:output     {:opt-label  "Output"
                                              :hover-text "Key fire spread model outputs based on modeling 6-hours of fire spread without fire suppression activities within 6 hours of time shown in time slider. Options include:\n
                                                           Relative Burn Probability - Relative likelihood that an area is burned by fires that have not yet ignited within the next six hours of time shown in time slider.\n
                                                           Impacted Structures - Approximate number of residential structures within fire perimeter for fires starting at specific location and time in the future.\n
                                                           Fire Area - Modeled fire size in acres by ignition location and time of ignition.\n
                                                           Fire Volume - Modeled fire volume (fire area in acres multiplied by flame length in feet) by ignition location and time of ignition."
                                              :options    {:times-burned {:opt-label "Relative burn probability"
                                                                          :filter    "times-burned"
                                                                          :units     "Times"}
                                                           :impacted     {:opt-label "Impacted structures"
                                                                          :filter    "impacted-structures"
                                                                          :units     "Structures"}
                                                           :fire-area    {:opt-label "Fire area"
                                                                          :filter    "fire-area"
                                                                          :units     "Acres"}
                                                           :fire-volume  {:opt-label "Fire volume"
                                                                          :filter    "fire-volume"
                                                                          :units     "Acre-ft"}}}
                                 :pattern    {:opt-label  "Ignition Pattern"
                                              :hover-text "Fires are ignited randomly across California at various times in the future so their impacts can be modeled. Patterns include:\n
                                                           Human Caused - Anthropogenic fires (fires from all causes except lightning).\n
                                                           Transmission Lines - Fires ignited in close proximity to overhead electrical transmission lines.\n"
                                              :options    {:all        {:opt-label    "Human-caused ignitions"
                                                                        :filter       "all"}
                                                           :tlines     {:opt-label    "Transmission lines"
                                                                        :filter       "tlines"
                                                                        :clear-point? true}}}
                                 :fuel       {:opt-label  "Fuel"
                                              :hover-text "Source of surface and canopy fuel inputs:\n
                                                           LANDFIRE data (https://landfire.gov) at 30-m resolution customized by Pyrologix (http://pyrologix.com) for the United States Forest Service, Pacific Southwest Region (https://www.fs.usda.gov/r5).\n
                                                           California Forest Observatory (https://forestobservatory.com) 10 m fuels measured by satellite."
                                              :options    {:landfire {:opt-label "Custom LANDFIRE"
                                                                      :filter    "landfire"}
                                                           :cfo      {:opt-label "California Forest Observatory"
                                                                      :filter    "cfo"}}}
                                 :model      {:opt-label  "Model"
                                              :hover-text "Computer fire spread model used to generate active fire and risk forecasts.\n
                                                           ELMFIRE - Cloud-based operational fire spread model developed at Reax Engineering Inc. (https://doi.org/10.1016/j.firesaf.2013.08.014)."
                                              :options    {:elmfire {:opt-label "ELMFIRE"
                                                                     :filter    "elmfire"}}}
                                 :model-init {:opt-label  "Forecast Start Time"
                                              :hover-text "Start time for forecast cycle. New data is created every 12 hours."
                                              :options    {:loading {:opt-label "Loading..."}}}}}})

;; WMS and WFS options

(def wms-url "https://data.pyregence.org:8443/geoserver/wms")
(def wfs-url "https://data.pyregence.org:8443/geoserver/wfs")

(defn legend-url [layer]
  (str wms-url
       "?SERVICE=WMS"
       "&EXCEPTIONS=application/json"
       "&VERSION=1.3.0"
       "&REQUEST=GetLegendGraphic"
       "&FORMAT=application/json"
       "&LAYER=" layer))

(defn point-info-url [layer-group bbox]
  (str wms-url
       "?SERVICE=WMS"
       "&EXCEPTIONS=application/json"
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

(defn get-wfs-feature [layer extent]
  (str wfs-url
       "?SERVICE=WFS"
       "&REQUEST=GetFeature"
       "&TYPENAME=" layer
       "&OUTPUTFORMAT=application/json"
       "&SRSNAME=EPSG:3857"
       "&BBOX=" (str/join "," extent) ",EPSG:3857"))

;; Scroll speeds for time slider

(def speeds [{:opt-label ".5x" :delay 2000}
             {:opt-label "1x"  :delay 1000}
             {:opt-label "2x"  :delay 500}
             {:opt-label "5x"  :delay 200}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mapbox Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce mapbox-access-token "pk.eyJ1IjoicnNoZXBlcmQiLCJhIjoiY2tsYjNxMG02MDhscjJvbWRudjU2MGQ1cCJ9.VAXBvWkqQkqHaazVPZS2BQ")

(def default-sprite "mapbox://sprites/mspencer-sig/cka8jaky90i9m1iphwh79wr04/3nae2cnmmvrdazx877w1wcuez")

(defn style-url [id]
  (str "https://api.mapbox.com/styles/v1/mspencer-sig/" id "?access_token=" mapbox-access-token))

(def base-map-options
  {:mapbox-topo       {:opt-label "Mapbox Street Topo"
                       :source    (style-url "cka8jaky90i9m1iphwh79wr04")}
   :mapbox-satellite  {:opt-label "Mapbox Satellite"
                       :source    (style-url "ckm3suyjm0u6z17nx1t7udnvd")}
   :mapbox-sat-street {:opt-label "Mapbox Satellite Street"
                       :source    (style-url "ckm2hgkx04xuw17pahpins029")}})

(def base-map-default :mapbox-topo)
