(ns pyregence.components.fire-popup
  (:require [herb.core :refer [<class]]
            [pyregence.styles :as $]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $popup-btn []
  (with-meta
    {:background    ($/color-picker :yellow)
     :color         ($/color-picker :white)
     :border-radius "3px"
     :border        "none"
     :padding       "0.25rem 0.5rem"
     :margin-top    "0.5rem"}
    {:pseudo {:hover {:background-color ($/color-picker :yellow 0.8)}}}))

(defn- $p-popup-content []
  (with-meta
    {:display "flex" :flex-direction "column"}
    {:combinators {[:- :.mapboxgl-popup-close-button] {:font-size "2rem" :padding "3px"}
                   [:- :.mapboxgl-popup-close-button:focus] {:outline 0}}}))

(defn- $fire-popup-header []
  {:width         "180px"
   :overflow      "hidden"
   :text-overflow "ellipsis"
   :white-space   "nowrap"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- fire-property [property value]
  [:div [:strong property ": "] value])

(defn- fire-link [on-click]
  [:button {:class    (<class $popup-btn)
            :on-click on-click}
   "Click to View Forecast"])

(defn fire-popup
  "Popup body for active fires."
  [fire-name contain-per acres on-click show-link?]
  [:div {:class (<class $p-popup-content)}
   [:h6 {:style ($fire-popup-header)}
    fire-name]
   [:div
    [fire-property "Percent Contained" (str contain-per "%")]
    [fire-property "Acres Burned" acres]
    (when show-link? [fire-link on-click])]])
