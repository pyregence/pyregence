(ns pyregence.pages.near-term-forecast
  (:require
   [cljs.core.async.interop                             :refer-macros [<p!]]
   [clojure.core.async                                  :refer [<! go]]
   [clojure.edn                                         :as edn]
   [clojure.spec.alpha                                  :as s]
   [clojure.string                                      :as str]
   [cognitect.transit                                   :as t]
   [herb.core                                           :refer [<class]]
   [pyregence.components.map-controls.camera-tool       :refer [camera-tool]]
   [pyregence.components.map-controls.collapsible-panel :refer [collapsible-panel]]
   [pyregence.components.map-controls.information-tool  :refer [information-tool]]
   [pyregence.components.map-controls.legend-box        :refer [legend-box]]
   [pyregence.components.map-controls.match-drop-tool   :refer [match-drop-tool]]
   [pyregence.components.map-controls.measure-tool      :refer [measure-tool]]
   [pyregence.components.map-controls.mouse-lng-lat     :refer [mouse-lng-lat]]
   [pyregence.components.map-controls.scale-bar         :refer [scale-bar]]
   [pyregence.components.map-controls.time-slider       :refer [time-slider]]
   [pyregence.components.map-controls.tool-bar          :refer [tool-bar]]
   [pyregence.components.map-controls.zoom-bar          :refer [zoom-bar]]
   [pyregence.components.mapbox                         :as mb]
   [pyregence.components.messaging                      :refer [message-box-modal
                                                                toast-message!]]
   [pyregence.components.nav-bar                        :refer [nav-bar]]
   [pyregence.components.popups                         :refer [fire-popup]]
   [pyregence.components.svg-icons                      :as svg]
   [pyregence.config                                    :as c]
   [pyregence.state                                     :as !]
   [pyregence.styles                                    :as $]
   [pyregence.utils.async-utils                         :as u-async]
   [pyregence.utils.browser-utils                       :as u-browser]
   [pyregence.utils.data-utils                          :as u-data]
   [pyregence.utils.misc-utils                          :as u-misc]
   [pyregence.utils.number-utils                        :as u-num]
   [pyregence.utils.time-utils                          :as u-time]
   [react                                               :as react]
   [reagent.core                                        :as r]))

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

(defn- current-layer []
  (if (= (count @!/param-layers) 1)
    (first @!/param-layers)
    (get @!/param-layers @!/*layer-idx)))

(defn- get-current-layer-name []
  (:layer (current-layer) ""))

(defn- get-current-layer-time []
  (when (:times (current-layer))
    (nth (:times (current-layer)) @!/*layer-idx)))

(defn- get-current-layer-hour []
  (if-let [current-layer-time (get-current-layer-time)]
    (second (re-find #"[0-9]{4}-[0-9]{2}-[0-9]{2}T([0-9]{2})" current-layer-time))
    (:hour (current-layer) 0)))

(defn- get-current-layer-full-time []
  (if-let [sim-time (or (get-current-layer-time)
                        (:sim-time (current-layer)))]
    (u-time/date-string->iso-string sim-time @!/show-utc?)
    ""))

(defn- get-current-layer-extent []
  (let [current-extent (:extent (current-layer))]
    (cond
      (= current-extent ["0.0" "0.0" "-1.0" "-1.0"]) c/california-extent
      (seq current-extent) current-extent
      :else c/california-extent)))

(defn- get-current-layer-group []
  (:layer-group (current-layer) ""))

(defn- get-current-layer-key [key-name]
  (->> (get-forecast-opt :params)
       (map (fn [[key {:keys [options]}]]
              (get-in options [(get-in @!/*params [@!/*forecast key]) key-name])))
       (remove nil?)
       (first)))

(defn- get-current-option-key
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

(defn- utc-time->opt-label
  [utc-time]
  (u-time/date-string->iso-string
   utc-time
   (or (get-in @!/capabilities [@!/*forecast :always-utc?])
       @!/show-utc?)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data Processing Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- process-model-times!
  "Updates the necessary atoms based on the given model-times. This updates the
   :model-init values for each tab in config.cljs that are initially set to 'Loading...'"
  [model-times]
  (let [processed-times (into (u-data/reverse-sorted-map)
                              (map (fn [utc-time]
                                     [(keyword utc-time)
                                      {:opt-label (utc-time->opt-label utc-time)
                                       :utc-time  utc-time ; TODO is utc-time redundant?
                                       :filter    utc-time}])
                                   model-times))]
    (reset! !/processed-params
            (assoc-in (get-forecast-opt :params)
                      [:model-init :options]
                      processed-times))
    (swap! !/*params assoc-in [@!/*forecast :model-init]
           (or (when (processed-times @!/*last-start-time) @!/*last-start-time)
               (ffirst processed-times)))))

(defn- get-layers! [get-model-times?]
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
      (swap! !/*layer-idx #(max 0 (min % (- (count (or (:times (first @!/param-layers)) @!/param-layers)) 1))))
      (cond
        (and
         (= :active-fire @!/*forecast)
         (zero? @!/active-fire-count))
        (toast-message! "There are currently no active fire forecasts in the system.")

        (not (seq @!/param-layers))
        (toast-message! "There are no layers available for the selected parameters. Please try another combination.")))))

(defn- create-share-link
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
  [process-fn url & {:keys [loading-atom basic-auth]}]
  (go
    (let [base-headers    {"Accept"       "application/json, text/xml"
                           "Content-Type" "application/json"}
          request-headers (if basic-auth
                            (assoc base-headers "Authorization" (str "Basic " (js/window.btoa basic-auth)))
                            base-headers)]
      (<! (u-async/fetch-and-process url
                                     {:method "get"
                                      :headers request-headers}
                                     process-fn))
      (when loading-atom
        (reset! loading-atom false)))))

(defn- wrap-wms-errors [type response success-fn]
  (go
    (let [json-res (<p! (.json response))]
      (if-let [exceptions (u-misc/try-js-aget json-res "exceptions")]
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
             (if (= label "nolegend")
               nil ; We don't add any nolegend values to the legend, those values are just there to add extra colors to the actual layer when previewing it on Pyrecast
               {"label"    label
                "quantity" (if (= label "nodata")
                             "-9999" ; FIXME: if we end up having different nodata values later on, we will need to do regex on the "filter" key from the returned JSON
                             label)
                "color"    (if polygon-layer?
                             (get-in rule ["symbolizers" 0 "Polygon" "fill"])
                             (get-in rule ["symbolizers" 0 "Line" "stroke"]))
                "opacity"  "1.0"})))
         %)
    (filter identity %)))

(defn- process-raster-colormap-legend
  "Parses the JSON data from GetLegendGraphic from a layer using raster colormap styling."
  [rules]
  (as-> rules %
    (u-misc/try-js-aget % 0 "symbolizers" 0 "Raster" "colormap" "entries")
    (js->clj %)))

(defn- process-legend!
  "Populates the legend-list atom with the result of the request from GetLegendGraphic.
   Also populates the no-data-quantities atom with all quantities associated
   with `nodata` points."
  [json-res]
  (let [rules (u-misc/try-js-aget json-res "Legend" 0 "rules")]
    (!/set-state-legend-list! (as-> rules %
                                (cond
                                  (u-misc/try-js-aget % 0 "symbolizers" 0 "Raster")
                                  (process-raster-colormap-legend %)

                                  (u-misc/try-js-aget % 0 "symbolizers" 0 "Polygon")
                                  (process-vector-layer-legend % true)

                                  (u-misc/try-js-aget % 0 "symbolizers" 0 "Line")
                                  (process-vector-layer-legend % false)

                                  :else (process-raster-colormap-legend %))
                                (remove (fn [leg] (nil? (get leg "label"))) %)
                                (doall %)))))

(defn- get-current-layer-geoserver-credentials
  "Returns the GeoServer credentials associated with a specific layer for
   users that are a part of at least one PSPS organization. Determines the credentials
   by finding the utility company associated with the currently selected layer (via the values in the *params atom) and then
   looking up that utility company's credentials using the `!/user-psps-orgs-list` atom.
   Note that this assumes that the key associated with a specific utility company
   is **exactly** the same as the `org-unique-id` set in the organizations DB table.
   The Euro forecast (associated with the `:ecmwf` key on the weather tab) is an edge
   case because each PSPS company has access to it. Returns `nil` if the user does
   not belong to a PSPS organization or the current layer doesn't require GeoServer credentials."
  []
  (when (seq @!/user-psps-orgs-list)
    (when-some [keypath (case @!/*forecast
                          :fuels        :only-underlays
                          :fire-weather [:fire-weather :model]
                          :fire-risk    [:fire-risk :pattern]
                          :active-fire  :only-underlays
                          :psps-zonal   [:psps-zonal :utility]
                          nil)]
      (let [selected-org-id   (if (= keypath :only-underlays)
                                keypath
                                (name (get-in @!/*params keypath)))
            matching-psps-org (cond
                                (= selected-org-id "ecmwf") ; "ecmwf" is the Euro weather forecast which all utility companies have access to
                                (first @!/user-psps-orgs-list)

                                (= selected-org-id :only-underlays) ; The fuels and active fire tabs don't have any utility-specific layers, so only underlays are an option here
                                (first (filter #(and (seq @!/most-recent-optional-layer)
                                                     ((:filter-set @!/most-recent-optional-layer) (:org-unique-id %)))
                                               @!/user-psps-orgs-list))

                                :else
                                (first (filter #(or (= selected-org-id (:org-unique-id %)) ; There should only be one matching entry because each `:org-unique-id` is unique
                                                    (and (seq @!/most-recent-optional-layer) ; We're dealing with an optional layer that's associated with a utility company
                                                         ((:filter-set @!/most-recent-optional-layer) (:org-unique-id %))))
                                               @!/user-psps-orgs-list)))]
        (:geoserver-credentials matching-psps-org)))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn- get-legend!
  "Makes a call to GetLegendGraphic and passes the resulting JSON to process-legend!"
  [layer]
  (when (u-data/has-data? layer)
    (get-data #(wrap-wms-errors "legend" % process-legend!)
              (c/legend-url layer
                            (get-any-level-key :geoserver-key)
                            (get-psps-layer-style))
              :basic-auth (when (= :psps (get-any-level-key :geoserver-key))
                            (get-current-layer-geoserver-credentials)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Point Information Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- process-timeline-point-info!
  "Resets the !/last-clicked-info atom according the the JSON resulting from a
   call to GetFeatureInfo. Note that layers with multiple columns per layer
   have multiple values that you can use for point information, thus they need
   to be parsed differently."
  [json-res]
  (let [features (u-misc/try-js-aget json-res "features")]
    (if (empty? features)
      (reset! !/last-clicked-info [])
      (let [multi-column-info? (some-> features
                                       (u-misc/try-js-aget 0 "properties")
                                       (js/Object.keys)
                                       (.-length)
                                       (> 1))
            band-extraction-fn (fn [pi-layer]
                                 (if multi-column-info?
                                   (some->> (get-psps-column-name)
                                            (u-misc/try-js-aget pi-layer "properties"))
                                   (some->> (u-misc/try-js-aget  pi-layer "properties")
                                            (js/Object.values)
                                            (first))))
            feature-info       (map (fn [pi-layer]
                                      {:band   (band-extraction-fn pi-layer)
                                       :vec-id (some-> pi-layer
                                                       (u-misc/try-js-aget "id")
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
  (let [features (u-misc/try-js-aget json-res "features")]
    (if (empty? features)
      (reset! !/last-clicked-info [])
      (reset! !/last-clicked-info
              (u-num/to-precision 2 (some-> features
                                            (first)
                                            (u-misc/try-js-aget "properties")
                                            (js/Object.values)
                                            (first)))))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn get-point-info!
  "Called when you use the point information tool and click a point on the map.
   Processes the JSON result from GetFeatureInfo differently depending on whether or not
   the layer has single-point-info or timeline-point-info. This processing is used
   to reset! the !/last-clicked-info atom for use in rendering the information-tool.
   Takes in one argument, the bounding box of the currently selected point."
  [point-info-bbox]
  (let [layer-name          (get-current-layer-name)
        layer-group         (get-current-layer-group)
        single?             (str/blank? layer-group)
        layer               (if single? layer-name layer-group)
        process-point-info! (if single? process-single-point-info! process-timeline-point-info!)]
    (when-not (u-data/missing-data? layer point-info-bbox)
      (reset! !/point-info-loading? true)
      (get-data #(wrap-wms-errors "point information" % process-point-info!)
                (c/point-info-url layer
                                  (str/join "," point-info-bbox)
                                  (if single? 1 50000)
                                  (get-any-level-key :geoserver-key)
                                  (when (= @!/*forecast :psps-zonal)
                                    c/all-psps-columns))
                :loading-atom !/point-info-loading?
                :basic-auth   (when (= :psps (get-any-level-key :geoserver-key))
                                (get-current-layer-geoserver-credentials))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; More Data Processing Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- select-layer! [new-layer]
  (reset! !/*layer-idx new-layer)
  (mb/swap-active-layer! (get-current-layer-name)
                         (get-any-level-key :geoserver-key)
                         (/ @!/active-opacity 100)
                         (get-psps-layer-style)
                         (get-current-layer-time)))

(defn- select-layer-by-hour! [hour]
  (select-layer! (first (keep-indexed (fn [idx layer]
                                        (when (= hour (:hour layer)) idx))
                                      @!/param-layers))))

(defn- clear-info! []
  (mb/remove-markers!)
  (reset! !/last-clicked-info [])
  (reset! !/show-measure-tool? false)
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
  [get-model-times? clear? auto-zoom? max-zoom]
  (go
    (<! (get-layers! get-model-times?))
    (let [source   (get-current-layer-name)
          style-fn (get-current-layer-key :style-fn)]
      (<! (mb/reset-active-layer! source
                                  style-fn
                                  (get-any-level-key :geoserver-key)
                                  (/ @!/active-opacity 100)
                                  (get-psps-layer-style)
                                  (get-current-layer-time)))
      (mb/clear-popup!)
      ; When we have a style-fn (which indicates a WFS layer) add the feature highlight.
      ; For now, the only dropdown layer that is WFS is the *Active Fires layer.
      (when style-fn
        (mb/add-feature-highlight! "fire-active" "fire-active" :click-fn init-fire-popup!))
      (get-legend! source))
    (if clear?
      (clear-info!)
      (get-point-info! (mb/get-overlay-bbox)))
    (when auto-zoom?
      (mb/zoom-to-extent! (get-current-layer-extent) (current-layer) max-zoom))))

(defn- select-param!
  "The function called whenever an input dropdown is changed on the collapsible panel.
   Resets the proper state atoms with the new, selected inputs from the UI."
  [val & keys]
  (swap! !/*params assoc-in (cons @!/*forecast keys) val)
  (!/set-state-legend-list! [])
  (reset! !/last-clicked-info nil)
  (let [main-key (first keys)]
    (when (= main-key :model-init)
      (reset! !/*last-start-time val))
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

(defn- set-show-info! [show?]
  (if (and show? (get-forecast-opt :block-info?))
    (toast-message! "There is currently no point information available for this layer.")
    (do (reset! !/show-info? show?)
        (clear-info!))))

(defn- select-time-zone! [utc?]
  (reset! !/show-utc? utc?)
  (swap! !/last-clicked-info #(mapv (fn [{:keys [js-time] :as layer}]
                                      (assoc layer
                                             :date (u-time/get-date-from-js js-time @!/show-utc?)
                                             :time (u-time/get-time-from-js js-time @!/show-utc?)))
                                    @!/last-clicked-info))
  (swap! !/processed-params  #(update-in %
                                         [:model-init :options]
                                         (fn [options]
                                           (u-data/mapm (fn [[k {:keys [utc-time] :as v}]]
                                                          [k (assoc v
                                                                    :opt-label
                                                                    (utc-time->opt-label utc-time))])
                                                        options)))))

(defn- params->selected-options
  "Parses url query parameters into the selected options"
  [options-config forecast params]
  {forecast (as-> options-config oc
              (get-in oc [forecast :params])
              (keys oc)
              (select-keys params oc)
              (u-data/mapm (fn [[k v]] [k (keyword v)]) oc))})

;;; Capabilities
(defn- process-capabilities! [fire-names user-layers options-config psps-orgs-list user-psps-orgs-list & [selected-options]]
  (reset! !/capabilities
          ;; Add in all layers from the organiation_layers DB table
          (-> (reduce (fn [acc {:keys [layer_path layer_config]}]
                        (let [layer-path   (edn/read-string layer_path)
                              layer-config (edn/read-string layer_config)]
                          (if (and (s/valid? ::layer-path   layer-path)
                                   (s/valid? ::layer-config layer-config))
                            (assoc-in acc layer-path layer-config)
                            acc)))
                      options-config
                      user-layers) ; TODO the resulting array map gets turned into a hash map when we have > than 9 items
              ;; Add in available active fire names
              (update-in [:active-fire :params :fire-name :options]
                         merge
                         fire-names)
              ;; Set the default risk tab ignition pattern option to the logged in user's organization (when applicable)
              ;; Note that we default to using the first organization in the case where a user belongs to more than one org
              (assoc-in [:fire-risk :params :pattern :default-option] (keyword (:org-unique-id (first user-psps-orgs-list))))
              ;; Add in the PSPS tab for all organizations that are permitted to see it
              (assoc-in [:psps-zonal :allowed-orgs] (into #{} psps-orgs-list))
              ;; Add in the specific PSPS layer options for the user's organization
              (assoc-in [:psps-zonal :params :utility :options]
                        (reduce (fn [acc {:keys [org-unique-id org-name]}]
                                  (assoc acc
                                         (keyword org-unique-id)
                                         {:opt-label  org-name
                                          :filter     org-unique-id}))
                                {}
                                user-psps-orgs-list))))
  (reset! !/*params (u-data/mapm
                     (fn [[forecast _]]
                       (let [params (get-in @!/capabilities [forecast :params])]
                         [forecast (merge (u-data/mapm (fn [[k v]]
                                                         [k (or (get-in selected-options [forecast k])
                                                                (:default-option v)
                                                                (ffirst (:options v)))])
                                                       params))]))
                     options-config)))

(defn- initialize! [{:keys [user-id forecast-type forecast layer-idx lat lng zoom] :as params}]
  (go
    (reset! !/loading? true)
    (let [{:keys [options-config layers]} (c/get-forecast forecast-type)
          user-layers-chan                (u-async/call-clj-async! "get-user-layers" user-id)
          fire-names-chan                 (u-async/call-clj-async! "get-fire-names" user-id)
          fire-cameras-chan               (u-async/call-clj-async! "get-cameras")
          user-orgs-list-chan             (u-async/call-clj-async! "get-organizations" user-id)
          psps-orgs-list-chan             (u-async/call-clj-async! "get-psps-organizations")
          fire-names                      (edn/read-string (:body (<! fire-names-chan)))
          active-fire-count               (count fire-names)]
      (reset! !/active-fire-count active-fire-count)
      (reset! !/user-orgs-list (edn/read-string (:body (<! user-orgs-list-chan))))
      (reset! !/psps-orgs-list (edn/read-string (:body (<! psps-orgs-list-chan))))
      (reset! !/user-psps-orgs-list (filter (fn [org] (some #(= (:org-unique-id org) %) @!/psps-orgs-list))
                                            @!/user-orgs-list))
      (reset! !/*forecast-type forecast-type)

      (reset! !/*forecast
              (cond
                (= :long-term forecast-type)
                (or (keyword forecast)
                    (keyword (forecast-type @!/default-forecasts)))

                ;; other wise it's near term
                (zero? active-fire-count)
                :fire-weather

                :else
                :active-fire))
      (reset! !/*layer-idx (if layer-idx (js/parseInt layer-idx) 0))
      (mb/init-map! "map"
                    layers
                    get-current-layer-geoserver-credentials
                    #(select-forecast! @!/*forecast)
                    (if (every? nil? [lng lat zoom]) {} {:center [lng lat] :zoom zoom}))
      (process-capabilities! fire-names
                             (edn/read-string (:body (<! user-layers-chan)))
                             options-config
                             @!/psps-orgs-list
                             @!/user-psps-orgs-list
                             (params->selected-options options-config @!/*forecast params))
      (reset! !/the-cameras (edn/read-string (:body (<! fire-cameras-chan))))
      (when (and (not-empty @!/capabilities)
                 (not-empty @!/*params))
        (reset! !/loading? false)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $control-layer []
  {:height   "100%"
   :position "absolute"
   :width    "100%"})

(defn- $message-modal [loading-message?]
  {:background-color "white"
   :border-radius    "3px"
   :display          "flex"
   :flex-direction   "column"
   :margin           (cond
                       (and @!/mobile? loading-message?) "10rem 4rem .5rem 4rem"
                       @!/mobile?                        ".25rem"
                       :else                             "8rem auto")
   :max-height       (if @!/mobile? "calc(100% - .5rem)" "50%")
   :overflow         "hidden"
   :width            (if @!/mobile? "unset" "25rem")})

(defn- $p-mb-cursor []
  (with-meta
    {}
    {:combinators {[:descendant :.mapboxgl-canvas-container] {:cursor "inherit"}}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- control-layer [user-id]
  (let [my-box (r/atom #js {})
        ref    (react/createRef)]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (let [this-node      (.-current ref)
              update-my-box! (fn [& _] (reset! my-box (.getBoundingClientRect this-node)))]
          (update-my-box!)
          (if (nil? (.-ResizeObserver js/window)) ;; Handle older mobile browsers
            (.addEventListener js/document "resize" update-my-box!)
            (-> (js/ResizeObserver. update-my-box!)
                (.observe this-node)))))
      :render
      (fn []
        [:div {:ref   ref
               :style ($control-layer)}
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
              [match-drop-tool @my-box #(reset! !/show-match-drop? false) user-id])
            (when @!/show-measure-tool?
              [measure-tool @my-box #(reset! !/show-measure-tool? false)])
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

(defn- pop-up []
  [:div#pin {:style ($/fixed-size "2rem")}
   [svg/pin]])

(defn- map-layer []
  (r/with-let [mouse-down? (r/atom false)
               cursor-fn   #(cond
                              @mouse-down?               "grabbing"
                              (or @!/show-info?
                                  @!/show-match-drop?
                                  @!/show-measure-tool?) "crosshair"
                              :else                      "grab")]
    [:div#map {:class (<class $p-mb-cursor)
               :style {:cursor (cursor-fn) :height "100%" :position "absolute" :width "100%"}
               :on-mouse-down #(reset! mouse-down? true)
               :on-mouse-up   #(reset! mouse-down? false)}]))

(defn- message-modal []
  (r/with-let [show-me? (r/atom (not @!/dev-mode?))]
    (when @show-me?
      [:div#message-modal {:style ($/modal)}
       [:div {:style ($message-modal false)}
        [:div {:style {:background ($/color-picker :yellow)
                       :width      "100%"}}
         [:label {:style {:font-size "1.5rem" :padding ".5rem 0 0 .5rem"}}
          "Disclaimer"]]
        [:div {:style {:overflow "auto" :padding ".5rem"}}
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
                   :on-click #(u-browser/jump-to-url! "https://pyregence.org/")}
           "Decline"]
          [:label {:class (<class $/p-form-button)
                   :style {:margin        ".5rem"
                           :padding-left  "1.75rem"
                           :padding-right "1.75rem"}
                   :on-click #(reset! show-me? false)}
           "Accept"]]]]])))

(defn- loading-modal []
  [:div#loading-modal {:style ($/modal)}
   [:div {:style ($message-modal true)}
    [:h3 {:style {:margin-bottom "0"
                  :padding       "1rem"
                  :text-align    "center"}}
     "Loading..."]]])

(defn root-component
  "Component definition for the \"Near Term\" and \"Long Term\" Forecast Pages."
  [{:keys [user-id] :as params}]
  (r/create-class
   {:component-did-mount
    (fn [_]
      (let [update-fn (fn [& _]
                        (-> js/window (.scrollTo 0 0))
                        (reset! !/mobile? (> 800.0 (.-innerWidth js/window)))
                        (js/setTimeout mb/resize-map! 50))]
        (-> js/window (.addEventListener "touchend" update-fn))
        (-> js/window (.addEventListener "resize"   update-fn))
        (initialize! params)
        (update-fn)))
    :reagent-render
    (fn [_]
      [:div#near-term-forecast
       {:style ($/combine $/root {:height "100%" :padding 0 :position "relative" :overflow :hidden})}
       [message-box-modal]
       (when @!/loading? [loading-modal])
       [message-modal]
       [nav-bar {:capabilities     @!/capabilities
                 :current-forecast @!/*forecast
                 :is-admin?        (->> @!/user-orgs-list
                                        (filter #(= "admin" (:role %)))
                                        (count)
                                        (< 0)) ; admin of at least one org
                 :logged-in?       user-id
                 :mobile?          @!/mobile?
                 :user-orgs-list   @!/user-orgs-list
                 :select-forecast! select-forecast!
                 :user-id          user-id}]
       [:div {:style {:height "100%" :position "relative" :width "100%"}}
        (when (and @mb/the-map
                   (not-empty @!/capabilities)
                   (not-empty @!/*params))
          [control-layer user-id])
        [map-layer]
        [pop-up]]])}))
