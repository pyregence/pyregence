(ns pyregence.pages.near-term-forecast
  (:require [reagent.core :as r]
            [reagent.dom :as rd]
            [clojure.string :as str]
            [clojure.core.async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [pyregence.styles :as $]
            [pyregence.utils  :as u]
            [pyregence.config :as c]
            [pyregence.components.common       :refer [radio]]
            [pyregence.components.map-controls :as mc]
            [pyregence.components.messaging    :refer [toast-message
                                                       toast-message!
                                                       process-toast-messages!]]
            [pyregence.components.openlayers   :as ol]
            [pyregence.components.svg-icons    :refer [pin]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce minZoom           (r/atom 0))
(defonce maxZoom           (r/atom 28))
(defonce *zoom             (r/atom 10))
(defonce legend-list       (r/atom []))
(defonce layer-list        (r/atom []))
(defonce *layer-idx        (r/atom 0))
(defonce last-clicked-info (r/atom []))
(defonce animate?          (r/atom false))
(defonce *speed            (r/atom 1))
(defonce *base-map         (r/atom 0))
(defonce show-utc?         (r/atom true))
(defonce lon-lat           (r/atom [0 0]))
(defonce show-info?        (r/atom false))
(defonce show-measure?     (r/atom false))
(defonce *forecast         (r/atom 1))
(defonce *params           (r/atom {}))
(defonce processed-params  (r/atom []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-forecast-opt [key-name]
  (get-in c/forecast-options [@*forecast key-name]))

(defn get-fire-names [forecast-layers]
  (->> forecast-layers
       (group-by :fire-name)
       (mapv (fn [[fire-name opt-vec]]
               {:opt-label  fire-name
                :filter     fire-name
                :model-init (->> opt-vec
                                 (map :model-init)
                                 (distinct)
                                 (set))}))))

(defn get-model-times [forecast-layers]
  (->> forecast-layers
       (map :model-init)
       (distinct)
       (sort)
       (reverse)
       (mapv (fn [option]
               {:opt-label option
                :filter    option}))))

(defn process-params! []
  (reset! processed-params
          (let [forecast-filter (get-forecast-opt :filter)
                forecast-layers (filter (fn [layer]
                                          (= forecast-filter (:forecast layer)))
                                        @layer-list)]
            (->> (get-forecast-opt :params)
                 (mapv (fn [{:keys [opt-label] :as param}]
                         (cond
                           (= "Model Time" opt-label)
                           (assoc param :options (get-model-times forecast-layers))

                           (= "Fire Name" opt-label)
                           (assoc param :options (get-fire-names forecast-layers))

                           :else
                           param)))))))

(defn filtered-layers []
  (let [selected-set (-> (map (fn [*option {:keys [options]}]
                                (get-in options [*option :filter]))
                              @*params
                              @processed-params)
                         (set)
                         (conj (get-forecast-opt :filter)))]
    (filterv (fn [{:keys [filter-set]}] (= selected-set filter-set))
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

(defn get-current-layer-key [key-name]
  (->>
   (map (fn [*option {:keys [options]}]
          (get-in options [*option key-name]))
        @*params
        (get-forecast-opt :params))
   (remove nil?)
   (first)))

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

(defn split-risk-layer-name [name-string]
  (let [[workspace layer]           (str/split name-string #":")
        [forecast init-timestamp]   (str/split workspace   #"_(?=\d{8}_)")
        [layer-group sim-timestamp] (str/split layer       #"_(?=\d{8}_)")
        init-js-date                (apply js-date-from-string (str/split init-timestamp #"_"))
        sim-js-date                 (apply js-date-from-string (str/split sim-timestamp  #"_"))]
    {:layer-group (str workspace ":" layer-group)
     :forecast    forecast
     :filter-set  (into #{forecast init-timestamp} (str/split layer-group #"_"))
     :model-init  init-timestamp
     :sim-js-date sim-js-date
     :date        (get-date-from-js sim-js-date)
     :time        (get-time-from-js sim-js-date)
     :hour        (- (/ (- sim-js-date init-js-date) 1000 60 60) 6)}))

(defn split-active-layer-name [name-string]
  (let [[workspace layer]    (str/split name-string #":")
        [forecast fire-name] (str/split workspace   #"_")
        params               (str/split layer       #"_")
        init-timestamp       (str (get params 0) "_" (get params 1))
        sim-js-date          (js-date-from-string (get params 6) (get params 7))]
    {:layer-group ""
     :forecast    forecast
     :fire-name   fire-name
     :filter-set  (into #{forecast fire-name init-timestamp} (subvec params 2 6))
     :model-init  init-timestamp
     :sim-js-date sim-js-date
     :date        (get-date-from-js sim-js-date)
     :time        (get-time-from-js sim-js-date)
     :hour        0}))

(defn process-capabilities! [response]
  (go
    (reset! layer-list
            (as-> (<p! (.text response)) xml
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
                                           (vec))
                            merge-fn  #(merge % {:layer full-name :extent coords})]
                        (cond
                          (re-matches #"([a-z|-]+_)\d{8}_\d{2}:([a-z|-]+_){4}\d{8}_\d{6}" full-name)
                          (merge-fn (split-risk-layer-name full-name))

                          (re-matches #"([a-z|-]+_)[a-z|-|\d]+:\d{8}_\d{6}_([a-z|-]+_){2}\d{2}_([a-z|-]+_)\d{8}_\d{6}" full-name)
                          (merge-fn (split-active-layer-name full-name)))))
                    xml)))))

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
    (js/setTimeout loop-animation! (get-in c/speeds [@*speed :delay]))))

(defn select-zoom! [zoom]
  (reset! *zoom (max @minZoom
                     (min @maxZoom
                          zoom)))
  (ol/set-zoom! @*zoom))

(defn clear-info! []
  (ol/clear-point!)
  (reset! last-clicked-info [])
  (when (get-forecast-opt :block-info?)
    (reset! show-info? false)))

(defn find-index-vec-map [key-name val coll]
  (first (keep-indexed (fn [i entry]
                         (when (= val (get entry key-name)) i))
                       coll)))

(defn check-param-filter []
  (swap! *params
         (fn [params]
           (->> params
                (map-indexed (fn [idx cur-param]
                               (let [{:keys [filter-on filter-key filter options]} (get @processed-params idx)
                                     filter-set (get-in @processed-params [filter-on :options cur-param filter-key])]
                                 (if (and filter-on (not (contains? filter-set filter)))
                                   (or (find-index-vec-map :filter (first filter-set) options) -1)
                                   cur-param))))
                (vec)))))

(defn change-type! [clear?]
  (check-param-filter)
  (ol/swap-active-layer! (get-current-layer-name))
  (get-legend!           (get-current-layer-name))
  (if clear?
    (clear-info!)
    (get-point-info! (ol/get-overlay-bbox)))
  (when (get-forecast-opt :auto-zoom?)
    (ol/zoom-to-extent! (get-current-layer-extent))))

(defn select-param! [idx val]
  (swap! *params assoc idx val)
  (change-type! (get-current-layer-key :clear-point?)))

(defn select-forecast! [id]
  (reset! *forecast id)
  (process-params!)
  (reset! *params (mapv (constantly 0) @processed-params)) ; TODO, move this to process-params
  (change-type! true))

(defn set-show-info! [show?]
  (if (get-forecast-opt :block-info?)
    (toast-message! "There is currently no point information available for this layer.")
    (do (reset! show-info? show?)
        (if show?
          (ol/add-popup-on-single-click! get-point-info!)
          (do (ol/remove-popup-on-single-click!)
              (clear-info!))))))

(defn select-base-map! [id]
  (reset! *base-map id)
  (ol/set-base-map-source! (get-in c/base-map-options [@*base-map :source])))

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
      (ol/add-map-mouse-move! #(reset! lon-lat %))
      (let [[cur min max] (ol/get-zoom-info)]
        (reset! *zoom cur)
        (reset! minZoom min)
        (reset! maxZoom max))
      (ol/add-map-zoom-end! #(reset! *zoom %))
      (<! layers-chan)
      (select-forecast! @*forecast)
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

(defn $forecast-label [selected?]
  (merge
   {:cursor "pointer"
    :margin "0 1rem 0 1rem"}
   (when selected? {:color "white"})))

(defn $control-layer []
  {:height   "100%"
   :position "absolute"
   :width    "100%"})

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
        [:div {:style ($control-layer)}
         [mc/tool-bar show-info? show-measure? set-show-info!]
         [mc/zoom-bar @*zoom select-zoom! get-current-layer-extent]
         [mc/collapsible-panel
          @*base-map
          select-base-map!
          @*params
          select-param!
          @processed-params]
         (when (and @show-info? (aget @my-box "height"))
           [mc/information-tool
            @my-box
            *layer-idx
            select-layer!
            (get-current-layer-key :units)
            (get-current-layer-hour)
            @legend-list
            @last-clicked-info])
         (when (and @show-measure? (aget @my-box "height"))
           [mc/measure-tool @my-box @lon-lat])
         [mc/legend-box legend-list (get-forecast-opt :reverse-legend?)]
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
          *speed]])})))

(defn pop-up []
  [:div#pin {:style ($/fixed-size "2rem")}
   [pin]])

(defn map-layer []
  (r/with-let [mouse-down? (r/atom false)
               cursor-fn   #(cond
                              @mouse-down?                    "grabbing"
                              (or @show-info? @show-measure?) "crosshair" ; TODO get custom cursor image from Ryan
                              :else                           "grab")]
    [:div#map {:style {:height "100%" :position "absolute" :width "100%" :cursor (cursor-fn)}
               :on-mouse-down #(reset! mouse-down? true)
               :on-mouse-up #(reset! mouse-down? false)}]))

(defn theme-select []
  [:div {:style {:position "absolute" :right "3rem" :display "flex"}}
   [:label {:style {:margin "4px .5rem 0"}} "Theme:"]
   [radio "Light" $/light? true  #(reset! $/light? %)]
   [radio "Dark"  $/light? false #(reset! $/light? %)]])

(defn root-component [_]
  (r/create-class
   {:component-did-mount
    (fn [_]
      (process-toast-messages!)
      (init-map!))

    :reagent-render
    (fn [_]
      [:div {:style ($/combine $/root {:height "100%" :padding 0})}
       [toast-message]
       [:div {:class "bg-yellow"
              :style ($app-header)}
        [theme-select]
        [:span
         (doall (map-indexed (fn [i {:keys [opt-label]}]
                               [:label {:key i
                                        :style ($forecast-label (= @*forecast i))
                                        :on-click #(select-forecast! i)}
                                opt-label])
                             c/forecast-options))]]
       [:div {:style {:height "100%" :position "relative" :width "100%"}}
        [control-layer]
        [map-layer]
        [pop-up]]])}))
