(ns pyregence.components.map-controls
  (:require [reagent.core :as r]
            [herb.core :refer [<class]]
            [pyregence.styles :as $]
            [pyregence.utils  :as u]
            [pyregence.config :as c]
            [pyregence.components.openlayers :as ol]
            [pyregence.components.vega       :refer [vega-box]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Common
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce show-panel?  (r/atom true))
(defonce show-legend? (r/atom false))

(defn $dropdown []
  {:background-color "white"
   :border           "1px solid"
   :border-color     ($/color-picker :sig-brown)
   :border-radius    "2px"
   :font-family      "inherit"
   :height           "2rem"
   :padding          ".25rem .5rem"})


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
   :left             (if show? "0" "-18rem")
   :overflow         "auto"
   :position         "absolute"
   :transition       "all 200ms ease-in"
   :width            "18rem"
   :z-index          "1000"})

(defn layer-section []
  {:border        "1px solid black"
   :border-radius "3px"
   :margin        ".75rem"
   :padding       ".75rem"})

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
  (r/with-let [active-opacity    (r/atom 70.0)
               hillshade-opacity (r/atom 50.0)
               show-hillshade?   (r/atom false)]
    [:div#collapsible-panel {:style ($collapsible-panel @show-panel?)}
     [:div {:style {:overflow "auto"}}
      [:div#baselayer {:style (layer-section)}
       [:h4 "Base Map"]
       [panel-dropdown "Map" *base-map c/base-map-options select-base-map!]
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
      [:div#activelayer {:style ($/combine (layer-section) {:margin-top "1rem"})}
       [:h4 "Fire Layer"]
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
;; Toolbars
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $tool-bar [panel-open?]
  {:background-color "white"
   :border           "1px solid black"
   :border-radius    "5px"
   :display          "flex"
   :flex-direction   "column"
   :left             (if panel-open? "19rem" "1rem")
   :position         "absolute"
   :transition       "all 200ms ease-in"
   :z-index          "100"})

(defn $p-tb-button []
  (with-meta
    {:border-radius "4px"
     :cursor        "pointer"
     :font-weight   "bold"
     :font-size     "1.5rem"
     :text-align    "center"}
    {:pseudo {:hover {:background-color ($/color-picker :sig-brown 0.1)}}}))

(defn tool-bar-button [icon title on-click]
  [:span {:class (<class $p-tb-button)
          :style ($/combine ($/fixed-size "2.5rem") {:padding-top ".25rem"})
          :title title
          :on-click on-click}
   icon])

(defn hs-str [hide?]
  (if hide? "Hide" "Show"))

(defn tool-bar [show-info? show-measure?]
  [:div#tool-bar {:style ($/combine ($tool-bar @show-panel?) {:top "1rem"})}
   (map-indexed (fn [i [icon title on-click]]
                  ^{:key i} (tool-bar-button icon title on-click))
                [["L" (str (hs-str @show-panel?) " layer selection")   #(swap! show-panel? not)]
                 ["i" (str (hs-str @show-panel?) " point information") #(do (swap! show-info? not)
                                                                            (reset! show-measure? false))]
                 ["M" (str (hs-str @show-panel?) " measure tool")      #(do (swap! show-measure? not)
                                                                            (reset! show-info? false))]
                 ["L" (str (hs-str @show-panel?) " legend")            #(swap! show-legend? not)]])])

(defn zoom-bar [*zoom select-zoom! get-current-layer-extent]
  [:div#zoom-bar {:style ($/combine ($tool-bar @show-panel?) {:bottom "1rem"})}
   (map-indexed (fn [i [icon title on-click]]
                  ^{:key i} (tool-bar-button icon title on-click))
                [["M" "Center on my location"    #(some-> js/navigator .-geolocation (.getCurrentPosition ol/set-center-my-location!))]
                ;;  ["C" "Center on selected point" #(ol/center-on-overlay!)] ; TODO add this action the the information panel
                 ["E" "Zoom to fit layer"        #(ol/zoom-to-extent! (get-current-layer-extent))]
                 ["+" "Zoom in"                  #(select-zoom! (inc *zoom))]
                 ["-" "Zoom out"                 #(select-zoom! (dec *zoom))]])])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Measure Tool
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $measure-tool []
  {:background-color "white"
   :border           "1px solid black"
   :border-radius    "5px"
   :right            "1rem"
   :padding          ".5rem"
   :position         "absolute"
   :top              "1rem"
   :width            "14rem"
   :z-index          "100"})

(defn measure-tool [lon-lat]
  [:div#measure-tool {:style ($measure-tool)}
   [:label {:style {:width "50%" :text-align "left" :padding-left ".5rem"}}
    "Lat:" (u/to-precision 4 (get lon-lat 1))]
   [:label {:style {:width "50%" :text-align "left"}}
    "Lon:" (u/to-precision 4 (get lon-lat 0))]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Information Tool
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn information-tool [my-box select-layer! units cur-hour legend-list last-clicked-info]
  [:div#info-tool
   [vega-box
    my-box
    select-layer!
    units
    cur-hour
    legend-list
    last-clicked-info]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Legend Box
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $legend-box []
  {:background-color "white"
   :bottom           "3rem"
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
  (when @show-legend?
    [:div#legend-box {:style ($legend-box)}
     [:div {:style {:display "flex" :flex-direction "column"}}
      (doall (map-indexed (fn [i leg]
                            ^{:key i}
                            [:div {:style ($/combine $/flex-row {:justify-content "flex-start" :padding ".5rem"})}
                             [:div {:style ($legend-color (get leg "color"))}]
                             [:label (get leg "label")]])
                          @legend-list))]]))