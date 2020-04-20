(ns pyregence.pages.near-term-forecast
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [pyregence.components.openlayers :as ol]
            [pyregence.styles :as $]
            [pyregence.utils  :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce cur-layer         (r/atom 0))
(defonce legend-list       (r/atom []))
(defonce layer-list        (r/atom []))
(defonce last-clicked-info (r/atom 0))
(defonce layer-interval    (r/atom nil))
(defonce *layer-type       (r/atom 0))
(defonce layer-types       [{:opt_id 0 :opt_label "Fire Area"           :filter "fire-area"}
                            {:opt_id 1 :opt_label "Fire Volume"         :filter "fire-volume"}
                            {:opt_id 2 :opt_label "Impacted Structures" :filter "impacted-structures"}
                            {:opt_id 3 :opt_label "Times Burned"        :filter "times-burned"}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn filtered-layers []
  (let [filter-text (u/find-key-by-id layer-types @*layer-type :filter)]
    (filterv (fn [layer-name]
               (str/includes? layer-name
                              filter-text))
             @layer-list)))

(defn get-current-layer []
  (get (filtered-layers) @cur-layer ""))

(defn get-data! [process-fn url]
  (-> (.fetch js/window
              url
              (clj->js {:method "get"
                        :headers {"Accept" "application/json, text/xml"
                                  "Content-Type" "application/json"}}))
      (.then  (fn [response] (if (.-ok response)
                               (process-fn response)
                               (.reject js/Promise response))))
      (.catch (fn [response] (.log js/console response)))))

(defn process-legend [response]
  (-> (.json response)
      (.then (fn [json]
               (reset! legend-list
                       (-> json
                           js->clj
                           (get-in ["Legend" 0 "rules" 0 "symbolizers" 0 "Raster" "colormap" "entries"])))))))

(defn get-legend! [layer]
  (get-data! process-legend
             (str "https://californiafireforecast.com:8443/geoserver/demo/wms"
                  "?SERVICE=WMS"
                  "&VERSION=1.3.0"
                  "&REQUEST=GetLegendGraphic"
                  "&FORMAT=application/json"
                  "&LAYER=" layer)))

(defn process-capabilities [response]
  (-> (.text response)
      (.then (fn [text]
               (reset! layer-list
                       (mapv (fn [layer] (get layer "Name"))
                             (-> text
                                 ol/wms-capabilities
                                 js->clj
                                 (get-in ["Capability" "Layer" "Layer"]))))))))

(defn get-layers! []
  (get-data! process-capabilities
             (str "https://californiafireforecast.com:8443/geoserver/demo/wms"
                  "?SERVICE=WMS"
                  "&VERSION=1.3.0"
                  "&REQUEST=GetCapabilities"
                  "&NAMESPACE=demo")))

(defn process-point-info [response]
  (-> (.json response)
      (.then (fn [json] (reset! last-clicked-info
                                (-> json
                                    js->clj
                                    (get-in ["features" 0 "properties" "GRAY_INDEX"])))))))

(defn get-point-info! [point-info]
  (get-data! process-point-info
             (str "https://californiafireforecast.com:8443/geoserver/demo/wms"
                  "?SERVICE=WMS"
                  "&VERSION=1.3.0"
                  "&REQUEST=GetFeatureInfo"
                  "&INFO_FORMAT=application/json"
                  "&LAYERS=demo:" (get-current-layer)
                  "&QUERY_LAYERS=demo:" (get-current-layer)
                  "&TILED=true"
                  "&I=0"
                  "&J=0"
                  "&WIDTH=1"
                  "&HEIGHT=1"
                  "&CRS=" (:crs point-info)
                  "&STYLES="
                  "&BBOX=" (str/join "," (:bbox point-info)))))

(defn increment-layer! []
  (swap! cur-layer #(mod (inc %) (count (filtered-layers))))
  (ol/swap-active-layer! (get-current-layer)))

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

(defn $collapsible-panel [show?]
  {:background-color "white"
   :border-right     "2px solid black"
   :height           "100%"
   :position         "absolute"
   :transition       "all 200ms ease-in"
   :width            "20rem"
   :z-index          "1000"
   :left             (if show? "0" "-20rem")})

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

(defn $dropdown []
  {:background-color "white"
   :border           "1px solid"
   :border-color     ($/color-picker :sig-brown)
   :border-radius    "2px"
   :font-family      "inherit"
   :height           "2rem"
   :padding          ".25rem .5rem"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn legend-box []
  [:div#legend-box {:style ($legend-box)}
   [:div {:style {:display "flex" :flex-direction "column"}}
    (->> @legend-list
         (remove (fn [leg] (= "nodata" (get leg "label"))))
         (map-indexed (fn [i leg]
                        ^{:key i}
                        [:div {:style ($/combine $/flex-row {:justify-content "flex-start"})}
                         [:div {:style ($legend-color (get leg "color"))}]
                         [:label (get leg "label")]]))
         (doall))
    [:label {:style {:padding ".5rem"}}
     (str "Last Clicked: " (or @last-clicked-info 0))]]])

(defn time-slider []
  [:div#time-slider {:style ($time-slider)}
   [:input {:style {:margin "1rem" :width "10rem"}
            :type "range" :min "0" :max (dec (count (filtered-layers))) :value @cur-layer
            :on-change #(do (reset! cur-layer (u/input-int-value %))
                            (ol/swap-active-layer! (get-current-layer)))}]
   [:button {:style {:padding "0 .25rem" :margin ".5rem"}
             :type "button"
             :disabled @layer-interval
             :on-click #(do (increment-layer!)
                            (reset! layer-interval (js/setInterval increment-layer! 1000)))}
    "Play"]
   [:button {:style {:padding "0 .25rem" :margin ".5rem"}
             :type "button"
             :disabled (not @layer-interval)
             :on-click #(do (.clearInterval js/window @layer-interval)
                            (reset! layer-interval nil))}
    "Stop"]])

(defn layer-dropdown []
  [:div {:style {:display "flex" :flex-direction "column" :padding "0 3rem"}}
   [:label "Select Layer"]
   [:select {:style ($dropdown)
             :value (or @*layer-type -1)
             :on-change #(do (reset! *layer-type (u/input-int-value %))
                             (ol/swap-active-layer! (get-current-layer))
                             (get-legend! (get-current-layer)))}
    (doall (map (fn [{:keys [opt_id opt_label]}]
                  [:option {:key opt_id :value opt_id} opt_label])
                layer-types))]])

(defn collapsible-panel []
  (r/with-let [show-panel?   (r/atom true)
               layer-opacity (r/atom 100.0)]
    [:div#collapsible-panel {:style ($collapsible-panel @show-panel?)}
     [:div {:style ($collapse-button)
            :on-click #(swap! show-panel? not)}
      [:label {:style {:padding-top "2px"}} (if @show-panel? "<<" ">>")]]
     [:div {:style ($/combine $/flex-col {:margin "2rem"})}
      [:label (str "Active Layer Opacity: " @layer-opacity)]
      [:input {:style {:margin-top ".25rem" :width "13rem"}
               :type "range" :min "0" :max "100" :value @layer-opacity
               :on-change #(do
                             (reset! layer-opacity (u/input-int-value %))
                             (ol/set-active-layer-opacity! (/ @layer-opacity 100.0)))}]]
     [layer-dropdown]]))

(defn mask-layer []
  [:div {:style {:height "100%" :position "absolute" :width "100%"}}
   [collapsible-panel]
   [legend-box]
   [time-slider]])

(defn root-component [_]
  (r/create-class
   {:component-did-mount
    (fn [_]
      (-> (get-layers!)
          (.then (fn []
                   (get-legend!  (get-current-layer))
                   (ol/init-map! (get-current-layer)
                                 #(ol/add-map-single-click! get-point-info!))))))

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
