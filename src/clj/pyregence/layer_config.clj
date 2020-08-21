(ns pyregence.layer-config)

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
                                                                       :units     "%"})}
                                    :model-init {:opt-label  "Forecast Start Time"
                                                 :hover-text "Start time for forecast cycle, new data comes every 6 hours."
                                                 :options    {:loading {:opt-label "Loading..."}}}}}
   :active-fire {:opt-label   "Active Fire Forecast"
                 :filter      "fire-spread-forecast"
                 :block-info? true
                 :hover-text  "3-day forecasts of active fires with burning areas established from satellite-based heat detection."
                 :params      {:fire-name  {:opt-label  "Fire Name"
                                            :auto-zoom? true
                                            :sort?      true
                                            :hover-text "Fire name as listed in the CALFIRE incident report.\n
                                                         Also included is the CALFIRE incident report overview."
                                            :options    {:loading {:opt-label "Loading..."}}}
                               :output     {:opt-label  "Output"
                                            :hover-text "Burned Area - Area burned by fire. Colors represent how long before the time shown in the time slider that an area burned."
                                            :options    {:burned {:opt-label "Burned area"
                                                                  :filter    "burned-area"
                                                                  :units     "Hours"}}}
                               :burn-pct   {:opt-label      "Burn Probability"
                                            :default-option :50
                                            :hover-text     "Burn Probability - Each active fire forecast is an ensemble of 1,000 separate fire spread runs and burn probability refers to the percentage of those simulations in which a given pixel burned."
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
                                            :hover-text "Source of surface and canopy fuel inputs.\n
                                                         Federal LANDFIRE program (https://landfire.gov/) gridded inputs at 30 m resolution."
                                            :options    {:landfire {:opt-label "LANDFIRE"
                                                                    :filter    "landfire"}}}
                               :model      {:opt-label  "Model"
                                            :hover-text "Computer fire spread model used to generate active fire and risk forecasts.\n
                                                         ELMFIRE - Cloud-based operational fire spread model spread developed at Reax Engineering Inc. (https://doi.org/10.1016/j.firesaf.2013.08.014)."
                                            :options    {:elmfire {:opt-label "ELMFIRE"
                                                                   :filter    "elmfire"}}}
                               :model-init {:opt-label  "Forecast Start Time"
                                            :hover-text "Start time for forecast cycle. New data is created when active fires are sensed by satellites."
                                            :options    {:loading {:opt-label "Loading..."}}}}}
   :fire-risk {:opt-label       "Risk Forecast"
               :filter          "fire-risk-forecast"
               :reverse-legend? true
               :hover-text      "5-day forecast of fire consequence maps. Every day over 500 million hypothetical fires are ignited across California to evaluate potential fire risk.\n"
               :params          {:output     {:opt-label  "Output"
                                              :hover-text "Key fire spread model outputs based on modeling 6-hours of fire spread without fire suppression activities within 6 hours of time shown in time slider. Options include:\n
                                                           Relative Burn Probability - Relatively likelihood that an area is burned by fires that have not yet ignited within the next six hours of time shown in time slider.\n
                                                           Impacted Structures - Approximate number of residential structures within fire perimeter for fires starting at specific location and time in the future.\n
                                                           Fire area - Modeled fire size in acres by ignition location and time of ignition.\n
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
                                              :hover-text "Source of surface and canopy fuel inputs.\n
                                                           Federal LANDFIRE program (https://landfire.gov/) gridded inputs at 30 m resolution."
                                              :options    {:landfire {:opt-label "LANDFIRE"
                                                                      :filter    "landfire"}}}
                                 :model      {:opt-label  "Model"
                                              :hover-text "Computer fire spread model used to generate active fire and risk forecasts.\n
                                                           ELMFIRE	Cloud-based operational fire spread model spread developed at Reax Engineering Inc. (https://doi.org/10.1016/j.firesaf.2013.08.014)."
                                              :options    {:elmfire {:opt-label "ELMFIRE"
                                                                     :filter    "elmfire"}}}
                                 :model-init {:opt-label  "Forecast Start Time"
                                              :hover-text "Start time for forecast cycle. New data is created every 12 hours."
                                              :options    {:loading {:opt-label "Loading..."}}}}}})
