(ns pyregence.pages.near-term-forecast
  (:require [reagent.core :as r]
            [reagent.dom :as rd]
            [clojure.string :as str]
            [clojure.core.async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [herb.core :refer [<class]]
            [pyregence.styles :as $]
            [pyregence.utils  :as u]
            [pyregence.components.openlayers :as ol]
            [pyregence.components.vega       :refer [vega-box]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce minZoom           (r/atom 0))
(defonce maxZoom           (r/atom 28))
(defonce *zoom             (r/atom 10))
(defonce legend-list       (r/atom []))
(defonce layer-list        (r/atom []))
(defonce *layer-idx        (r/atom 0))
(defonce last-clicked-info (r/atom nil))
(defonce animate?          (r/atom false))
(defonce *layer-type       (r/atom 0))
(defonce *speed            (r/atom 1))
(defonce *base-map         (r/atom 0))
(defonce show-utc?         (r/atom true))
(defonce lon-lat           (r/atom [0 0]))

;; Static Data

(defonce layer-types [{:opt-id 0
                       :opt-label "Fire Area"
                       :filter "fire-area"
                       :units "Acres"}
                      {:opt-id 1
                       :opt-label "Fire Volume"
                       :filter "fire-volume"
                       :units "Acre-ft"}
                      {:opt-id 2
                       :opt-label "Impacted Structures"
                       :filter "impacted-structures"
                       :units "Structures"}
                      {:opt-id 3
                       :opt-label "Times Burned"
                       :filter "times-burned"
                       :units "Times"}])
(defonce speeds      [{:opt-id 0 :opt-label ".5x" :delay 2000}
                      {:opt-id 1 :opt-label "1x"  :delay 1000}
                      {:opt-id 2 :opt-label "2x"  :delay 500}
                      {:opt-id 3 :opt-label "5x"  :delay 200}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn filtered-layers []
  (let [filter-text (u/find-key-by-id layer-types @*layer-type :filter)]
    (filterv (fn [{:keys [type]}] (= type filter-text))
             @layer-list)))

(defn current-layer []
  (get (filtered-layers) @*layer-idx))

(defn get-current-layer-name []
  (:layer (current-layer) ""))

(defn get-current-layer-hour []
  (:hour (current-layer) 0))

(defn get-current-layer-full-time []
  (str/join "-" ((juxt :date :time) (current-layer))))

(defn get-current-layer-extent []
  (:extent (current-layer) [-124.83131903974008 32.36304641169675 -113.24176261416054 42.24506977982483]))

(defn get-data
  "Asynchronously fetches the JSON or XML resource at url. Returns a
  channel containing the result of calling process-fn on the response
  or nil if an error occurred."
  [process-fn url]
  (u/fetch-and-process url
                       {:method "get"
                        :headers {"Accept" "application/json, text/xml"
                                  "Content-Type" "application/json"}}
                       process-fn))

(defn process-legend! [response]
  (go
    (reset! legend-list
            (as-> (<p! (.json response)) data
              (u/try-js-aget data "Legend" 0 "rules" 0 "symbolizers" 0 "Raster" "colormap" "entries")
              (js->clj data)
              (remove (fn [leg] (= "nodata" (get leg "label"))) data)
              (doall data)))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn get-legend! [layer]
  (get-data process-legend!
            (str "https://californiafireforecast.com:8443/geoserver/demo/wms"
                 "?SERVICE=WMS"
                 "&VERSION=1.3.0"
                 "&REQUEST=GetLegendGraphic"
                 "&FORMAT=application/json"
                 "&LAYER=" layer)))

(defn js-date-from-string [date time]
  (js/Date. (str (subs date 0 4) "-"
                 (subs date 4 6) "-"
                 (subs date 6 8) "T"
                 (subs time 0 2) ":00:00.000z")))

(defn pad-zero [num]
  (let [num-str (str num)]
    (if (= 2 (count num-str))
      num-str
      (str "0" num-str))))

(defn get-date-from-js [js-date]
  (if @show-utc?
    (subs (.toISOString js-date) 0 10)
    (str (.getFullYear js-date)
         "-"
         (pad-zero (+ 1 (.getMonth js-date)))
         "-"
         (pad-zero (.getDate js-date)))))

(defn get-time-from-js [js-date]
  (if @show-utc?
    (str (subs (.toISOString js-date) 11 16) " UTC")
    (str (pad-zero (.getHours js-date))
         ":"
         (pad-zero (.getMinutes js-date))
         " "
         (-> js-date
             (.toLocaleTimeString "en-us" #js {:timeZoneName "short"})
             (str/split " ")
             (peek)))))

(defn process-capabilities! [response]
  (go
    (reset! layer-list
            (->> (-> (<p! (.text response))
                     ol/wms-capabilities
                     (u/try-js-aget "Capability" "Layer" "Layer"))
                 (remove #(str/starts-with? (aget % "Name") "lg-"))
                 (mapv (fn [layer]
                         (let [full-name        (aget layer "Name")
                               [type date time] (str/split full-name "_") ; TODO this might break if we expand file names
                               cur-date         (js-date-from-string date time)
                               base-date        (js-date-from-string "20200424" "130000")] ; TODO find first date for each group. This may come with model information
                           {:layer  full-name
                            :type   type
                            :extent  (aget layer "EX_GeographicBoundingBox")
                            :js-date cur-date
                            :date    (get-date-from-js cur-date)
                            :time    (get-time-from-js cur-date)
                            :hour    (/ (- cur-date base-date) 1000 60 60)})))))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn get-layers! []
  (get-data process-capabilities!
            (str "https://californiafireforecast.com:8443/geoserver/demo/wms"
                 "?SERVICE=WMS"
                 "&VERSION=1.3.0"
                 "&REQUEST=GetCapabilities"
                 "&NAMESPACE=demo")))

(defn process-point-info! [response]
  (go
    (reset! last-clicked-info
            (mapv (fn [pi li]
                    (merge (select-keys li [:js-date :time :date :hour :type])
                           {:band (u/try-js-aget pi "properties" "GRAY_INDEX")}))
                  (-> (<p! (.json response))
                      (u/try-js-aget "features"))
                  (filtered-layers)))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn get-point-info! [point-info]
  (reset! last-clicked-info nil)
  (when point-info
    (let [layer-str (u/find-key-by-id layer-types @*layer-type :filter)]
      (get-data process-point-info!
                (str "https://californiafireforecast.com:8443/geoserver/demo/wms"
                     "?SERVICE=WMS"
                     "&VERSION=1.3.0"
                     "&REQUEST=GetFeatureInfo"
                     "&INFO_FORMAT=application/json"
                     "&LAYERS=demo:lg-" layer-str
                     "&QUERY_LAYERS=demo:lg-" layer-str
                     "&FEATURE_COUNT=1000"
                     "&TILED=true"
                     "&I=0"
                     "&J=0"
                     "&WIDTH=1"
                     "&HEIGHT=1"
                     "&CRS=EPSG:3857"
                     "&STYLES="
                     "&BBOX=" (str/join "," point-info))))))

(defn select-layer! [new-layer]
  (reset! *layer-idx new-layer)
  (ol/swap-active-layer! (get-current-layer-name)))

(defn cycle-layer! [change]
  (select-layer! (mod (+ change @*layer-idx) (count (filtered-layers)))))

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

(defn select-layer-type! [id]
  (reset! *layer-type id)
  (ol/swap-active-layer! (get-current-layer-name))
  (get-point-info!       (ol/get-overlay-bbox))
  (get-legend!           (get-current-layer-name)))

(defn select-base-map! [id]
  (reset! *base-map id)
  (ol/set-base-map-source! (u/find-key-by-id ol/base-map-options @*base-map :source)))

(defn select-time-zone! [utc?]
  (reset! show-utc? utc?)
  (reset! layer-list
          (mapv (fn [{:keys [js-date] :as layer}]
                  (assoc layer
                         :date (get-date-from-js js-date)
                         :time (get-time-from-js js-date)))
                @layer-list))
  (reset! last-clicked-info
          (mapv (fn [{:keys [js-date] :as layer}]
                  (assoc layer
                         :date (get-date-from-js js-date)
                         :time (get-time-from-js js-date)))
                @last-clicked-info)))

(defn init-map! []
  (go
    (<! (get-layers!))
    (get-legend! (get-current-layer-name))
    (ol/init-map! (get-current-layer-name))
    (ol/add-map-single-click! get-point-info!)
    (ol/add-map-mouse-move! #(reset! lon-lat %))
    (let [[cur min max] (ol/get-zoom-info)]
      (reset! *zoom cur)
      (reset! minZoom min)
      (reset! maxZoom max))
    (ol/add-map-zoom-end! select-zoom!)))

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
   :bottom           "1rem"
   :border           "1px solid black"
   :border-radius    "5px"
   :right            ".5rem"
   :position         "absolute"
   :z-index          "100"})

(defn $legend-color [color]
  {:background-color color
   :height           "1rem"
   :margin-right     ".5rem"
   :width            "1rem"})

(defn $time-slider []
  {:align-items      "center"
   :background-color "white"
   :border           "1px solid black"
   :border-radius    "5px"
   :display          "flex"
   :margin-right     "auto"
   :margin-left      "auto"
   :left             "0"
   :right            "0"
   :padding          ".5rem"
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
   :right            "5rem"
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

(defn $mouse-info []
  {:background-color "white"
   :bottom           "1rem"
   :border           "1px solid black"
   :border-radius    "5px"
   :right            "12rem"
   :padding          ".5rem"
   :position         "absolute"
   :width            "14rem"
   :z-index          "100"})

(defn $pop-up-box []
  {:background-color "white"
   :border-radius    "5px"
   :box-shadow       "0 0 2px 1px rgba(0,0,0,0.1)"
   :margin-bottom    ".5rem"
   :padding          ".25rem .5rem"})

(defn $pop-up-arrow []
  {:background-color "white"
   :bottom           "0"
   :height           "1rem"
   :left             "0"
   :margin-left      "auto"
   :margin-right     "auto"
   :right            "0"
   :position         "absolute"
   :transform        "rotate(45deg)"
   :width            "1rem"
   :z-index          "-1"})

(defn $radio [checked?]
  (merge
   (when checked? {:background-color ($/color-picker :black 0.6)})
   {:border        "2px solid"
    :border-color  ($/color-picker :black)
    :border-radius "100%"
    :height        "1rem"
    :margin-right  ".4rem"
    :width         "1rem"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn legend-box []
  [:div#legend-box {:style ($legend-box)}
   [:div {:style {:display "flex" :flex-direction "column"}}
    (doall (map-indexed (fn [i leg]
                          ^{:key i}
                          [:div {:style ($/combine $/flex-row {:justify-content "flex-start" :padding ".5rem"})}
                           [:div {:style ($legend-color (get leg "color"))}]
                           [:label (get leg "label")]])
                        @legend-list))]])

(defn radio [label state condition]
  [:div {:style ($/flex-row)
         :on-click #(select-time-zone! condition)}
   [:div {:style ($/combine [$radio (= @state condition)])}]
   [:label {:style {:font-size ".8rem" :margin-top "2px"}} label]])

(defn time-slider []
  [:div#time-slider {:style ($time-slider)}
   [:div {:style ($/combine $/flex-col {:align-items "flex-start" :margin-right "1rem"})}
    [radio "UTC"   show-utc? true]
    [radio "Local" show-utc? false]]
   [:div {:style ($/flex-col)}
    [:input {:style {:width "12rem"}
             :type "range" :min "0" :max (dec (count (filtered-layers))) :value @*layer-idx
             :on-change #(select-layer! (u/input-int-value %))}]
    [:label {:style {:font-size ".75rem"}}
     (get-current-layer-full-time)]]
   [:button {:style {:padding ".25rem" :margin-left ".5rem"}
             :type "button"
             :on-click #(cycle-layer! -1)}
    "<<"]
   [:button {:style {:padding ".25rem"}
             :type "button"
             :on-click #(do (swap! animate? not)
                            (loop-animation!))}
    (if @animate? "Stop" "Play")]
   [:button {:style {:padding ".25rem"}
             :type "button"
             :on-click #(cycle-layer! 1)}
    ">>"]
   [:select {:style ($/combine $dropdown)
             :value (or @*speed 1)
             :on-change #(reset! *speed (u/input-int-value %))}
    (doall (map (fn [{:keys [opt-id opt-label]}]
                  [:option {:key opt-id :value opt-id} opt-label])
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
           :title "Center on selected point"
           :on-click #(ol/center-on-overlay!)}
    "C"]
   [:span {:class (<class $p-zoom-button-common)
           :style ($/combine ($/fixed-size "1.75rem") {:margin "1px" :padding ".25rem 0 0 .5rem" :font-size ".9rem"})
           :title "Zoom to fit layer"
           :on-click #(ol/zoom-to-extent! (get-current-layer-extent))}
    "E"]])

(defn layer-dropdown []
  [:div {:style {:display "flex" :flex-direction "column" :padding "0 3rem"}}
   [:label "Select Layer"]
   [:select {:style ($dropdown)
             :value (or @*layer-type -1)
             :on-change #(select-layer-type! (u/input-int-value %))}
    (doall (map (fn [{:keys [opt_id opt_label]}]
                  [:option {:key opt_id :value opt_id} opt_label])
                layer-types))]])

(defn panel-dropdown [title state options call-back]
  [:div {:style {:display "flex" :flex-direction "column"}}
   [:label title]
   [:select {:style ($dropdown)
             :value (or @state -1)
             :on-change #(call-back (u/input-int-value %))}
    (doall (map (fn [{:keys [opt-id opt-label]}]
                  [:option {:key opt-id :value opt-id} opt-label])
                options))]])

(defn collapsible-panel []
  (r/with-let [show-panel?       (r/atom true)
               active-opacity    (r/atom 70.0)
               hillshade-opacity (r/atom 50.0)
               show-hillshade?   (r/atom false)]
    [:div#collapsible-panel {:style ($collapsible-panel @show-panel?)}
     [:div {:style ($collapse-button)
            :on-click #(swap! show-panel? not)}
      [:label {:style {:padding-top "2px"}} (if @show-panel? "<<" ">>")]]
     [:div {:style {:display "flex" :flex-direction "column" :padding "3rem"}}
      [:div#baselayer
       [panel-dropdown "Base Layer" *base-map ol/base-map-options select-base-map!]
       [:div {:style {:margin-top ".5rem"}}
        [:div {:style {:display "flex"}}
         [:input {:style {:margin ".25rem .5rem 0 0"}
                  :type "checkbox"
                  :on-click #(do (swap! show-hillshade? not)
                                 (ol/set-visible-by-title! "hillshade" @show-hillshade?))}]
         [:label "Show hill shade"]]
        (when @show-hillshade?
          [:<> [:label (str "Opacity: " @hillshade-opacity)]
           [:input {:style {:width "100%"}
                    :type "range" :min "0" :max "100" :value @hillshade-opacity
                    :on-change #(do (reset! hillshade-opacity (u/input-int-value %))
                                    (ol/set-opacity-by-title! "hillshade" (/ @hillshade-opacity 100.0)))}]])]]
      [:div#activelayer {:style {:margin-top "2rem"}}
       [panel-dropdown "Active Layer" *layer-type layer-types select-layer-type!]
       [:div {:style {:margin-top ".5rem"}}
        [:label (str "Opacity: " @active-opacity)]
        [:input {:style {:width "100%"}
                 :type "range" :min "0" :max "100" :value @active-opacity
                 :on-change #(do (reset! active-opacity (u/input-int-value %))
                                 (ol/set-opacity-by-title! "active" (/ @active-opacity 100.0)))}]]]]]))

(defn mouse-info []
  [:div#mouse-info {:style ($mouse-info)}
   [:label {:style {:width "50%" :text-align "left" :padding-left ".5rem"}}
    "Lat:" (u/to-precision 4 (get @lon-lat 1))]
   [:label {:style {:width "50%" :text-align "left"}}
    "Lon:" (u/to-precision 4 (get @lon-lat 0))]])

(defn control-layer []
  (let [myself (r/atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [this] (reset! myself (rd/dom-node this)))

      :render
      (fn []
        [:div {:style {:height "100%" :position "absolute" :width "100%"}}
         [collapsible-panel]
         [legend-box]
         (when @myself
           [vega-box
            myself
            select-layer!
            (u/find-key-by-id layer-types @*layer-type :units)
            (get-current-layer-hour)
            @legend-list
            @last-clicked-info])
         [time-slider]
         [zoom-slider]
         [mouse-info]])})))

(defn pop-up []
  [:div#popup
   [:div {:style ($pop-up-box)}
    [:label (if @last-clicked-info
              (str (:band (get @last-clicked-info @*layer-idx))
                   " "
                   (u/find-key-by-id layer-types @*layer-type :units))
              "...")]]
   [:div {:style ($pop-up-arrow)}]])

(defn root-component [_]
  (r/create-class
   {:component-did-mount
    (fn [_] (init-map!))

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
