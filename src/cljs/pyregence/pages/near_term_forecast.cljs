(ns pyregence.pages.near-term-forecast
  (:require [reagent.core :as r]
            [reagent.dom  :as rd]
            [herb.core    :refer [<class]]
            [cognitect.transit :as t]
            [clojure.edn        :as edn]
            [clojure.spec.alpha :as s]
            [clojure.string     :as str]
            [clojure.core.async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [pyregence.state  :as !]
            [pyregence.styles :as $]
            [pyregence.utils  :as u]
            [pyregence.config :as c]
            [pyregence.components.mapbox    :as mb]
            [pyregence.components.popups    :refer [fire-popup]]
            [pyregence.components.common    :refer [tool-tip-wrapper]]
            [pyregence.components.messaging :refer [message-box-modal toast-message!]]
            [pyregence.components.svg-icons :as svg]
            [pyregence.components.map-controls.camera-tool       :refer [camera-tool]]
            [pyregence.components.map-controls.collapsible-panel :refer [collapsible-panel]]
            [pyregence.components.map-controls.information-tool  :refer [information-tool]]
            [pyregence.components.map-controls.legend-box        :refer [legend-box]]
            [pyregence.components.map-controls.match-drop-tool   :refer [match-drop-tool]]
            [pyregence.components.map-controls.mouse-lng-lat     :refer [mouse-lng-lat]]
            [pyregence.components.map-controls.scale-bar         :refer [scale-bar]]
            [pyregence.components.map-controls.time-slider       :refer [time-slider]]
            [pyregence.components.map-controls.tool-bar          :refer [tool-bar]]
            [pyregence.components.map-controls.zoom-bar          :refer [zoom-bar]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::clear-point?  boolean?)
(s/def ::filter        string?)
(s/def ::filter-set    set?)
(s/def ::geoserver-key keyword?)
(s/def ::opt-label     string?)
(s/def ::units         string?)
(s/def ::z-index       int?)
(s/def ::layer-config  (s/keys :req-un [::opt-label (or ::filter ::filter-set)]
                               :opt-un [::clear-point? ::geoserver-key ::units ::z-index]))
(s/def ::layer-path    (s/and (s/coll-of keyword? :kind vector? :min-count 2)
                              (s/cat :forecast #{:fuels :fire-weather :fire-risk :active-fire :psps-zonal}
                                     :second   #(or (= % :params)
                                                    (= % :underlays))
                                     :etc      (s/+ keyword?))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-forecast-opt [key-name]
  (get-in @!/capabilities [@!/*forecast key-name]))

(defn current-layer []
  (get @!/param-layers @!/*layer-idx))

(defn get-current-layer-name []
  (:layer (current-layer) ""))

(defn get-current-layer-hour []
  (:hour (current-layer) 0))

(defn get-current-layer-full-time []
  (if-let [sim-time (:sim-time (current-layer))]
    (u/time-zone-iso-date sim-time @!/show-utc?)
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

(defn- get-options-key [key-name]
  (some #(get % key-name)
        (vals (get-forecast-opt :params))))

;; TODO, can we make this the default everywhere?
(defn- get-any-level-key
  "Gets the first non-nil value of a given key starting from the bottom level in
   a forecast in `config.cljs` and going to the top. Allows for bottom level keys
   to override a default top level key (such as for `:time-slider?`)."
  [key-name]
  (first (filter some?
                 [(get-current-layer-key key-name)
                  (get-options-key       key-name)
                  (get-forecast-opt      key-name)])))

(defn- get-psps-layer-style
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

(defn- get-psps-column-name
  "Returns the name of the point info column for a PSPS layer."
  []
  (when (= @!/*forecast :psps-zonal)
      (str (name (get-in @!/*params [:psps-zonal :model]))
           "_"
           (name (get-in @!/*params [:psps-zonal :quantity]))
           "_"
           (name (get-in @!/*params [:psps-zonal :statistic])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data Processing Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- process-model-times!
  "Updates the necessary atoms based on the given model-times. This updates the
   :model-init values for each tab in config.cljs that are initially set to 'Loading...'"
  [model-times]
  (let [processed-times (into (u/reverse-sorted-map)
                              (map (fn [utc-time]
                                     [(keyword utc-time)
                                      {:opt-label (u/time-zone-iso-date utc-time @!/show-utc?)
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
                                               (:body (<! (u/call-clj-async! "get-layers"
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

(defn- get-data
  "Asynchronously fetches the JSON or XML resource at url. Returns a
   channel containing the result of calling process-fn on the response
   or nil if an error occurred. Takes in a loading-atom which should be true when
   passed in. The loading-atom is set to false after the resource has been fetched."
  [process-fn url & [loading-atom]]
  (go
   (<! (u/fetch-and-process url
                            {:method "get"
                             :headers {"Accept" "application/json, text/xml"
                                       "Content-Type" "application/json"}}
                            process-fn))
   (when loading-atom
     (reset! loading-atom false))))

(defn wrap-wms-errors [type response success-fn]
  (go
    (let [json-res (<p! (.json response))]
      (if-let [exceptions (u/try-js-aget json-res "exceptions")]
        (do
          (println exceptions)
          (toast-message! (str "Error retrieving " type ". See console for more details.")))
        (success-fn json-res)))))

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
      (u/try-js-aget % 0 "symbolizers" 0 "Raster" "colormap" "entries")
      (js->clj %)))

(defn- process-legend!
  "Populates the legend-list atom with the result of the request from GetLegendGraphic.
   Also populates the no-data-quantities atom with all quantities associated
   with `nodata` points."
  [json-res]
  (let [rules (u/try-js-aget json-res "Legend" 0 "rules")]
    (!/set-state-legend-list! (as-> rules %
                                (cond
                                  (u/try-js-aget % 0 "symbolizers" 0 "Raster")
                                  (process-raster-colormap-legend %)

                                  (u/try-js-aget % 0 "symbolizers" 0 "Polygon")
                                  (process-vector-layer-legend % true)

                                  (u/try-js-aget % 0 "symbolizers" 0 "Line")
                                  (process-vector-layer-legend % false)

                                  :else (process-raster-colormap-legend %))
                                (remove (fn [leg] (nil? (get leg "label"))) %)
                                (doall %)))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn- get-legend!
  "Makes a call to GetLegendGraphic and passes the resulting JSON to process-legend!"
  [layer]
  (when (u/has-data? layer)
    (get-data #(wrap-wms-errors "legend" % process-legend!)
              (c/legend-url layer
                            (get-any-level-key :geoserver-key)
                            (get-psps-layer-style)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Point Information Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- process-timeline-point-info!
  "Resets the !/last-clicked-info atom according the the JSON resulting from a
   call to GetFeatureInfo. Note that layers with multiple columns per layer
   have multiple values that you can use for point information, thus they need
   to be parsed differently."
  [json-res]
  (let [features (u/try-js-aget json-res "features")]
    (if (empty? features)
      (reset! !/last-clicked-info [])
      (let [multi-column-info? (some-> features
                                       (u/try-js-aget 0 "properties")
                                       (js/Object.keys)
                                       (.-length)
                                       (> 1))
            band-extraction-fn (fn [pi-layer]
                                 (if multi-column-info?
                                   (some->> (get-psps-column-name)
                                            (u/try-js-aget pi-layer "properties"))
                                   (some->> (u/try-js-aget  pi-layer "properties")
                                            (js/Object.values)
                                            (first))))
            feature-info       (map (fn [pi-layer]
                                      {:band   (band-extraction-fn pi-layer)
                                       :vec-id (some-> pi-layer
                                                       (u/try-js-aget "id")
                                                       (str/split #"\.")
                                                       (peek))})
                                    features)
            vec-id-counts      (frequencies (map :vec-id feature-info))
            vec-id-max         (key (apply max-key val vec-id-counts))]
        (reset! !/last-clicked-info
                (->> feature-info
                     (filter (fn [pi-layer] (= (:vec-id pi-layer) vec-id-max)))
                     (mapv (fn [{:keys [sim-time hour]} pi-layer]
                             (let [js-time (u/js-date-from-string sim-time)]
                                 (assoc pi-layer
                                        :js-time js-time
                                        :date    (u/get-date-from-js js-time @!/show-utc?)
                                        :time    (u/get-time-from-js js-time @!/show-utc?)
                                        :hour    hour)))
                           @!/param-layers)))))))

(defn- process-single-point-info!
  "Resets the !/last-clicked-info atom according the the JSON resulting from a
   call to GetFeatureInfo for single-point-info layers."
  [json-res]
  (let [features (u/try-js-aget json-res "features")]
    (if (empty? features)
      (reset! !/last-clicked-info [])
      (reset! !/last-clicked-info
              (u/to-precision 2 (some-> features
                                        (first)
                                        (u/try-js-aget "properties")
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
    (when-not (u/missing-data? layer point-info)
      (reset! !/point-info-loading? true)
      (get-data #(wrap-wms-errors "point information" % process-point-info!)
                (c/point-info-url layer
                                  (str/join "," point-info)
                                  (if single? 1 50000)
                                  (get-any-level-key :geoserver-key)
                                  (when (= @!/*forecast :psps-zonal)
                                    c/all-psps-columns))
                !/point-info-loading?))))

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
      (when style-fn
        (mb/add-feature-highlight! "fire-active" "fire-active" :click-fn init-fire-popup!))
      (get-legend! source))
    (if clear?
      (clear-info!)
      (get-point-info! (mb/get-overlay-bbox)))
    (when (or zoom? (= @!/*forecast :active-fire))
      (mb/zoom-to-extent! (get-current-layer-extent) (current-layer) max-zoom))))

(defn- select-param!
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

(defn- select-forecast!
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

(defn set-show-info! [show?]
  (if (and show? (get-forecast-opt :block-info?))
    (toast-message! "There is currently no point information available for this layer.")
    (do (reset! !/show-info? show?)
        (clear-info!))))

(defn select-time-zone! [utc?]
  (reset! !/show-utc? utc?)
  (swap! !/last-clicked-info #(mapv (fn [{:keys [js-time] :as layer}]
                                      (assoc layer
                                             :date (u/get-date-from-js js-time @!/show-utc?)
                                             :time (u/get-time-from-js js-time @!/show-utc?)))
                                    @!/last-clicked-info))
  (swap! !/processed-params  #(update-in %
                                       [:model-init :options]
                                       (fn [options]
                                         (u/mapm (fn [[k {:keys [utc-time] :as v}]]
                                                   [k (assoc v
                                                             :opt-label
                                                             (u/time-zone-iso-date utc-time @!/show-utc?))])
                                                 options)))))

(defn- params->selected-options
  "Parses url query parameters into the selected options"
  [options-config forecast params]
  {forecast (as-> options-config oc
              (get-in oc [forecast :params])
              (keys oc)
              (select-keys params oc)
              (u/mapm (fn [[k v]] [k (keyword v)]) oc))})

;;; Capabilities
(defn process-capabilities! [fire-names user-layers options-config]
  (reset! !/capabilities
          (-> (reduce (fn [acc {:keys [layer_path layer_config]}]
                        (let [layer-path   (edn/read-string layer_path)
                              layer-config (edn/read-string layer_config)]
                          (if (and (s/valid? ::layer-path   layer-path)
                                   (s/valid? ::layer-config layer-config))
                            (assoc-in acc layer-path layer-config)
                            acc)))
                      options-config
                      user-layers)
              (update-in [:active-fire :params :fire-name :options]
                         merge
                         fire-names)))
  (reset! !/*params (u/mapm
                     (fn [[forecast _]]
                       (let [params           (get-in @!/capabilities [forecast :params])
                             selected-options (params->selected-options options-config @!/*forecast params)]
                         [forecast (merge (u/mapm (fn [[k v]]
                                                    [k (or (get-in selected-options [forecast k])
                                                           (:default-option v)
                                                           (ffirst (:options v)))])
                                                  params))]))
                     options-config)))

(defn refresh-fire-names! [user-id]
  (go
    (as-> (u/call-clj-async! "get-fire-names" user-id) fire-names
      (<! fire-names)
      (:body fire-names)
      (edn/read-string fire-names)
      (swap! !/capabilities update-in [:active-fire :params :fire-name :options] merge fire-names))))

(defn- initialize! [{:keys [user-id forecast-type forecast layer-idx lat lng zoom] :as params}]
  (go
    (let [{:keys [options-config layers]} (c/get-forecast forecast-type)
          user-layers-chan                (u/call-clj-async! "get-user-layers" user-id)
          fire-names-chan                 (u/call-clj-async! "get-fire-names" user-id)
          fire-cameras                    (u/call-clj-async! "get-cameras")]
      (reset! !/*forecast-type forecast-type)
      (reset! !/*forecast (or (keyword forecast)
                              (keyword (forecast-type @!/default-forecasts))))
      (reset! !/*layer-idx (if layer-idx (js/parseInt layer-idx) 0))
      (mb/init-map! "map" layers (if (every? nil? [lng lat zoom]) {} {:center [lng lat] :zoom zoom}))
      (process-capabilities! (edn/read-string (:body (<! fire-names-chan)))
                             (edn/read-string (:body (<! user-layers-chan)))
                             options-config)
      (<! (select-forecast! @!/*forecast))
      (reset! !/user-org-list (edn/read-string (:body (<! (u/call-clj-async! "get-org-list" user-id)))))
      (reset! !/the-cameras (edn/read-string (:body (<! fire-cameras))))
      (reset! !/loading? false))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $app-header []
  {:align-items     "center"
   :display         "flex"
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

(defn $message-modal [loading-message?]
  {:background-color "white"
   :border-radius    "3px"
   :display          "flex"
   :flex-direction   "column"
   :margin           (cond
                       (and @!/mobile? loading-message?) "10rem 4rem .5rem 4rem"
                       @!/mobile?                ".25rem"
                       :else                  "8rem auto")
   :overflow         "hidden"
   :max-height       (if @!/mobile? "calc(100% - .5rem)" "50%")
   :width            (if @!/mobile? "unset" "25rem")})

(defn $p-mb-cursor []
  (with-meta
    {}
    {:combinators {[:descendant :.mapboxgl-canvas-container] {:cursor "inherit"}}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn control-layer [user-id]
  (let [my-box (r/atom #js {})]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (let [this-node      (rd/dom-node this)
              update-my-box! (fn [& _] (reset! my-box (.getBoundingClientRect this-node)))]
          (update-my-box!)
          (if (nil? (.-ResizeObserver js/window)) ;; Handle older mobile browsers
            (.addEventListener js/document "resize" update-my-box!)
            (-> (js/ResizeObserver. update-my-box!)
                (.observe this-node)))))

      :render
      (fn []
        [:div {:style ($control-layer)}
         [collapsible-panel
          (get @!/*params @!/*forecast)
          select-param!
          (get-any-level-key :underlays)]
         (when (aget @my-box "height")
           [:<>
            (when @!/show-info?
              [information-tool
               get-point-info!
               @my-box
               select-layer-by-hour!
               (get-current-layer-key :units)
               (get-current-layer-key :convert)
               (or (get-current-layer-key :no-convert) #{})
               (get-current-layer-hour)
               #(set-show-info! false)])
            (when @!/show-match-drop?
              [match-drop-tool @my-box #(reset! !/show-match-drop? false) refresh-fire-names! user-id])
            (when @!/show-camera?
              [camera-tool @my-box #(reset! !/show-camera? false)])])
         [legend-box
          (get-any-level-key :reverse-legend?)
          (get-any-level-key :time-slider?)
          (get-current-layer-key :units)]
         [tool-bar
          set-show-info!
          get-any-level-key
          user-id]
         [scale-bar (get-any-level-key :time-slider?)]
         (when-not @!/mobile? [mouse-lng-lat])
         [zoom-bar
          (get-current-layer-extent)
          (current-layer)
          create-share-link
          (get-any-level-key :time-slider?)]
         (when (get-any-level-key :time-slider?)
           [time-slider
            (get-current-layer-full-time)
            select-layer!
            select-time-zone!])])})))

(defn pop-up []
  [:div#pin {:style ($/fixed-size "2rem")}
   [svg/pin]])

(defn map-layer []
  (r/with-let [mouse-down? (r/atom false)
               cursor-fn   #(cond
                              @mouse-down?                           "grabbing"
                              (or @!/show-info? @!/show-match-drop?) "crosshair" ; TODO get custom cursor image from Ryan
                              :else                                  "grab")]
    [:div#map {:class (<class $p-mb-cursor)
               :style {:height "100%" :position "absolute" :width "100%" :cursor (cursor-fn)}
               :on-mouse-down #(reset! mouse-down? true)
               :on-mouse-up   #(reset! mouse-down? false)}]))

(defn message-modal []
  (r/with-let [show-me? (r/atom (not @!/dev-mode?))]
    (when @show-me?
      [:div#message-modal {:style ($/modal)}
       [:div {:style ($message-modal false)}
        [:div {:style {:background ($/color-picker :yellow)
                       :width      "100%"}}
         [:label {:style {:padding ".5rem 0 0 .5rem" :font-size "1.5rem"}}
          "Disclaimer"]]
        [:div {:style {:padding ".5rem" :overflow "auto"}}
         [:label {:style {:margin-bottom ".5rem"}}
          "This site is currently a work in progress and is in a Beta testing phase.
           It provides access to an experimental fire spread forecast tool. Use at your own risk."]
         [:label
          "Your use of this web site is undertaken at your sole risk.
           This site is available on an “as is” and “as available” basis without warranty of any kind.
           We do not warrant that this site will (i) be uninterrupted or error-free; or (ii) result in any desired outcome.
           We are not responsible for the availability or content of other services, data or public information
           that may be used by or linked to this site. To the fullest extent permitted by law, the Pyregence Consortium,
           and each and every individual, entity and collaborator therein, hereby disclaims (for itself, its affiliates,
           subcontractors, and licensors) all representations and warranties, whether express or implied, oral or written,
           with respect to this site, including without limitation, all implied warranties of title, non-infringement,
           quiet enjoyment, accuracy, integration, merchantability or fitness for any particular purpose,
           and all warranties arising from any course of dealing, course of performance, or usage of trade."]
         [:label {:style {:margin "1rem .25rem 0 0"}}
          "Please see our "
          [:a {:style {:margin-right ".25rem"}
               :href "/terms-of-use"
               :target "_blank"} "Terms of Use"]
          "and"
          [:a {:style {:margin-left ".25rem"}
               :href "/privacy-policy"
               :target "_blank"} "Privacy Policy"]
          "."]]
        [:div {:style ($/combine $/flex-row {:justify-content "center"})}
         [:span
          [:label {:class (<class $/p-form-button)
                   :style {:padding-left  "1.75rem"
                           :padding-right "1.75rem"}
                   :on-click #(u/jump-to-url! "https://pyregence.org/")}
           "Decline"]
          [:label {:class (<class $/p-form-button)
                   :style {:margin        ".5rem"
                           :padding-left  "1.75rem"
                           :padding-right "1.75rem"}
                   :on-click #(reset! show-me? false)}
           "Accept"]]]]])))

(defn loading-modal []
  [:div#message-modal {:style ($/modal)}
   [:div {:style ($message-modal true)}
    [:h3 {:style {:margin-bottom "0"
                  :padding       "1rem"
                  :text-align    "center"}}
     "Loading..."]]])

(defn root-component [{:keys [user-id] :as params}]
  (let [height (r/atom "100%")]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (let [update-fn (fn [& _]
                          (-> js/window (.scrollTo 0 0))
                          (reset! !/mobile? (> 800.0 (.-innerWidth js/window)))
                          (reset! height  (str (- (.-innerHeight js/window)
                                                  (-> js/document
                                                      (.getElementById "header")
                                                      .getBoundingClientRect
                                                      (aget "height")))
                                               "px"))
                          (js/setTimeout mb/resize-map! 50))]
          (-> js/window (.addEventListener "touchend" update-fn))
          (-> js/window (.addEventListener "resize"   update-fn))
          (initialize! params)
          (update-fn)))

      :reagent-render
      (fn [_]
        [:div#near-term-forecast
         {:style ($/combine $/root {:height @height :padding 0 :position "relative"})}
         [message-box-modal]
         (when @!/loading? [loading-modal])
         [message-modal]
         [:div {:style ($/combine $app-header {:background ($/color-picker :yellow)})}
          [:span {:style {:display "flex" :padding ".25rem 0"}}
           (doall (map (fn [[key {:keys [opt-label hover-text allowed-org]}]]
                         (when (or (nil? allowed-org)
                                   (some (fn [{org-name :opt-label}]
                                           (= org-name allowed-org))
                                         @!/user-org-list))
                           ^{:key key}
                           [tool-tip-wrapper
                            hover-text
                            :top
                            [:label {:style    ($forecast-label (= @!/*forecast key))
                                     :on-click #(select-forecast! key)}
                             opt-label]]))
                       @!/capabilities))]
          (when-not @!/mobile?
            (if user-id
              [:div {:style {:display "flex" :position "absolute" :right "1rem"}}
               (when (> (count @!/user-org-list) 0)
                 [tool-tip-wrapper
                  "Visit the admin page"
                  :top
                  [:a {:style      ($/combine ($/fixed-size "1.5rem")
                                              {:cursor "pointer" :margin-right "1rem"})
                       :aria-label "Visit the admin page"
                       :href       "/admin"}
                   [svg/admin-user]]])
               [:label {:style {:margin ".16rem 1rem 0 0" :cursor "pointer"}
                        :on-click (fn []
                                    (go (<! (u/call-clj-async! "log-out"))
                                        (-> js/window .-location .reload)))}
                "Log Out"]]
              [:span {:style {:position "absolute" :right "3rem" :display "flex"}}
               ;; Remove for the time being
               ;; [:label {:style {:margin-right "1rem" :cursor "pointer"}
               ;;          :on-click #(u/jump-to-url! "/register")} "Register"]
               [:label {:style {:cursor "pointer"}
                        :on-click #(u/jump-to-url! "/login")} "Log In"]]))]
         [:div {:style {:height "100%" :position "relative" :width "100%"}}
          (when (and @mb/the-map
                     (not-empty @!/capabilities)
                     (not-empty @!/*params))
            [control-layer user-id])
          [map-layer]
          [pop-up]]])})))
