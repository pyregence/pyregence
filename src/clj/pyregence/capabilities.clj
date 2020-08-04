(ns pyregence.capabilities
  (:require [clojure.string     :as str]
            [clojure.edn        :as edn]
            [clojure.spec.alpha :as s]
            [clj-http.client    :as client]
            [pyregence.database     :refer [call-sql]]
            [pyregence.layer-config :refer [forecast-options]]
            [pyregence.logging      :refer [log-str]]
            [pyregence.views        :refer [data-response]]))

;;; Spec

(s/def ::opt-label    string?)
(s/def ::filter       string?)
(s/def ::units        string?)
(s/def ::layer-config (s/keys :req-un [::opt-label ::filter] :opt-un [::units]))
(s/def ::layer-path   (s/and (s/coll-of keyword? :kind vector? :min-count 3)
                             (s/cat :forecast #{:fire-risk :active-fire :fire-weather}
                                    :params   #(= % :params)
                                    :etc      (s/+ keyword?))))

;;; State

(defonce capabilities (atom []))
(defonce layers       (atom []))

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

;;; Capabilities

(defn fire-name-capitalization [fire-name]
  (let [parts (str/split fire-name #"-")]
    (str/join " "
              (into [(str/upper-case (first parts))]
                    (map str/capitalize (rest parts))))))

(defn get-fire-names [forecast-layers]
  (->> forecast-layers
       (group-by :fire-name)
       (sort)
       (mapcat (fn [[fire-name opt-vec]]
                 [(keyword fire-name)
                  {:opt-label  (fire-name-capitalization fire-name)
                   :filter     fire-name
                   :model-init (into #{} (map :model-init) opt-vec)}]))
       (apply array-map)))

(defn get-model-times [forecast-layers]
  (->> forecast-layers
       (map :model-init)
       (distinct)
       (sort #(compare %2 %1))
       (mapcat (fn [option]
                 [(keyword option)
                  {:opt-label option ; This label will get overwritten on the front end when time-zone is processed
                   :utc-time  option
                   :filter    option}]))
       (apply array-map)))

(defn process-capabilities! []
  (reset! capabilities
          (mapm (fn [[key vals]]
                  (let [forecast-layers (filter (fn [{:keys [forecast]}]
                                                  (= (:filter vals) forecast))
                                                @layers)
                        has-fire-name?  (get-in vals [:params :fire-name])]
                    [key (cond-> vals
                           has-fire-name? (assoc-in [:params :fire-name  :options]
                                                    (get-fire-names  forecast-layers))
                           :always        (assoc-in [:params :model-init :options]
                                                    (get-model-times forecast-layers)))]))
                forecast-options)))

;;; Layers

(defn split-risk-layer-name [name-string]
  (let [[workspace layer]           (str/split name-string #":")
        [forecast init-timestamp]   (str/split workspace   #"_(?=\d{8}_)")
        [layer-group sim-timestamp] (str/split layer       #"_(?=\d{8}_)")]
    {:layer-group (str workspace ":" layer-group)
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
    {:layer-group ""
     :forecast    forecast
     :fire-name   fire-name
     :filter-set  (into #{forecast fire-name init-timestamp} (str/split layer-group #"_"))
     :model-init  init-timestamp
     :sim-time    sim-timestamp
     :hour        0}))

(defn process-layers! []
  (let [xml-response (:body (client/get (str "https://data.pyregence.org:8443/geoserver/wms"
                                             "?SERVICE=WMS"
                                             "&VERSION=1.3.0"
                                             "&REQUEST=GetCapabilities")))]
    (reset! layers
            (as-> xml-response xml
              (str/replace xml "\n" "")
              (re-find #"<Layer>.*(?=</Layer>)" xml)
              (str/replace-first xml "<Layer>" "")
              (re-seq #"<Layer.+?</Layer>" xml)
              (partition-all 1000 xml)
              (pmap (fn [layer-group]
                      (->> layer-group
                           (keep
                            (fn [layer]
                              (let [full-name (->  (re-find #"<Name>.+?(?=</Name>)" layer)
                                                   (str/replace #"<Name>" ""))
                                    coords    (->> (re-find #"<BoundingBox CRS=\"CRS:84.+?\"/>" layer)
                                                   (re-seq #"[\d|\.|-]+")
                                                   (rest)
                                                   (vec))
                                    merge-fn  #(merge % {:layer full-name :extent coords})]
                                (cond
                                  (re-matches #"([a-z|-]+_)\d{8}_\d{2}:([a-z|-]+\d*_)+\d{8}_\d{6}" full-name)
                                  (merge-fn (split-risk-layer-name full-name))

                                  (re-matches #"([a-z|-]+_)[a-z|-]+\d*_\d{8}_\d{6}:([a-z|-]+_){2}\d{2}_([a-z|-]+_)\d{8}_\d{6}" full-name)
                                  (merge-fn (split-active-layer-name full-name))))))
                           (vec)))
                    xml)
              (apply concat xml)
              (vec xml)))))

;;; Routes

(defn set-capabilities! []
  (try
    (process-layers!)
    (process-capabilities!)
    (log-str (count @layers) " layers added to capabilities.")
    (catch Exception e
      (log-str "Failed to load capabilities."))))

(defn remove-workspace! [workspace-name]
  (swap! layers #(filterv (fn [{:keys [workspace]}]
                            (not= workspace workspace-name))
                          %))
  (process-capabilities!))

(defn get-capabilities [user-id]
  (when-not (seq @capabilities) (set-capabilities!))
  (data-response (reduce (fn [acc {:keys [layer_path layer_config]}]
                           (let [layer-path   (edn/read-string layer_path)
                                 layer-config (edn/read-string layer_config)]
                             (if (and (s/valid? ::layer-path   layer-path)
                                      (s/valid? ::layer-config layer-config))
                               (assoc-in acc layer-path layer-config)
                               acc)))
                         @capabilities
                         (call-sql "get_user_layers_list" user-id))
                 {:type :transit}))

;; TODO Check if layers still exist in capabilities and respond with an error if not.
(defn get-layers [selected-set-str]
  (let [selected-set (edn/read-string selected-set-str)]
    (data-response (filterv (fn [{:keys [filter-set]}]
                              (= selected-set filter-set))
                            @layers)
                   {:type :transit})))
