(ns pyregence.pages.near-term-forecast
  (:require [reagent.core :as r]
            [reagent.dom :as rd]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.core.async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
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
(defonce *model-time       (r/atom 0))
(defonce model-times       (r/atom []))
(defonce show-utc?         (r/atom true))
(defonce lon-lat           (r/atom [0 0]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn filtered-layers []
  (let [output-type (u/find-key-by-id c/output-types @*output-type :filter)
        fuel-type   (u/find-key-by-id c/fuel-types   @*fuel-type   :filter)
        ign-pattern (u/find-key-by-id c/ign-patterns @*ign-pattern :filter)
        model       (u/find-key-by-id c/models       @*model       :filter)
        model-time  (u/find-key-by-id @model-times   @*model-time  :filter)]
    (filterv (fn [{:keys [filter]}]
               (set/subset? #{output-type fuel-type ign-pattern model model-time "fire-risk-forecast"}
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
              (c/legend-url (str/replace layer "tlines" "all")))))

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
  (let [[workspace layer]           (str/split name-string #":")
        [forecast model-run]        (str/split workspace   #"_(?=\d{8}_)")
        [init-timestamp ignition]   (str/split model-run   #"-")
        [layer-group sim-timestamp] (str/split layer       #"_(?=\d{8}_)")
        init-js-date                (apply js-date-from-string (str/split init-timestamp #"_"))
        sim-js-date                 (apply js-date-from-string (str/split sim-timestamp  #"_"))]
    {:layer-group (str workspace ":" layer-group)
     :filter      (into #{forecast ignition init-timestamp} (str/split layer-group #"_"))
     :model-init  init-timestamp
     :sim-js-date sim-js-date
     :date        (get-date-from-js sim-js-date)
     :time        (get-time-from-js sim-js-date)
     :hour        (- (/ (- sim-js-date init-js-date) 1000 60 60) 6)}))

(defn process-capabilities! [response]
  (go
    (let [layers (as-> (<p! (.text response)) xml
                   (str/replace xml "\n" "")
                   (re-find #"<Layer>.*(?=</Layer>)" xml)
                   (str/replace-first xml "<Layer>" "")
                   (re-seq #"<Layer.+?</Layer>" xml)
                   (keep (fn [layer]
                           (let [full-name (->  (re-find #"<Name>.+?(?=</Name>)" layer)
                                                (str/replace #"<Name>" ""))
                                 coords    (->> (re-find #"<BoundingBox CRS=\"CRS:84.+?\"/>" layer)
                                                (re-seq #"[\d|\.|-]+")
                                                (rest)
                                                (vec))]
                             (when (re-matches #"[a-z|-]+_\d{8}_\d{2}-[a-z|-]+:[a-z|-]+_[a-z|-]+_[a-z|-]+_\d{8}_\d{6}" full-name)
                               (merge
                                (split-layer-name full-name)
                                {:layer  full-name
                                 :extent coords}))))
                         xml))]
      (reset! model-times
              (->> layers
                   (map :model-init)
                   (distinct)
                   (sort)
                   (reverse)
                   (map-indexed (fn [i model]
                                  {:opt-id    i
                                   :opt-label (if (= 0 i) "Current" model)
                                   :filter    model}))))
      (reset! layer-list layers))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn get-layers! []
  (get-data process-capabilities! c/capabilities-url))

(defn process-point-info! [response]
  (go
    (reset! last-clicked-info
            (as-> (<p! (.json response)) pi
              (u/try-js-aget pi "features")
              (map (fn [pi-layer]
                     {:band   (first (.values js/Object (u/try-js-aget pi-layer "properties")))
                      :vec-id (peek  (str/split (u/try-js-aget pi-layer "id") #"\."))})
                   pi)
              (filter (fn [pi-layer] (= (:vec-id pi-layer) (:vec-id (first pi))))
                      pi)
              (mapv (fn [pi-layer f-layer]
                      (merge (select-keys f-layer [:sim-js-date :time :date :hour])
                             pi-layer))
                    pi
                    (filtered-layers))))))

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
          (mapv (fn [{:keys [sim-js-date] :as layer}]
                  (assoc layer
                         :date (get-date-from-js sim-js-date)
                         :time (get-time-from-js sim-js-date)))
                @layer-list))
  (reset! last-clicked-info
          (mapv (fn [{:keys [sim-js-date] :as layer}]
                  (assoc layer
                         :date (get-date-from-js sim-js-date)
                         :time (get-time-from-js sim-js-date)))
                @last-clicked-info)))

(defn init-map! []
  (go
    (let [layers-chan (get-layers!)]
      (ol/init-map!)
      (select-base-map! @*base-map)
      (ol/add-map-single-click! get-point-info!)
      (ol/add-map-mouse-move! #(reset! lon-lat %))
      (let [[cur min max] (ol/get-zoom-info)]
        (reset! *zoom cur)
        (reset! minZoom min)
        (reset! maxZoom max))
      (ol/add-map-zoom-end! select-zoom!)
      (<! layers-chan)
      (select-layer! @*layer-idx)
      (ol/set-visible-by-title! "active" true)
      (get-legend! (get-current-layer-name)))))

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
         [mc/collapsible-panel
          *base-map
          select-base-map!
          *model
          *fuel-type
          *ign-pattern
          *output-type
          *model-time
          @model-times
          select-layer-option!]
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
