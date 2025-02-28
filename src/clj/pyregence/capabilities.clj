(ns pyregence.capabilities
  (:require [clj-http.client     :as client]
            [clojure.edn         :as edn]
            [clojure.set         :as set]
            [clojure.string      :as str]
            [triangulum.config   :refer [get-config]]
            [triangulum.database :refer [call-sql]]
            [triangulum.logging  :refer [log log-str]]
            [triangulum.response :refer [data-response]]
            [triangulum.utils    :as u]))

;;; State

(defonce layers (atom {}))

(def site-url (get-config :triangulum.email/base-url))
(def psps-geoserver-admin-username (get-config :pyregence.capabilities/psps :geoserver-admin-username))
(def psps-geoserver-admin-password (get-config :pyregence.capabilities/psps :geoserver-admin-password))
(def private-layer-geoservers #{:psps})

;;; Helper Functions

(defn java-date-from-string [date-str]
  (.parse (java.text.SimpleDateFormat. "yyyyMMdd_HHmmss") date-str))

(defn layers-exist?
  "Based on a given GeoServer key and workspace, checks to see if any layers
   exist in the layers atom."
  [geoserver-key geoserver-workspace]
  (->> @layers
       (geoserver-key)
       (filter #(= geoserver-workspace (:workspace %)))
       (count)
       (pos?)))

;;; Layers

(defn- split-risk-weather-psps-layer-name
  "Gets information about a risk, weather, or PSPS layer based on the layer's name.
   The layer is assumed to be in the format `forecast-type_model-name_forecast-start-time:layer-group_timestamp`
   e.g. `fire-risk-forecast_tlines_20231031_12:elmfire_landfire_times-burned_20231105_060000`
        `fire-weather-forecast_hrrr_20231031_18:tmpf_20231031_190000`
        `psps-zonal_nve_20231031_18:deenergization-zones_20231031_180000`"
  [name-string]
  (let [[workspace layer]             (str/split name-string #":")
        [forecast model-name ts1 ts2] (str/split workspace #"_")
        init-timestamp                (str ts1 "_" ts2)
        [layer-group sim-timestamp]   (str/split layer #"_(?=\d{8}_)")]
    {:workspace   workspace
     :layer-group (str workspace ":" layer-group)
     :forecast    forecast
     :filter-set  (into #{forecast init-timestamp model-name} (str/split layer-group #"_"))
     :model-init  init-timestamp
     :sim-time    sim-timestamp
     :hour        (/ (- (.getTime (java-date-from-string sim-timestamp))
                        (.getTime (java-date-from-string (str init-timestamp "0000"))))
                     1000.0 60 60)}))

(defn- split-fire-spread-forecast-layer-name
  "Gets information about a fire spread layer based on the layer's name.
   The layer is assumed to be in the format:
   `fire-spread-forecast_fire-name_forecast-start-time:<elmfire|gridfire>_landfire_percentile_output-type
   e.g. `fire-spread-forecast_ky-bradford-town_20231105_191700:elmfire_landfire_10_hours-since-burned`"
  [name-string]
  (let [[workspace layer]              (str/split name-string #":")
        [forecast fire-name ts1 ts2]   (str/split workspace #"_")
        [model fuel percentile output] (str/split layer #"_")
        model-init                     (str ts1 "_" ts2)]
    {:workspace   workspace
     :fire-name   fire-name
     :forecast    forecast
     :filter-set  #{forecast fire-name model fuel percentile output model-init}
     :model-init  model-init
     :layer-group ""}))

(defn- split-isochrones-layer-name
  "Gets information about an active fire isochrones layer based on its name.
   The layer is assumed to be in the format:
   `fire-spread-forecast_fire-name_forecast-start-time:<elmfire|gridfire>_landfire_percentile_isochrones_fire-name_forecast-start-time_percentile`
   e.g. `fire-spread-forecast_ky-bradford-town_20231105_191700:elmfire_landfire_10_isochrones_ky-bradford-town_20231105_191700_10`"
  [name-string]
  (let [[workspace layer]                      (str/split name-string #":")
        [forecast fire-name init-ts1 init-ts2] (str/split workspace   #"_")
        [layer-group _]                        (str/split layer       #"_(?=\d{8}_)")
        init-timestamp                         (str init-ts1 "_" init-ts2)]
    {:workspace   workspace
     :layer-group ""
     :forecast    forecast
     :fire-name   fire-name
     :filter-set  (into #{forecast fire-name init-timestamp} (str/split layer-group #"_"))
     :model-init  init-timestamp
     :hour        0}))

(defn- split-fire-detections
  "Gets information about a fire detections layer (e.g. MODIS or VIIRS) based on its name."
  [name-string]
  (let [[workspace layer]   (str/split name-string #":")
        [forecast type]     (str/split workspace #"_")
        [filter model-init] (str/split layer #"_(?=\d{8}_)")]
    {:workspace   workspace
     :layer-group ""
     :forecast    forecast
     :type        type
     :filter-set  #{forecast filter model-init}
     :model-init  model-init
     :hour        0}))

(defn- split-fuels
  "Gets information about a fuels layer based on its name."
  [name-string]
  (let [[workspace layer] (str/split name-string #":")
        [forecast model]  (str/split workspace #"_")]
    {:workspace   workspace
     :layer-group ""
     :forecast    forecast
     :filter-set  #{"fuels" model layer "20210407_000000"}
     :model-init  "20210407_000000"
     :hour        0}))

(defn- split-wg4-scenarios
  "Gets information about a WG4 (climate) layer based on its name."
  [name-string]
  (let [[workspace layer] (str/split name-string #":")
        [_ parameters]    (str/split layer #"_geoTiff_")
        [_ model prob measure year] (re-matches #"([^_]+)_([^_]+)_AA_all_([^_]+)_mean_(\d+)" parameters)]
    {:workspace   workspace
     :layer-group (str workspace ":" model "_" prob "_AA_all_" measure "_mean")
     :forecast    model
     :filter-set  #{workspace model prob measure "20210407_000000"}
     :model-init  "20210407_000000"
     :sim-time    (str year "0101_000000")
     :hour        (- (Integer/parseInt year) 1954)}))

(defn- split-psps-underlays
  "Gets information about a PSPS static layer based on its name (e.g. `psps-static_nve:nve-trans`)."
  [name-string]
  (let [[workspace layer]  (str/split name-string #":")
        [forecast utility] (str/split workspace #"_")]
    {:workspace   workspace
     :layer-group ""
     :forecast    forecast
     :type        layer
     :filter-set  #{forecast layer utility}
     :model-init  ""
     :hour        0}))

(defn process-layers!
  "Makes a call to GetCapabilities and uses regex on the resulting XML response
   to generate a vector of layer entries where each entry is a map. The info
   in each entry map is generated based on the title of the layer. Different layer
   types have their information generated in different ways using the split-
   functions above. Optionally can provide `basic-auth`, which is used for password
   protected workspaces. `basic-auth` must be either a string of the form
   \"username:password\" or a tuple of the form `[username passwords]`."
  [geoserver-url workspace-name basic-auth]
  (let [base-url     (-> geoserver-url
                         (u/end-with "/")
                         (str "wms?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetCapabilities"
                              (when workspace-name
                                (str "&NAMESPACE=" workspace-name))))
        xml-response (-> base-url
                         (client/get (when basic-auth {:basic-auth basic-auth}))
                         (:body))]
    (as-> xml-response xml
      (str/replace xml "\n" "")
      (re-find #"(?<=<Layer>).*(?=</Layer>)" xml)
      (re-seq #"(?<=<Layer).+?(?=</Layer>)" xml)
      (partition-all 1000 xml)
      (pmap (fn [layer-group]
              (->> layer-group
                   (keep
                    (fn [layer]
                      (let [full-name (re-find #"(?<=<Name>).+?(?=</Name>)" layer)
                            coords    (->> (re-find #"<BoundingBox CRS=\"CRS:84.+?\"/>" layer)
                                           (re-seq #"[\d|\.|-]+")
                                           (rest)
                                           (vec))
                            times     (some-> (re-find #"<Dimension .*>(.*)</Dimension>" layer) ; Times are used in ImageMosaic layers
                                              (last)
                                              (str/split #","))
                            merge-fn  #(merge % {:layer full-name :extent coords :times times})]
                        (cond
                          (re-matches #"([a-z|-]+_[a-z0-9|-]+_)\d{8}_\d{2}:([A-Za-z0-9|-]+\d*_)+\d{8}_\d{6}" full-name)
                          (merge-fn (split-risk-weather-psps-layer-name full-name))

                          (and (re-matches #"[a-z|-]+_[a-z|-]+[a-z|\d|-]*_\d{8}_\d{6}:([a-z|-]+_){2}\d{2}_[a-z|-]+" full-name)
                               (or (get-config :triangulum.views/client-keys :features :match-drop) (not (str/includes? full-name "match-drop"))))
                          (merge-fn (split-fire-spread-forecast-layer-name full-name))

                          (and (str/includes? full-name "isochrones")
                               (re-matches #"([a-z|-]+_)[a-z|-]+[a-z|\d|-]*_\d{8}_\d{6}:([a-z|-]+_){2}\d{2}_isochrones_[a-z|\d|-]*_\d{8}_\d{6}_\d{2}" full-name))
                          (merge-fn (split-isochrones-layer-name full-name))

                          (or (re-matches #"fire-detections.*_\d{8}_\d{6}" full-name)
                              (re-matches #"fire-detections.*:(goes16-rgb|fire-history|conus-buildings|us-transmission-lines|.*boundaries).*" full-name))
                          (merge-fn (split-fire-detections full-name))

                          (str/starts-with? full-name "fuels")
                          (merge-fn (split-fuels full-name))

                          (re-matches #"climate_FireSim.*_\d{4}" full-name)
                          (merge-fn (split-wg4-scenarios full-name))

                          (str/starts-with? full-name "psps-static")
                          (merge-fn (split-psps-underlays full-name))))))
                   (vec)))
            xml)
      (apply concat xml)
      (vec xml))))

;;; Routes

;; TODO note that calling this fn on a regex does not work properly. Passing in
;; a workspace name as a regex is a GeoSync use case, so this should be updated to accept a regex
(defn remove-workspace!
  "Given a specific geoserver-key and a specific workspace-name, removes any
   layers from that workspace from the layers atom."
  [{:strs [geoserver-key workspace-name]}]
  (swap! layers
         update
         (keyword geoserver-key)
         #(filterv (fn [{:keys [workspace]}]
                     (not= workspace workspace-name)) %))
  (data-response (str workspace-name " removed from " site-url ".")))

(defn get-all-layers []
  (data-response (mapcat #(map :filter-set (val %)) @layers)))

(defn set-capabilities!
  "Populates the layers atom with all of the layers that are returned
   from a call to GetCapabilities on a specific GeoServer. Passing in a
   `geoserver-key` specifies which GeoServer to call GetCapabilities on and
   passing in an optional `workspace-name` allows you to call GetCapabilities
   on just that workspace by passing it into `process-layers!`."
  [{:strs [geoserver-key workspace-name]}]
  (let [geoserver-key (keyword geoserver-key)
        basic-auth    (when (private-layer-geoservers geoserver-key)
                        (str psps-geoserver-admin-username ":" psps-geoserver-admin-password))]
    (if-not (contains? (get-config :triangulum.views/client-keys :geoserver) geoserver-key)
      (log-str "Failed to load capabilities. The GeoServer URL passed in was not found in config.edn.")
      (let [timeout-ms    (* 2.5 60 1000) ; 2.5 minutes
            future-result (future
                            (try
                              (let [stdout?       (= 0 (count @layers))
                                    geoserver-url (get-config :triangulum.views/client-keys :geoserver geoserver-key)
                                    new-layers    (process-layers! geoserver-url workspace-name basic-auth)
                                    message       (str (count new-layers) " layers from " geoserver-url " added to " site-url ".")]
                                (if workspace-name
                                  (do
                                    (remove-workspace! {"geoserver-key"  (name geoserver-key)
                                                        "workspace-name" workspace-name})
                                    (swap! layers update geoserver-key concat new-layers))
                                  (swap! layers assoc geoserver-key new-layers))
                                (log message :force-stdout? stdout?)
                                (data-response message))
                              (catch Exception e
                                (log-str "Failed to load capabilities for "
                                         (get-config :triangulum.views/client-keys :geoserver geoserver-key)
                                         "\n" (ex-message e)))))]
        (if-let [result (deref future-result timeout-ms nil)]
          result
          (log-str (quot timeout-ms 1000) " seconds timeout occurred while loading capabilities for "
                   (get-config :triangulum.views/client-keys :geoserver geoserver-key)))))))

(defn set-all-capabilities!
  "Calls set-capabilities! on all GeoServer URLs provided in config.edn."
  []
  (doseq [geoserver-key (keys (get-config :triangulum.views/client-keys :geoserver))]
    (set-capabilities! {"geoserver-key" (name geoserver-key)}))
  (data-response (str (reduce + (map count (vals @layers)))
                      " total layers added to " site-url ".")))

(defn fire-name-capitalization [fire-name]
  (let [parts (str/split fire-name #"-")]
    (str/join " "
              (into [(str/upper-case (first parts))]
                    (map str/capitalize (rest parts))))))

; FIXME get user-id from session on backend
(defn get-fire-names
  "Returns all unique fires from the layers atom parsed into the format
   needed on the front-end. Takes special care to deal with match drop fires.
   An example return value can be seen below:
   {:foo {:opt-label \"foo\", :filter \"foo\", :auto-zoom? true,
    :bar {:opt-label \"bar\", :filter \"bar\", :auto-zoom? true}}"
  [user-id]
  (let [match-drop-names (->> (call-sql "get_user_match_names" user-id)
                              (reduce (fn [acc row]
                                        (assoc acc
                                               (:match_job_id row)
                                               (str (:display_name row)
                                                    " (Match Drop)")))
                                      {}))]
    (->> (apply merge (:trinity @layers) (:match-drop @layers))
         (filter (fn [{:keys [forecast]}]
                   (= "fire-spread-forecast" forecast)))
         (map :fire-name)
         (distinct)
         (mapcat (fn [fire-name]
                   (let [match-job-id (some-> fire-name
                                              (str/split #"match-drop-")
                                              (second)
                                              (Integer/parseInt))]
                     ;; We either want to pass a fire name to the front end if the fire in question is
                     ;; an active fire (nil? match-job-id) or it's a Match Drop that matches our deployment
                     ;; environment. Since development and production Match Drops can have the same ID,
                     ;; we have to make sure the fire-name starts with the specific md-prefix specified in config.edn
                     ;; so that we don't double-count the Match Drops.
                     (when (or (nil? match-job-id)
                               (and (contains? match-drop-names match-job-id)
                                    (str/starts-with? fire-name (get-config :pyregence.match-drop/match-drop :md-prefix))))
                       [(keyword fire-name)
                        {:opt-label      (or (get match-drop-names match-job-id)
                                             (fire-name-capitalization fire-name))
                         :filter        fire-name
                         :auto-zoom?    true
                         :geoserver-key (if match-job-id :match-drop :trinity)}]))))
         (apply array-map))))

(defn get-user-layers [user-id]
  ; TODO get user-id from session on backend
  (data-response (call-sql "get_user_layers_list" user-id)))

;; TODO update remote_api handler so individual params dont need edn/read-string
(defn get-layers
  "Based on the given geoserver-key and set of strings to filter the layers by,
   returns all of the matching layers from the layers atom and their associated
   model-times. The selected-set-str is compared against the :filter-set property
   of each layer in the layers atom. Any subsets lead to that layer being returned."
  [geoserver-key selected-set-str]
  (when-not (seq @layers) (set-all-capabilities!))
  (let [selected-set (edn/read-string selected-set-str)
        available    (filterv (fn [layer] (set/subset? selected-set (:filter-set layer)))
                              (geoserver-key @layers))
        model-times  (->> available
                          (map :model-init)
                          (distinct)
                          (remove nil?)
                          (sort #(compare %2 %1)))]
    (data-response (if (selected-set (first model-times))
                     {:layers available}
                     (let [max-time     (first model-times)
                           complete-set (if max-time
                                          (conj selected-set max-time)
                                          selected-set)]
                       {:layers      (filterv (fn [{:keys [filter-set]}]
                                                (= complete-set filter-set))
                                              available)
                        :model-times (seq model-times)}))
                   {:type :transit})))

(defn get-layer-name [geoserver-key selected-set-str]
  (let [selected-set (edn/read-string selected-set-str)]
    (data-response (->> (geoserver-key @layers)
                        (filter (fn [layer] (set/subset? selected-set (:filter-set layer))))
                        (sort #(compare (:model-init %2) (:model-init %1)))
                        (first)
                        (:layer)))))
