(ns pyregence.capabilities
  (:require [clojure.string    :as str]
            [cognitect.transit :as transit]
            [clj-http.client   :as client]
            [pyregence.views   :refer [data-response]]
            [pyregence.logging :refer [log-str]])
  (:import [java.io ByteArrayOutputStream]))

(defonce capabilities (atom []))
(defonce out-str      (atom []))

(defn java-date-from-string [date-str]
  (.parse (java.text.SimpleDateFormat. "yyyyMMdd_hhmmss") date-str))

(defn split-risk-layer-name [name-string]
  (let [[workspace layer]           (str/split name-string #":")
        [forecast init-timestamp]   (str/split workspace   #"_(?=\d{8}_)")
        [layer-group sim-timestamp] (str/split layer       #"_(?=\d{8}_)")]
    {:layer-group (str workspace ":" layer-group)
     :forecast    forecast
     :filter-set  (into #{forecast init-timestamp} (str/split layer-group #"_"))
     :model-init  init-timestamp
     :sim-time    sim-timestamp
     :hour        (- (/ (- (.getTime (java-date-from-string sim-timestamp))
                           (.getTime (java-date-from-string (str init-timestamp "0000")))) 1000 60 60) 6)}))

(defn split-active-layer-name [name-string]
  (let [[workspace layer]    (str/split name-string #":")
        [forecast fire-name] (str/split workspace   #"_")
        params               (str/split layer       #"_")
        init-timestamp       (str (get params 0) "_" (get params 1))]
    {:layer-group ""
     :forecast    forecast
     :fire-name   fire-name
     :filter-set  (into #{forecast fire-name init-timestamp} (subvec params 2 6))
     :model-init  init-timestamp
     :sim-time    (str (get params 6) "_" (get params 7))
     :hour        0}))

(defn set-capabilities! []
  (try
    (let [out      (ByteArrayOutputStream. 4096)
          writer   (transit/writer out :json)
          responce (:body (client/get (str "https://data.pyregence.org:8443/geoserver/wms"
                                           "?SERVICE=WMS"
                                           "&VERSION=1.3.0"
                                           "&REQUEST=GetCapabilities")))]
      (reset! capabilities
              (as-> responce xml
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

                                    (re-matches #"([a-z|-]+_)[a-z|-]+\d*:\d{8}_\d{6}_([a-z|-]+_){2}\d{2}_([a-z|-]+_)\d{8}_\d{6}" full-name)
                                    (merge-fn (split-active-layer-name full-name))))))
                             (vec)))
                      xml)
                (apply concat xml)
                (vec xml)))
      (transit/write writer @capabilities)
      (reset! out-str (.toString out))
      (log-str (count @capabilities) " layers added to capabilities."))
    (catch Exception e
      (log-str "Failed to load capabilities."))))

(defn get-capabilities []
  (when-not (seq @capabilities) (set-capabilities!))
  (data-response 200 @out-str :transit))
