(ns pyregence.pages.near-term-forecast
  (:require [reagent.core :as r]
            [pyregence.components.openlayers :as ol]
            [pyregence.styles :as $]
            [pyregence.utils  :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce cur-layer      (r/atom 0))
(defonce legend-list    (r/atom []))
(defonce layer-list     (r/atom []))
(defonce layer-interval (r/atom nil))

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

(defn increment-layer []
  (swap! cur-layer #(mod (inc %) (count @layer-list)))
  (ol/swap-active-layer! (nth @layer-list @cur-layer)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $app-header []
  {:align-items     "center"
   :display         "flex"
   :height          "2.5rem"
   :justify-content "center"
   :position        "relative"
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

(defn $time-slider []
  {:background-color "white"
   :border           "1px solid black"
   :border-radius    "5px"
   :display          "flex"
   :margin-right     "auto"
   :margin-left      "auto"
   :left             "0"
   :right            "0"
   :position         "absolute"
   :bottom           "1rem"
   :width            "fit-content"
   :z-index          "100"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn legend-box []
  [:div {:style ($legend-box) :id "legend-box"}
   [:div {:style {:display "flex" :flex-direction "column"}}
    (->> @legend-list
         (remove (fn [leg] (= "nodata" (get leg "label"))))
         (map-indexed (fn [i leg]
                        ^{:key i}
                        [:div {:style ($/combine $/flex-row {:justify-content "flex-start"})}
                         [:div {:style ($legend-color (get leg "color"))}]
                         [:label (get leg "label")]]))
         (doall))]])

(defn time-slider []
  [:div {:style ($time-slider) :id "time-slider"}
   [:input {:style {:margin "1rem" :width "10rem"}
            :type "range" :min "0" :max (- (count @layer-list) 1) :value @cur-layer
            :on-change #(do
                          (reset! cur-layer (u/input-int-value %))
                          (ol/swap-active-layer! (nth @layer-list @cur-layer)))}]
   [:button {:style {:padding "0 .25rem" :margin ".5rem"}
             :type "button"
             :on-click #(when-not @layer-interval
                          (increment-layer)
                          (reset! layer-interval (js/setInterval increment-layer 1000)))}
    "Play"]
   [:button {:style {:padding "0 .25rem" :margin ".5rem"}
             :type "button"
             :on-click #(when @layer-interval
                          (.clearInterval js/window @layer-interval)
                          (reset! layer-interval nil))}
    "Stop"]])

(defn $collapsable-panel [show?]
  (merge
   {:background-color "white"
    :border-right     "2px solid black"
    :height           "100%"
    :position         "absolute"
    :transition       "all 200ms ease-in"
    :width            "20rem"
    :z-index          "1000"}
   (if show?
     {:left "0"}
     {:left "-20rem"})))

(defn $collapse-button []
  {:background-color "white"
   :border-right     "2px solid black"
   :border-top       "2px solid black"
   :border-bottom    "2px solid black"
   :border-left      "4px solid white"
   :border-radius    "0 5px 5px 0"
   :height           "2rem"
   :position         "absolute"
   :right            "-2rem"
   :top              ".5rem"
   :width            "2rem"})

(defn collapsable-panel []
  (r/with-let [show-panel? (r/atom true)]
    [:div {:id "collapsable-panel" :style ($collapsable-panel @show-panel?)}
     [:div {:style ($collapse-button)
            :on-click #(swap! show-panel? not)}
      [:label {:style {:padding-top "2px"}} (if @show-panel? "<<" ">>")]]
     [:label {:style {:padding "2rem"}} "test"]]))

(defn mask-layer []
  [:div {:style {:height "100%" :position "absolute" :width "100%"}}
   [collapsable-panel]
   [legend-box]
   [time-slider]])

(defn root-component [_]
  (r/create-class
   {:component-did-mount
    (fn [_]
      (-> (get-layers!)
          (.then (fn []
                   (get-legend!)
                   (ol/init-map! (nth @layer-list @cur-layer))))))

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
       [:div {:style {:height "100%" :position "relative" :width "100%"}}
        [mask-layer]
        [:div#map {:style {:height "100%" :position "absolute" :width "100%"}}]]])}))
