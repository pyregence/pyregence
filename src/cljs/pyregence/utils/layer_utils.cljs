(ns pyregence.utils.layer-utils
  (:require
            [clojure.core.async          :refer [go <!]]
            [clojure.string              :as str]
            [cognitect.transit           :as t]
            [pyregence.components.mapbox :as mb]
            [pyregence.components.popups :refer [fire-popup]]
            [pyregence.components.messaging :refer [toast-message!]]
            [pyregence.config            :as c]
            [pyregence.state             :as !]
            [pyregence.utils.async-utils :as u-async]
            [pyregence.utils.misc-utils  :as u-misc :refer [try-js-aget]]
            [pyregence.utils.time-utils  :as u-time]
            ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility Functions - Layers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-forecast-opt [key-name]
  (get-in @!/capabilities [@!/*forecast key-name]))

(defn current-layer []
  (get @!/param-layers @!/*layer-idx))

(defn get-current-layer-name []
  (:layer (current-layer) ""))

(defn get-current-layer-hour []
  (:hour (current-layer) 0))

(defn get-current-layer-full-time []
  (if-let [sim-time (:sim-time (current-layer))]
    (u-time/time-zone-iso-date sim-time @!/show-utc?)
    ""))

(defn get-current-layer-extent []
  (:extent (current-layer) c/california-extent))

(defn get-current-layer-group []
  (:layer-group (current-layer) ""))

(defn get-current-layer-key [key-name]
  (->> (get-forecast-opt :params)
       (map (fn [[key {:keys [options]}]]
              (get-in options [(get-in @!/*params [@!/*forecast key]) key-name])))
       (remove nil?)
       (first)))

(defn get-current-option-key
  "Retreive the value for a particular parameter's option."
  [param-key option-key key-name]
  (get-in (get-forecast-opt :params) [param-key :options option-key key-name]))

(defn get-options-key [key-name]
  (some #(get % key-name)
        (vals (get-forecast-opt :params))))

;; TODO, can we make this the default everywhere?
(defn get-any-level-key
  "Gets the first non-nil value of a given key starting from the bottom level in
   a forecast in `config.cljs` and going to the top. Allows for bottom level keys
   to override a default top level key (such as for `:time-slider?`)."
  [key-name]
  (first (filter some?
                 [(get-current-layer-key key-name)
                  (get-options-key       key-name)
                  (get-forecast-opt      key-name)])))

(defn get-psps-layer-style
  "Returns the name of the CSS style for a PSPS layer."
  []
  (when (= @!/*forecast :psps-zonal)
      (str "poly-"
           (name (get-in @!/*params [:psps-zonal :model]))
           "-"
           (name (get-in @!/*params [:psps-zonal :quantity]))
           "-"
           (name (get-in @!/*params [:psps-zonal :statistic]))
           "-css")))

(defn get-psps-column-name
  "Returns the name of the point info column for a PSPS layer."
  []
  (when (= @!/*forecast :psps-zonal)
      (str (name (get-in @!/*params [:psps-zonal :model]))
           "_"
           (name (get-in @!/*params [:psps-zonal :quantity]))
           "_"
           (name (get-in @!/*params [:psps-zonal :statistic])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data Processing Functions - bye
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn process-model-times!
  "Updates the necessary atoms based on the given model-times. This updates the
   :model-init values for each tab in config.cljs that are initially set to 'Loading...'"
  [model-times]
  (let [processed-times (into (u-misc/reverse-sorted-map)
                              (map (fn [utc-time]
                                     [(keyword utc-time)
                                      {:opt-label (u-time/time-zone-iso-date utc-time @!/show-utc?)
                                       :utc-time  utc-time ; TODO is utc-time redundant?
                                       :filter    utc-time}])
                                   model-times))]
    (reset! !/processed-params
            (assoc-in (get-forecast-opt :params)
                      [:model-init :options]
                      processed-times))
    (swap! !/*params assoc-in [@!/*forecast :model-init] (ffirst processed-times))))

(defn get-layers! [get-model-times?]
  (go
    (let [params       (dissoc (get @!/*params @!/*forecast) (when get-model-times? :model-init))
          selected-set (or (some (fn [[key {:keys [options]}]]
                                   (get-in options [(params key) :filter-set]))
                                 @!/processed-params)
                           (into #{(get-forecast-opt :filter)}
                                 (->> @!/processed-params
                                      (map (fn [[key {:keys [options]}]]
                                             (get-in options [(params key) :filter])))
                                      (remove nil?))))
          {:keys [layers model-times]} (t/read (t/reader :json)
                                               (:body (<! (u-async/call-clj-async! "get-layers"
                                                                             (get-any-level-key :geoserver-key)
                                                                             (pr-str selected-set)))))]
      (when model-times (process-model-times! model-times))
      (reset! !/param-layers layers)
      (swap! !/*layer-idx #(max 0 (min % (- (count @!/param-layers) 1))))
      (when-not (seq @!/param-layers)
        (toast-message! "There are no layers available for the selected parameters. Please try another combination.")))))

(defn create-share-link
  "Generates a link with forecast and parameters encoded in a URL"
  []
  (let [center          (mb/get-center)
        zoom            (get (mb/get-zoom-info) 0)
        selected-params (get @!/*params @!/*forecast)
        page-params     (merge selected-params
                               center
                               {:forecast  @!/*forecast
                                :layer-idx @!/*layer-idx
                                :zoom      zoom})]
    (->> page-params
         (map (fn [[k v]] (cond
                            (keyword? v)
                            (str (name k) "=" (name v))

                            (or (string? v) (number? v))
                            (str (name k) "=" v))))
         (str/join "&")
         (str js/location.origin js/location.pathname "?"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Legend Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- process-vector-layer-legend
  "Parses the JSON data from GetLegendGraphic from a vector layer."
  [rules polygon-layer?]
  (as-> rules %
    (js->clj %)
    (map (fn [rule]
           (let [label (get rule "title")]
             {"label"    label
              "quantity" (if (= label "nodata")
                           "-9999" ; FIXME: if we end up having different nodata values later on, we will need to do regex on the "filter" key from the returned JSON
                           label)
              "color"    (if polygon-layer?
                           (get-in rule ["symbolizers" 0 "Polygon" "fill"])
                           (get-in rule ["symbolizers" 0 "Line" "stroke"]))
              "opacity"  "1.0"}))
         %)))

(defn- process-raster-colormap-legend
  "Parses the JSON data from GetLegendGraphic from a layer using raster colormap styling."
  [rules]
  (as-> rules %
      (try-js-aget % 0 "symbolizers" 0 "Raster" "colormap" "entries")
      (js->clj %)))

(defn- process-legend!
  "Populates the legend-list atom with the result of the request from GetLegendGraphic.
   Also populates the no-data-quantities atom with all quantities associated
   with `nodata` points."
  [json-res]
  (let [rules (try-js-aget json-res "Legend" 0 "rules")]
    (!/set-state-legend-list! (as-> rules %
                                (cond
                                  (try-js-aget % 0 "symbolizers" 0 "Raster")
                                  (process-raster-colormap-legend %)

                                  (try-js-aget % 0 "symbolizers" 0 "Polygon")
                                  (process-vector-layer-legend % true)

                                  (try-js-aget % 0 "symbolizers" 0 "Line")
                                  (process-vector-layer-legend % false)

                                  :else (process-raster-colormap-legend %))
                                (remove (fn [leg] (nil? (get leg "label"))) %)
                                (doall %)))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn- get-legend!
  "Makes a call to GetLegendGraphic and passes the resulting JSON to process-legend!"
  [layer]
  (when (u-misc/has-data? layer)
    (u-async/get-data #(u-async/wrap-wms-errors "legend" % process-legend!)
              (c/legend-url layer
                            (get-any-level-key :geoserver-key)
                            (get-psps-layer-style)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; More Data Processing Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn select-layer! [new-layer]
  (reset! !/*layer-idx new-layer)
  (mb/swap-active-layer! (get-current-layer-name)
                         (get-any-level-key :geoserver-key)
                         (/ @!/active-opacity 100)
                         (get-psps-layer-style)))

(defn select-layer-by-hour! [hour]
  (select-layer! (first (keep-indexed (fn [idx layer]
                                        (when (= hour (:hour layer)) idx))
                                      @!/param-layers))))

(defn clear-info! []
  (mb/clear-point!)
  (reset! !/last-clicked-info [])
  (when (get-forecast-opt :block-info?)
    (reset! !/show-info? false)))

(declare select-param!) ;; FIXME: Look at ways to decouple callbacks

(defn- forecast-exists? [fire-name]
  (contains? (get-in @!/capabilities [:active-fire :params :fire-name :options])
             (keyword fire-name)))

(defn- init-fire-popup! [feature _]
  (let [properties (-> feature (aget "properties") (js->clj))
        lnglat     (-> properties (select-keys ["longitude" "latitude"]) (vals))
        {:strs [name prettyname containper acres]} properties
        body       (fire-popup prettyname
                               containper
                               acres
                               #(select-param! (keyword name) :fire-name)
                               (forecast-exists? name))]
    (mb/init-popup! "fire" lnglat body {:width "200px"})
    (mb/set-center! lnglat 0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Point Information Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- process-timeline-point-info!
  "Resets the !/last-clicked-info atom according the the JSON resulting from a
   call to GetFeatureInfo. Note that layers with multiple columns per layer
   have multiple values that you can use for point information, thus they need
   to be parsed differently."
  [json-res]
  (let [features (try-js-aget json-res "features")]
    (if (empty? features)
      (reset! !/last-clicked-info [])
      (let [multi-column-info? (some-> features
                                       (try-js-aget 0 "properties")
                                       (js/Object.keys)
                                       (.-length)
                                       (> 1))
            band-extraction-fn (fn [pi-layer]
                                 (if multi-column-info?
                                   (some->> (get-psps-column-name)
                                            (try-js-aget pi-layer "properties"))
                                   (some->> (try-js-aget  pi-layer "properties")
                                            (js/Object.values)
                                            (first))))
            feature-info       (map (fn [pi-layer]
                                      {:band   (band-extraction-fn pi-layer)
                                       :vec-id (some-> pi-layer
                                                       (try-js-aget "id")
                                                       (str/split #"\.")
                                                       (peek))})
                                    features)
            vec-id-counts      (frequencies (map :vec-id feature-info))
            vec-id-max         (key (apply max-key val vec-id-counts))]
        (reset! !/last-clicked-info
                (->> feature-info
                     (filter (fn [pi-layer] (= (:vec-id pi-layer) vec-id-max)))
                     (mapv (fn [{:keys [sim-time hour]} pi-layer]
                             (let [js-time (u-time/js-date-from-string sim-time)]
                                 (assoc pi-layer
                                        :js-time js-time
                                        :date    (u-time/get-date-from-js js-time @!/show-utc?)
                                        :time    (u-time/get-time-from-js js-time @!/show-utc?)
                                        :hour    hour)))
                           @!/param-layers)))))))

(defn- process-single-point-info!
  "Resets the !/last-clicked-info atom according the the JSON resulting from a
   call to GetFeatureInfo for single-point-info layers."
  [json-res]
  (let [features (try-js-aget json-res "features")]
    (if (empty? features)
      (reset! !/last-clicked-info [])
      (reset! !/last-clicked-info
              (u-misc/to-precision 2 (some-> features
                                        (first)
                                        (try-js-aget "properties")
                                        (js/Object.values)
                                        (first)))))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn get-point-info!
  "Called when you use the point information tool and click a point on the map.
   Processes the JSON result from GetFeatureInfo differently depending on whether or not
   the layer has single-point-info or timeline-point-info. This processing is used
   to reset! the !/last-clicked-info atom for use in rendering the information-tool."
  [point-info]
  (let [layer-name          (get-current-layer-name)
        layer-group         (get-current-layer-group)
        single?             (str/blank? layer-group)
        layer               (if single? layer-name layer-group)
        process-point-info! (if single? process-single-point-info! process-timeline-point-info!)]
    (when-not (u-misc/missing-data? layer point-info)
      (reset! !/point-info-loading? true)
      (u-async/get-data #(u-async/wrap-wms-errors "point information" % process-point-info!)
                (c/point-info-url layer
                                  (str/join "," point-info)
                                  (if single? 1 50000)
                                  (get-any-level-key :geoserver-key)
                                  (when (= @!/*forecast :psps-zonal)
                                    c/all-psps-columns))
                !/point-info-loading?))))

(defn- change-type!
  "Changes the type of data that is being shown on the map."
  [get-model-times? clear? zoom? max-zoom]
  (go
    (<! (get-layers! get-model-times?))
    (let [source   (get-current-layer-name)
          style-fn (get-current-layer-key :style-fn)]
      (mb/reset-active-layer! source
                              style-fn
                              (get-any-level-key :geoserver-key)
                              (/ @!/active-opacity 100)
                              (get-psps-layer-style))
      (mb/clear-popup!)
      ; When we have a style-fn (which indicates a WFS layer) add the feature highlight.
      ; For now, the only dropdown layer that is WFS is the *Active Fires layer.
      (when (some? style-fn)
        (mb/add-feature-highlight! "fire-active" "fire-active" :click-fn init-fire-popup!))
      (get-legend! source))
    (if clear?
      (clear-info!)
      (get-point-info! (mb/get-overlay-bbox)))
    (when (or zoom? (= @!/*forecast :active-fire))
      (mb/zoom-to-extent! (get-current-layer-extent) (current-layer) max-zoom))))

(defn select-param!
  "The function called whenever an input dropdown is changed on the collapsible panel.
   Resets the proper state atoms with the new, selected inputs from the UI."
  [val & keys]
  (swap! !/*params assoc-in (cons @!/*forecast keys) val)
  (!/set-state-legend-list! [])
  (reset! !/last-clicked-info nil)
  (let [main-key (first keys)]
    (when (= main-key :fire-name)
      (reset! !/*layer-idx 0)
      (swap! !/*params assoc-in (cons @!/*forecast [:burn-pct]) :50)
      (reset! !/animate? false))
    (change-type! (not (#{:burn-pct :model-init} main-key)) ;; TODO: Make this a config
                  (get-current-layer-key :clear-point?)
                  (get-current-option-key main-key val :auto-zoom?)
                  (get-any-level-key     :max-zoom))))

(defn select-forecast!
  "The function called whenever you select a new forecast/tab."
  [key]
  (go
    (!/set-state-legend-list! [])
    (reset! !/last-clicked-info nil)
    (reset! !/*forecast key)
    (reset! !/processed-params (get-forecast-opt :params))
    (mb/set-multiple-layers-visibility! #"isochrones" false) ; hide isochrones underlay when switching tabs
    (<! (change-type! true
                      true
                      (get-any-level-key :auto-zoom?)
                      (get-any-level-key :max-zoom)))))
