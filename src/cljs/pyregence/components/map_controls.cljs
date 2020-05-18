(ns pyregence.components.map-controls
  (:require [reagent.core :as r]
            [herb.core :refer [<class]]
            [pyregence.styles :as $]
            [pyregence.utils  :as u]
            [pyregence.config :as c]
            [pyregence.components.openlayers :as ol]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Common Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $dropdown []
  {:background-color "white"
   :border           "1px solid"
   :border-color     ($/color-picker :sig-brown)
   :border-radius    "2px"
   :font-family      "inherit"
   :height           "2rem"
   :padding          ".25rem .5rem"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Legend Box
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $legend-box []
  {:background-color "white"
   :bottom           "1rem"
   :border           "1px solid black"
   :border-radius    "5px"
   :right            ".5rem"
   :position         "absolute"
   :z-index          "100"})

(defn $legend-color [color]
  {:background-color color
   :height           "1rem"
   :margin-right     ".5rem"
   :width            "1rem"})

(defn legend-box [legend-list]
  [:div#legend-box {:style ($legend-box)}
   [:div {:style {:display "flex" :flex-direction "column"}}
    (doall (map-indexed (fn [i leg]
                          ^{:key i}
                          [:div {:style ($/combine $/flex-row {:justify-content "flex-start" :padding ".5rem"})}
                           [:div {:style ($legend-color (get leg "color"))}]
                           [:label (get leg "label")]])
                        @legend-list))]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Time Slider
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $radio [checked?]
  (merge
   (when checked? {:background-color ($/color-picker :black 0.6)})
   {:border        "2px solid"
    :border-color  ($/color-picker :black)
    :border-radius "100%"
    :height        "1rem"
    :margin-right  ".4rem"
    :width         "1rem"}))

(defn $time-slider []
  {:align-items      "center"
   :background-color "white"
   :border           "1px solid black"
   :border-radius    "5px"
   :display          "flex"
   :margin-right     "auto"
   :margin-left      "auto"
   :left             "0"
   :right            "0"
   :padding          ".5rem"
   :position         "absolute"
   :bottom           "1rem"
   :width            "fit-content"
   :z-index          "100"})

(defn radio [label state condition select-time-zone!]
  [:div {:style ($/flex-row)
         :on-click #(select-time-zone! condition)}
   [:div {:style ($/combine [$radio (= @state condition)])}]
   [:label {:style {:font-size ".8rem" :margin-top "2px"}} label]])

(defn time-slider [filtered-layers
                   *layer-idx
                   get-current-layer-full-time
                   select-layer!
                   cycle-layer!
                   show-utc?
                   select-time-zone!
                   animate?
                   loop-animation!
                   *speed]
  [:div#time-slider {:style ($time-slider)}
   [:div {:style ($/combine $/flex-col {:align-items "flex-start" :margin-right "1rem"})}
    [radio "UTC"   show-utc? true  select-time-zone!]
    [radio "Local" show-utc? false select-time-zone!]]
   [:div {:style ($/flex-col)}
    [:input {:style {:width "12rem"}
             :type "range" :min "0" :max (dec (count (filtered-layers))) :value *layer-idx
             :on-change #(select-layer! (u/input-int-value %))}]
    [:label {:style {:font-size ".75rem"}}
     (get-current-layer-full-time)]]
   [:button {:style {:padding ".25rem" :margin-left ".5rem"}
             :type "button"
             :on-click #(cycle-layer! -1)}
    "<<"]
   [:button {:style {:padding ".25rem"}
             :type "button"
             :on-click #(do (swap! animate? not)
                            (loop-animation!))}
    (if @animate? "Stop" "Play")]
   [:button {:style {:padding ".25rem"}
             :type "button"
             :on-click #(cycle-layer! 1)}
    ">>"]
   [:select {:style ($/combine $dropdown)
             :value (or @*speed 1)
             :on-change #(reset! *speed (u/input-int-value %))}
    (doall (map (fn [{:keys [opt-id opt-label]}]
                  [:option {:key opt-id :value opt-id} opt-label])
                c/speeds))]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Collapsible Panel
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $collapsible-panel [show?]
  {:background-color "white"
   :border-right     "2px solid black"
   :height           "100%"
   :position         "absolute"
   :transition       "all 200ms ease-in"
   :width            "20rem"
   :z-index          "1000"
   :left             (if show? "0" "-20rem")})

(defn $collapse-button []
  {:background-color "white"
   :border-right     "2px solid black"
   :border-top       "2px solid black"
   :border-bottom    "2px solid black"
   :border-left      "4px solid white"
   :border-radius    "0 5px 5px 0"
   :height           "2rem"
   :position         "absolute"
   :right            "-2rem"
   :top              ".5rem"
   :width            "2rem"})

(defn panel-dropdown [title state options call-back]
  [:div {:style {:display "flex" :flex-direction "column" :margin-top ".25rem"}}
   [:label title]
   [:select {:style ($dropdown)
             :value (or @state -1)
             :on-change #(call-back (u/input-int-value %))}
    (doall (map (fn [{:keys [opt-id opt-label]}]
                  [:option {:key opt-id :value opt-id} opt-label])
                options))]])

(defn collapsible-panel [*base-map
                         select-base-map!
                         *model
                         *fuel-type
                         *ign-pattern
                         *output-type
                         *model-time
                         model-times
                         select-layer-option!]
  (r/with-let [show-panel?       (r/atom true)
               active-opacity    (r/atom 70.0)
               hillshade-opacity (r/atom 50.0)
               show-hillshade?   (r/atom false)]
    [:div#collapsible-panel {:style ($collapsible-panel @show-panel?)}
     [:div {:style ($collapse-button)
            :on-click #(swap! show-panel? not)}
      [:label {:style {:padding-top "2px"}} (if @show-panel? "<<" ">>")]]
     [:div {:style {:display "flex" :flex-direction "column" :padding "3rem"}}
      [:div#baselayer
       [panel-dropdown "Base Layer" *base-map c/base-map-options select-base-map!]
       [:div {:style {:margin-top ".5rem"}}
        [:div {:style {:display "flex"}}
         [:input {:style {:margin ".25rem .5rem 0 0"}
                  :type "checkbox"
                  :on-click #(do (swap! show-hillshade? not)
                                 (ol/set-visible-by-title! "hillshade" @show-hillshade?))}]
         [:label "Hill shade overlay"]]
        (when @show-hillshade?
          [:<> [:label (str "Opacity: " @hillshade-opacity)]
           [:input {:style {:width "100%"}
                    :type "range" :min "0" :max "100" :value @hillshade-opacity
                    :on-change #(do (reset! hillshade-opacity (u/input-int-value %))
                                    (ol/set-opacity-by-title! "hillshade" (/ @hillshade-opacity 100.0)))}]])]]
      [:div#activelayer {:style {:margin-top "2rem"}}
       [panel-dropdown "Model"      *model       c/models       #(select-layer-option! *model       %)]
       [panel-dropdown "Model Time" *model-time  model-times    #(select-layer-option! *model-time  %)]
       [panel-dropdown "Fuel"       *fuel-type   c/fuel-types   #(select-layer-option! *fuel-type   %)]
       [panel-dropdown "Ignition"   *ign-pattern c/ign-patterns #(select-layer-option! *ign-pattern %)]
       [panel-dropdown "Output"     *output-type c/output-types #(select-layer-option! *output-type %)]
       [:div {:style {:margin-top ".5rem"}}
        [:label (str "Opacity: " @active-opacity)]
        [:input {:style {:width "100%"}
                 :type "range" :min "0" :max "100" :value @active-opacity
                 :on-change #(do (reset! active-opacity (u/input-int-value %))
                                 (ol/set-opacity-by-title! "active" (/ @active-opacity 100.0)))}]]]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Zoom Slider
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $zoom-slider []
  {:background-color "white"
   :border           "1px solid black"
   :border-radius    "5px"
   :right            "4rem"
   :position         "absolute"
   :bottom           "6rem"
   :display          "flex"
   :transform        "rotate(270deg)"
   :width            "12rem"
   :height           "2rem"
   :z-index          "100"})

(defn $p-zoom-button-common []
  (with-meta
    {:border-radius "4px"
     :cursor        "pointer"
     :font-weight   "bold"
     :transform     "rotate(90deg)"}
    {:pseudo {:hover {:background-color ($/color-picker :sig-brown 0.1)}}}))

(defn zoom-slider [minZoom maxZoom *zoom select-zoom! get-current-layer-extent]
  [:div#zoom-slider {:style ($zoom-slider)}
   [:span {:class (<class $p-zoom-button-common)
           :style ($/combine ($/fixed-size "1.75rem") {:margin "1px" :padding ".15rem 0 0 .5rem"})
           :title "Center on my location"
           :on-click #(some-> js/navigator .-geolocation (.getCurrentPosition ol/set-center-my-location!))} ; TODO should I also zoom to a min zoom level?
    "M"]
   [:span {:class (<class $p-zoom-button-common)
           :style ($/combine ($/fixed-size "1.75rem") {:margin "1px" :padding ".15rem 0 0 .75rem"})
           :on-click #(select-zoom! (dec *zoom))}
    "-"]
   [:input {:style {:min-width "0"}
            :type "range" :min minZoom :max maxZoom :value *zoom
            :on-change #(select-zoom! (u/input-int-value %))}]
   [:span {:class (<class $p-zoom-button-common)
           :style ($/combine ($/fixed-size "1.75rem") {:margin "1px" :padding ".15rem 0 0 .5rem"})
           :on-click #(select-zoom! (inc *zoom))}
    "+"]
   [:span {:class (<class $p-zoom-button-common)
           :style ($/combine ($/fixed-size "1.75rem") {:margin "1px" :padding ".25rem 0 0 .5rem" :font-size ".9rem"})
           :title "Center on selected point"
           :on-click #(ol/center-on-overlay!)}
    "C"]
   [:span {:class (<class $p-zoom-button-common)
           :style ($/combine ($/fixed-size "1.75rem") {:margin "1px" :padding ".25rem 0 0 .5rem" :font-size ".9rem"})
           :title "Zoom to fit layer"
           :on-click #(ol/zoom-to-extent! (get-current-layer-extent))}
    "E"]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mouse Location Information
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $mouse-info []
  {:background-color "white"
   :bottom           "1rem"
   :border           "1px solid black"
   :border-radius    "5px"
   :right            "12rem"
   :padding          ".5rem"
   :position         "absolute"
   :width            "14rem"
   :z-index          "100"})

(defn mouse-info [lon-lat]
  [:div#mouse-info {:style ($mouse-info)}
   [:label {:style {:width "50%" :text-align "left" :padding-left ".5rem"}}
    "Lat:" (u/to-precision 4 (get lon-lat 1))]
   [:label {:style {:width "50%" :text-align "left"}}
    "Lon:" (u/to-precision 4 (get lon-lat 0))]])
