(ns pyregence.components.map-controls.scale-bar
  (:require [pyregence.components.mapbox :as mb]
            [pyregence.geo-utils         :as g]
            [pyregence.state             :as !]
            [pyregence.styles            :as $]
            [reagent.core                :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $scale-line [time-slider?]
  {:background-color ($/color-picker :bg-color)
   :padding          ".2rem 0"
   :bottom           (if (and @!/mobile? time-slider?) "90px" "36px")
   :left             "auto"
   :right            "70px"
   :user-select      "none"})

(defn- $scale-line-inner []
  {:border-color ($/color-picker :border-color)
   :border-style "solid"
   :border-width "0 2px 2px 2px"
   :color        ($/color-picker :border-color)
   :font-size    ".75rem"
   :font-weight  "bold"
   :margin       ".5rem"
   :text-align   "center"
   :transition   "all .25s"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn scale-bar
  "Scale bar control which resizes based on map zoom/location."
  [time-slider?]
  (r/with-let [max-width    100.0
               scale-params (r/atom {:distance 0 :ratio 1 :units "ft"})
               move-event   (mb/add-map-move! #(reset! scale-params (g/imperial-scale (mb/get-distance-meters))))]
    [:div#scale-bar {:style ($/combine $/tool ($scale-line time-slider?) {:width (* (:ratio @scale-params) max-width)})}
     [:div {:style ($scale-line-inner)}
      (str (:distance @scale-params) " " (:units @scale-params))]]
    (finally
      (mb/remove-event! move-event))))
