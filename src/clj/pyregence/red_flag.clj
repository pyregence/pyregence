(ns pyregence.red-flag
  (:require [clojure.data.json :as json]
            [clojure.edn       :as edn]
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
(def ^:private fire-weather  #{"FW" "Fire Weather"})

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
      (json/read-str body :key-fn keyword))))

(defn- red-flag-warnings [{:keys [features]}]
  (vec (filter (fn [{:keys [properties]}] (fire-weather (:PHENOM properties))) features)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
                                      (red-flag-warnings)
                                      (->feature-collection))]
                     (reset-cache! warnings)
                     warnings))
                 {:type :json}))
