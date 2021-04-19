(ns pyregence.capabilities
  (:require [clojure.edn        :as edn]
            [clojure.string     :as str]
            [clojure.set        :as set]
            [clj-http.client    :as client]
            [pyregence.database :refer [call-sql]]
            [pyregence.logging  :refer [log log-str]]
            [pyregence.views    :refer [data-response]]))

;;; State

(defonce layers (atom []))

;;; Helper Functions

(defn java-date-from-string [date-str]
  (.parse (java.text.SimpleDateFormat. "yyyyMMdd_HHmmss") date-str))

(defn mapm [f coll]
  (persistent!
   (reduce (fn [acc cur]
             (conj! acc (f cur)))
           (transient {})
           coll)))

(defn filterm [pred coll]
  (persistent!
   (reduce (fn [acc cur]
             (if (pred cur)
               (conj! acc cur)
               acc))
           (transient {})
           coll)))

;;; Layers

(defn split-risk-layer-name [name-string]
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
                     1000 60 60)}))

(defn split-active-layer-name [name-string]
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

(defn split-fire-detections [name-string]
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

(defn- split-fuels [name-string]
  (let [[workspace layer]   (str/split name-string #":")
        [forecast model]     (str/split workspace #"_")]
    {:workspace   workspace
     :layer-group ""
     :forecast    forecast
     :filter-set  #{forecast model layer "20210407_000000"}
     :model-init  "20210407_000000"
     :hour        0}))

(defn process-layers! [workspace-name]
  (let [xml-response (:body (client/get (str "https://data.pyregence.org:8443/geoserver/wms"
                                             "?SERVICE=WMS"
                                             "&VERSION=1.3.0"
                                             "&REQUEST=GetCapabilities"
                                             (when workspace-name (str "&NAMESPACE=" workspace-name)))))]
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
                            merge-fn  #(merge % {:layer full-name :extent coords})]
                        (cond
                          (re-matches #"([a-z|-]+_)\d{8}_\d{2}:([a-z|-]+\d*_)+\d{8}_\d{6}" full-name)
                          (merge-fn (split-risk-layer-name full-name))

                          (re-matches #"([a-z|-]+_)[a-z|-]+[a-z|\d|-]*_\d{8}_\d{6}:([a-z|-]+_){2}\d{2}_([a-z|-]+_)\d{8}_\d{6}" full-name)
                          (merge-fn (split-active-layer-name full-name))

                          (str/starts-with? full-name "fire-detections")
                          (merge-fn (split-fire-detections full-name))

                          (str/starts-with? full-name "fuels-and-topography")
                          (merge-fn (split-fuels full-name))))))
                   (vec)))
            xml)
      (apply concat xml)
      (vec xml))))

;;; Routes

(defn remove-workspace! [workspace-name]
  (swap! layers #(filterv (fn [{:keys [workspace]}]
                            (not= workspace workspace-name))
                          %))
  (data-response (str workspace-name " removed.")))

(defn set-capabilities! [& [workspace-name]]
  (try
    (let [stdout?    (= 0 (count @layers))
          new-layers (process-layers! workspace-name)
          message    (str (count new-layers) " layers added to capabilities.")]
      (if workspace-name
        (do
          (remove-workspace! workspace-name)
          (swap! layers #(into % new-layers)))
        (reset! layers new-layers))
      (log message :force-stdout? stdout?)
      (data-response message))
    (catch Exception _
      (log-str "Failed to load capabilities."))))

(defn fire-name-capitalization [fire-name]
  (let [parts (str/split fire-name #"-")]
    (str/join " "
              (into [(str/upper-case (first parts))]
                    (map str/capitalize (rest parts))))))

(defn get-fire-names []
  (->> @layers
       (filter (fn [{:keys [forecast]}]
                 (= "fire-spread-forecast" forecast)))
       (map :fire-name)
       (distinct)
       (mapcat (fn [fire-name]
                 [(keyword fire-name)
                  {:opt-label (fire-name-capitalization fire-name)
                   :filter    fire-name}]))
       (apply array-map)))

(defn get-user-layers [user-id]
  ; TODO get user-id from session on backend
  (data-response (call-sql "get_user_layers_list" user-id)))

;; TODO update remote_api handler so individual params dont need edn/read-string
(defn get-layers [selected-set-str]
  (when-not (seq @layers) (set-capabilities!))
  (let [selected-set (edn/read-string selected-set-str)
        available    (filterv (fn [layer] (set/subset? selected-set (:filter-set layer))) @layers)
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

(defn get-layer-name [selected-set-str]
  (let [selected-set (edn/read-string selected-set-str)]
    (data-response (->> @layers
                        (filter (fn [layer] (set/subset? selected-set (:filter-set layer))))
                        (sort #(compare (:model-init %2) (:model-init %1)))
                        (first)
                        (:layer)))))
