(ns pyregence.pages.near-term-forecast
  (:require [reagent.core :as r]
            [reagent.dom :as rd]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.core.async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [goog.string :refer [format]]
            [pyregence.styles :as $]
            [pyregence.utils  :as u]
            [pyregence.config :as c]
            [pyregence.components.map-controls :as mc]
            [pyregence.components.openlayers   :as ol]
            [pyregence.components.vega         :refer [vega-box]]))

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
(defonce *model            (r/atom 0))
(defonce *fuel-type        (r/atom 0))
(defonce *ign-pattern      (r/atom 0))
(defonce *output-type      (r/atom 0))
(defonce *speed            (r/atom 1))
(defonce *base-map         (r/atom 0))
(defonce show-utc?         (r/atom true))
(defonce lon-lat           (r/atom [0 0]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn filtered-layers []
  (let [output-type (u/find-key-by-id c/output-types @*output-type :filter)
        fuel-type   (u/find-key-by-id c/fuel-types   @*fuel-type   :filter)
        ign-pattern (u/find-key-by-id c/ign-patterns @*ign-pattern :filter)]
    (filterv (fn [{:keys [filter]}]
               (set/subset? #{output-type fuel-type ign-pattern "elmfire" "fire-risk-forecast" "20200516_06"}
                            filter))
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

(defn get-current-layer-group []
  (:layer-group (current-layer) ""))

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
  (when (u/has-data? layer)
    (get-data process-legend!
              (c/legend-url layer))))

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

(defn split-layer-name [name-string]
  (let [[workspace base]    (str/split name-string ":")
        [forecast model]    (str/split workspace #"_(?=\d{8}_)")
        [model ingnition]   (str/split model "-")
        [options date-time] (str/split base #"_(?=\d{6}_)") ; FIXME, this is temporary to match current layers (should be {8})
        [date time]         (str/split (str "20" date-time) "_") ; FIXME, this is temporary to match current layers
        [b-date b-time]     (str/split model "_")
        base-date           (js-date-from-string b-date b-time)
        cur-date            (js-date-from-string date time)]
    {:layer-group (str workspace ":" options)
     :filter      (set/union #{forecast ingnition model} (set (str/split options "_")))
     :model       model
     :js-date     cur-date
     :date        (get-date-from-js cur-date)
     :time        (get-time-from-js cur-date)
     :hour        (- (/ (- cur-date base-date) 1000 60 60) 6)}))

(defn process-capabilities! [response]
  (go
    (reset! layer-list
            (as-> (<p! (.text response)) layers
              (ol/wms-capabilities layers)
              (u/try-js-aget layers "Capability" "Layer" "Layer")
              (map (fn [layer]
                     (let [full-name (aget layer "Name")]
                       (when (re-matches #"[a-z|-]+_\d{8}_\d{2}-[a-z|-]+:[a-z|-]+_[a-z|-]+_[a-z|-]+_\d{6}_\d{6}" full-name) ; FIXME, temp for bad script. Should be {8}
                         (merge
                          (split-layer-name full-name)
                          {:layer  full-name
                           :extent (aget layer "EX_GeographicBoundingBox")}))))
                   layers)
              (remove nil? layers)
              (vec layers)))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn get-layers! []
  (get-data process-capabilities!
            (format c/capabilities-url)))

(defn process-point-info! [response]
  (go
    (reset! last-clicked-info
            (mapv (fn [pi li]
                    (merge (select-keys li [:js-date :time :date :hour])
                           {:band (u/try-js-aget pi "properties" "GRAY_INDEX")}))
                  (-> (<p! (.json response))
                      (u/try-js-aget "features"))
                  (filtered-layers)))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn get-point-info! [point-info]
  (reset! last-clicked-info nil)
  (let [layer-group (get-current-layer-group)]
    (when-not (u/missing-data? layer-group point-info)
      (get-data process-point-info!
                (c/point-info-url layer-group
                                  (str/join "," point-info))))))

(defn select-layer! [new-layer]
  (reset! *layer-idx new-layer)
  (ol/swap-active-layer! (get-current-layer-name)))

(defn cycle-layer! [change]
  (select-layer! (mod (+ change @*layer-idx) (count (filtered-layers)))))

(defn loop-animation! []
  (when @animate?
    (cycle-layer! 1)
    (js/setTimeout loop-animation! (u/find-key-by-id c/speeds @*speed :delay))))

(defn select-zoom! [zoom]
  (when-not (= zoom @*zoom)
    (reset! *zoom (max @minZoom
                       (min @maxZoom
                            zoom)))
    (ol/set-zoom! @*zoom)))

(defn select-layer-option! [*option id]
  (reset! *option id)
  (ol/swap-active-layer! (get-current-layer-name))
  (get-point-info!       (ol/get-overlay-bbox))
  (get-legend!           (get-current-layer-name)))

(defn select-base-map! [id]
  (reset! *base-map id)
  (ol/set-base-map-source! (u/find-key-by-id c/base-map-options @*base-map :source)))

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
    (ol/init-map! (get-current-layer-name)
                  (u/find-key-by-id c/base-map-options @*base-map :source))
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn control-layer []
  (let [my-box (r/atom #js {})]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (let [this-node      (rd/dom-node this)
              update-my-box! (fn [& _] (reset! my-box (.getBoundingClientRect this-node)))]
          (update-my-box!)
          (-> (js/ResizeObserver. update-my-box!)
              (.observe this-node))))

      :render
      (fn []
        [:div {:style {:height "100%" :position "absolute" :width "100%"}}
         [mc/collapsible-panel *base-map select-base-map! *model *fuel-type *ign-pattern *output-type select-layer-option!]
         [mc/legend-box legend-list]
         (when (aget @my-box "height")
           [vega-box
            @my-box
            select-layer!
            (u/find-key-by-id c/output-types @*output-type :units)
            (get-current-layer-hour)
            @legend-list
            @last-clicked-info])
         [mc/time-slider
          filtered-layers
          @*layer-idx
          get-current-layer-full-time
          select-layer!
          cycle-layer!
          show-utc?
          select-time-zone!
          animate?
          loop-animation!
          *speed]
         [mc/zoom-slider @minZoom @maxZoom @*zoom select-zoom! get-current-layer-extent]
         [mc/mouse-info @lon-lat]])})))

(defn pop-up []
  [:div#popup
   [:div {:style ($pop-up-box)}
    [:label (if @last-clicked-info
              (str (:band (get @last-clicked-info @*layer-idx))
                   " "
                   (u/find-key-by-id c/output-types @*output-type :units))
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
