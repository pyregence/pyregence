(ns pyregence.red-flag
  (:require [clojure.string    :refer [lower-case]]
            [clojure.data.json :as json]
            [clj-http.client   :as client]
            [pyregence.views   :refer [data-response]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; NOAA Red Flag Warning
;; Data: https://www.weather.gov/fire/
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private hazards-url   "https://www.wrh.noaa.gov/map/json/WR_All_Hazards.json")
(def ^:private cache-max-age (* 60 1000)) ; Once an hour
(def ^:private keep-hazards  #{"FW" "Fire Weather"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Cache
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^:private cache       (atom nil))
(defonce ^:private cache-time  (atom nil))

(defn- reset-cache! [new-cache]
  (reset! cache-time (System/currentTimeMillis))
  (reset! cache new-cache))

(defn- valid-cache? []
  (and (some? @cache)
       (< (- (System/currentTimeMillis) @cache-time) cache-max-age)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- all-hazards []
  (let [{:keys [status body]} (client/get hazards-url)]
    (when (= 200 status)
      (json/read-str body :key-fn (comp keyword lower-case)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- filter-hazards [{:keys [features]}]
  (->> features
    (filterv (fn [{:keys [properties]}] (keep-hazards (:phenom properties))))
    (mapv (fn [f] {:geometry   (:geometry f)
                   :properties (select-keys (:properties f)
                                            [:cap_id :onset :phenom :event :msg_type :url :color])}))))

(defn- ->feature-collection [features]
  {:type     "FeatureCollection"
   :features features})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-red-flag-layer
  "Builds a GeoJSON response of the NOAA Red Flag warnings."
  []
  (data-response (if (valid-cache?)
                   @cache
                   (let [warnings (-> (all-hazards)
                                      (filter-hazards)
                                      (->feature-collection))]
                     (reset-cache! warnings)
                     warnings))
                 {:type :json}))
