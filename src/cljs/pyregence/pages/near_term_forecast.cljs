(ns pyregence.pages.near-term-forecast
  (:require [reagent.core :as r]
            [pyregence.components.openlayers :as ol]
            [pyregence.styles :as $]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce cur-layer (atom 0))

(def layers-list ["fire-area_20171006_070000"
                  "fire-area_20171007_070000"
                  "fire-area_20171008_070000"
                  "fire-area_20171009_070000"
                  "fire-area_20171010_070000"])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $app-header []
  {:align-items     "center"
   :display         "flex"
   :height          "2.5rem"
   :justify-content "center"
   :width           "100%"})

(defn $tool-label [selected?]
  (if selected?
    {:color "white"}
    {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component [_]
  (r/create-class
   {:component-did-mount
    #(ol/init-map! (get layers-list @cur-layer))

    :reagent-render
    (fn [_]
      [:div {:style ($/combine $/root {:height "100%" :padding 0})}
       [:div {:class "bg-yellow"
              :style ($app-header)}
        [:span
         [:label {:style ($tool-label false)} "Fire Weather"]
         [:label {:style ($/combine [$tool-label false] [$/margin "2rem" :h])} "Active Fire Forecast"]
         [:label {:style ($tool-label true)} "Risk Forecast"]]
        [:label {:style {:position "absolute" :right "3rem"}} "Login"]]
       [:div#map {:style {:height "100%" :position "relative" :width "100%"}}
        [:button {:style {:padding ".25rem" :position "absolute" :top "4.5rem" :left ".25rem" :z-index "100"}
                  :type "button"
                  :on-click (fn []
                              (swap! cur-layer #(mod (inc %) (count layers-list)))
                              (ol/swap-active-layer! (get layers-list @cur-layer)))}
         "Next"]]])}))
