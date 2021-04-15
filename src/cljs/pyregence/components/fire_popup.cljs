(ns pyregence.components.fire-popup
  (:require [herb.core :refer [<class]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn fire-popup
  "Popup body for active fires."
  [fire-name contain-per acres]
  [:div {:class (<class $p-popup-content)}
   [:h6 {:style ($fire-popup-header)}
    fire-name]
   [:dl
    [fire-property "Percent Contained" (str contain-per "%")]
    [fire-property "Acres Burned" acres]]])
