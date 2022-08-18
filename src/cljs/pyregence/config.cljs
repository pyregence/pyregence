(ns pyregence.config
  (:require [clojure.string               :as str]
            [pyregence.state              :as !]
            [pyregence.utils.number-utils :as u-num]
            [pyregence.utils.string-utils :as u-str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Feature Flags
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn feature-enabled?
  "Checks whether or not a specific feature is enabled."
  [feature-name]
  (get @!/feature-flags feature-name))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Geographic Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def california-extent [-124.83131903974008 32.36304641169675 -113.24176261416054 42.24506977982483])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; WG3 Forecast
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def common-underlays
  {:conus-buildings    {:enabled?      #(feature-enabled? :structures)
                        :opt-label     "Structures"
                        :z-index       104
                        :filter-set    #{"fire-detections" "conus-buildings"}
                        :geoserver-key :shasta}})

(def near-term-forecast-underlays
  (array-map
   :us-trans-lines          {:opt-label     "Transmission lines"
                             :z-index       107
                             :filter-set    #{"fire-detections" "us-transmission-lines"}
                             :geoserver-key :shasta}
   :current-year-perimeters {:opt-label     "2022 fire perimeters"
                             :z-index       103
                             :filter-set    #{"fire-detections" "current-year-perimeters"}
                             :geoserver-key :shasta}
   :viirs-hotspots          {:opt-label     "VIIRS hotspots"
                             :z-index       102
                             :filter-set    #{"fire-detections" "viirs-timestamped"}
                             :geoserver-key :shasta}
   :modis-hotspots          {:opt-label     "MODIS hotspots"
                             :z-index       101
                             :filter-set    #{"fire-detections" "modis-timestamped"}
                             :geoserver-key :shasta}
   :goes-imagery            {:opt-label     "Live satellite (GOES-16)"
                             :z-index       100
                             :filter-set    #{"fire-detections" "goes16-rgb"}
                             :geoserver-key :shasta}))

(def near-term-forecast-options
  {:fuels        {:opt-label     "Fuels"
                  :filter        "fuels"
                  :geoserver-key :shasta
                  :underlays     (merge common-underlays near-term-forecast-underlays)
                  :time-slider?  false
                  :hover-text    "Layers related to fuel and potential fire behavior."
                  :params        {:layer {:opt-label  "Layer"
                                          :hover-text [:p {:style {:margin-bottom "0"}}
                                                       "Geospatial surface and canopy fuel inputs, forecasted ember ignition probability and head fire spread rate & flame length."
                                                       [:br]
                                                       [:br]
                                                       "Use the "
                                                       [:strong "Point Information"]
                                                       " tool for more detailed information about a selected point."]
                                          :options    (array-map
                                                       :fbfm40 {:opt-label       "Fire Behavior Fuel Model 40"
                                                                :filter          "fbfm40"
                                                                :units           ""
                                                                :reverse-legend? false}
                                                       :asp    {:opt-label       "Aspect"
                                                                :filter          "asp"
                                                                :units           ""
                                                                :convert         #(str (u/direction %) " (" % "°)")
                                                                :reverse-legend? false
                                                                :disabled-for    #{:cecs}}
                                                       :slp    {:opt-label       "Slope (degrees)"
                                                                :filter          "slp"
                                                                :units           "\u00B0"
                                                                :reverse-legend? true
                                                                :disabled-for    #{:cecs}}
                                                       :dem    {:opt-label       "Elevation (ft)"
                                                                :filter          "dem"
                                                                :units           "ft"
                                                                :convert         #(u-num/to-precision 1 (* % 3.28084))
                                                                :reverse-legend? true
                                                                :disabled-for    #{:cecs}}
                                                       :cc     {:opt-label       "Canopy Cover (%)"
                                                                :filter          "cc"
                                                                :units           "%"
                                                                :reverse-legend? true
                                                                :disabled-for    #{:cecs}}
                                                       :ch     {:opt-label       "Canopy Height (m)"
                                                                :filter          "ch"
                                                                :units           "m"
                                                                :no-convert      #{:cfo}
                                                                :convert         #(u-num/to-precision 1 (/ % 10))
                                                                :reverse-legend? true
                                                                :disabled-for    #{:cecs}}
                                                       :cbh    {:opt-label       "Canopy Base Height (m)"
                                                                :filter          "cbh"
                                                                :units           "m"
                                                                :no-convert      #{:cfo}
                                                                :convert         #(u-num/to-precision 1 (/ % 10))
                                                                :reverse-legend? true
                                                                :disabled-for    #{:cecs}}
                                                       :cbd    {:opt-label       "Crown Bulk Density (kg/m\u00b3)"
                                                                :filter          "cbd"
                                                                :units           "kg/m\u00b3"
                                                                :convert         #(u-num/to-precision 2 (/ % 100))
                                                                :no-convert      #{:cfo}
                                                                :reverse-legend? true
                                                                :disabled-for    #{:cecs}})}
                                  :model {:opt-label  "Source"
                                          :hover-text [:p {:style {:margin-bottom "0"}}
                                                       "Stock "
                                                       [:strong "LANDFIRE 2.2.0"]
                                                       " data ("
                                                       [:a {:href   "https://landfire.gov"
                                                            :target "_blank"}
                                                        "https://landfire.gov"]
                                                       ") at 30 m resolution."
                                                       [:br]
                                                       [:br]
                                                       [:strong "California Forest Observatory"]
                                                       " – Summer 2020 at 10 m resolution. Courtesy of the California Forest Observatory ("
                                                       [:a {:href   "https://forestobservatory.com"
                                                            :target "_blank"}
                                                        "https://forestobservatory.com"]
                                                       "), © Salo Sciences, Inc. 2020."
                                                       [:br]
                                                       [:br]
                                                       [:strong "2022 California fuelscape"]
                                                       " prepared by Pyrologix, LLC ("
                                                       [:a {:href   "https://pyrologix.com"
                                                            :target "_blank"}
                                                        "https://pyrologix.com"]
                                                       "), 2022."
                                                       [:br]
                                                       [:br]
                                                       [:strong "California Ecosystem Climate Solutions"]
                                                       " - Data provided by the "
                                                       [:a {:href   "https://california-ecosystem-climate.solutions/"
                                                            :target "_blank"}
                                                        "California Ecosystem Climate Solutions"]
                                                       ", Wang et al. (2021)."]
                                          :options    {:landfire      {:opt-label "LANDFIRE 2.2.0"
                                                                       :filter    "landfire-2.2.0"}
                                                       :cfo           {:opt-label "California Forest Obs."
                                                                       :filter    "cfo-2020"}
                                                       :ca-fuelscapes {:opt-label "2022 CA fuelscape"
                                                                       :filter    "ca-2022-fuelscape"}
                                                       :cecs          {:opt-label    "CA Ecosystem Climate Solutions"
                                                                       :filter       "cecs"
                                                                       :disabled-for #{:asp :slp :dem :cc :ch :cbh :cbd}}}}
                                  :model-init {:opt-label  "Model Creation Time"
                                               :hover-text "Time the data was created."
                                               :options    {:loading {:opt-label "Loading..."}}}}}
   :fire-weather {:opt-label       "Weather"
                  :filter          "fire-weather-forecast"
                  :geoserver-key   :shasta
                  :underlays       (merge common-underlays near-term-forecast-underlays)
                  :reverse-legend? true
                  :time-slider?    true
                  :hover-text      "Gridded weather forecasts from several US operational weather models including key parameters that affect wildfire behavior."
                  :params          {:band       {:opt-label  "Weather Parameter"
                                                 :hover-text [:p {:style {:margin-bottom "0"}}
                                                              "Gridded weather forecasts from several US operational weather models having different spatial resolutions and forecast durations. Available quantities include common weather parameters and fire weather indices:"
                                                              [:br]
                                                              [:br]
                                                              [:strong "Fosberg Fire Weather Index (FFWI)"]
                                                              " - A fuel-independent measure of potential spread rate based on wind speed, relative humidity, and temperature."
                                                              [:br]
                                                              [:br]
                                                              [:strong "Vapor Pressure Deficit (VPD)"]
                                                              " - Difference between amount of moisture in air and how much it can hold when saturated."
                                                              [:br]
                                                              [:br]
                                                              [:strong "Hot Dry Windy Index (HDWI)"]
                                                              " - Similar to FFWI, but based on VPD."
                                                              [:br]
                                                              [:br]
                                                              [:strong "Firebrand Ignition Probability"]
                                                              " - An estimate of the probability that a burning ember could ignite a receptive fuel bed based on its temperature and moisture content."]
                                                 :options    (array-map
                                                              :tmpf   {:opt-label "Temperature (\u00B0F)"
                                                                       :filter    "tmpf"
                                                                       :units     "\u00B0F"}
                                                              :ffwi   {:opt-label "Fosberg Fire Weather Index"
                                                                       :filter    "ffwi"
                                                                       :units     ""}
                                                              :meq    {:opt-label "Fine dead fuel moisture (%)"
                                                                       :filter    "meq"
                                                                       :units     "%"}
                                                              :pign   {:opt-label "Firebrand ignition probability (%)"
                                                                       :filter    "pign"
                                                                       :units     "%"}
                                                              :rh     {:opt-label "Relative humidity (%)"
                                                                       :filter    "rh"
                                                                       :units     "%"}
                                                              :wd     {:opt-label       "Wind direction (\u00B0)"
                                                                       :filter          "wd"
                                                                       :units           "\u00B0"
                                                                       :reverse-legend? false}
                                                              :ws     {:opt-label "Sustained wind speed (mph)"
                                                                       :filter    "ws"
                                                                       :units     "mph"}
                                                              :wg     {:opt-label "Wind gust (mph)"
                                                                       :filter    "wg"
                                                                       :units     "mph"}
                                                              :apcp   {:opt-label       "Accumulated precipitation (in)"
                                                                       :filter          "apcp"
                                                                       :units           "inches"
                                                                       :disabled-for    #{:gfs0p125 :gfs0p25 :hybrid :nam-awip12 :rtma-ru}
                                                                       :reverse-legend? false}
                                                              :apcp01 {:opt-label       "1-hour precipitation (in)"
                                                                       :filter          "apcp01"
                                                                       :units           "inches"
                                                                       :disabled-for    #{:gfs0p25 :nam-awip12 :nbm :rtma-ru}
                                                                       :reverse-legend? false}
                                                              :apcp03 {:opt-label       "3-hour precipitation (in)"
                                                                       :filter          "apcp03"
                                                                       :units           "inches"
                                                                       :disabled-for    #{:gfs0p125 :gfs0p25 :hrrr :hybrid :nam-conusnest :nbm :rtma-ru}
                                                                       :reverse-legend? false}
                                                              :apcp06 {:opt-label       "6-hour precipitation (in)"
                                                                       :filter          "apcp06"
                                                                       :units           "inches"
                                                                       :disabled-for    #{:gfs0p125 :hrrr :hybrid :nam-awip12 :nam-conusnest :nbm :rtma-ru}
                                                                       :reverse-legend? false}
                                                              :vpd    {:opt-label    "Vapor pressure deficit (hPa)"
                                                                       :filter       "vpd"
                                                                       :units        "hPa"
                                                                       :disabled-for #{:nbm}}
                                                              :hdw    {:opt-label    "Hot-Dry-Windy Index (hPa*m/s)"
                                                                       :filter       "hdw"
                                                                       :units        "hPa*m/s"
                                                                       :disabled-for #{:nbm}}
                                                              :smoke  {:opt-label    "Smoke density (\u00b5g/m\u00b3)"
                                                                       :filter       "smoke"
                                                                       :units        "\u00b5g/m\u00b3"
                                                                       :disabled-for #{:gfs0p125 :gfs0p25 :hybrid :nam-awip12 :nam-conusnest :nbm :rtma-ru}}
                                                              :tcdc   {:opt-label    "Total cloud cover (%)"
                                                                       :filter       "tcdc"
                                                                       :units        "%"
                                                                       :disabled-for #{:gfs0p125 :gfs0p25 :hrrr :hybrid :nam-awip12 :nam-conusnest :nbm}})}
                                    :model      {:opt-label  "Model"
                                                 :hover-text [:p {:style {:margin-bottom "0"}}
                                                              [:strong "Hybrid"]
                                                              " - Blend of HRRR, NAM 3 km, and GFS 0.125\u00B0 to 8 days."
                                                              [:br]
                                                              [:br]
                                                              [:strong "GFS 0.125\u00B0"]
                                                              " - Global Forecast System at 0.125\u00B0 (approx 13 km) resolution to 16 days."
                                                              [:br]
                                                              [:br]
                                                              [:strong "GFS 0.250\u00B0"]
                                                              " - Global Forecast System at 0.250\u00B0 (approx 26 km) resolution to 16 days."
                                                              [:br]
                                                              [:br]
                                                              [:strong "HRRR"]
                                                              " - High Resolution Rapid Refresh at 3 km resolution to 48 hours."
                                                              [:br]
                                                              [:br]
                                                              [:strong "NAM 12 km"]
                                                              " - North American Mesoscale Model at 12 km resolution to 84 hours."
                                                              [:br]
                                                              [:br]
                                                              [:strong "NAM 3 km"]
                                                              " -  North American Mesoscale Model at 3 km resolution to 60 hours."
                                                              [:br]
                                                              [:br]
                                                              [:strong "NBM"]
                                                              " - National Blend of Models at 2.5 km to 11 days."
                                                              [:br]
                                                              [:br]
                                                              [:strong "RTMA"]
                                                              " - Real Time Mesoscale Analysis at 2.5 km resolution updated every 15 minutes."]
                                                 :options    {:hybrid        {:opt-label    "Hybrid"
                                                                              :filter       "hybrid"
                                                                              :disabled-for #{:apcp :apcp03 :apcp06 :smoke :tcdc}}
                                                              :gfs0p125      {:opt-label    "GFS 0.125\u00B0"
                                                                              :filter       "gfs0p125"
                                                                              :disabled-for #{:apcp :apcp03 :apcp06 :smoke :tcdc}}
                                                              :gfs0p25       {:opt-label    "GFS 0.250\u00B0"
                                                                              :filter       "gfs0p25"
                                                                              :disabled-for #{:apcp :apcp01 :apcp03 :smoke :tcdc}}
                                                              :hrrr          {:opt-label    "HRRR"
                                                                              :filter       "hrrr"
                                                                              :disabled-for #{:apcp03 :apcp06 :tcdc}}
                                                              :nam-awip12    {:opt-label    "NAM 12 km"
                                                                              :filter       "nam-awip12"
                                                                              :disabled-for #{:apcp :apcp01 :apcp06 :smoke :tcdc}}
                                                              :nam-conusnest {:opt-label    "NAM 3 km"
                                                                              :filter       "nam-conusnest"
                                                                              :disabled-for #{:apcp03 :apcp06 :smoke :tcdc}}
                                                              :nbm           {:opt-label    "NBM"
                                                                              :filter       "nbm"
                                                                              :disabled-for #{:apcp01 :apcp03 :apcp06 :hdw :smoke :tcdc :vpd}}
                                                              :rtma-ru       {:opt-label    "RTMA"
                                                                              :filter       "rtma-ru"
                                                                              :disabled-for #{:apcp :apcp01 :apcp03 :apcp06 :smoke}}}}
                                    :model-init {:opt-label  "Forecast Start Time"
                                                 :hover-text "Start time for the forecast cycle, new data comes every 6 hours."
                                                 :options    {:loading {:opt-label "Loading..."}}}}}
   :fire-risk    {:opt-label       "Risk"
                  :filter          "fire-risk-forecast"
                  :geoserver-key   :shasta
                  :underlays       (merge common-underlays near-term-forecast-underlays)
                  :reverse-legend? true
                  :time-slider?    true
                  :hover-text      "5-day forecast of fire consequence maps. Every day over 500 million hypothetical fires are ignited across California to evaluate potential fire risk.\n"
                  :params          {:output     {:opt-label  "Output"
                                                 :hover-text [:p {:style {:margin-bottom "0"}}
                                                              "Key fire spread model outputs based on modeling 6-hours of fire spread without fire suppression activities within 6 hours of time shown in time slider. Options include:"
                                                              [:br]
                                                              [:br]
                                                              [:strong "Relative Burn Probability"]
                                                              " - Relative likelihood that an area is burned by fires that have not yet ignited within the next six hours of time shown in time slider."
                                                              [:br]
                                                              [:br]
                                                              [:strong "Impacted Structures"]
                                                              " - Approximate number of residential structures within fire perimeter for fires starting at specific location and time in the future."
                                                              [:br]
                                                              [:br]
                                                              [:strong "Fire Area"]
                                                              " - Modeled fire size in acres by ignition location and time of ignition."
                                                              [:br]
                                                              [:br]
                                                              [:strong "Fire Volume"]
                                                              " - Modeled fire volume (fire area in acres multiplied by flame length in feet) by ignition location and time of ignition."
                                                              [:br]
                                                              [:br]
                                                              [:strong "Crown Fire Area"]
                                                              " - Description coming soon!"
                                                              [:br]
                                                              [:br]
                                                              [:strong "Power Line Ignition Rate"]
                                                              " - Estimated power line ignition rate."]
                                                 :options    {:times-burned    {:opt-label "Relative burn probability"
                                                                                :filter    "times-burned"
                                                                                :units     "Times"}
                                                              :impacted        {:opt-label "Impacted structures"
                                                                                :filter    "impacted-structures"
                                                                                :units     "Structures"}
                                                              :fire-area       {:opt-label "Fire area"
                                                                                :filter    "fire-area"
                                                                                :units     "Acres"}
                                                              :fire-volume     {:opt-label "Fire volume"
                                                                                :filter    "fire-volume"
                                                                                :units     "Acre-ft"}
                                                              :crown-fire-area {:opt-label    "Crown fire area"
                                                                                :filter       "crown-fire-area"
                                                                                :units        "Acres"
                                                                                :disabled-for #{:tlines :nve}}
                                                              :plignrate       {:opt-label    "Power line ignition rate"
                                                                                :filter       "plignrate"
                                                                                :units        "Ignitions/line-mi/hr"
                                                                                :disabled-for #{:all :tlines}}}}
                                    :pattern    {:opt-label  "Ignition Pattern"
                                                 :hover-text [:p {:style {:margin-bottom "0"}}
                                                              "Fires are ignited randomly across California at various times in the future so their impacts can be modeled. Patterns include:"
                                                              [:br]
                                                              [:br]
                                                              [:strong "Human Caused"]
                                                              " - Anthropogenic fires (fires from all causes except lightning)."
                                                              [:br]
                                                              [:br]
                                                              [:strong "Transmission Lines"]
                                                              " - Fires ignited in close proximity to overhead electrical transmission lines."]
                                                 :options    {:all        {:opt-label    "Human-caused ignitions"
                                                                           :filter       "all"
                                                                           :disabled-for #{:plignrate}}
                                                              :tlines     {:opt-label    "Transmission lines"
                                                                           :filter       "tlines"
                                                                           :clear-point? true
                                                                           :disabled-for #{:plignrate :crown-fire-area}}}}
                                    :fuel       {:opt-label  "Fuel"
                                                 :hover-text [:p {:style {:margin-bottom "0"}}
                                                              "Source of surface and canopy fuel inputs:"
                                                              [:br]
                                                              [:br]
                                                              [:strong "- 2022 California fuelscape"]
                                                              " prepared by Pyrologix, LLC ("
                                                              [:a {:href   "https://pyrologix.com"
                                                                   :target "_blank"}
                                                               "https://pyrologix.com"]
                                                              "), 2022."
                                                              [:br]
                                                              [:br]
                                                              [:strong "- California Forest Observatory"]
                                                              " – Summer 2020 at 10 m resolution. Courtesy of the California Forest Observatory ("
                                                              [:a {:href   "https://forestobservatory.com"
                                                                   :target "_blank"}
                                                               "https://forestobservatory.com"]
                                                              "), © Salo Sciences, Inc. 2020."]
                                                 :options    {:landfire {:opt-label "2022 CA fuelscape"
                                                                         :filter    "landfire"}
                                                              :cfo      {:opt-label    "2020 CA Forest Obs."
                                                                         :filter       "cfo"
                                                                         :disabled-for #{:times-burned :impacted :fire-area :fire-volume :plignrate :crown-fire-area}}}}
                                    :model      {:opt-label  "Model"
                                                 :hover-text [:p {:style {:margin-bottom "0"}}
                                                              "Computer fire spread model used to generate active fire and risk forecasts."
                                                              [:br]
                                                              [:br]
                                                              [:strong "ELMFIRE"]
                                                              " (Eulerian Level Set Model of FIRE spread) is a cloud-based deterministic fire model developed by Chris Lautenberger at Reax Engineering. Details on its mathematical implementation have been published in Fire Safety Journal ("
                                                              [:a {:href   "https://doi.org/10.1016/j.firesaf.2013.08.014"
                                                                   :target "_blank"}
                                                               "https://doi.org/10.1016/j.firesaf.2013.08.014"]
                                                              ")."]
                                                 :options    {:elmfire {:opt-label "ELMFIRE"
                                                                        :filter    "elmfire"}}}
                                    :model-init {:opt-label  "Forecast Start Time"
                                                 :hover-text "Hundreds of millions of fires are ignited across California at various times in the future and their spread is modeled under forecasted weather conditions. Data are refreshed each day at approximately 5 AM PDT."
                                                 :options    {:loading {:opt-label "Loading..."}}}}}
   :active-fire  {:opt-label       "Active Fires"
                  :filter          "fire-spread-forecast"
                  :geoserver-key   :trinity
                  :underlays       (merge common-underlays
                                          near-term-forecast-underlays
                                          {:isochrones {:opt-label        "Modeled perimeter"
                                                        :z-index          125
                                                        :filter-set       #{"isochrones"}
                                                        :dependent-inputs [:fire-name :burn-pct :fuel :model :model-init]
                                                        :disabled-for     #{:active-fires :gridfire}
                                                        :geoserver-key    :trinity}})
                  :block-info?     true
                  :reverse-legend? false
                  :time-slider?    true
                  :hover-text      "7-day forecasts of active fires with burning areas established from satellite-based heat detection."
                  :params          {:fire-name  {:opt-label      "Fire Name"
                                                 :sort?          true
                                                 :hover-text     "Provides a list of active fires for which forecasts are available. To zoom to a specific fire, select it from the dropdown menu."
                                                 :default-option :active-fires
                                                 :options        {:active-fires    {:opt-label     "*All Active Fires"
                                                                                    :style-fn      :default
                                                                                    :filter-set    #{"fire-detections" "active-fires"}
                                                                                    :auto-zoom?    false
                                                                                    :time-slider?  false
                                                                                    :geoserver-key :shasta}}}
                                    :output     {:opt-label  "Output"
                                                 :hover-text "This shows the areas where our models forecast the fire to spread over 3 days. Time can be advanced with the slider below, and the different colors on the map provide information about when an area is forecast to burn."
                                                 :options    {:burned {:opt-label       "Forecasted fire location"
                                                                       :filter          "hours-since-burned"
                                                                       :units           ""}}}
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
                                                 :hover-text [:p {:style {:margin-bottom "0"}}
                                                              "Source of surface and canopy fuel inputs:"
                                                              [:br]
                                                              [:br]
                                                              [:strong  "- 2022 California fuelscape"]
                                                              " prepared by Pyrologix, LLC ("
                                                              [:a {:href   "https://pyrologix.com"
                                                                   :target "_blank"}
                                                               "https://pyrologix.com"]
                                                              "), 2022."
                                                              [:br]
                                                              [:br]
                                                              [:strong "- California Forest Observatory"]
                                                              " – Summer 2020 at 10 m resolution. Courtesy of the California Forest Observatory ("
                                                              [:a {:href   "https://forestobservatory.com"
                                                                   :target "_blank"}
                                                               "https://forestobservatory.com"]
                                                              "), © Salo Sciences, Inc. 2020."]
                                                 :options    {:landfire {:opt-label "CA fuelscape / LANDFIRE 2.2.0"
                                                                         :filter    "landfire"}}}
                                    :model      {:opt-label  "Model"
                                                 :hover-text [:p {:style {:margin-bottom "0"}}
                                                              [:strong "ELMFIRE"]
                                                              " (Eulerian Level Set Model of FIRE spread) is a cloud-based deterministic fire model developed by Chris Lautenberger at Reax Engineering. Details on its mathematical implementation have been published in Fire Safety Journal ("
                                                              [:a {:href   "https://doi.org/10.1016/j.firesaf.2013.08.014"
                                                                   :target "_blank"}
                                                               "https://doi.org/10.1016/j.firesaf.2013.08.014"]
                                                              ")."
                                                              [:br]
                                                              [:br]
                                                              [:strong "GridFire"]
                                                              " is a fire behavior model developed by Gary Johnson of Spatial Informatics Group. It combines empirical equations from the wildland fire science literature with the performance of a raster-based spread algorithm using the method of adaptive time steps and fractional distances."]
                                                 :options    {:elmfire  {:opt-label "ELMFIRE"
                                                                         :filter    "elmfire"}
                                                              :gridfire {:enabled?  #(feature-enabled? :gridfire)
                                                                         :opt-label "GridFire"
                                                                         :filter    "gridfire"}}}
                                    :model-init {:opt-label  "Forecast Start Time"
                                                 :hover-text "This shows the date and time (24 hour time) from which the prediction starts. To view a different start time, select one from the dropdown menu. This data is automatically updated when active fires are sensed by satellites."
                                                 :options    {:loading {:opt-label "Loading..."}}}}}
   :psps-zonal   {:opt-label       "PSPS"
                  :geoserver-key   :psps
                  :filter          "psps-zonal"
                  :underlays       (merge common-underlays near-term-forecast-underlays)
                  :allowed-org     "NV Energy"
                  :reverse-legend? true
                  :time-slider?    true
                  :hover-text      "Public Safety Power Shutoffs (PSPS) zonal statistics."
                  :params          {:quantity   {:opt-label  "Zonal Quantity"
                                                 :hover-text [:p {:style {:margin-bottom "0"}}
                                                              "Public Safety Power Shutoffs (PSPS) Zonal Quantity. Options include:"
                                                              [:br]
                                                              [:br]
                                                              [:strong "Fosberg Fire Weather Index (FFWI)"]
                                                              " - A fuel-independent measure of potential spread rate based on wind speed, relative humidity, and temperature."
                                                              [:br]
                                                              [:br]
                                                              [:strong "Hot Dry Windy Index"]
                                                              " - Similar to FFWI, but based on Vapor Pressure Deficit (VPD)."
                                                              [:br]
                                                              [:br]
                                                              [:strong "Relative Burn Probability"]
                                                              " - Relative likelihood that an area is burned by fires that have not yet ignited within the next six hours of time shown in time slider."
                                                              [:br]
                                                              [:br]
                                                              [:strong "Impacted Structures"]
                                                              " - Approximate number of residential structures within fire perimeter for fires starting at specific location and time in the future."
                                                              [:br]
                                                              [:br]
                                                              [:strong "Fire Area"]
                                                              " - Modeled fire size in acres by ignition location and time of ignition."
                                                              [:br]
                                                              [:br]
                                                              [:strong "Fire Volume"]
                                                              " - Modeled fire volume (fire area in acres multiplied by flame length in feet) by ignition location and time of ignition."]
                                                 :options    (array-map
                                                              :ws   {:opt-label "Sustained wind speed (mph)"
                                                                     :filter    "nve"
                                                                     :units     "mph"}
                                                              :wg   {:opt-label "Wind gust (mph)"
                                                                     :filter    "nve"
                                                                     :units     "mph"}
                                                              :area {:opt-label "Fire area (acres)"
                                                                     :filter    "nve"
                                                                     :units     "Acres"}
                                                              :str  {:opt-label "Impacted structures"
                                                                     :filter    "nve"
                                                                     :units     "Structures"}
                                                              :vol  {:opt-label "Fire volume (acre-ft)"
                                                                     :filter    "nve"
                                                                     :units     "Acre-ft"})}
                                    :statistic  {:opt-label      "Statistic"
                                                 :hover-text     "Options are minimum, mean, or maximum."
                                                 :default-option :a
                                                 :options        {:l {:opt-label "Minimum"
                                                                      :filter    "deenergization-zones"}
                                                                  :a {:opt-label "Mean"
                                                                      :filter    "deenergization-zones"}
                                                                  :h {:opt-label "Maximum"
                                                                      :filter    "deenergization-zones"}}}
                                    :model      {:opt-label  "Model"
                                                 :hover-text [:p {:style {:margin-bottom "0"}}
                                                              [:strong "ELMFIRE"]
                                                              " (Eulerian Level Set Model of FIRE spread) is a cloud-based deterministic fire model developed by Chris Lautenberger at Reax Engineering. Details on its mathematical implementation have been published in Fire Safety Journal ("
                                                              [:a {:href   "https://doi.org/10.1016/j.firesaf.2013.08.014"
                                                                   :target "_blank"}
                                                               "https://doi.org/10.1016/j.firesaf.2013.08.014"]
                                                              ")."]
                                                 :options    {:h {:opt-label    "HRRR"
                                                                  :disabled-for #{:area :str :vol}}
                                                              :l {:opt-label    "ELMFIRE"
                                                                  :disabled-for #{:wg :ws}}}}
                                    :model-init {:opt-label  "Forecast Start Time"
                                                 :hover-text "Start time for forecast cycle, new data comes every 6 hours."
                                                 :options    {:loading {:opt-label "Loading..."}}}}}})

(def near-term-forecast-layers
  "All layers added in addition to the default Mapbox layers and their
   associated metadata for the near term forecast.

   forecast-layer? - Layers corresponding to a forecast. Excludes layers such as fire-cameras and underlays."
  {:fire-spread-forecast  {:forecast-layer? true}
   :fire-active           {:forecast-layer? true}
   :fire-active-labels    {:forecast-layer? true}
   :fire-detections       {:forecast-layer? false}
   :fire-risk-forecast    {:forecast-layer? true}
   :fire-weather-forecast {:forecast-layer? true}
   :fuels-and-topography  {:forecast-layer? true}
   :fire-history          {:forecast-layer? false}
   :fire-history-labels   {:forecast-layer? false}
   :isochrones            {:forecast-layer? false}
   :psps-static           {:forecast-layer? false}
   :psps-zonal            {:forecast-layer? true}
   :red-flag              {:forecast-layer? false}
   :fire-cameras          {:forecast-layer? false}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; WG4 Forecast
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def long-term-forecast-options
  {:fire-scenarios {:opt-label        "Fire Scenarios"
                    :filter           "climate_FireSim"
                    :geoserver-key    :pyreclimate
                    :underlays        common-underlays
                    :hover-text       "Wildfire scenario projections for area burned with varied emissions and population scenarios."
                    :reverse-legend?  true
                    :block-info?      false
                    :time-slider?     true
                    :disable-camera?  true
                    :disable-flag?    true
                    :disable-history? true
                    :params           {:model      {:opt-label  "Global Climate Model"
                                                    :hover-text "Four climate models selected by the California's Climate Action Team as priority models for research contributing to California's Fourth Climate Change Assessment.\n
                                                                 Projected future climate from these four models can be described as producing:
                                                                 HadGEM2-ES - A warmer/dry simulation
                                                                 CNRM-CM5 - A cooler/wetter simulation
                                                                 CanESM2 - An average simulation
                                                                 MIROC5 - A model that is most unlike the first three to offer the best coverage of different possibilities."
                                                    :auto-zoom? true
                                                    :options    {:can-esm2   {:opt-label "CanESM2"
                                                                              :filter    "CanESM2"
                                                                              :units     "ha"}
                                                                 :hadgem2-es {:opt-label "HadGEM2-ES"
                                                                              :filter    "HadGEM2-ES"
                                                                              :units     "ha"}
                                                                 :cnrm-cm5   {:opt-label "CNRM-CM5"
                                                                              :filter    "CNRM-CM5"
                                                                              :units     "ha"}
                                                                 :miroc5     {:opt-label "MIROC5"
                                                                              :filter    "MIROC5"
                                                                              :units     "ha"}}}
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

(def long-term-forecast-layers
  "All layers added in addition to the default Mapbox layers and their
   associated metadata for the loading term forecast.

   forecast-layer? - Layers corresponding to a forecast. Excludes layers such as fire-cameras."
  {:climate      {:forecast-layer? true}
   :fire-cameras {:forecast-layer? false}
   :red-flag     {:forecast-layer? false}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Forecast Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private forecasts {:near-term {:options-config near-term-forecast-options
                                      :layers         near-term-forecast-layers}
                          :long-term {:options-config long-term-forecast-options
                                      :layers         long-term-forecast-layers}})

(defn get-forecast
  "Retrieves the forecast options and default tab."
  [forecast-type]
  {:pre [(contains? #{:long-term :near-term} forecast-type)]}
  (forecast-type forecasts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fire Behavior Fuel Model lookup table
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
                           :description "Blowdown is total, fuelbed not compacted, foliage still attached. Spread rate very high; flame length very high."}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; WFS/WMS Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- wms-url [geoserver-key]
  (str (u-str/end-with (geoserver-key @!/geoserver-urls) "/") "wms"))

(defn- wfs-url [geoserver-key]
  (str (u-str/end-with (geoserver-key @!/geoserver-urls) "/") "wfs"))

(defn- mvt-url [geoserver-key]
  (str (u-str/end-with ((keyword geoserver-key) @!/geoserver-urls) "/") "gwc/service/wmts"))

(defn legend-url
  "Generates a URL for the legend given a layer."
  [layer geoserver-key style]
  (str (wms-url geoserver-key)
       "?SERVICE=WMS"
       "&EXCEPTIONS=application/json"
       "&VERSION=1.3.0"
       "&REQUEST=GetLegendGraphic"
       "&FORMAT=application/json"
       "&LAYER=" layer
       "&STYLE=" (or style "")))

(def all-psps-columns
  "A list of all PSPS column names. To be used as the input to the propertyName
   parameter for GetFeatureInfo in order to filter out extra info."
  (str/join ","
            ["h_wg_a"
             "h_wg_h"
             "h_wg_l"
             "h_ws_a"
             "h_ws_h"
             "h_ws_l"
             "l_area_a"
             "l_area_h"
             "l_area_l"
             "l_str_a"
             "l_str_h"
             "l_str_l"
             "l_vol_a"
             "l_vol_h"
             "l_vol_l"]))

(defn point-info-url
  "Generates a URL for the point information."
  [layer-group bbox feature-count geoserver-key & [properties]]
  (str (wms-url geoserver-key)
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
       "&BBOX=" bbox
       (when properties
         (str "&propertyName=" properties))))

(defn wms-layer-url
  "Generates a Web Mapping Service (WMS) url to download a PNG tile.

   Mapbox GL requires tiles to be projected to EPSG:3857 (Web Mercator)."
  ([layer geoserver-key style]
   (str (mvt-url geoserver-key)
        "?REQUEST=GetTile"
        "&SERVICE=WMTS"
        "&VERSION=1.0.0"
        "&LAYER=" layer
        "&STYLE=" (or style "")
        "&FORMAT=image/png"
        "&TILEMATRIX=EPSG:900913:{z}"
        "&TILEMATRIXSET=EPSG:900913"
        "&TILECOL={x}&TILEROW={y}"))

  ([layer geoserver-key style layer-time]
   (if (feature-enabled? :image-mosaic-gwc)
     (str (mvt-url geoserver-key)
          "?REQUEST=GetTile"
          "&SERVICE=WMTS"
          "&VERSION=1.0.0"
          "&LAYER=" layer
          "&STYLE=" (or style "")
          "&FORMAT=image/png"
          "&TILEMATRIX=EPSG:900913:{z}"
          "&TILEMATRIXSET=EPSG:900913"
          "&TILECOL={x}&TILEROW={y}"
          "&TIME=" layer-time)
     (str (wms-url geoserver-key)
          "?SERVICE=WMS"
          "&VERSION=1.3.0"
          "&REQUEST=GetMap"
          "&FORMAT=image/png"
          "&TRANSPARENT=true"
          "&WIDTH=256"
          "&HEIGHT=256"
          "&CRS=EPSG%3A3857"
          "&STYLES=" (or style "")
          "&FORMAT_OPTIONS=dpi%3A113"
          "&BBOX={bbox-epsg-3857}"
          "&LAYERS=" layer
          "&TIME=" layer-time))))

(defn wfs-layer-url
  "Generates a Web Feature Service (WFS) url to download an entire vector data
   set as GeoJSON.

   Mapbox GL does support GeoJSON in EPSG:4326. However, it does not support WFS."
  [layer geoserver-key]
  (str (wfs-url geoserver-key)
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
  [layer geoserver-key]
  (str (mvt-url geoserver-key)
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

(def speeds [{:opt-label ".25x" :delay 4000}
             {:opt-label ".5x"  :delay 2000}
             {:opt-label "1x"   :delay 1000}
             {:opt-label "2x"   :delay 500}
             {:opt-label "5x"   :delay 200}])

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

(defn- style-url [id]
  (str "https://api.mapbox.com/styles/v1/mspencer-sig/" id "?access_token=" @!/mapbox-access-token))

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
