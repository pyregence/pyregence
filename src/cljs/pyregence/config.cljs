(ns pyregence.config
  (:require [clojure.set                  :as set]
            [clojure.string               :as str]
            [pyregence.state              :as !]
            [pyregence.utils.misc-utils   :as u-misc]
            [pyregence.utils.number-utils :as u-num]
            [pyregence.utils.string-utils :as u-str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NFDRS Weather Params
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def nfdrs-weather-params
  {:erc    {:opt-label "Energy Release Component (ERC, Btu/sq ft)"
            :filter "erc"
            :units "(ERC, Btu/sq ft)"}
   :ercperc {:opt-label "ERC percentile"
             :filter "ercperc"}
   :bi {:opt-label "burning index (BI, ft * 10)"
        :filter "bi"
        :units "(BI, ft * 10)"}
   :biperc {:opt-label  "BI percentile"
            :filter "biperc"}
   :sc      {:opt-label "spread component (ft/min)"
             :filter "sc"
             :units "(ft/min)"}
   :scperc  {:opt-label  "SC percentile"
             :filter     "scperc"}
   :ic    {:opt-label "Ignition Component (%)"
           :filter "ic"}
   :sfdiperc {:opt-label "SFDI percentile (%)"
              :filter "sfdiperc"}
   :sfdicat  {:opt-label "SFDI category (1=low, 2=moderate, 3=high, 4=very high, 5=severe)"
              :filter "sfdicat"}
   :lh {:opt-label "Live herbaceous fuel moisture (% or fraction)"
        :filter "lh"
        :units "(% or fraction)"}
   :lw {:opt-label "Live woody fuel moisture (% or fraction)"
        :filter "lw"
        :units "(% or fraction)"}
   :m1  {:opt-label "1-hour fuel moisture (% or fraction)"
         :filter "m1"
         :units "(% or fraction)"}
   :m10 {:opt-label "10-hour fuel moisture (% or fraction)"
         :filter "m10"
         :units "(% or fraction)"}
   :m100 {:opt-label "100-hour fuel moisture (% or fraction)"
          :filter "m100"
          :units "(% or fraction)"}
   :m1000 {:opt-label "1000-hour fuel moisture (% or fraction)"
           :filter "m1000"
           :units "(% or fraction)"}
   :kbdiI​ {:opt-label "Keetch Byram Drought Index (0-800)"
            :filter "kbdiI"
            :units "Index (0-800)"}})

(defn nfdrs?
  []
  (#{:nfdrs-variable :nfdrs-constant} (-> @!/*params :fire-weather :model)))

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
;; Match Drop Fuel Versions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-fuel-version "2.5.0")

(defn match-drop-fuel-versions
  ([] (match-drop-fuel-versions ["2.5.0" "2.4.0" "2.3.0" "2.2.0" "2.1.0" "1.4.0" "1.3.0" "1.0.5"]))
  ([versions]
   (into (array-map)
         (map (fn [v] [v {:opt-label (str "LANDFIRE " v)}]))
         versions)))

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
(def all-utility-companies #{:anza :atco :beartooth :bighorn :butte :canadian-valley
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
                                                                     :reverse-legend? false
                                                                     :disabled-for    #{:cffdrs-2024}}
                                                            :fbp    {:opt-label       "Fire Behaviour Prediction System"
                                                                     :filter          "fbp"
                                                                     :units           ""
                                                                     :reverse-legend? false
                                                                     :disabled-for    #{:cecs :cfo :ca-fuelscapes-2022 :ca-fuelscapes-2021
                                                                                         :fire-factor-2022 :fire-factor-2023 :fire-factor-2024
                                                                                         :landfire-1.0.5 :landfire-1.3.0 :landfire-1.4.0
                                                                                         :landfire-2.0.0 :landfire-2.1.0 :landfire-2.2.0
                                                                                         :landfire-2.3.0 :landfire-2.4.0 :landfire-2.5.0
                                                                                         :landfire-2.5.0-2.4.0}}
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
                                                                     :disabled-for    #{:cecs :cffdrs-2024}}
                                                            :ch     {:opt-label       "Canopy Height (m)"
                                                                     :filter          "ch"
                                                                     :units           "m"
                                                                     :no-convert      #{:cfo}
                                                                     :convert         #(u-num/to-precision 1 (/ % 10))
                                                                     :reverse-legend? true
                                                                     :disabled-for    #{:cecs :cffdrs-2024}}
                                                            :cbh    {:opt-label       "Canopy Base Height (m)"
                                                                     :filter          "cbh"
                                                                     :units           "m"
                                                                     :no-convert      #{:cfo}
                                                                     :convert         #(u-num/to-precision 1 (/ % 10))
                                                                     :reverse-legend? true
                                                                     :disabled-for    #{:cecs :cffdrs-2024}}
                                                            :cbd    {:opt-label       "Crown Bulk Density (kg/m\u00b3)"
                                                                     :filter          "cbd"
                                                                     :units           "kg/m\u00b3"
                                                                     :convert         #(u-num/to-precision 2 (/ % 100))
                                                                     :no-convert      #{:cfo}
                                                                     :reverse-legend? true
                                                                     :disabled-for    #{:cecs :cffdrs-2024}})}
                                  :model      {:opt-label  "Source"
                                               :hover-text [:p {:style {:margin-bottom "0"}}
                                                            [:strong "LANDFIRE"]
                                                            " –  Stock data provided by "
                                                            [:a {:href   "https://landfire.gov"
                                                                 :target "_blank"}
                                                             "LANDFIRE"]
                                                            " at 30 m resolution. For more detailed version descriptions, please visit "
                                                            [:a {:href   "https://landfire.gov/version_download.php"
                                                                 :target "_blank}"
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
                                                            ", Wang et al. (2021)."
                                                            [:br]
                                                            [:br]
                                                            [:strong "CFFDRS 2024"]
                                                            " - Canadian Forest Fire Danger Rating System. Details coming soon."]
                                               :options    (array-map
                                                            :cffdrs-2024          {:opt-label    "CFFDRS 2024"
                                                                                   :filter       "cffdrs-2024"
                                                                                   :disabled-for #{:fbfm40 :cc :ch :cbh :cbd}}
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
                                                                                :disabled-for (set/union all-utility-companies all-utility-companies-planning #{:tlines})}
                                                              :plignrate       {:opt-label    "Power line ignition rate"
                                                                                :filter-set   #{"fire-risk-forecast" "plignrate"}
                                                                                :units        "Ignitions/line-mi/hr"
                                                                                :disabled-for (set/union all-utility-companies all-utility-companies-planning #{:all :tlines})}
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
                  ;; NOTE: array-map is used to preserve insertion order. With 9+ entries a
                  ;; map literal becomes an unordered hash-map, which would scramble the
                  ;; collapsible-panel render order of these params.
                  :params          (array-map
                                    :fire-name       {:opt-label      "Fire Name"
                                                      :sort?          true
                                                      :hover-text     "Provides a list of active fires for which forecasts are available. To zoom to a specific fire, select it from the dropdown menu."
                                                      :default-option :active-fires
                                                      :resets         {:match-drop-name :none :wui-fire-name :none}
                                                      :options        {:none         {:opt-label "\u2014"
                                                                                      :hidden?   true}
                                                                       :active-fires {:opt-label            "*All Active Fires"
                                                                                      :style-fn             :default
                                                                                      :exclusive-filter-set #{"fire-detections" "active-fires"}
                                                                                      :auto-zoom?           true
                                                                                      :time-slider?         false
                                                                                      :geoserver-key        :shasta}}}
                                    :match-drop-name {:opt-label      "Match Drop"
                                                      :sort?          true
                                                      :hidden?        true
                                                      :hover-text     "Select a match drop fire to view."
                                                      :default-option :none
                                                      :resets         {:fire-name :none :wui-fire-name :none}
                                                      :options        {:none {:opt-label "None"}}}
                                    :wui-fire-name   {:opt-label      "WUI Fire"
                                                      :sort?          true
                                                      :hidden?        true
                                                      :hover-text     "Select a WUI active fire to view."
                                                      :default-option :none
                                                      :resets         {:fire-name :none :match-drop-name :none}
                                                      :options        {:none {:opt-label "None"}}}
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
                                                                             :filter    "pyretec"}}}
                                    :model-init {:opt-label  "Forecast Start Time"
                                                 :hover-text "This shows the date and time (24 hour time) from which the prediction starts. To view a different start time, select one from the dropdown menu. This data is automatically updated when active fires are sensed by satellites."
                                                 :disabled   (fn [selected-set]
                                                               (some (->> selected-set
                                                                          (vals)
                                                                          (filter keyword?)
                                                                          (set))
                                                                     #{:active-fires}))
                                                 :options    {:loading {:opt-label "Loading..."}}})}
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
   :match-drop-forecast   {:forecast-layer? true}
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
;; Fire Behaviour Prediction (FBP) System fuel type lookup table
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  "fbp-lookup is keyed by the integer raster values in fbp.tif, per the
   LF2025_CFFDRS.csv attribute table crosswalk. Two gaps remain, flagged inline
   with TODO descriptions: value 12 (D-2, standalone) and value 13 (D-1/D-2
   combined) have no official short description in the source FBP fuels table --
   fill these in from the CFFDRS FBP System Guide if a more precise Point Info
   Tool blurb is wanted.")

(def fbp-lookup {-9999 {:label       "Fill-NoData"
                        :fuel-type   "Fill-NoData"
                        :description "No data; outside CFFDRS 2024 coverage area."}
                 1     {:label       "Spruce-Lichen Woodland"
                        :fuel-type   "C-1"
                        :description "Open conifer stand of varying heights. Branches reach to the ground. Lichens thickly cover open floor. Open conifer stand of varying heights. Branches reach to the ground. Lichens thickly cover open floor."}
                 2     {:label       "Boreal Spruce"
                        :fuel-type   "C-2"
                        :description "Pure, moderately dense conifer stand. Branches reach to the ground. Lichens cover dead branches. Thick ground layer of organics."}
                 3     {:label       "Mature Jack or Lodgepole Pine"
                        :fuel-type   "C-3"
                        :description "Dense stand of mature conifers. Smaller trees grow beneath thick canopy. Forest floor scattered with debris and carpet of feather moss."}
                 4     {:label       "Immature Jack or Lodgepole Pine"
                        :fuel-type   "C-4"
                        :description "Dense stand of conifers. Lots of standing dead trees and fallen debris. Needle litter covers the ground."}
                 5     {:label       "Red and White Pine"
                        :fuel-type   "C-5"
                        :description "Mature stand of conifers. Some smaller shrubs and trees. Debris is minimal. Ground is a mixture of herbaceous plants and pine litter."}
                 6     {:label       "Conifer Plantation"
                        :fuel-type   "C-6"
                        :description "Mature plantation of conifers. Canopy is dense and closed. Absence of understory vegetation. Thick layer of needle litter."}
                 7     {:label       "Ponderosa Pine-Douglas-Fir"
                        :fuel-type   "C-7"
                        :description "Mixed, nonuniform conifer stand. Space between trees. Forest floor covered thinly in grasses, herbs, a few shrubs and needles."}
                 11    {:label       "Leafless Aspen"
                        :fuel-type   "D-1"
                        :description "Pure, semi-mature deciduous stand pre bud or post leaf fall. No conifers. Medium to tall shrubs. Layer of dry plant material on ground."}
                 12    {:label       "Green Aspen (with BUI Thresholding)"
                        :fuel-type   "D-2"
                        :description "TODO -- no source description available."}
                 13    {:label       "Aspen"
                        :fuel-type   "D-1/D-2"
                        :description "TODO -- combined class (D-1/D-2); no source description available for one or more constituents."}
                 21    {:label       "Jack or Lodgepole Pine Slash"
                        :fuel-type   "S-1"
                        :description "Cleared area with debris from the logging of mature conifers. Leftover tops and branches still have half their foliage."}
                 22    {:label       "White Spruce-Balsam Slash"
                        :fuel-type   "S-2"
                        :description "Debris from the logging of mature conifers visible. Leftover tops and branches have half their foliage. Moss, needles, rotting wood remain."}
                 23    {:label       "Coastal Cedar-Hemlock-Douglas-Fir Slash"
                        :fuel-type   "S-3"
                        :description "Debris from the logging of old mixed forests visible. A lot of branches, bits of trees and leaves and needles remain."}
                 31    {:label       "Matted Grass"
                        :fuel-type   "O-1a"
                        :description "Large, grass covered area. Shrubs and trees in the background. Dried or dead material in grassland."}
                 32    {:label       "Standing Grass"
                        :fuel-type   "O-1b"
                        :description "Large, grass covered area. Shrubs and trees in the background. Dried or dead material in grassland."}
                 33    {:label       "Grass"
                        :fuel-type   "O-1a/O-1b"
                        :description "O-1a: Large, grass covered area. Shrubs and trees in the background. Dried or dead material in grassland. / O-1b: Large, grass covered area. Shrubs and trees in the background. Dried or dead material in grassland."}
                 40    {:label       "Boreal Mixedwood-Leafless"
                        :fuel-type   "M-1"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 50    {:label       "Boreal Mixedwood-Green"
                        :fuel-type   "M-2"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 60    {:label       "Boreal Mixedwood"
                        :fuel-type   "M-1/M-2"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2: Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 70    {:label       "Dead Balsam Fir Mixedwood-Leafless"
                        :fuel-type   "M-3"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 80    {:label       "Dead Balsam Fir Mixedwood-Green"
                        :fuel-type   "M-4"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 90    {:label       "Dead Balsam Fir Mixedwood"
                        :fuel-type   "M-3/M-4"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4: Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 100   {:label       "Not Available"
                        :fuel-type   "Non-fuel"
                        :description "No data available for this pixel."}
                 101   {:label       "Non-fuel"
                        :fuel-type   "Non-fuel"
                        :description "Not enough fuel to propagate wildland fire."}
                 102   {:label       "Water"
                        :fuel-type   "Non-fuel"
                        :description "Open water."}
                 103   {:label       "Unknown"
                        :fuel-type   "Non-fuel"
                        :description "Unknown or unclassified pixel."}
                 104   {:label       "Unclassified"
                        :fuel-type   "Non-fuel"
                        :description "Unknown or unclassified pixel."}
                 105   {:label       "Vegetated Non-Fuel"
                        :fuel-type   "Non-fuel"
                        :description "Vegetated cover with insufficient fuel to propagate wildland fire."}
                 405   {:label       "Boreal Mixedwood-Leafless (05% Conifer)"
                        :fuel-type   "M-1 (05 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 410   {:label       "Boreal Mixedwood-Leafless (10% Conifer)"
                        :fuel-type   "M-1 (10 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 415   {:label       "Boreal Mixedwood-Leafless (15% Conifer)"
                        :fuel-type   "M-1 (15 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 420   {:label       "Boreal Mixedwood-Leafless (20% Conifer)"
                        :fuel-type   "M-1 (20 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 425   {:label       "Boreal Mixedwood-Leafless (25% Conifer)"
                        :fuel-type   "M-1 (25 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 430   {:label       "Boreal Mixedwood-Leafless (30% Conifer)"
                        :fuel-type   "M-1 (30 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 435   {:label       "Boreal Mixedwood-Leafless (35% Conifer)"
                        :fuel-type   "M-1 (35 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 440   {:label       "Boreal Mixedwood-Leafless (40% Conifer)"
                        :fuel-type   "M-1 (40 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 445   {:label       "Boreal Mixedwood-Leafless (45% Conifer)"
                        :fuel-type   "M-1 (45 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 450   {:label       "Boreal Mixedwood-Leafless (50% Conifer)"
                        :fuel-type   "M-1 (50 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 455   {:label       "Boreal Mixedwood-Leafless (55% Conifer)"
                        :fuel-type   "M-1 (55 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 460   {:label       "Boreal Mixedwood-Leafless (60% Conifer)"
                        :fuel-type   "M-1 (60 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 465   {:label       "Boreal Mixedwood-Leafless (65% Conifer)"
                        :fuel-type   "M-1 (65 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 470   {:label       "Boreal Mixedwood-Leafless (70% Conifer)"
                        :fuel-type   "M-1 (70 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 475   {:label       "Boreal Mixedwood-Leafless (75% Conifer)"
                        :fuel-type   "M-1 (75 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 480   {:label       "Boreal Mixedwood-Leafless (80% Conifer)"
                        :fuel-type   "M-1 (80 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 485   {:label       "Boreal Mixedwood-Leafless (85% Conifer)"
                        :fuel-type   "M-1 (85 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 490   {:label       "Boreal Mixedwood-Leafless (90% Conifer)"
                        :fuel-type   "M-1 (90 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 495   {:label       "Boreal Mixedwood-Leafless (95% Conifer)"
                        :fuel-type   "M-1 (95 PC)"
                        :description "Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages."}
                 505   {:label       "Boreal Mixedwood-Green (05% Conifer)"
                        :fuel-type   "M-2 (05 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 510   {:label       "Boreal Mixedwood-Green (10% Conifer)"
                        :fuel-type   "M-2 (10 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 515   {:label       "Boreal Mixedwood-Green (15% Conifer)"
                        :fuel-type   "M-2 (15 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 520   {:label       "Boreal Mixedwood-Green (20% Conifer)"
                        :fuel-type   "M-2 (20 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 525   {:label       "Boreal Mixedwood-Green (25% Conifer)"
                        :fuel-type   "M-2 (25 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 530   {:label       "Boreal Mixedwood-Green (30% Conifer)"
                        :fuel-type   "M-2 (30 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 535   {:label       "Boreal Mixedwood-Green (35% Conifer)"
                        :fuel-type   "M-2 (35 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 540   {:label       "Boreal Mixedwood-Green (40% Conifer)"
                        :fuel-type   "M-2 (40 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 545   {:label       "Boreal Mixedwood-Green (45% Conifer)"
                        :fuel-type   "M-2 (45 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 550   {:label       "Boreal Mixedwood-Green (50% Conifer)"
                        :fuel-type   "M-2 (50 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 555   {:label       "Boreal Mixedwood-Green (55% Conifer)"
                        :fuel-type   "M-2 (55 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 560   {:label       "Boreal Mixedwood-Green (60% Conifer)"
                        :fuel-type   "M-2 (60 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 565   {:label       "Boreal Mixedwood-Green (65% Conifer)"
                        :fuel-type   "M-2 (65 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 570   {:label       "Boreal Mixedwood-Green (70% Conifer)"
                        :fuel-type   "M-2 (70 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 575   {:label       "Boreal Mixedwood-Green (75% Conifer)"
                        :fuel-type   "M-2 (75 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 580   {:label       "Boreal Mixedwood-Green (80% Conifer)"
                        :fuel-type   "M-2 (80 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 585   {:label       "Boreal Mixedwood-Green (85% Conifer)"
                        :fuel-type   "M-2 (85 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 590   {:label       "Boreal Mixedwood-Green (90% Conifer)"
                        :fuel-type   "M-2 (90 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 595   {:label       "Boreal Mixedwood-Green (95% Conifer)"
                        :fuel-type   "M-2 (95 PC)"
                        :description "Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 605   {:label       "Boreal Mixedwood (05% Conifer)"
                        :fuel-type   "M-1/M-2 (05 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (05 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 610   {:label       "Boreal Mixedwood (10% Conifer)"
                        :fuel-type   "M-1/M-2 (10 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (10 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 615   {:label       "Boreal Mixedwood (15% Conifer)"
                        :fuel-type   "M-1/M-2 (15 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (15 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 620   {:label       "Boreal Mixedwood (20% Conifer)"
                        :fuel-type   "M-1/M-2 (20 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (20 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 625   {:label       "Boreal Mixedwood (25% Conifer)"
                        :fuel-type   "M-1/M-2 (25 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (25 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 630   {:label       "Boreal Mixedwood (30% Conifer)"
                        :fuel-type   "M-1/M-2 (30 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (30 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 635   {:label       "Boreal Mixedwood (35% Conifer)"
                        :fuel-type   "M-1/M-2 (35 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (35 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 640   {:label       "Boreal Mixedwood (40% Conifer)"
                        :fuel-type   "M-1/M-2 (40 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (40 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 645   {:label       "Boreal Mixedwood (45% Conifer)"
                        :fuel-type   "M-1/M-2 (45 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (45 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 650   {:label       "Boreal Mixedwood (50% Conifer)"
                        :fuel-type   "M-1/M-2 (50 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (50 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 655   {:label       "Boreal Mixedwood (55% Conifer)"
                        :fuel-type   "M-1/M-2 (55 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (55 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 660   {:label       "Boreal Mixedwood (60% Conifer)"
                        :fuel-type   "M-1/M-2 (60 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (60 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 665   {:label       "Boreal Mixedwood (65% Conifer)"
                        :fuel-type   "M-1/M-2 (65 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (65 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 670   {:label       "Boreal Mixedwood (70% Conifer)"
                        :fuel-type   "M-1/M-2 (70 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (70 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 675   {:label       "Boreal Mixedwood (75% Conifer)"
                        :fuel-type   "M-1/M-2 (75 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (75 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 680   {:label       "Boreal Mixedwood (80% Conifer)"
                        :fuel-type   "M-1/M-2 (80 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (80 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 685   {:label       "Boreal Mixedwood (85% Conifer)"
                        :fuel-type   "M-1/M-2 (85 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (85 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 690   {:label       "Boreal Mixedwood (90% Conifer)"
                        :fuel-type   "M-1/M-2 (90 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (90 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 695   {:label       "Boreal Mixedwood (95% Conifer)"
                        :fuel-type   "M-1/M-2 (95 PC)"
                        :description "M-1: Mixed stand of conifers and leafless deciduous trees. Wide range to structural variability and development stages. / M-2 (95 PC): Mixed stand of conifers and deciduous trees of varying heights and sizes. Trees are leafed out. Forest floor is impossible to differentiate."}
                 705   {:label       "Dead Balsam Fir Mixedwood-Leafless (05% Dead Fir)"
                        :fuel-type   "M-3 (05 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 710   {:label       "Dead Balsam Fir Mixedwood-Leafless (10% Dead Fir)"
                        :fuel-type   "M-3 (10 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 715   {:label       "Dead Balsam Fir Mixedwood-Leafless (15% Dead Fir)"
                        :fuel-type   "M-3 (15 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 720   {:label       "Dead Balsam Fir Mixedwood-Leafless (20% Dead Fir)"
                        :fuel-type   "M-3 (20 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 725   {:label       "Dead Balsam Fir Mixedwood-Leafless (25% Dead Fir)"
                        :fuel-type   "M-3 (25 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 730   {:label       "Dead Balsam Fir Mixedwood-Leafless (30% Dead Fir)"
                        :fuel-type   "M-3 (30 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 735   {:label       "Dead Balsam Fir Mixedwood-Leafless (35% Dead Fir)"
                        :fuel-type   "M-3 (35 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 740   {:label       "Dead Balsam Fir Mixedwood-Leafless (40% Dead Fir)"
                        :fuel-type   "M-3 (40 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 745   {:label       "Dead Balsam Fir Mixedwood-Leafless (45% Dead Fir)"
                        :fuel-type   "M-3 (45 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 750   {:label       "Dead Balsam Fir Mixedwood-Leafless (50% Dead Fir)"
                        :fuel-type   "M-3 (50 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 755   {:label       "Dead Balsam Fir Mixedwood-Leafless (55% Dead Fir)"
                        :fuel-type   "M-3 (55 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 760   {:label       "Dead Balsam Fir Mixedwood-Leafless (60% Dead Fir)"
                        :fuel-type   "M-3 (60 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 765   {:label       "Dead Balsam Fir Mixedwood-Leafless (65% Dead Fir)"
                        :fuel-type   "M-3 (65 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 770   {:label       "Dead Balsam Fir Mixedwood-Leafless (70% Dead Fir)"
                        :fuel-type   "M-3 (70 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 775   {:label       "Dead Balsam Fir Mixedwood-Leafless (75% Dead Fir)"
                        :fuel-type   "M-3 (75 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 780   {:label       "Dead Balsam Fir Mixedwood-Leafless (80% Dead Fir)"
                        :fuel-type   "M-3 (80 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 785   {:label       "Dead Balsam Fir Mixedwood-Leafless (85% Dead Fir)"
                        :fuel-type   "M-3 (85 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 790   {:label       "Dead Balsam Fir Mixedwood-Leafless (90% Dead Fir)"
                        :fuel-type   "M-3 (90 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 795   {:label       "Dead Balsam Fir Mixedwood-Leafless (95% Dead Fir)"
                        :fuel-type   "M-3 (95 PDF)"
                        :description "Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 805   {:label       "Dead Balsam Fir Mixedwood-Green (05% Dead Fir)"
                        :fuel-type   "M-4 (05 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 810   {:label       "Dead Balsam Fir Mixedwood-Green (10% Dead Fir)"
                        :fuel-type   "M-4 (10 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 815   {:label       "Dead Balsam Fir Mixedwood-Green (15% Dead Fir)"
                        :fuel-type   "M-4 (15 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 820   {:label       "Dead Balsam Fir Mixedwood-Green (20% Dead Fir)"
                        :fuel-type   "M-4 (20 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 825   {:label       "Dead Balsam Fir Mixedwood-Green (25% Dead Fir)"
                        :fuel-type   "M-4 (25 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 830   {:label       "Dead Balsam Fir Mixedwood-Green (30% Dead Fir)"
                        :fuel-type   "M-4 (30 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 835   {:label       "Dead Balsam Fir Mixedwood-Green (35% Dead Fir)"
                        :fuel-type   "M-4 (35 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 840   {:label       "Dead Balsam Fir Mixedwood-Green (40% Dead Fir)"
                        :fuel-type   "M-4 (40 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 845   {:label       "Dead Balsam Fir Mixedwood-Green (45% Dead Fir)"
                        :fuel-type   "M-4 (45 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 850   {:label       "Dead Balsam Fir Mixedwood-Green (50% Dead Fir)"
                        :fuel-type   "M-4 (50 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 855   {:label       "Dead Balsam Fir Mixedwood-Green (55% Dead Fir)"
                        :fuel-type   "M-4 (55 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 860   {:label       "Dead Balsam Fir Mixedwood-Green (60% Dead Fir)"
                        :fuel-type   "M-4 (60 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 865   {:label       "Dead Balsam Fir Mixedwood-Green (65% Dead Fir)"
                        :fuel-type   "M-4 (65 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 870   {:label       "Dead Balsam Fir Mixedwood-Green (70% Dead Fir)"
                        :fuel-type   "M-4 (70 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 875   {:label       "Dead Balsam Fir Mixedwood-Green (75% Dead Fir)"
                        :fuel-type   "M-4 (75 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 880   {:label       "Dead Balsam Fir Mixedwood-Green (80% Dead Fir)"
                        :fuel-type   "M-4 (80 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 885   {:label       "Dead Balsam Fir Mixedwood-Green (85% Dead Fir)"
                        :fuel-type   "M-4 (85 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 890   {:label       "Dead Balsam Fir Mixedwood-Green (90% Dead Fir)"
                        :fuel-type   "M-4 (90 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 895   {:label       "Dead Balsam Fir Mixedwood-Green (95% Dead Fir)"
                        :fuel-type   "M-4 (95 PDF)"
                        :description "Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 905   {:label       "Dead Balsam Fir Mixedwood (05% Dead Fir)"
                        :fuel-type   "M-3/M-4 (05 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (05 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 910   {:label       "Dead Balsam Fir Mixedwood (10% Dead Fir)"
                        :fuel-type   "M-3/M-4 (10 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (10 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 915   {:label       "Dead Balsam Fir Mixedwood (15% Dead Fir)"
                        :fuel-type   "M-3/M-4 (15 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (15 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 920   {:label       "Dead Balsam Fir Mixedwood (20% Dead Fir)"
                        :fuel-type   "M-3/M-4 (20 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (20 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 925   {:label       "Dead Balsam Fir Mixedwood (25% Dead Fir)"
                        :fuel-type   "M-3/M-4 (25 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (25 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 930   {:label       "Dead Balsam Fir Mixedwood (30% Dead Fir)"
                        :fuel-type   "M-3/M-4 (30 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (30 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 935   {:label       "Dead Balsam Fir Mixedwood (35% Dead Fir)"
                        :fuel-type   "M-3/M-4 (35 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (35 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 940   {:label       "Dead Balsam Fir Mixedwood (40% Dead Fir)"
                        :fuel-type   "M-3/M-4 (40 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (40 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 945   {:label       "Dead Balsam Fir Mixedwood (45% Dead Fir)"
                        :fuel-type   "M-3/M-4 (45 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (45 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 950   {:label       "Dead Balsam Fir Mixedwood (50% Dead Fir)"
                        :fuel-type   "M-3/M-4 (50 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (50 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 955   {:label       "Dead Balsam Fir Mixedwood (55% Dead Fir)"
                        :fuel-type   "M-3/M-4 (55 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (55 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 960   {:label       "Dead Balsam Fir Mixedwood (60% Dead Fir)"
                        :fuel-type   "M-3/M-4 (60 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (60 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 965   {:label       "Dead Balsam Fir Mixedwood (65% Dead Fir)"
                        :fuel-type   "M-3/M-4 (65 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (65 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 970   {:label       "Dead Balsam Fir Mixedwood (70% Dead Fir)"
                        :fuel-type   "M-3/M-4 (70 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (70 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 975   {:label       "Dead Balsam Fir Mixedwood (75% Dead Fir)"
                        :fuel-type   "M-3/M-4 (75 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (75 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 980   {:label       "Dead Balsam Fir Mixedwood (80% Dead Fir)"
                        :fuel-type   "M-3/M-4 (80 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (80 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 985   {:label       "Dead Balsam Fir Mixedwood (85% Dead Fir)"
                        :fuel-type   "M-3/M-4 (85 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (85 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 990   {:label       "Dead Balsam Fir Mixedwood (90% Dead Fir)"
                        :fuel-type   "M-3/M-4 (90 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (90 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}
                 995   {:label       "Dead Balsam Fir Mixedwood (95% Dead Fir)"
                        :fuel-type   "M-3/M-4 (95 PDF)"
                        :description "M-3: Defoliated, mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor. / M-4 (95 PDF): Mixedwood stand. Significant top breakage, windthrow, draped lichen and other woody material. Mosses, needles and leaves on forest floor."}})

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
