(ns pyregence.components.map-controls.mouse-lng-lat
  (:require [clojure.pprint   :refer [cl-format]]
            [pyregence.components.mapbox :as mb]
            [pyregence.styles            :as $]
            [reagent.core                :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $mouse-lng-lat []
  {:background-color ($/color-picker :bg-color)
   :bottom           "84px"
   :left             "auto"
   :padding          ".2rem"
   :right            "70px"})

(defn- $mouse-lng-lat-inner []
  {:font-size   "0.85rem"
   :font-weight "bold"
   :margin      "0.15rem 0.25rem 0 0.25rem"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn mouse-lng-lat
  "Shows the current Longitude/latitude based on current mouse position."
  []
  (r/with-let [moving-lng-lat (r/atom [0 0])
               move-event     (mb/add-mouse-move-xy! #(reset! moving-lng-lat %))]
    [:div {:style ($/combine $/tool $mouse-lng-lat)}
     [:div#mouse-lng-lat {:style ($mouse-lng-lat-inner)}
      [:div {:style {:display         "flex"
                     :justify-content "space-between"}}
       [:span {:style {:padding-right "0.25rem"}} "Lat: "]
       [:span (cl-format nil "~,4f" (get @moving-lng-lat 1))]]
      [:div {:style {:display         "flex"
                     :justify-content "space-between"}}
       [:span {:style {:padding-right "0.25rem"}} "Lon: "]
       [:span (cl-format nil "~,4f" (get @moving-lng-lat 0))]]]]
    (finally
      (mb/remove-event! move-event))))
