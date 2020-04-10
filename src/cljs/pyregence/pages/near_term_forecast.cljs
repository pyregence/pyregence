(ns pyregence.pages.near-term-forecast
  (:require [reagent.core :as r]
            [pyregence.components.openlayers :as ol]
            [pyregence.styles :as $]))

(def layers-list ["fire-area_20171006_070000"
                  "fire-area_20171007_070000"
                  "fire-area_20171008_070000"
                  "fire-area_20171009_070000"
                  "fire-area_20171010_070000"])

(defonce cur-layer (atom 0))

(defn root-component [_]
  (r/create-class
   {:component-did-mount
    #(ol/init-map! (get layers-list @cur-layer))

    :reagent-render
    (fn [_]
      [:div {:style ($/combine $/root {:height "100%" :padding 0})}
       [:div {:style {:display "flex"}}
        [:h1 "Click next to see the next time step fire area layer."]
        [:button {:style {:padding ".25rem" :margin-left "1rem"}
                  :type "button"
                  :on-click (fn []
                              (swap! cur-layer #(mod (inc %) (count layers-list)))
                              (ol/swap-active-layer! (get layers-list @cur-layer)))}
         "Next"]]
       [:div#map {:style {:height "100%" :width "100%"}}]])}))
