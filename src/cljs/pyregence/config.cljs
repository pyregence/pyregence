(ns pyregence.config
  (:require [clojure.string  :as str]
            [pyregence.utils :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Feature Flags
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^:private features (atom nil))

(defn set-feature-flags!
  "Sets the features atom with the specified features from `config.edn`."
  [config]
  (reset! features (:features config)))

(defn feature-enabled?
  "Checks whether or not a specific featue is enabled."
  [feature-name]
  (get @features feature-name))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Geographic Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def california-extent [-124.83131903974008 32.36304641169675 -113.24176261416054 42.24506977982483])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Layer options
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def near-term-forecast-default :active-fire)
(def near-term-forecast-options
  {:fuels        {:opt-label       "Fuels"
                  :filter          "fuels"
                  :reverse-legend? false
                  :hover-text      "Layers related to fuel and potential fire behavior."
                  :params          {:model {:opt-label  "Source"
                                            :hover-text "Stock LANDFIRE 2.0.0 data (https://landfire.gov) at 30 m resolution.\n
                                                         California Forest Observatory – Summer 2020 at 10 m resolution. Courtesy of the California Forest Observatory (https://forestobservatory.com), © Salo Sciences, Inc. 2020.\n
                                                         2021 California fuelscape prepared by Pyrologix, LLC (https://pyrologix.com), 2021."
                                            :options    {:landfire      {:opt-label "LANDFIRE 2.0.0"
                                                                         :filter    "landfire-2.0.0"}
                                                         :cfo           {:opt-label "California Forest Obs."
                                                                         :filter    "cfo-2020"}
                                                         :ca-fuelscapes {:opt-label "2021 CA fuelscape"
                                                                         :filter    "ca-fuelscapes"}}}
                                    :layer {:opt-label  "Layer"
                                            :hover-text "Geospatial surface and canopy fuel inputs, forecasted ember ignition probability and head fire spread rate & flame length."
                                            :options    (array-map
                                                         :fbfm40 {:opt-label "Fire Behavior Fuel Model 40"
                                                                  :filter    "fbfm40"
                                                                  :units     ""}
                                                         :asp    {:opt-label "Aspect"
                                                                  :filter    "asp"
                                                                  :units     ""
                                                                  :convert   #(str (u/direction %) " (" % "°)")}
                                                         :slp    {:opt-label "Slope (degrees)"
                                                                  :filter    "slp"
                                                                  :units     "°"}
                                                         :dem    {:opt-label "Elevation (ft)"
                                                                  :filter    "dem"
                                                                  :units     "ft"
                                                                  :convert   #(u/to-precision 1 (* % 3.28084))}
                                                         :cc     {:opt-label "Canopy Cover (%)"
                                                                  :filter    "cc"
                                                                  :units     "%"}
                                                         :ch     {:opt-label "Canopy Height (m)"
                                                                  :filter    "ch"
                                                                  :units     "m"
                                                                  :convert   #(u/to-precision 1 (/ % 10))}
                                                         :cbh    {:opt-label "Canopy Base Height (m)"
                                                                  :filter    "cbh"
                                                                  :units     "m"
                                                                  :convert   #(u/to-precision 1 (/ % 10))}
                                                         :cbd    {:opt-label "Crown Bulk Density (kg/m^3)"
                                                                  :filter    "cbd"
                                                                  :units     "kg/m^3"
                                                                  :convert   #(u/to-precision 2 %)})}
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
                                                              :tmpf   {:opt-label "Temperature (F)"
                                                                       :filter    "tmpf"
                                                                       :units     "\u00B0F"}
                                                              :ffwi   {:opt-label "Fosberg Fire Weather Index"
                                                                       :filter    "ffwi"
                                                                       :units     ""}
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
                                                             ELMFIRE (Eulerian Level Set Model of FIRE spread) is a cloud-based deterministic fire model developed by Chris Lautenberger at Reax Engineering. Details on its mathematical implementation have been published in Fire Safety Journal (https://doi.org/10.1016/j.firesaf.2013.08.014)."
                                                 :options    {:elmfire {:opt-label "ELMFIRE"
                                                                        :filter    "elmfire"}}}
                                    :model-init {:opt-label  "Forecast Start Time"
                                                 :hover-text "Hundreds of millions of fires are ignited across California at various times in the future and their spread is modeled under forecasted weather conditions. Data are refreshed each day at approximately 5 AM PDT."
                                                 :options    {:loading {:opt-label "Loading..."}}}}}
   :active-fire  {:opt-label   "Active Fires"
                  :filter      "fire-spread-forecast"
                  :block-info? true
                  :hover-text  "3-day forecasts of active fires with burning areas established from satellite-based heat detection."
                  :params      {:fire-name  {:opt-label      "Fire Name"
                                             :sort?          true
                                             :hover-text     "Provides a list of active fires for which forecasts are available. To zoom to a specific fire, select it from the dropdown menu."
                                             :underlays      {:us-buildings    {:enabled?   #(feature-enabled? :structures)
                                                                                :opt-label  "Structures"
                                                                                :z-index    4
                                                                                :filter-set #{"fire-detections" "us-buildings"}}
                                                              :nifs-perimeters {:opt-label  "NIFS Perimeters"
                                                                                :z-index    3
                                                                                :filter-set #{"fire-detections" "nifs-perimeters"}}
                                                              :viirs-hotspots  {:opt-label  "VIIRS Hotspots"
                                                                                :z-index    2
                                                                                :filter-set #{"fire-detections" "viirs-timestamped"}}
                                                              :modis-hotspots  {:opt-label  "MODIS Hotspots"
                                                                                :z-index    1
                                                                                :filter-set #{"fire-detections" "modis-timestamped"}}
                                                              :goes-imagery    {:opt-label  "Live satellite (GOES-16)"
                                                                                :z-index    0
                                                                                :filter-set #{"fire-detections" "goes16-rgb"}}}
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
                                                          :gridfire {:enabled?  #(feature-enabled? :gridfire)
                                                                     :opt-label "GridFire"
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
                    :params     {:model      {:opt-label  "Global Climate Model"
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
                                 :prob       {:opt-label  "RCP Scenario"
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
                                 :measure    {:opt-label  "Population Growth Scenario"
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

(def fbfm40-lookup {91    {:label       "NB1"
                           :fuel-type   "Non-burnable"
                           :description "Urban or suburban development; insufficient wildland fuel to carry wildland fire."}
                    92    {:label       "NB2"
                           :fuel-type   "Non-burnable"
                           :description "Snow/ice."}
                    93    {:label       "NB3"
                           :fuel-type   "Non-burnable"
                           :description "Agricultural field, maintained in nonburnable condition."}
                    98    {:label       "NB8"
                           :fuel-type   "Non-burnable"
                           :description "Open water."}
                    99    {:label       "NB9"
                           :fuel-type   "Non-burnable"
                           :description "Bare ground."}
                    101   {:label       "GR1"
                           :fuel-type   "Grass"
                           :description "Grass is short, patchy, and possibly heavily grazed. Spread rate moderate; flame length low."}
                    102   {:label       "GR2"
                           :fuel-type   "Grass"
                           :description "Moderately coarse continuous grass, average depth about 1 foot. Spread rate high; flame length moderate."}
                    103   {:label       "GR3"
                           :fuel-type   "Grass"
                           :description "Very coarse grass, average depth about 2 feet. Spread rate high; flame length moderate."}
                    104   {:label       "GR4"
                           :fuel-type   "Grass"
                           :description "Moderately coarse continuous grass, average depth about 2 feet. Spread rate very high; flame length high."}
                    105   {:label       "GR5"
                           :fuel-type   "Grass"
                           :description "Dense, coarse grass, average depth about 1 to 2 feet. Spread rate very high; flame length high."}
                    106   {:label       "GR6"
                           :fuel-type   "Grass"
                           :description "Dryland grass about 1 to 2 feet tall. Spread rate very high; flame length very high."}
                    107   {:label       "GR7"
                           :fuel-type   "Grass"
                           :description "Moderately coarse continuous grass, average depth about 3 feet. Spread rate very high; flame length very high."}
                    108   {:label       "GR8"
                           :fuel-type   "Grass"
                           :description "Heavy, coarse, continuous grass 3 to 5 feet tall. Spread rate very high; flame length very high."}
                    109   {:label       "GR9"
                           :fuel-type   "Grass"
                           :description "Very heavy, coarse, continuous grass 5 to 8 feet tall. Spread rate extreme; flame length extreme."}
                    121   {:label       "GS1"
                           :fuel-type   "Grass-Shrub"
                           :description "Shrubs are about 1 foot high, low grass load. Spread rate moderate; flame length low."}
                    122   {:label       "GS2"
                           :fuel-type   "Grass-Shrub"
                           :description "Shrubs are 1 to 3 feet high, moderate grass load. Spread rate high; flame length moderate."}
                    123   {:label       "GS3"
                           :fuel-type   "Grass-Shrub"
                           :description "Moderate grass/shrub load, average grass/shrub depth less than 2 feet. Spread rate high; flame length moderate."}
                    124   {:label       "GS4"
                           :fuel-type   "Grass-Shrub"
                           :description "Heavy grass/shrub load, depth greater than 2 feet. Spread rate high; flame length very high."}
                    141   {:label       "SH1"
                           :fuel-type   "Shrub"
                           :description "Low shrub fuel load, fuelbed depth about 1 foot; some grass may be present. Spread rate very low; flame length very low."}
                    142   {:label       "SH2"
                           :fuel-type   "Shrub"
                           :description "Moderate fuel load (higher than SH1), depth about 1 foot, no grass fuel present. Spread rate low; flame length low."}
                    143   {:label       "SH3"
                           :fuel-type   "Shrub"
                           :description "Moderate shrub load, possibly with pine overstory or herbaceous fuel, fuel bed depth 2 to 3 feet. Spread rate low; flame length low."}
                    144   {:label       "SH4"
                           :fuel-type   "Shrub"
                           :description "Low to moderate shrub and litter load, possibly with pine overstory, fuel bed depth about 3 feet. Spread rate high; flame length moderate."}
                    145   {:label       "SH5"
                           :fuel-type   "Shrub"
                           :description "Heavy shrub load, depth 4 to 6 feet. Spread rate very high; flame length very high."}
                    146   {:label       "SH6"
                           :fuel-type   "Shrub"
                           :description "Dense shrubs, little or no herb fuel, depth about 2 feet. Spread rate high; flame length high."}
                    147   {:label       "SH7"
                           :fuel-type   "Shrub"
                           :description "Very heavy shrub load, depth 4 to 6 feet. Spread rate lower than SH5, but flame length similar. Spread rate high; flame length very high."}
                    148   {:label       "SH8"
                           :fuel-type   "Shrub"
                           :description "Dense shrubs, little or no herb fuel, depth about 3 feet. Spread rates high; flame length high."}
                    149   {:label       "SH9"
                           :fuel-type   "Shrub"
                           :description "Dense, finely branched shrubs with significant fine dead fuel, about 4 to 6 feet tall; some herbaceous fuel may be present. Spread rate high, flame length very high."}
                    161   {:label       "TU1"
                           :fuel-type   "Timber-Understory"
                           :description "Fuelbed is low load of grass and/or shrub with litter. Spread rate low; flame length low."}
                    162   {:label       "TU2"
                           :fuel-type   "Timber-Understory"
                           :description "Fuelbed is moderate litter load with shrub component. Spread rate moderate; flame length low."}
                    163   {:label       "TU3"
                           :fuel-type   "Timber-Understory"
                           :description "Fuelbed is moderate litter load with grass and shrub components. Spread rate high; flame length moderate."}
                    164   {:label       "TU4"
                           :fuel-type   "Timber-Understory"
                           :description "Fuelbed is short conifer trees with grass or moss understory. Spread rate moderate; flame length moderate."}
                    165   {:label       "TU5"
                           :fuel-type   "Timber-Understory"
                           :description "Fuelbed is high load conifer litter with shrub understory. Spread rate moderate; flame length moderate."}
                    181   {:label       "TL1"
                           :fuel-type   "Timber-Litter"
                           :description "Light to moderate load, fuels 1 to 2 inches deep. Spread rate very low; flame length very low."}
                    182   {:label       "TL2"
                           :fuel-type   "Timber-Litter"
                           :description "Low load, compact. Spread rate very low; flame length very low."}
                    183   {:label       "TL3"
                           :fuel-type   "Timber-Litter"
                           :description "Moderate load conifer litter. Spread rate very low; flame length low."}
                    184   {:label       "TL4"
                           :fuel-type   "Timber-Litter"
                           :description "Moderate load, includes small diameter downed logs. Spread rate low; flame length low."}
                    185   {:label       "TL5"
                           :fuel-type   "Timber-Litter"
                           :description "High load conifer litter; light slash or mortality fuel. Spread rate low; flame length low."}
                    186   {:label       "TL6"
                           :fuel-type   "Timber-Litter"
                           :description "Moderate load, less compact. Spread rate moderate; flame length low."}
                    187   {:label       "TL7"
                           :fuel-type   "Timber-Litter"
                           :description "Heavy load, includes larger diameter downed logs. Spread rate low; flame length low."}
                    188   {:label       "TL8"
                           :fuel-type   "Timber-Litter"
                           :description "Moderate load and compactness may include small amount of herbaceous load. Spread rate moderate; flame length low."}
                    189   {:label       "TL9"
                           :fuel-type   "Timber-Litter"
                           :description "Very high load broadleaf litter; heavy needle-drape in otherwise sparse shrub layer. Spread rate moderate; flame length moderate."}
                    201   {:label       "SB1"
                           :fuel-type   "Slash-Blowdown"
                           :description "Fine fuel load is 10 to 20 tons/acre, weighted toward fuels 1 to 3 inches diameter class, depth is less than 1 foot. Spread rate moderate; flame length low."}
                    202   {:label       "SB2"
                           :fuel-type   "Slash-Blowdown"
                           :description "Fine fuel load is 7 to 12 tons/acre, evenly distributed across 0 to 0.25, 0.25 to 1, and 1 to 3 inch diameter classes, depth is about 1 foot. Spread rate moderate; flame length moderate. Blowdown is scattered, with many trees still standing. Spread rate moderate; flame length moderate."}
                    203   {:label       "SB3"
                           :fuel-type   "Slash-Blowdown"
                           :description "Fine fuel load is 7 to 12 tons/acre, weighted toward 0 to 0.25 inch diameter class, depth is more than 1 foot. Spread rate high; flame length high. Blowdown is moderate, trees compacted to near the ground. Spread rate high; flame length high."}
                    204   {:label       "SB4"
                           :fuel-type   "Slash-Blowdown"
                           :description "Blowdown is total, fuelbed not compacted, foliage still attached. Spread rate very high; flame length very high."}
                    -9999 {:label       "NoData"
                           :fuel-type   "No Data"
                           :description "No data are available for this pixel"}})

(defn get-forecast
  "Retrieves the forecast options and default tab."
  [forecast-type]
  {:pre [(contains? #{:long-term :near-term} forecast-type)]}
  (forecast-type forecasts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; WFS/WMS Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^:private geoserver-base-url (atom nil))
(defn- wms-url [] (str (u/end-with @geoserver-base-url "/") "wms"))
(defn- wfs-url [] (str (u/end-with @geoserver-base-url "/") "wfs"))
(defn- mvt-url [] (str (u/end-with @geoserver-base-url "/") "gwc/service/wmts"))

(defn set-geoserver-base-url!
  "Sets the base URL of the Geoserver given the value from `config.edn`."
  [url]
  (reset! geoserver-base-url url))

(defn legend-url
  "Generates a URL for the legend given a layer."
  [layer]
  (str (wms-url)
       "?SERVICE=WMS"
       "&EXCEPTIONS=application/json"
       "&VERSION=1.3.0"
       "&REQUEST=GetLegendGraphic"
       "&FORMAT=application/json"
       "&LAYER=" layer))

(defn point-info-url
  "Generates a URL for the point information."
  [layer-group bbox feature-count]
  (str (wms-url)
       "?SERVICE=WMS"
       "&EXCEPTIONS=application/json"
       "&VERSION=1.3.0"
       "&REQUEST=GetFeatureInfo"
       "&INFO_FORMAT=application/json"
       "&LAYERS=" layer-group
       "&QUERY_LAYERS=" layer-group
       "&FEATURE_COUNT=" feature-count
       "&TILED=true"
       "&I=0"
       "&J=0"
       "&WIDTH=1"
       "&HEIGHT=1"
       "&CRS=EPSG:3857"
       "&STYLES="
       "&BBOX=" bbox))

(defn wms-layer-url
  "Generates a Web Mapping Service (WMS) url to download a PNG tile.

   Mapbox GL requires tiles to be projected to EPSG:3857 (Web Mercator)."
  [layer]
  (str (mvt-url)
       "?REQUEST=GetTile"
       "&SERVICE=WMTS"
       "&VERSION=1.0.0"
       "&LAYER=" layer
       "&STYLE="
       "&FORMAT=image/png"
       "&TILEMATRIX=EPSG:900913:{z}"
       "&TILEMATRIXSET=EPSG:900913"
       "&TILECOL={x}&TILEROW={y}"))

(defn wfs-layer-url
  "Generates a Web Feature Service (WFS) url to download an entire vector data
   set as GeoJSON.

   Mapbox GL does support GeoJSON in EPSG:4326. However, it does not support WFS."
  [layer]
  (str (wfs-url)
       "?SERVICE=WFS"
       "&VERSION=1.3.0"
       "&REQUEST=GetFeature"
       "&OUTPUTFORMAT=application/json"
       "&SRSNAME=EPSG:4326"
       "&TYPENAME=" layer))

(defn mvt-layer-url
  "Generates a Mapbox Vector Tile (MVT) URL to be used with with Mapbox GL.

   When adding MVT data, the projection must be EPSG:3857 (Web Mercator) or
   EPSG:900913 (Google Web Mercator). EPSG:900913 is used since GeoServer's
   embedded [GeoWebCache](https://www.geowebcache.org/) Web Map Tile Service (WMTS)
   supports EPSG:900913 by default, but does not support EPSG:3857 by default."
  [layer]
  (str (mvt-url)
       "?REQUEST=GetTile"
       "&SERVICE=WMTS"
       "&VERSION=1.0.0"
       "&LAYER=" layer
       "&STYLE="
       "&FORMAT=application/vnd.mapbox-vector-tile"
       "&TILEMATRIX=EPSG:900913:{z}"
       "&TILEMATRIXSET=EPSG:900913"
       "&TILECOL={x}&TILEROW={y}"))

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

(defonce mapbox-access-token (atom nil))

(defn set-mapbox-access-token!
  "Sets the Mapbox access token given the value from `config.edn`."
  [token]
  (reset! mapbox-access-token token))

(def default-sprite "mapbox://sprites/mspencer-sig/cka8jaky90i9m1iphwh79wr04/3nae2cnmmvrdazx877w1wcuez")

(defn- style-url [id]
  (str "https://api.mapbox.com/styles/v1/mspencer-sig/" id "?access_token=" @mapbox-access-token))

(defn base-map-options
  "Provides the configuration for the different Mapbox map view options."
  []
  {:mapbox-topo       {:opt-label "Mapbox Street Topo"
                       :source    (style-url "cka8jaky90i9m1iphwh79wr04")}
   :mapbox-satellite  {:opt-label "Mapbox Satellite"
                       :source    (style-url "ckm3suyjm0u6z17nx1t7udnvd")}
   :mapbox-sat-street {:opt-label "Mapbox Satellite Street"
                       :source    (style-url "ckm2hgkx04xuw17pahpins029")}})

(def base-map-default :mapbox-topo)

(def mapbox-dem-url "mapbox://mapbox.mapbox-terrain-dem-v1")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Dev-mode Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce dev-mode? (atom nil))

(defn set-dev-mode!
  "Sets the dev mode given the value from `config.edn`."
  [val]
  (reset! dev-mode? val))
