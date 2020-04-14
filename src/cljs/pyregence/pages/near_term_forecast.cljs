(ns pyregence.pages.near-term-forecast
  (:require [reagent.core :as r]
            [pyregence.components.openlayers :as ol]
            [pyregence.styles :as $]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce cur-layer   (atom 0))
(defonce legend-list (r/atom []))
(defonce layer-list  (r/atom []))

(def point-layers-list  ["fire-area_20171006_070000"
                         "fire-area_20171007_070000"
                         "fire-area_20171008_070000"
                         "fire-area_20171009_070000"
                         "fire-area_20171010_070000"])

(def interp-layers-list ["fire-area_20200414_070000"
                         "fire-area_20200415_070000"])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-data! [process-fn url]
  (let [fetch-params {:method "get"
                      :headers {"Accept" "application/json, text/xml"
                                "Content-Type" "application/json"}}]
    (-> (.fetch js/window
                url
                (clj->js fetch-params))
        (.then  (fn [response] (if (.-ok response)
                                 (process-fn response)
                                 (.reject js/Promise response))))
        (.catch (fn [response] (.log js/console response))))))

(defn process-legend [response]
  (-> (.json response)
      (.then (fn [json]
               (reset! legend-list
                       (-> json
                           js->clj
                           (get-in ["Legend" 0 "rules" 0 "symbolizers" 0 "Raster" "colormap" "entries"])))))))

(defn get-legend! []
  (get-data! process-legend
             (str "http://californiafireforecast.com:8181/geoserver/demo/wms"
                  "?SERVICE=WMS"
                  "&VERSION=1.3.0"
                  "&REQUEST=GetLegendGraphic"
                  "&FORMAT=application/json"
                  "&LAYER=demo%3Afire-area_20200414_070000")))

(defn process-capabilities [response]
  (-> (.text response)
      (.then (fn [text]
               (reset! layer-list
                       (map (fn [layer] (get layer "Name"))
                            (-> text
                                ol/wms-capabilities
                                js->clj
                                (get-in ["Capability" "Layer" "Layer"]))))))))

(defn get-layers! []
  (get-data! process-capabilities
             (str "http://californiafireforecast.com:8181/geoserver/demo/wms"
                  "?SERVICE=WMS"
                  "&VERSION=1.3.0"
                  "&REQUEST=GetCapabilities"
                  "&NAMESPACE=demo")))

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

(defn $legend-box []
  {:background-color "white"
   :border           "1px solid black"
   :border-radius    "5px"
   :right            ".5rem"
   :position         "absolute"
   :top              ".5rem"
   :z-index          "100"})

(defn $legend-color [color]
  {:background-color color
   :height           "1rem"
   :margin-right     ".5rem"
   :width            "1rem"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn legend-box []
  [:div {:style ($legend-box)}
   [:div {:style {:display "flex" :flex-direction "column"}}
    (->> @legend-list
         (remove (fn [leg] (= "nodata" (get leg "label"))))
         (map-indexed (fn [i leg]
                        ^{:key i}
                        [:div {:style ($/combine $/flex-row {:justify-content "flex-start"})}
                         [:div {:style ($legend-color (get leg "color"))}]
                         [:label (get leg "label")]]))
         (doall))]])

(defn root-component [_]
  (r/create-class
   {:component-did-mount
    (fn [_]
      (get-layers!)
      (get-legend!)
      (ol/init-map! (get interp-layers-list @cur-layer)))

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
        [legend-box]
        [:button {:style {:padding ".25rem" :position "absolute" :top "4.5rem" :left ".25rem" :z-index "100"}
                  :type "button"
                  :on-click (fn []
                              (swap! cur-layer #(mod (inc %) (count interp-layers-list)))
                              (ol/swap-active-layer! (get interp-layers-list @cur-layer)))}
         "Next"]]])}))
