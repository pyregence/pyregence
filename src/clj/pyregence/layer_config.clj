(ns pyregence.layer-config)

;; Layer options

(def forecast-options
  {:fire-weather {:opt-label       "Fire Weather"
                  :filter          "fire-weather-forecast"
                  :reverse-legend? true
                  :params          {:model-init {:opt-label "Forecast Start Time"
                                                 :options   {:loading {:opt-label "Loading..."}}}
                                    :band       {:opt-label "Weather Band"
                                                 :options   (array-map
                                                             :ffwi   {:opt-label "Fosberg Fire Weather Index"
                                                                      :filter    "ffwi"
                                                                      :units     ""}
                                                             :tmpf   {:opt-label "Temperature (2m)"
                                                                      :filter    "tmpf"
                                                                      :units     "deg F"}
                                                             :rh     {:opt-label "Relative humidity (2m)"
                                                                      :filter    "rh"
                                                                      :units     "%"}
                                                             :ws     {:opt-label "Sustained wind speed (20-ft)"
                                                                      :filter    "ws"
                                                                      :units     "mph"}
                                                             :wg     {:opt-label "Wind gust (20-ft)"
                                                                      :filter    "wg"
                                                                      :units     "mph"}
                                                             :apcp01 {:opt-label "Accumulated precipitation (1 hour)"
                                                                      :filter    "apcp01"
                                                                      :units     "inches"}
                                                             :meq    {:opt-label "Equilibrium moisture content"
                                                                      :filter    "meq"
                                                                      :units     "%"})}}}
   :active-fire {:opt-label   "Active Fire Forecast"
                 :filter      "fire-spread-forecast"
                 :block-info? true
                 :params      {:fire-name  {:opt-label  "Fire Name"
                                            :auto-zoom? true
                                            :options    {:loading {:opt-label "Loading..."}}}
                               :model      {:opt-label "Model"
                                            :options   {:elmfire {:opt-label "ELMFIRE"
                                                                  :filter    "elmfire"}}}
                               :model-init {:opt-label  "Forecast Start Time"
                                            :filter-on  :fire-name
                                            :filter-key :model-init
                                            :options    {:loading {:opt-label "Loading..."}}}
                               :fuel       {:opt-label "Fuel"
                                            :options   {:landfire {:opt-label "LANDFIRE"
                                                                   :filter    "landfire"}}}
                               :burn-pct   {:opt-label      "Burn Probability"
                                            :default-option :50
                                            :options        {:90 {:opt-label "90"
                                                                  :filter    "90"}
                                                             :70 {:opt-label "70"
                                                                  :filter    "70"}
                                                             :50 {:opt-label "50"
                                                                  :filter    "50"}
                                                             :30 {:opt-label "30"
                                                                  :filter    "30"}
                                                             :10 {:opt-label "10"
                                                                  :filter    "10"}}}
                               :output     {:opt-label "Output"
                                            :options   {:burned {:opt-label "Burned area"
                                                                 :filter    "burned-area"
                                                                 :units     "Hours"}}}}}
   :fire-risk {:opt-label       "Risk Forecast"
               :filter          "fire-risk-forecast"
               :reverse-legend? true
               :params          {:model      {:opt-label "Model"
                                              :options   {:elmfire {:opt-label "ELMFIRE"
                                                                    :filter    "elmfire"}}}
                                 :model-init {:opt-label "Forecast Start Time"
                                              :options   {:loading {:opt-label "Loading..."}}}
                                 :fuel       {:opt-label "Fuel"
                                              :options   {:landfire {:opt-label "LANDFIRE"
                                                                     :filter    "landfire"}}}
                                 :pattern    {:opt-label "Ignition Pattern"
                                              :options   {:all    {:opt-label    "Human-caused ignitions"
                                                                   :filter       "all"}
                                                          :tlines {:opt-label    "Transmission lines"
                                                                   :filter       "tlines"
                                                                   :clear-point? true}}}
                                 :output     {:opt-label "Output"
                                              :options   {:fire-area    {:opt-label "Fire area"
                                                                         :filter    "fire-area"
                                                                         :units     "Acres"}
                                                          :fire-volume  {:opt-label "Fire volume"
                                                                         :filter    "fire-volume"
                                                                         :units     "Acre-ft"}
                                                          :impacted     {:opt-label "Impacted structures"
                                                                         :filter    "impacted-structures"
                                                                         :units     "Structures"}
                                                          :times-burned {:opt-label "Relative burn probability"
                                                                         :filter    "times-burned"
                                                                         :units     "Times"}}}}}})
