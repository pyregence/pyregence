(ns pyregence.config
  (:require [clojure.string               :as str]
            [pyregence.state              :as !]
            [pyregence.utils.misc-utils   :as u-misc]
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

;; TODO add in an API route to dynamically grab this from the org_unique_id column in the organizations table
(def all-utility-companies #{:anza :beartooth :bighorn :butte :canadian-valley
                             :capital :clp :columbia-basin :consumers :cotton :cowlitz
                             :dso :flathead :garkane :grand-valley :highline
                             :holy-cross :la-plata :lea-county :liberty :lincoln
                             :midwest :missoula :mountain-view :nodak :north-fork
                             :northwestern :nve :okanogan-county :otec :pacificorp
                             :pnm :poudre-valley :ravalli :rushmore :san-isabel
                             :southeast-colorado :springer :srp :tep :trico :wasco})

(def all-utility-companies-planning (into #{} (map #(keyword (str (name %) "-planning")) all-utility-companies)))

(def near-term-forecast-underlays
  (array-map
   :state-boundaries        {:opt-label     "U.S. States"
                             :z-index       108
                             :filter-set    #{"fire-detections" "state-boundaries"}
                             :geoserver-key :shasta}
   :county-boundaries       {:opt-label     "U.S. Counties"
                             :z-index       108
                             :filter-set    #{"fire-detections" "county-boundaries"}
                             :geoserver-key :shasta}
   :us-trans-lines          {:opt-label     "Transmission lines"
                             :z-index       107
                             :filter-set    #{"fire-detections" "us-transmission-lines"}
                             :geoserver-key :shasta}
   :current-year-perimeters {:opt-label     "2026 fire perimeters"
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
   :goes-imagery            {:opt-label     "Live satellite (GOES-19)"
                             :z-index       100
                             :filter-set    #{"fire-detections" "goes19-rgb"}
                             :geoserver-key :shasta}))

(def near-term-forecast-options
  {:fuels        {:opt-label     "Fuels"
                  :filter        "fuels"
                  :geoserver-key :shasta
                  :underlays     (merge common-underlays near-term-forecast-underlays)
                  :time-slider?  false
                  :hover-text    "Layers related to fuel and potential fire behavior."
                  :params        {:layer      {:opt-label  "Layer"
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
                                                                     :convert         #(str (u-misc/direction %) " (" % "°)")
                                                                     :reverse-legend? false
                                                                     :disabled-for    #{:cecs :cfo :fire-factor-2022 :fire-factor-2023 :fire-factor-2024 :landfire-1.0.5 :landfire-1.3.0 :landfire-1.4.0 :landfire-2.0.0 :landfire-2.1.0 :landfire-2.3.0 :landfire-2.4.0 :landfire-2.5.0-2.4.0 :landfire-2.5.0}}
                                                            :slp    {:opt-label       "Slope (degrees)"
                                                                     :filter          "slp"
                                                                     :units           "\u00B0"
                                                                     :reverse-legend? true
                                                                     :disabled-for    #{:cecs :cfo :fire-factor-2022 :fire-factor-2023 :fire-factor-2024 :landfire-1.0.5 :landfire-1.3.0 :landfire-1.4.0 :landfire-2.0.0 :landfire-2.1.0 :landfire-2.3.0 :landfire-2.4.0 :landfire-2.5.0-2.4.0 :landfire-2.5.0}}
                                                            :dem    {:opt-label       "Elevation (ft)"
                                                                     :filter          "dem"
                                                                     :units           "ft"
                                                                     :convert         #(u-num/to-precision 1 (* % 3.28084))
                                                                     :reverse-legend? true
                                                                     :disabled-for    #{:cecs :cfo :fire-factor-2022 :fire-factor-2023 :fire-factor-2024 :landfire-1.0.5 :landfire-1.3.0 :landfire-1.4.0 :landfire-2.0.0 :landfire-2.1.0 :landfire-2.3.0 :landfire-2.4.0 :landfire-2.5.0-2.4.0 :landfire-2.5.0}}
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
                                  :model      {:opt-label  "Source"
                                               :hover-text [:p {:style {:margin-bottom "0"}}
                                                            [:strong "LANDFIRE"]
                                                            " –  Stock data provided by "
                                                            [:a {:href   "https://landfire.gov"
                                                                 :target "_blank"}
                                                             "LANDFIRE"]
                                                            " at 30 m resolution. For more detailed version descriptions, please visit "
                                                            [:a {:href   "https://landfire.gov/version_download.php"
                                                                 :target "_blank"}
                                                             "this link"]
                                                            " and click the \"LF Version Descriptions\" button."
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
                                                            [:strong "Fire Factor\u2122"]
                                                            " -  Data provided by "
                                                            [:a {:href   "https://riskfactor.com/methodology/fire"
                                                                 :target "_blank"}
                                                             "Fire Factor"]
                                                            ", which uses the "
                                                            [:a {:href   "https://www.mdpi.com/2571-6255/5/4/117"
                                                                 :target "_blank"}
                                                             "First Street Foundation Wildfire Model"]
                                                            "."
                                                            [:br]
                                                            [:br]
                                                            [:strong "California Ecosystem Climate Solutions"]
                                                            " - Data provided by the "
                                                            [:a {:href   "https://california-ecosystem-climate.solutions/"
                                                                 :target "_blank"}
                                                             "California Ecosystem Climate Solutions"]
                                                            ", Wang et al. (2021)."]
                                               :options    (array-map
                                                            :landfire-2.5.0       {:opt-label    "LANDFIRE 2.5.0 (2025 capable)"
                                                                                   :filter       "landfire-2.5.0"
                                                                                   :disabled-for #{:asp :slp :dem}}
                                                            :landfire-2.5.0-2.4.0 {:opt-label    "LANDFIRE 2.5.0/2.4.0 (2025/2024 capable)"
                                                                                   :filter       "landfire-2.5.0-2.4.0"
                                                                                   :disabled-for #{:asp :slp :dem}}
                                                            :landfire-2.4.0       {:opt-label    "LANDFIRE 2.4.0 (2024 capable)"
                                                                                   :filter       "landfire-2.4.0"
                                                                                   :disabled-for #{:asp :slp :dem}}
                                                            :landfire-2.3.0       {:opt-label    "LANDFIRE 2.3.0 (2023 capable)"
                                                                                   :filter       "landfire-2.3.0"
                                                                                   :disabled-for #{:asp :slp :dem}}
                                                            :landfire-2.2.0       {:opt-label "LANDFIRE 2.2.0 (2021 capable)"
                                                                                   :filter    "landfire-2.2.0"}
                                                            :landfire-2.1.0       {:opt-label    "LANDFIRE 2.1.0 (2020 capable)"
                                                                                   :filter       "landfire-2.1.0"
                                                                                   :disabled-for #{:asp :slp :dem}}
                                                            :landfire-2.0.0       {:opt-label    "LANDFIRE 2.0.0 (2019 capable)"
                                                                                   :filter       "landfire-2.0.0"
                                                                                   :disabled-for #{:asp :slp :dem}}
                                                            :landfire-1.4.0       {:opt-label    "LANDFIRE 1.4.0 (2016 capable)"
                                                                                   :filter       "landfire-1.4.0"
                                                                                   :disabled-for #{:asp :slp :dem}}
                                                            :landfire-1.3.0       {:opt-label    "LANDFIRE 1.3.0 (2014 capable)"
                                                                                   :filter       "landfire-1.3.0"
                                                                                   :disabled-for #{:asp :slp :dem}}
                                                            :landfire-1.0.5       {:opt-label    "LANDFIRE 1.0.5 (~2008 capable)"
                                                                                   :filter       "landfire-1.0.5"
                                                                                   :disabled-for #{:asp :slp :dem}}
                                                            :cfo                  {:opt-label    "California Forest Obs."
                                                                                   :filter       "cfo-2020"
                                                                                   :disabled-for #{:asp :slp :dem}}
                                                            :ca-fuelscapes-2022   {:opt-label "2022 CA fuelscape"
                                                                                   :filter    "ca-2022-fuelscape"}
                                                            :ca-fuelscapes-2021   {:opt-label "2021 CA fuelscape"
                                                                                   :filter    "ca-2021-fuelscape"}
                                                            :fire-factor-2024     {:opt-label    "Fire Factor 2024"
                                                                                   :filter       "fire-factor-2024"
                                                                                   :disabled-for #{:asp :slp :dem}}
                                                            :fire-factor-2023     {:opt-label    "Fire Factor 2023"
                                                                                   :filter       "fire-factor-2023"
                                                                                   :disabled-for #{:asp :slp :dem}}
                                                            :fire-factor-2022     {:opt-label    "Fire Factor 2022"
                                                                                   :filter       "fire-factor-2022"
                                                                                   :disabled-for #{:asp :slp :dem}}
                                                            :cecs                 {:opt-label    "CA Ecosystem Climate Solutions"
                                                                                   :filter       "cecs"
                                                                                   :disabled-for #{:asp :slp :dem :cc :ch :cbh :cbd}})}
                                  :model-init {:opt-label  "Model Creation Time"
                                               :hover-text "Time the data was created."
                                               :options    {:loading {:opt-label "Loading..."}}}}}
   :fire-weather {:opt-label       "Weather"
                  :filter          "fire-weather-forecast"
                  :geoserver-key   :shasta
                  :underlays       (merge common-underlays near-term-forecast-underlays)
                  :reverse-legend? true
                  :time-slider?    true
                  :always-utc?     true
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
                                                              :rh      {:opt-label "Relative humidity (%)"
                                                                        :filter    "rh"
                                                                        :units     "%"}
                                                              :tmpf    {:opt-label "Temperature (\u00B0F)"
                                                                        :filter    "tmpf"
                                                                        :units     "\u00B0F"}
                                                              :ffwi    {:opt-label "Fosberg Fire Weather Index"
                                                                        :filter    "ffwi"
                                                                        :units     ""}
                                                              :meq     {:opt-label "Fine dead fuel moisture (%)"
                                                                        :filter    "meq"
                                                                        :units     "%"}
                                                              :pign    {:opt-label "Firebrand ignition probability (%)"
                                                                        :filter    "pign"
                                                                        :units     "%"}
                                                              :wd      {:opt-label       "Wind direction (\u00B0)"
                                                                        :filter          "wd"
                                                                        :units           "\u00B0"
                                                                        :reverse-legend? false}
                                                              :ws      {:opt-label "Sustained wind speed (mph)"
                                                                        :filter    "ws"
                                                                        :units     "mph"}
                                                              :wg      {:opt-label "Wind gust (mph)"
                                                                        :filter    "wg"
                                                                        :units     "mph"}
                                                              :apcptot {:opt-label       "Accumulated precipitation (in)"
                                                                        :filter          "apcptot"
                                                                        :units           "inches"
                                                                        :disabled-for    #{:gfs0p125 :hybrid :rtma-ru :ecmwf :nve}
                                                                        :reverse-legend? false}
                                                              :apcp01  {:opt-label       "1-hour precipitation (in)"
                                                                        :filter          "apcp01"
                                                                        :units           "inches"
                                                                        :disabled-for    #{:nam-awip12 :nbm :cansac-wrf :rtma-ru :ecmwf :nve}
                                                                        :reverse-legend? false}
                                                              :vpd     {:opt-label    "Vapor pressure deficit (hPa)"
                                                                        :filter       "vpd"
                                                                        :units        "hPa"
                                                                        :disabled-for #{:nbm :ecmwf :nve}}
                                                              :hdw     {:opt-label    "Hot-Dry-Windy Index (hPa*m/s)"
                                                                        :filter       "hdw"
                                                                        :units        "hPa*m/s"
                                                                        :disabled-for #{:nbm :ecmwf}}
                                                              :smoke   {:opt-label    "Smoke density (\u00b5g/m\u00b3)"
                                                                        :filter       "smoke"
                                                                        :units        "\u00b5g/m\u00b3"
                                                                        :disabled-for #{:gfs0p125 :gfs0p25 :hybrid :nam-awip12 :nam-conusnest :nbm :cansac-wrf :rtma-ru :ecmwf :nve}}
                                                              :tcdc    {:opt-label    "Total cloud cover (%)"
                                                                        :filter       "tcdc"
                                                                        :units        "%"
                                                                        :disabled-for #{:gfs0p125 :gfs0p25 :hybrid :nam-awip12 :nbm :cansac-wrf :ecmwf :nve}})}
                                    :model      {:opt-label  "Model"
                                                 :hover-text [:p {:style {:margin-bottom "0"}}
                                                              [:strong "NBM"]
                                                              " - National Blend of Models at 2.5 km to 11 days."
                                                              [:br]
                                                              [:br]
                                                              [:strong "HRRR"]
                                                              " - High Resolution Rapid Refresh at 3 km resolution to 48 hours."
                                                              [:br]
                                                              [:br]
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
                                                              [:strong "NAM 12 km"]
                                                              " - North American Mesoscale Model at 12 km resolution to 84 hours."
                                                              [:br]
                                                              [:br]
                                                              [:strong "NAM 3 km"]
                                                              " -  North American Mesoscale Model at 3 km resolution to 60 hours."
                                                              [:br]
                                                              [:br]
                                                              [:strong "CANSAC WRF"]
                                                              " - California and Nevada Smoke and Air Committee (CANSAC) Weather Research and Forecasting (WRF) forecast model from Desert Research Institute."
                                                              " Two cycles per day (00z and 12z) at very high (1.33 km) resolution. See "
                                                              [:a {:href   "https://cansac.dri.edu/"
                                                                   :target "_blank"}
                                                               "https://cansac.dri.edu/"]
                                                              " for details."
                                                              [:br]
                                                              [:br]
                                                              [:strong "RTMA"]
                                                              " - Real Time Mesoscale Analysis at 2.5 km resolution updated every 15 minutes."]
                                                 :options    (array-map
                                                              :nbm           {:opt-label    "NBM"
                                                                              :filter       "nbm"
                                                                              :disabled-for #{:apcp01 :hdw :smoke :tcdc :vpd}}
                                                              :hrrr          {:opt-label "HRRR"
                                                                              :filter    "hrrr"}
                                                              :hybrid        {:opt-label    "Hybrid"
                                                                              :filter       "hybrid"
                                                                              :disabled-for #{:apcptot :smoke :tcdc}}
                                                              :gfs0p125      {:opt-label    "GFS 0.125\u00B0"
                                                                              :filter       "gfs0p125"
                                                                              :disabled-for #{:apcptot :smoke :tcdc}}
                                                              :gfs0p25       {:opt-label    "GFS 0.250\u00B0"
                                                                              :filter       "gfs0p25"
                                                                              :disabled-for #{:smoke :tcdc}}
                                                              :nam-awip12    {:opt-label    "NAM 12 km"
                                                                              :filter       "nam-awip12"
                                                                              :disabled-for #{:apcp01 :smoke :tcdc}}
                                                              :nam-conusnest {:opt-label    "NAM 3 km"
                                                                              :filter       "nam-conusnest"
                                                                              :disabled-for #{:smoke}}
                                                              :cansac-wrf    {:opt-label    "CANSAC WRF"
                                                                              :filter       "cansac-wrf"
                                                                              :disabled-for #{:apcp01 :smoke :tcdc}}
                                                              :rtma-ru       {:opt-label    "RTMA"
                                                                              :filter       "rtma-ru"
                                                                              :disabled-for #{:apcptot :apcp01 :smoke}})}
                                    :model-init {:opt-label  "Forecast Start Time"
                                                 :hover-text "Start time for the forecast cycle, new data comes every 6 hours."
                                                 :options    {:loading {:opt-label "Loading..."}}}}}
   :fire-risk    {:opt-label       "Risk"
                  :geoserver-key   :shasta
                  :underlays       (merge common-underlays near-term-forecast-underlays)
                  :reverse-legend? true
                  :time-slider?    true
                  :always-utc?     true
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
                                                              " - Estimated power line ignition rate in ignitions per line-mile per hour."]
                                                 :options    (array-map
                                                              :times-burned    {:opt-label    "Relative burn probability"
                                                                                :filter-set   #{"fire-risk-forecast" "times-burned"}
                                                                                :units        "Times"
                                                                                :disabled-for all-utility-companies-planning}
                                                              :impacted        {:opt-label    "Impacted structures"
                                                                                :filter-set   #{"fire-risk-forecast" "impacted-structures"}
                                                                                :units        "Structures"
                                                                                :disabled-for all-utility-companies-planning}
                                                              :fire-area       {:opt-label    "Fire area"
                                                                                :filter-set   #{"fire-risk-forecast" "fire-area"}
                                                                                :units        "Acres"
                                                                                :disabled-for all-utility-companies-planning}
                                                              :fire-volume     {:opt-label    "Fire volume"
                                                                                :filter-set   #{"fire-risk-forecast" "fire-volume"}
                                                                                :disabled-for all-utility-companies-planning
                                                                                :units        "Acre-ft"}
                                                              :crown-fire-area {:opt-label    "Crown fire area"
                                                                                :filter-set   #{"fire-risk-forecast" "crown-fire-area"}
                                                                                :units        "Acres"
                                                                                :disabled-for (conj all-utility-companies all-utility-companies-planning :tlines)}
                                                              :plignrate       {:opt-label    "Power line ignition rate"
                                                                                :filter-set   #{"fire-risk-forecast" "plignrate"}
                                                                                :units        "Ignitions/line-mi/hr"
                                                                                :disabled-for (conj all-utility-companies all-utility-companies-planning :all :tlines)}
                                                              :area-p          {:opt-label    "Fire area percentile"
                                                                                :filter-set   #{"fire-risk-planning" "area-p"}
                                                                                :units        "%"
                                                                                :time-slider? false
                                                                                :disabled-for (conj all-utility-companies :all :tlines)}
                                                              :struct-p        {:opt-label    "Structure consequence percentile"
                                                                                :filter-set   #{"fire-risk-planning" "struct-p"}
                                                                                :units        "%"
                                                                                :time-slider? false
                                                                                :disabled-for (conj all-utility-companies :all :tlines)}
                                                              :timber-p        {:opt-label    "Timber consequence percentile"
                                                                                :filter-set   #{"fire-risk-planning" "timber-p"}
                                                                                :units        "%"
                                                                                :time-slider? false
                                                                                :disabled-for (conj all-utility-companies :all :tlines)}
                                                              :comp-p          {:opt-label    "Composite consequence percentile"
                                                                                :filter-set   #{"fire-risk-planning" "comp-p"}
                                                                                :units        "%"
                                                                                :time-slider? false
                                                                                :disabled-for (conj all-utility-companies :all :tlines)})}
                                    :pattern    {:opt-label  "Ignition Pattern"
                                                 :hover-text [:p {:style {:margin-bottom "0"}}
                                                              "Fires are ignited randomly across California at various times in the future so their impacts can be modeled. Patterns include:"
                                                              [:br]
                                                              [:br]
                                                              [:strong "Human Caused"]
                                                              " - Anthropogenic fires (fires from all causes except lightning)."
                                                              [:br]
                                                              [:br]
                                                              [:strong "Transmission Lines/Overhead Lines"]
                                                              " - Fires ignited in close proximity to overhead electrical transmission lines."]
                                                 :options    {:all    {:opt-label    "All-cause fires"
                                                                       :auto-zoom?   true
                                                                       :filter       "all"
                                                                       :disabled-for #{:plignrate :area-p :struct-p :timber-p :comp-p}}
                                                              :tlines {:opt-label    "Transmission lines"
                                                                       :auto-zoom?   true
                                                                       :filter       "tlines"
                                                                       :clear-point? true
                                                                       :disabled-for #{:crown-fire-area :plignrate :area-p :struct-p :timber-p :comp-p}}}}
                                    :fuel       {:opt-label  "Fuel"
                                                 :hover-text [:p {:style {:margin-bottom "0"}}
                                                              "Source of surface and canopy fuel inputs:"
                                                              [:br]
                                                              [:br]
                                                              [:a {:href   "https://landfire.gov"
                                                                   :target "_blank"}
                                                               "LANDFIRE"]
                                                              " 2.3.0/2.2.0 at 30 m resolution. For more detailed version descriptions, please visit "
                                                              [:a {:href   "https://landfire.gov/version_download.php"
                                                                   :target "_blank"}
                                                               "this link"]
                                                              " and click the \"LF Version Descriptions\" button."]
                                                 :options    {:landfire {:opt-label "LANDFIRE 2.3.0/2.2.0"
                                                                         :filter    "landfire"}}}
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
                  :underlays       (merge common-underlays
                                          near-term-forecast-underlays
                                          {:isochrones {:opt-label        "Modeled perimeter"
                                                        :z-index          125
                                                        :filter-set       #{"isochrones"}
                                                        :dependent-inputs [:fire-name :burn-pct :fuel :model :model-init]
                                                        :disabled-for     #{:active-fires}
                                                        :geoserver-key    :trinity}})
                  :block-info?     true
                  :reverse-legend? true
                  :time-slider?    true
                  :hover-text      "14-day forecasts of active fires with burning areas established from satellite-based heat detection."
                  :params          {:fire-name  {:opt-label      "Fire Name"
                                                 :sort?          true
                                                 :hover-text     "Provides a list of active fires for which forecasts are available. To zoom to a specific fire, select it from the dropdown menu."
                                                 :default-option :active-fires
                                                 :options        {:active-fires {:opt-label            "*All Active Fires"
                                                                                 :style-fn             :default
                                                                                 :exclusive-filter-set #{"fire-detections" "active-fires"}
                                                                                 :auto-zoom?           true
                                                                                 :time-slider?         false
                                                                                 :geoserver-key        :shasta}}}
                                    :output     {:opt-label  "Output"
                                                 :hover-text "Available outputs are fire location, crown fire type (surface fire, passive, or active), flame length (ft), and surface fire spread rate (ft/min). Time can be advanced with the slider centered below."
                                                 :options    {:burned       {:opt-label       "Forecasted fire location"
                                                                             :filter          "hours-since-burned"
                                                                             :units           ""
                                                                             :reverse-legend? false}
                                                              :crown-fire   {:opt-label       "Crown Fire"
                                                                             :filter          "crown-fire"
                                                                             :units           ""
                                                                             :reverse-legend? false}
                                                              :flame-length {:opt-label "Flame Length"
                                                                             :filter    "flame-length"
                                                                             :units     "ft"}
                                                              :spread-rate  {:opt-label "Spread Rate"
                                                                             :filter    "spread-rate"
                                                                             :units     "ft/min"}}}
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
                                    :fuel       {:opt-label  "Fuels"
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
                                                 :options    {:landfire {:opt-label "LANDFIRE 2.4.0/2.3.0"
                                                                         :filter    "landfire"}}}
                                    :weather    {:opt-label  "Weather Model"
                                                 :hover-text [:p {:style {:margin-bottom "0"}}
                                                              [:strong "Hybrid"]
                                                              " - Blend of HRRR, NAM 3 km, and GFS 0.125\u00B0 to 8 days."]
                                                 :options    {:hybrid {:opt-label "Hybrid"}}}
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
                                                              " is a fire behavior model developed by Gary Johnson of Spatial Informatics Group. It combines empirical equations from the wildland fire science literature with the performance of a raster-based spread algorithm using the method of adaptive time steps and fractional distances."
                                                              [:br]
                                                              [:br]
                                                              [:a {:href   "https://pypi.org/project/pyretechnics/"
                                                                   :target "_blank"}
                                                               [:strong "Pyretechnics"]]
                                                              " is an "
                                                              [:a {:href   "https://github.com/pyregence/pyretechnics/ "
                                                                   :target "_blank"}
                                                               "open source library"]
                                                              ", created by the PyreCast team, that provides modules for surface, crown, and spot fire behavior along with areal burning and fire perimeter tracking. In PyreCast, it is configured to run using a novel implementation of the Eulerian Level Set Model of Fire Spread, similar to the ELMFIRE model. Pyretechnics documentation can be found on its official documentation "
                                                              [:a {:href  "https://pyregence.github.io/pyretechnics/"
                                                                   :target "_blank"}
                                                               "site"]
                                                              "."]
                                                 :options    {:elmfire      {:opt-label "ELMFIRE"
                                                                             :filter    "elmfire"}
                                                              :pyretechnics {:enabled?  #(feature-enabled? :pyretechnics)
                                                                             :opt-label "Pyretechnics (Beta)"
                                                                             :filter    "pyretechnics"}
                                                              :gridfire     {:enabled?  #(feature-enabled? :gridfire)
                                                                             :opt-label "GridFire"
                                                                             :filter    "gridfire"}}}
                                    :model-init {:opt-label  "Forecast Start Time"
                                                 :hover-text "This shows the date and time (24 hour time) from which the prediction starts. To view a different start time, select one from the dropdown menu. This data is automatically updated when active fires are sensed by satellites."
                                                 :disabled   (fn [selected-set]
                                                               (some (->> selected-set
                                                                          (vals)
                                                                          (filter keyword?)
                                                                          (set))
                                                                     #{:active-fires}))
                                                 :options    {:loading {:opt-label "Loading..."}}}}}
   :psps-zonal   {:opt-label       "PSPS"
                  :filter          "psps-zonal"
                  :geoserver-key   :psps
                  :underlays       (merge common-underlays near-term-forecast-underlays)
                  :reverse-legend? true
                  :time-slider?    true
                  :hover-text      "Public Safety Power Shutoffs (PSPS) zonal statistics."
                  :params          {:quantity   {:opt-label  "Zonal Quantity"
                                                 :hover-text [:<>
                                                              [:p {:style {:margin-bottom "0"}}
                                                               "Public Safety Power Shutoffs (PSPS) Zonal Quantity. Options include:"
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
                                                               [:strong "Power Line Ignition Rate"]
                                                               " - Estimated power line ignition rate in ignitions per line-mile per hour."
                                                               [:strong "Fosberg Fire Weather Index (FFWI)"]
                                                               " - A fuel-independent measure of potential spread rate based on wind speed, relative humidity, and temperature."
                                                               [:br]
                                                               [:br]
                                                               [:strong "Firebrand Ignition Probability"]
                                                               " - An estimate of the probability that a burning ember could ignite a receptive fuel bed based on its temperature and moisture content."]
                                                              [:div {:style {:margin-top 10}}
                                                               [:hr]
                                                               [:strong "Note"]
                                                               ": The impacted structrures, fire area, fire volume, and power line ignition rate quantities are only available with the ELMFIRE model."]]
                                                 :options    (array-map
                                                              :str   {:opt-label    "Impacted structures"
                                                                      :units        "Structures"
                                                                      :disabled-for #{:r :n1 :n2 :g1 :g2 :b}}
                                                              :area  {:opt-label    "Fire area (acres)"
                                                                      :units        "Acres"
                                                                      :disabled-for #{:r :n1 :n2 :g1 :g2 :b}}
                                                              :vol   {:opt-label    "Fire volume (acre-ft)"
                                                                      :units        "Acre-ft"
                                                                      :disabled-for #{:r :n1 :n2 :g1 :g2 :b}}
                                                              :pligr {:opt-label    "Power line ignition rate"
                                                                      :units        "Ignitions/line-mi/hr"
                                                                      :disabled-for #{:r :n1 :n2 :g1 :g2 :b}}
                                                              :ws    {:opt-label    "Sustained wind speed (mph)"
                                                                      :units        "mph"
                                                                      :disabled-for #{:m}}
                                                              :wg    {:opt-label    "Wind gust (mph)"
                                                                      :units        "mph"
                                                                      :disabled-for #{:m}}
                                                              :wd    {:opt-label    "Wind direction (\u00B0)"
                                                                      :units        "\u00B0"
                                                                      :disabled-for #{:m}}
                                                              :ffwi  {:opt-label    "Fosberg Fire Weather Index"
                                                                      :units        ""
                                                                      :disabled-for #{:m}}
                                                              :rh    {:opt-label    "Relative humidity (%)"
                                                                      :units        "%"
                                                                      :disabled-for #{:m}}
                                                              :tmpf  {:opt-label    "Temperature (\u00B0F)"
                                                                      :units        "\u00B0F"
                                                                      :disabled-for #{:m}}
                                                              :pign  {:opt-label    "Firebrand ignition probability (%)"
                                                                      :units        "%"
                                                                      :disabled-for #{:m}})}
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
                                                              ")."
                                                              [:br]
                                                              [:br]
                                                              [:strong "HRRR"]
                                                              " - High Resolution Rapid Refresh at 3 km resolution to 48 hours."
                                                              [:br]
                                                              [:br]
                                                              [:strong "NAM 3 km"]
                                                              " -  North American Mesoscale Model at 3 km resolution to 60 hours."
                                                              [:br]
                                                              [:br]
                                                              [:strong "NAM 12 km"]
                                                              " - North American Mesoscale Model at 12 km resolution to 84 hours."
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
                                                              [:strong "NBM"]
                                                              " - National Blend of Models at 2.5 km to 11 days."]
                                                 :options    {:m  {:opt-label "ELMFIRE"}
                                                              :r  {:opt-label "HRRR"}
                                                              :n1 {:opt-label "NAM 3 km"}
                                                              :n2 {:opt-label "NAM 12 km"}
                                                              :g1 {:opt-label "GFS 0.125\u00B0"}
                                                              :g2 {:opt-label "GFS 0.250\u00B0"}
                                                              :b  {:opt-label "NBM"}}}
                                    :utility    {:opt-label  "Utility Company"
                                                 :hover-text "The utility company associated with the displayed zonal statistics."
                                                 :options    {:loading {:opt-label "Loading..."}}}
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
   :fire-risk-planning    {:forecast-layer? true}
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
            ["b_ffwi_a" "b_ffwi_h" "b_ffwi_l" "b_pign_a" "b_pign_h" "b_pign_l"
             "b_rh_a" "b_rh_h" "b_rh_l" "b_tmpf_a" "b_tmpf_h" "b_tmpf_l" "b_wd_a"
             "b_wd_h" "b_wd_l" "b_wg_a" "b_wg_h" "b_wg_l" "b_ws_a" "b_ws_h" "b_ws_l"
             "g1_ffwi_a" "g1_ffwi_h" "g1_ffwi_l" "g1_pign_a" "g1_pign_h" "g1_pign_l"
             "g1_rh_a" "g1_rh_h" "g1_rh_l" "g1_tmpf_a" "g1_tmpf_h" "g1_tmpf_l"
             "g1_wd_a" "g1_wd_h" "g1_wd_l" "g1_wg_a" "g1_wg_h" "g1_wg_l" "g1_ws_a"
             "g1_ws_h" "g1_ws_l" "g2_ffwi_a" "g2_ffwi_h" "g2_ffwi_l" "g2_pign_a"
             "g2_pign_h" "g2_pign_l" "g2_rh_a" "g2_rh_h" "g2_rh_l" "g2_tmpf_a"
             "g2_tmpf_h" "g2_tmpf_l" "g2_wd_a" "g2_wd_h" "g2_wd_l" "g2_wg_a"
             "g2_wg_h" "g2_wg_l" "g2_ws_a" "g2_ws_h" "g2_ws_l"
             "m_area_a" "m_area_h" "m_area_l" "m_pligr_a" "m_pligr_h" "m_pligr_l"
             "m_str_a" "m_str_h" "m_str_l" "m_vol_a" "m_vol_h" "m_vol_l" "n1_ffwi_a"
             "n1_ffwi_h" "n1_ffwi_l" "n1_pign_a" "n1_pign_h" "n1_pign_l" "n1_rh_a"
             "n1_rh_h" "n1_rh_l" "n1_tmpf_a" "n1_tmpf_h" "n1_tmpf_l" "n1_wd_a"
             "n1_wd_h" "n1_wd_l" "n1_wg_a" "n1_wg_h" "n1_wg_l" "n1_ws_a" "n1_ws_h"
             "n1_ws_l" "n2_ffwi_a" "n2_ffwi_h" "n2_ffwi_l" "n2_pign_a" "n2_pign_h"
             "n2_pign_l" "n2_rh_a" "n2_rh_h" "n2_rh_l" "n2_tmpf_a" "n2_tmpf_h"
             "n2_tmpf_l" "n2_wd_a" "n2_wd_h" "n2_wd_l" "n2_wg_a" "n2_wg_h"
             "n2_wg_l" "n2_ws_a" "n2_ws_h" "n2_ws_l" "r_ffwi_a" "r_ffwi_h"
             "r_ffwi_l" "r_pign_a" "r_pign_h" "r_pign_l" "r_rh_a" "r_rh_h"
             "r_rh_l" "r_tmpf_a" "r_tmpf_h" "r_tmpf_l" "r_wd_a" "r_wd_h"
             "r_wd_l" "r_wg_a" "r_wg_h" "r_wg_l" "r_ws_a" "r_ws_h" "r_ws_l"]))

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
;; Mapbox Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def mapbox-public-user-name
  "This is global for all the standard mapbox basemap styles.
   See documentation at https://docs.mapbox.com/api/maps/styles/#classic-mapbox-styles"
  "mapbox")

(defn- style-url [id]
  (str "https://api.mapbox.com/styles/v1/" mapbox-public-user-name "/" id "?access_token=" @!/mapbox-access-token))

(defn base-map-options
  "Provides the configuration for the different Mapbox map view options."
  []
  {:mapbox-streets           {:opt-label "Mapbox Streets"
                              :source    (style-url "streets-v11")}
   :mapbox-satellite-streets {:opt-label "Mapbox Satellite Streets"
                              :source    (style-url "satellite-streets-v11")}
   :mapbox-outdoors          {:opt-label "Mapbox Outdoors"
                              :source    (style-url "outdoors-v11")}
   :mapbox-light             {:opt-label "Mapbox Light"
                              :source    (style-url "light-v11")}
   :mapbox-dark              {:opt-label "Mapbox Dark"
                              :source    (style-url "dark-v11")}})

(def base-map-default :mapbox-streets)

(def mapbox-dem-url "mapbox://mapbox.mapbox-terrain-dem-v1")
