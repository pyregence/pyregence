(ns pyregence.capabilities
  (:require [clj-http.client              :as client]
            [clojure.edn                  :as edn]
            [clojure.set                  :as set]
            [clojure.string               :as str]
            [pyregence.views              :refer [data-response]]
            [triangulum.config            :refer [get-config]]
            [triangulum.database          :refer [call-sql]]
            [triangulum.logging           :refer [log log-str]]
            [triangulum.utils             :as u]))

;;; State

(defonce layers (atom {}))

;;; Helper Functions

(defn java-date-from-string [date-str]
  (.parse (java.text.SimpleDateFormat. "yyyyMMdd_HHmmss") date-str))

;;; Layers

(defn- split-fire-spread-forecast
  "Gets information about a fire spread layer based on the layer's name."
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

(defn- split-risk-weather-psps-layer-name
  "Gets information about a risk, weather, or PSPS layer based on the layer's name."
  [name-string]
  (let [[workspace layer]           (str/split name-string #":")
        [forecast init-timestamp]   (str/split workspace   #"_(?=\d{8}_)")
        [layer-group sim-timestamp] (str/split layer       #"_(?=\d{8}_)")]
    {:workspace   workspace
     :layer-group (str workspace ":" layer-group)
     :forecast    forecast
     :filter-set  (into #{forecast init-timestamp} (str/split layer-group #"_"))
     :model-init  init-timestamp
     :sim-time    sim-timestamp
     :hour        (/ (- (.getTime (java-date-from-string sim-timestamp))
                        (.getTime (java-date-from-string (str init-timestamp "0000"))))
                     1000.0 60 60)}))

(defn- split-active-layer-name
  "Gets information about an active fire layer based on its name."
  [name-string]
  (let [[workspace layer]                      (str/split name-string #":")
        [forecast fire-name init-ts1 init-ts2] (str/split workspace   #"_")
        [layer-group sim-timestamp]            (str/split layer       #"_(?=\d{8}_)")
        init-timestamp                         (str init-ts1 "_" init-ts2)]
    {:workspace   workspace
     :layer-group ""
     :forecast    forecast
     :fire-name   fire-name
     :filter-set  (into #{forecast fire-name init-timestamp} (str/split layer-group #"_"))
     :model-init  init-timestamp
     :sim-time    sim-timestamp
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
  "Gets information about a PSPS static layer based on its name."
  [name-string]
  (let [[workspace layer] (str/split name-string #":")
        [forecast type]   (str/split workspace #"_")]
    {:workspace   workspace
     :layer-group ""
     :forecast    forecast
     :type        type
     :filter-set  #{forecast type}
     :model-init  ""
     :hour        0}))

(defn process-layers!
  "Makes a call to GetCapabilities and uses regex on the resulting XML response
   to generate a vector of layer entries where each entry is a map. The info
   in each entry map is generated based on the title of the layer. Different layer
   types have their information generated in different ways using the split-
   functions above."
  [geoserver-key workspace-name]
  (let [xml-response (-> (get-config :geoserver geoserver-key)
                         (u/end-with "/")
                         (str "wms?SERVICE=WMS&VERSION=1.3.0&REQUEST=GetCapabilities"
                              (when workspace-name
                                (str "&NAMESPACE=" workspace-name)))
                         (client/get)
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
                          (re-matches #"([a-z|-]+_)\d{8}_\d{2}:([A-Za-z0-9|-]+\d*_)+\d{8}_\d{6}" full-name)
                          (merge-fn (split-risk-weather-psps-layer-name full-name))

                          (and (re-matches #"[a-z|-]+_[a-z|-]+[a-z|\d|-]*_\d{8}_\d{6}:([a-z|-]+_){2}\d{2}_[a-z|-]+" full-name)
                               (or (get-config :features :match-drop) (not (str/includes? full-name "match-drop"))))
                          (merge-fn (split-fire-spread-forecast full-name))

                          ;; TODO: Remove once fire forecasts are migrated to Image Mosiac format. Will still need isochrones to be read in properly.
                          (or (str/includes? full-name "isochrones")
                              (and (re-matches #"([a-z|-]+_)[a-z|-]+[a-z|\d|-]*_\d{8}_\d{6}:([a-z|-]+_){2}\d{2}_([a-z|-]+_)\d{8}_\d{6}" full-name)
                                   (or (get-config :features :match-drop) (not (str/includes? full-name "match-drop")))))
                          (merge-fn (split-active-layer-name full-name))

                          (or (re-matches #"fire-detections.*_\d{8}_\d{6}" full-name)
                              (re-matches #"fire-detections.*:(goes16-rgb|fire-history|conus-buildings|us-transmission-lines).*" full-name))
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

(defn remove-workspace!
  "Given a specific geoserver-key and a specific workspace-name, removes any
   layers from that workspace from the layers atom."
  [{:strs [geoserver-key workspace-name]}]
  (swap! layers
         update (keyword geoserver-key)
                #(filterv (fn [{:keys [workspace]}]
                            (not= workspace workspace-name)) %))
  (data-response (str workspace-name " removed from Pyrecast.")))

(defn get-all-layers []
  (data-response (mapcat #(map :filter-set (val %)) @layers)))

(defn set-capabilities!
  "Populates the layers atom with all of the layers that are returned
   from a call to GetCapabilities on a specific GeoServer. Passing in a
   geoserver-key specifies which GeoServer to call GetCapabilities on and
   passing in an optional workspace-name allows you to call GetCapabilities
   on just that workspace by passing it into process-layers!."
  [{:strs [geoserver-key workspace-name]}]
  (let [geoserver-key  (keyword geoserver-key)]
    (if (contains? (get-config :geoserver) geoserver-key)
      (try
        (let [stdout?    (= 0 (count @layers))
              new-layers (process-layers! geoserver-key workspace-name)
              message    (str (count new-layers) " layers added to capabilities.")]
          (if workspace-name
            (do
              (remove-workspace! {"geoserver-key"  (name geoserver-key)
                                  "workspace-name" workspace-name})
              (swap! layers update geoserver-key concat new-layers))
            (swap! layers assoc geoserver-key new-layers))
          (log message :force-stdout? stdout?)
          (data-response message))
        (catch Exception e
          (log-str "Failed to load capabilities.\n" (ex-message e))))
      (log-str "Failed to load capabilities. The GeoServer URL passed in was not found in config.edn."))))

(defn set-all-capabilities!
  "Calls set-capabilities! on all GeoServer URLs provided in config.edn."
  []
  (doseq [geoserver-key (keys (get-config :geoserver))]
    (set-capabilities! {"geoserver-key" (name geoserver-key)}))
  (data-response (str (reduce + (map count (vals @layers)))
                      " layers added to capabilities.")))

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
                                        (assoc acc (:match_job_id row)
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
                     (when (or (nil? match-job-id)
                               (contains? match-drop-names match-job-id))
                       [(keyword fire-name)
                        {:opt-label  (or (get match-drop-names match-job-id)
                                         (fire-name-capitalization fire-name))
                         :filter     fire-name
                         :auto-zoom? true}]))))
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
