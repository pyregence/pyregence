(ns pyregence.layer-config)

;; Layer options

(def forecast-options
  {:fire-weather {:opt-label       "Fire Weather"
                  :filter          "fire-weather-forecast"
                  :reverse-legend? true
                  :params          {:model-init {:opt-label "Forecast Start Time"
                                                 :options   {:loading {:opt-label "Loading..."}}}
                                    :band       {:opt-label "Weather Parameter"
                                                 :options   (array-map
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
                                                                      :units     "%"})}}}
   :active-fire {:opt-label   "Active Fire Forecast"
                 :filter      "fire-spread-forecast"
                 :block-info? true
                 :params      {:fire-name  {:opt-label  "Fire Name"
                                            :auto-zoom? true
                                            :options    {:loading {:opt-label "Loading..."}}}
                               :output     {:opt-label "Output"
                                            :options   {:burned {:opt-label "Burned area"
                                                                 :filter    "burned-area"
                                                                 :units     "Hours"}}}
                               :burn-pct   {:opt-label      "Burn Probability"
                                            :default-option :50
                                            :options        {:90 {:opt-label "90%"
                                                                  :filter    "90"}
                                                             :70 {:opt-label "70%"
                                                                  :filter    "70"}
                                                             :50 {:opt-label "50%"
                                                                  :filter    "50"}
                                                             :30 {:opt-label "30%"
                                                                  :filter    "30%"}
                                                             :10 {:opt-label "10%"
                                                                  :filter    "10"}}}
                               :fuel       {:opt-label "Fuel"
                                            :options   {:landfire {:opt-label "LANDFIRE"
                                                                   :filter    "landfire"}}}
                               :model      {:opt-label "Model"
                                            :options   {:elmfire {:opt-label "ELMFIRE"
                                                                  :filter    "elmfire"}}}
                               :model-init {:opt-label  "Forecast Start Time"
                                            :filter-on  :fire-name
                                            :filter-key :model-init
                                            :options    {:loading {:opt-label "Loading..."}}}}}
   :fire-risk {:opt-label       "Risk Forecast"
               :filter          "fire-risk-forecast"
               :reverse-legend? true
               :params          {:output     {:opt-label "Output"
                                              :options   {:impacted     {:opt-label "Impacted structures"
                                                                         :filter    "impacted-structures"
                                                                         :units     "Structures"}
                                                          :times-burned {:opt-label "Relative burn probability"
                                                                         :filter    "times-burned"
                                                                         :units     "Times"}
                                                          :fire-area    {:opt-label "Fire area"
                                                                         :filter    "fire-area"
                                                                         :units     "Acres"}
                                                          :fire-volume  {:opt-label "Fire volume"
                                                                         :filter    "fire-volume"
                                                                         :units     "Acre-ft"}}}
                                 :pattern    {:opt-label "Ignition Pattern"
                                              :options   {:all        {:opt-label    "Human-caused ignitions"
                                                                       :filter       "all"}
                                                          :tlines     {:opt-label    "Transmission lines"
                                                                       :filter       "tlines"
                                                                       :clear-point? true}
                                                          :liberty    {:opt-label   "Liberty Distribution Lines"
                                                                       :filter      "liberty"
                                                                       :clear-point? true
                                                                       :org-id       3}
                                                          :pacificorp {:opt-label   "Pacificorp Distribution Lines"
                                                                       :filter      "pacificorp"
                                                                       :clear-point? true
                                                                       :org-id       4}}}
                                 :fuel       {:opt-label "Fuel"
                                              :options   {:landfire {:opt-label "LANDFIRE"
                                                                     :filter    "landfire"}}}
                                 :model      {:opt-label "Model"
                                              :options   {:elmfire {:opt-label "ELMFIRE"
                                                                    :filter    "elmfire"}}}
                                 :model-init {:opt-label "Forecast Start Time"
                                              :options   {:loading {:opt-label "Loading..."}}}}}})
