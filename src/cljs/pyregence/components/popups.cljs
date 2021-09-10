(ns pyregence.components.popups
  (:require [herb.core        :refer [<class]]
            [pyregence.styles :as $]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $popup-btn []
  (with-meta
    {:background    ($/color-picker :yellow)
     :border        "none"
     :border-radius "3px"
     :color         ($/color-picker :white)
     :margin-top    "0.5rem"
     :padding       "0.25rem 0.5rem"}
    {:pseudo {:hover {:background-color ($/color-picker :yellow 0.8)}}}))

(defn- $popup-header []
  {:overflow      "hidden"
   :text-overflow "ellipsis"
   :white-space   "nowrap"
   :width         "180px"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fire Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- fire-property [property value]
  [:div [:strong property ": "] value])

(defn- fire-link [on-click]
  [:div {:style {:text-align "right" :width "100%"}}
   [:button {:class    (<class $popup-btn)
             :on-click on-click}
    "Click to View Forecast"]])

(defn fire-popup
  "Popup body for active fires."
  [fire-name contain-per acres on-click show-link?]
  [:div {:style {:display "flex" :flex-direction "column"}}
   [:h6 {:style ($popup-header)}
    fire-name]
   [:div
    [fire-property "Percent Contained" (str contain-per "%")]
    [fire-property "Acres Burned" (.toLocaleString acres)]
    (when show-link? [fire-link on-click])]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Red-Flag Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- red-flag-link [url]
  [:div {:style {:text-align "right" :width "100%"}}
   [:button {:class    (<class $popup-btn)
             :on-click #(js/window.open url)}
    "Click for More Details"]])

(defn red-flag-popup
  "Popup body for red-flag warning layer."
  [onset url prod-type]
  [:div {:style {:display "flex" :flex-direction "column"}}
   [:h6 {:style ($popup-header)}
    prod-type]
   [:div
    [fire-property "Onset date" onset]
    [red-flag-link url]]])
