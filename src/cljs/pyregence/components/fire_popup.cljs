(ns pyregence.components.fire-popup
  (:require [pyregence.styles :as $]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $fire-popup-header []
 {:width         "180px"
  :overflow      "hidden"
  :text-overflow "ellipsis"
  :white-space   "nowrap"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- fire-property [property value]
  [:<>
   [:dt {:style {:display "inline-block"}}
    property]
   [:dd {:style {:display "inline-block" :padding "0 0 0 0.2rem"}}
    value]])

(defn fire-popup
  "Popup body for active fires."
  [fire-name contain-per acres]
  [:div
   [:h6 {:style ($/combine $fire-popup-header)}
    fire-name]
   [:dl
    [fire-property "Percent Contained:" (str contain-per "%")]
    [fire-property "Acres Burned:" acres]]])
