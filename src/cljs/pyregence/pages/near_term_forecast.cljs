(ns pyregence.pages.near-term-forecast
  (:require [cljsjs.vega-embed]
            [reagent.core :as r]
            [reagent.dom :as rd]
            [clojure.string :as str]
            [herb.core :refer [<class]]
            [pyregence.components.openlayers :as ol]
            [pyregence.styles :as $]
            [pyregence.utils  :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce minZoom           (r/atom 0))
(defonce maxZoom           (r/atom 28))
(defonce *zoom             (r/atom 10))
(defonce *cur-layer        (r/atom 0))
(defonce legend-list       (r/atom []))
(defonce layer-list        (r/atom []))
(defonce last-clicked-info (r/atom nil))
(defonce animate?          (r/atom false))
(defonce *layer-type       (r/atom 0))
(defonce *speed            (r/atom 1))
(defonce layer-types       [{:opt_id 0 :opt_label "Fire Area"           :filter "fire-area"}
                            {:opt_id 1 :opt_label "Fire Volume"         :filter "fire-volume"}
                            {:opt_id 2 :opt_label "Impacted Structures" :filter "impacted-structures"}
                            {:opt_id 3 :opt_label "Times Burned"        :filter "times-burned"}])
(defonce speeds            [{:opt_id 0 :opt_label ".5x" :delay 2000}
                            {:opt_id 1 :opt_label "1x"  :delay 1000}
                            {:opt_id 2 :opt_label "2x"  :delay 500}
                            {:opt_id 3 :opt_label "5x"  :delay 200}])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn filtered-layers []
  (let [filter-text (u/find-key-by-id layer-types @*layer-type :filter)]
    (filterv (fn [{:keys [type]}] (= type filter-text))
             @layer-list)))

(defn get-current-layer-name []
  (or (:layer-name (get (filtered-layers) @*cur-layer))
      ""))

(defn get-current-layer-hour []
  (or (:hour (get (filtered-layers) @*cur-layer))
      0))

(defn get-current-layer-extent []
  (or (:extent (get (filtered-layers) @*cur-layer))
      [-124.83131903974008 32.36304641169675 -113.24176261416054 42.24506977982483]))

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

(defn date-from-string [date time]
  (js/Date. (subs date 0 4)
            (subs date 4 6)
            (subs date 6 8)
            (subs time 0 2)))

(defn process-capabilities [response]
  (-> (.text response)
      (.then (fn [text]
               (reset! layer-list
                       (mapv (fn [layer]
                               (let [full-name   (get layer "Name")
                                     [type date time] (str/split full-name "_") ; TODO this might break if we expand file names
                                     cur-date    (date-from-string date time)
                                     base-date   (date-from-string "20200417" "140000")] ; TODO find first date for each group. This may come with model information
                                 {:layer-name full-name
                                  :type       type
                                  :extent     (get layer "EX_GeographicBoundingBox")
                                  :date       cur-date
                                  :hour       (/ (- cur-date base-date) 1000 60 60)}))
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
      (.then (fn [json]
               (reset! last-clicked-info
                       (mapv (fn [pi li]
                               (merge (select-keys li [:date :hour :type])
                                      {:band (get-in pi ["properties" "GRAY_INDEX"])}))
                             (-> json
                                 js->clj
                                 (get "features"))
                             (filtered-layers)))))))

;; TODO, get info again if user selects new layer
(defn get-point-info! [point-info]
  (reset! last-clicked-info nil)
  (let [layers-str (str/join "," (map #(str "demo:" (:layer-name %))
                                      (filtered-layers)))]
    (get-data! process-point-info
               (str "https://californiafireforecast.com:8443/geoserver/demo/wms"
                    "?SERVICE=WMS"
                    "&VERSION=1.3.0"
                    "&REQUEST=GetFeatureInfo"
                    "&INFO_FORMAT=application/json"
                    "&LAYERS=" layers-str
                    "&QUERY_LAYERS=" layers-str
                    "&FEATURE_COUNT=1000"
                    "&TILED=true"
                    "&I=0"
                    "&J=0"
                    "&WIDTH=1"
                    "&HEIGHT=1"
                    "&CRS=EPSG:3857"
                    "&STYLES="
                    "&BBOX=" (str/join "," point-info)))))

(defn cycle-layer! [change]
  (swap! *cur-layer #(mod (+ change %) (count (filtered-layers))))
  (ol/swap-active-layer! (get-current-layer-name)))

(defn loop-animation! []
  (when @animate?
    (cycle-layer! 1)
    (js/setTimeout loop-animation! (u/find-key-by-id speeds @*speed :delay))))

(defn select-zoom! [zoom]
  (when-not (= zoom @*zoom)
    (reset! *zoom (max @minZoom
                       (min @maxZoom
                            zoom)))
    (ol/set-zoom! @*zoom)))

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
   :top              "16rem"
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
   :padding          ".75rem"
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

(defn $zoom-slider []
  {:background-color "white"
   :border           "1px solid black"
   :border-radius    "5px"
   :right            "-3rem"
   :position         "absolute"
   :bottom           "5rem"
   :display          "flex"
   :transform        "rotate(270deg)"
   :width            "10rem"
   :height           "2rem"
   :z-index          "100"})

(defn $p-zoom-button-common []
  (with-meta
    {:border-radius "4px"
     :cursor        "pointer"
     :font-weight   "bold"
     :transform     "rotate(90deg)"}
    {:pseudo {:hover {:background-color ($/color-picker :sig-brown 0.1)}}}))

(defn $pop-up-box []
  {:background-color "white"
   :border-radius    "5px"
   :box-shadow       "0 0 2px 1px rgba(0,0,0,0.1)"
   :margin-bottom    ".5rem"
   :padding          ".25rem .5rem"})

(defn $pop-up-arrow []
  {:background-color "white"
   :bottom           "0"
   :height           "1.5rem"
   :left             "0"
   :margin-left      "auto"
   :margin-right     "auto"
   :right            "0"
   :position         "absolute"
   :transform        "rotate(45deg)"
   :width            "1.5rem"
   :z-index          "-1"})

(defn $vega-box []
  {:background-color "white"
   :border           "1px solid black"
   :border-radius    "5px"
   :height           "15rem"
   :overflow         "hidden"
   :padding-top      "1rem"
   :position         "absolute"
   :right            ".5rem"
   :top              ".5rem"
   :width            "18rem"
   :z-index          "100"})

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
         (doall))]])

(defn time-slider []
  [:div#time-slider {:style ($time-slider)}
   [:input {:style {:width "10rem"}
            :type "range" :min "0" :max (dec (count (filtered-layers))) :value @*cur-layer
            :on-change #(do (reset! *cur-layer (u/input-int-value %))
                            (ol/swap-active-layer! (get-current-layer-name)))}]
   [:button {:style {:padding "0 .25rem" :margin-left ".5rem"}
             :type "button"
             :on-click #(cycle-layer! -1)}
    "<<"]
   [:button {:style {:padding "0 .25rem"}
             :type "button"
             :on-click #(do (swap! animate? not)
                            (loop-animation!))}
    (if @animate? "Stop" "Play")]
   [:button {:style {:padding "0 .25rem"}
             :type "button"
             :on-click #(cycle-layer! 1)}
    ">>"]
   [:select {:style ($/combine $dropdown)
             :value (or @*speed 1)
             :on-change #(reset! *speed (u/input-int-value %))}
    (doall (map (fn [{:keys [opt_id opt_label]}]
                  [:option {:key opt_id :value opt_id} opt_label])
                speeds))]])

(defn zoom-slider []
  [:div#zoom-slider {:style ($zoom-slider)}
   [:span {:class (<class $p-zoom-button-common)
           :style ($/combine ($/fixed-size "1.75rem") {:margin "1px" :padding ".15rem 0 0 .75rem"})
           :on-click #(select-zoom! (dec @*zoom))}
    "-"]
   [:input {:style {:min-width "0"}
            :type "range" :min @minZoom :max @maxZoom :value @*zoom
            :on-change #(select-zoom! (u/input-int-value %))}]
   [:span {:class (<class $p-zoom-button-common)
           :style ($/combine ($/fixed-size "1.75rem") {:margin "1px" :padding ".15rem 0 0 .5rem"})
           :on-click #(select-zoom! (inc @*zoom))}
    "+"]
   [:span {:class (<class $p-zoom-button-common)
           :style ($/combine ($/fixed-size "1.75rem") {:margin "1px" :padding ".25rem 0 0 .5rem" :font-size ".9rem"})
           :title "Zoom to Extent"
           :on-click #(ol/zoom-to-extent! (get-current-layer-extent))}
    "E"]])

(defn layer-dropdown []
  [:div {:style {:display "flex" :flex-direction "column" :padding "0 3rem"}}
   [:label "Select Layer"]
   [:select {:style ($dropdown)
             :value (or @*layer-type -1)
             :on-change #(do (reset! *layer-type (u/input-int-value %))
                             (ol/swap-active-layer! (get-current-layer-name))
                             (get-legend! (get-current-layer-name)))}
    (doall (map (fn [{:keys [opt_id opt_label]}]
                  [:option {:key opt_id :value opt_id} opt_label])
                layer-types))]])

(defn layer-line-plot []
  {:autosize {:type "fit-y"}
   :data     {:values (or @last-clicked-info [])}
   :layer    [{:encoding {:x {:field "hour" :type "quantitative" :title "hour"}
                          :y {:field "band" :type "quantitative" :title "acres"} ; TODO change with layer
                          :color {:field "type" :type "nominal" :legend nil}}
               :layer [{:mark "line"}
                       ;; Layer with all points for selection
                       {:mark      "point"
                        :selection {:point-hover {:type    "single"
                                                  :nearest true
                                                  :on      "mouseover"
                                                  :empty   "none"}}}
                       {:transform [{:filter {:or [{:field "hour" :lt (get-current-layer-hour)}
                                                   {:field "hour" :gt (get-current-layer-hour)}]}}]
                        :mark     {:type   "point"
                                   :filled false
                                   :fill   "white"}
                        :encoding {:size {:condition {:selection :point-hover :value 100}
                                          :value 50}}}
                       ;; There needs to be a range of values for nearest neighbor to work
                       {:transform [{:filter {:and [{:field "hour" :lt (inc (get-current-layer-hour))}
                                                    {:field "hour" :gt (dec (get-current-layer-hour))}]}}]
                        :mark {:type   "point"
                               :filled false
                               :fill   "black"}
                        :encoding {:size {:condition {:selection :point-hover :value 100}
                                          :value 50}}}]}]})

(defn try-get-js [obj & values]
  (try
    (reduce
     (fn [acc cur]
       (if (and acc (.hasOwnProperty acc cur))
         (aget acc cur)
         nil))
     obj
     values)
    (catch js/Error e (.log js/console e) nil)))

(defn render-vega [spec elem]
  (when spec
    (let [spec (clj->js spec)
          opts {:renderer "canvas"
                :mode     "vega-lite"}]
      (-> (js/vegaEmbed elem spec (clj->js opts))
          (.then (fn [result]
                   (.addEventListener (-> result .-view)
                                      "click"
                                      (fn [_ data]
                                        (when-let [index (or (try-get-js data "datum" "datum" "_vgsid_")
                                                             (try-get-js data "datum" "_vgsid_"))]
                                          (reset! *cur-layer (dec ^js/integer index))
                                          (ol/swap-active-layer! (get-current-layer-name)))))))
          (.catch (fn [err] (js/console.log err)))))))

(defn vega-box [props]
  (r/create-class
   {:component-did-mount
    (fn [this] (render-vega (:spec props) (rd/dom-node this)))

    :component-did-update
    (fn [this _] (render-vega (:spec (r/props this)) (rd/dom-node this)))

    :render
    (fn [] [:div#vega-box {:style ($vega-box)}])}))

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

(defn control-layer []
  [:div {:style {:height "100%" :position "absolute" :width "100%"}}
   [collapsible-panel]
   [legend-box]
   [vega-box {:spec (layer-line-plot)}]
   [time-slider]
   [zoom-slider]])

(defn pop-up []
  [:div#popup
   [:div {:style ($pop-up-box)}
    [:label (if @last-clicked-info
              (str (:band (get @last-clicked-info @*cur-layer))
                   " acre" ; TODO change with layer
                   (when-not (= 1 @last-clicked-info) "s"))
              "...")]]
   [:div {:style ($pop-up-arrow)}]])

(defn root-component [_]
  (r/create-class
   {:component-did-mount
    (fn [_]
      (-> (get-layers!)
          (.then (fn []
                   (get-legend!  (get-current-layer-name))
                   (ol/init-map! (get-current-layer-name))
                   (ol/add-map-single-click! get-point-info!)
                   (let [[cur min max] (ol/get-zoom-info)]
                     (reset! *zoom   cur)
                     (reset! minZoom min)
                     (reset! maxZoom max))
                   (ol/add-map-zoom-end! select-zoom!)))))

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
        [control-layer]
        [:div#map {:style {:height "100%" :position "absolute" :width "100%"}}
         [pop-up]]]])}))
