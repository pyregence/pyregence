(ns pyregence.config
  (:require [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Geographic Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def california-extent [-124.83131903974008 32.36304641169675 -113.24176261416054 42.24506977982483])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Layer options
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def near-term-forecast-default :fire-risk)
(def near-term-forecast-options
  {:fuels        {:opt-label       "Fuels"
                  :filter          "fuels"
                  :block-info?     true
                  :reverse-legend? false
                  :hover-text      "Layers related to fuel and potential fire behavior."
                  :params          {:model {:opt-label  "Source"
                                            :hover-text "Stock LANDFIRE 2.0.0 data (https://landfire.gov) at 30 m resolution.\n
                                                         California Forest Observatory – Summer 2020 at 10 m resolution. Courtesy of the California Forest Observatory (https://forestobservatory.com), © Salo Sciences, Inc. 2020.\n
                                                         2021 California fuelscape prepared by Pyrologix, LLC (https://pyrologix.com), 2021."
                                            :options    {:landfire      {:opt-label "LANDFIRE 2.0.0"
                                                                         :filter    "landfire-2.0.0"
                                                                         :units     ""}
                                                         :cfo           {:opt-label "California Forest Obs."
                                                                         :filter    "cfo-2020"
                                                                         :units     ""}
                                                         :ca-fuelscapes {:opt-label "2021 CA fuelscape"
                                                                         :filter    "ca-fuelscapes"
                                                                         :units     ""}}}
                                    :layer {:opt-label  "Layer"
                                            :hover-text "Geospatial surface and canopy fuel inputs, forecasted ember ignition probability and head fire spread rate & flame length."
                                            :options    (array-map
                                                         :fbfm40 {:opt-label "Fire Behavior Fuel Model 40"
                                                                  :filter    "fbfm40"
                                                                  :units     ""}
                                                         :asp    {:opt-label "Aspect"
                                                                  :filter    "asp"
                                                                  :units     ""
                                                                  :disabled  #{:cfo}}
                                                         :slp    {:opt-label "Slope (degrees)"
                                                                  :filter    "slp"
                                                                  :units     "degrees"
                                                                  :disabled  #{:cfo}}
                                                         :dem    {:opt-label "Elevation (ft)"
                                                                  :filter    "dem"
                                                                  :units     ""
                                                                  :disabled  #{:cfo}}
                                                         :cc     {:opt-label "Canopy Cover (%)"
                                                                  :filter    "cc"
                                                                  :units     "%"}
                                                         :ch     {:opt-label "Canopy Height (m)"
                                                                  :filter    "ch"
                                                                  :units     "m"}
                                                         :cbh    {:opt-label "Canopy Base Height (m)"
                                                                  :filter    "cbh"
                                                                  :units     "m"}
                                                         :cbd    {:opt-label "Crown Bulk Density (kg/m^3)"
                                                                  :filter    "cbd"
                                                                  :units     "kg/m^3"})}
                                    :model-init {:opt-label  "Model Creation Time"
                                                 :hover-text "Time the data was created."
                                                 :options    {:loading {:opt-label "Loading..."}}}}}
   :fire-weather {:opt-label       "Weather"
                  :filter          "fire-weather-forecast"
                  :reverse-legend? true
                  :hover-text      "8-day forecast of key parameters affecting wildfire behavior obtained from operational National Weather Service forecast models."
                  :params          {:band       {:opt-label  "Weather Parameter"
                                                 :hover-text "8-day forecast updated 4x daily pulled from three operational weather models. Available quantities include common weather parameters plus fire weather indices:\n
                                                              - Fosberg Fire Weather Index (FFWI): A fuel-independent measure of potential spread rate based on wind speed, relative humidity, and temperature
                                                              - Vapor pressure deficit (VPD): Difference between amount of moisture in air and how much it can hold when saturated
                                                              - Hot Dry Windy Index: Similar to FFWI, but based on VPD"
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
   :fire-risk    {:opt-label       "Risk"
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
                                                              - 2021 California fuelscape prepared by Pyrologix, LLC (https://pyrologix.com), 2021.\n
                                                              - California Forest Observatory – Summer 2020 at 10 m resolution. Courtesy of the California Forest Observatory (https://forestobservatory.com), © Salo Sciences, Inc. 2020."
                                                 :options    {:landfire {:opt-label "2021 CA fuelscape"
                                                                         :filter    "landfire"}
                                                              :cfo      {:opt-label "2020 CA Forest Obs."
                                                                         :filter    "cfo"}}}
                                    :model      {:opt-label  "Model"
                                                 :hover-text "Computer fire spread model used to generate active fire and risk forecasts.\n
                                                             ELMFIRE - Cloud-based operational fire spread model developed at Reax Engineering Inc. (https://doi.org/10.1016/j.firesaf.2013.08.014)."
                                                 :options    {:elmfire {:opt-label "ELMFIRE"
                                                                        :filter    "elmfire"}}}
                                    :model-init {:opt-label  "Forecast Start Time"
                                                 :hover-text "Twice per day, hundreds of millions of fires are ignited across California at various times in the future under forecasted weather conditions.
                                                              By modeling their spread, we can identify areas where fires starting at a specific time and location have the potential to spread rapidly or impact assets at risk.
                                                              Forecasts are currently generated from the 06z and 18z weather forecasts. Data are refreshed each day at approximately 5 AM PDT"
                                                 :options    {:loading {:opt-label "Loading..."}}}}}
   :active-fire  {:opt-label   "Active Fires"
                  :filter      "fire-spread-forecast"
                  :block-info? true
                  :hover-text  "3-day forecasts of active fires with burning areas established from satellite-based heat detection."
                  :params      {:fire-name  {:opt-label      "Fire Name"
                                             :sort?          true
                                             :hover-text     "Provides a list of active fires for which forecasts are available. To zoom to a specific fire, select it from the dropdown menu."
                                             :underlays      {:nifs-perimeters {:opt-label  "NIFS Perimeters"
                                                                                :z-index    3
                                                                                :filter-set #{"fire-detections" "nifs-perimeters"}}
                                                              :viirs-hotspots  {:opt-label  "VIIRS Hotspots"
                                                                                :z-index    2
                                                                                :filter-set #{"fire-detections" "viirs-timestamped"}}
                                                              :modis-hotspots  {:opt-label  "MODIS Hotspots"
                                                                                :z-index    1
                                                                                :filter-set #{"fire-detections" "modis-timestamped"}}}
                                             :default-option :active-fires
                                             :options        {:active-fires    {:opt-label  "*All Active Fires"
                                                                                :style-fn   :default
                                                                                :filter-set #{"fire-detections" "active-fires"}
                                                                                :auto-zoom? false}}}
                                :output     {:opt-label  "Output"
                                             :hover-text "This shows the areas where our models forecast the fire to spread over 3 days. Time can be advanced with the slider below, and the different colors on the map provide information about when an area is forecast to burn."
                                             :options    {:burned {:opt-label "Forecasted fire location"
                                                                   :filter    "hours-since-burned"
                                                                   :units     "Hours"}}}
                                :burn-pct   {:opt-label      "Predicted Fire Size"
                                             :default-option :50
                                             :hover-text     "Each fire forecast is an ensemble of 1,000 separate simulations to account for uncertainty in model inputs. This leads to a range of predicted fire sizes, five of which can be selected from the dropdown menu."
                                             :options        {:90 {:opt-label "Largest (90th percentile)"
                                                                   :filter    "90"}
                                                              :70 {:opt-label "Larger (70th percentile)"
                                                                   :filter    "70"}
                                                              :50 {:opt-label "Median (50th percentile)"
                                                                   :filter    "50"}
                                                              :30 {:opt-label "Smaller (30th percentile)"
                                                                   :filter    "30"}
                                                              :10 {:opt-label "Smallest (10th percentile)"
                                                                   :filter    "10"}}}
                                :fuel       {:opt-label  "Fuel"
                                             :hover-text "Source of surface and canopy fuel inputs:\n
                                                          - 2021 California fuelscape prepared by Pyrologix, LLC (https://pyrologix.com), 2021.\n
                                                          - California Forest Observatory – Summer 2020 at 10 m resolution. Courtesy of the California Forest Observatory (https://forestobservatory.com), © Salo Sciences, Inc. 2020."
                                             :options    {:landfire {:opt-label "CA fuelscape / LANDFIRE 2.0.0"
                                                                     :filter    "landfire"}}}
                                :model      {:opt-label  "Model"
                                             :hover-text "ELMFIRE (Eulerian Level Set Model of FIRE spread) is a cloud-based deterministic fire model developed by Chris Lautenberger at Reax Engineering. Details on its mathematical implementation have been published in Fire Safety Journal (https://doi.org/10.1016/j.firesaf.2013.08.014).\n
                                                          GridFire is a fire behavior model developed by Gary Johnson of Spatial Informatics Group. It combines empirical equations from the wildland fire science literature with the performance of a raster-based spread algorithm using the method of adaptive time steps and fractional distances."
                                             :options    {:elmfire  {:opt-label "ELMFIRE"
                                                                     :filter    "elmfire"}
                                                          :gridfire {:opt-label "GridFire"
                                                                     :filter    "gridfire"}}}
                                :model-init {:opt-label  "Forecast Start Time"
                                             :hover-text "This shows the date and time (24 hour time) from which the prediction starts. To view a different start time, select one from the dropdown menu. This data is automatically updated when active fires are sensed by satellites."
                                             :options    {:loading {:opt-label "Loading..."}}}}}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; WG4 Scenario Planning
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def long-term-forecast-default :fire-scenarios)
(def long-term-forecast-options
  {:fire-scenarios {:opt-label  "Fire Scenarios"
                    :filter     "wg4_FireSim"
                    :hover-text "Wildfire scenario projections for area burned with varied emissions and population scenarios."
                    :block-info? true
                    :params     {:model   {:opt-label  "Global Climate Model"
                                           :hover-text "Four climate models selected by the California's Climate Action Team as priority models for research contributing to California's Fourth Climate Change Assessment.\n
                                                        Projected future climate from these four models can be described as producing:
                                                        HadGEM2-ES - A warmer/dry simulation
                                                        CNRM-CM5 - A cooler/wetter simulation
                                                        CanESM2 - An average simulation
                                                        MIROC5 - A model that is most unlike the first three to offer the best coverage of different possibilities."
                                           :auto-zoom? true
                                           :options    {:can-esm2   {:opt-label "CanESM2"
                                                                     :filter    "CanESM2"
                                                                     :units     ""}
                                                        :hadgem2-es {:opt-label "HadGEM2-ES"
                                                                     :filter    "HadGEM2-ES"
                                                                     :units     ""}
                                                        :cnrm-cm5   {:opt-label "CNRM-CM5"
                                                                     :filter    "CNRM-CM5"
                                                                     :units     ""}
                                                        :miroc5     {:opt-label "MIROC5"
                                                                     :filter    "MIROC5"
                                                                     :units     ""}}}
                                 :prob    {:opt-label  "RCP Scenario"
                                           :hover-text "Representative Concentration Pathway (RCP) is the greenhouse gas concentration trajectory adopted by the IPCC.\n
                                                        Options include:
                                                        4.5 - emissions start declining starting in 2045 to reach half the levels of CO2 of 2050 by 2100.
                                                        8.5 - emissions keep rising throughout the 2100."
                                           :options    {:p45 {:opt-label "4.5"
                                                              :filter    "45"
                                                              :units     ""}
                                                        :p85 {:opt-label "8.5"
                                                              :filter    "85"
                                                              :units     ""}}}
                                 :measure {:opt-label  "Population Growth Scenario"
                                           :hover-text "Vary population growth."
                                           :options    {:bau  {:opt-label "Central"
                                                               :filter    "bau"
                                                               :units     ""}
                                                        :high {:opt-label "High"
                                                               :filter    "H"
                                                               :units     ""}
                                                        :low  {:opt-label "Low"
                                                               :filter    "L"
                                                               :units     ""}}}
                                 :model-init {:opt-label  "Scenario Year"
                                              :hover-text "Year"
                                              :options    {:loading {:opt-label "Loading..."}}}}}})

(def ^:private forecasts {:near-term {:options-config near-term-forecast-options
                                      :default        near-term-forecast-default}
                          :long-term {:options-config long-term-forecast-options
                                      :default        long-term-forecast-default}})

(defn get-forecast
  "Retrieve forecast options and default tab"
  [forecast-type]
  {:pre [(contains? #{:long-term :near-term} forecast-type)]}
  (forecast-type forecasts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; WFS/WMS Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn wms-layer-url [layer]
  (str wms-url
       "?SERVICE=WMS"
       "&VERSION=1.3.0"
       "&REQUEST=GetMap"
       "&FORMAT=image/png"
       "&TRANSPARENT=true"
       "&WIDTH=256"
       "&HEIGHT=256"
       "&CRS=EPSG%3A3857"
       "&STYLES="
       "&FORMAT_OPTIONS=dpi%3A113"
       "&BBOX={bbox-epsg-3857}"
       "&LAYERS=" layer))

(defn wfs-layer-url [layer]
  (str wfs-url
       "?SERVICE=WFS"
       "&VERSION=1.3.0"
       "&REQUEST=GetFeature"
       "&OUTPUTFORMAT=application/json"
       "&SRSNAME=EPSG:4326"
       "&TYPENAME=" layer))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Scroll speeds for time slider
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def speeds [{:opt-label ".5x" :delay 2000}
             {:opt-label "1x"  :delay 1000}
             {:opt-label "2x"  :delay 500}
             {:opt-label "5x"  :delay 200}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Match Drop Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def match-drop-instructions
  "Simulates a fire using real-time weather data.
   Click on a location to \"drop\" a match,
   then set the date and time to begin the simulation.")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mapbox Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce mapbox-access-token "pk.eyJ1IjoibXNwZW5jZXItc2lnIiwiYSI6ImNrYThsbHN4dTAzcGMyeG14MWY0d3U3dncifQ.TB_ZdQPDkyzHHAZ1FfYahw")

(def default-sprite "mapbox://sprites/mspencer-sig/cka8jaky90i9m1iphwh79wr04/3nae2cnmmvrdazx877w1wcuez")

(defn- style-url [id]
  (str "https://api.mapbox.com/styles/v1/mspencer-sig/" id "?access_token=" mapbox-access-token))

(def base-map-options
  {:mapbox-topo       {:opt-label "Mapbox Street Topo"
                       :source    (style-url "cka8jaky90i9m1iphwh79wr04")}
   :mapbox-satellite  {:opt-label "Mapbox Satellite"
                       :source    (style-url "ckm3suyjm0u6z17nx1t7udnvd")}
   :mapbox-sat-street {:opt-label "Mapbox Satellite Street"
                       :source    (style-url "ckm2hgkx04xuw17pahpins029")}})

(def base-map-default :mapbox-topo)

(def mapbox-dem-url "mapbox://mapbox.mapbox-terrain-dem-v1")
