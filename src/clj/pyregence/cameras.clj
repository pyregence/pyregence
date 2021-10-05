(ns pyregence.cameras
  (:require [clojure.data.json :as json]
            [clj-http.client   :as client]
            [pyregence.config  :refer [get-config]]
            [pyregence.views   :refer [data-response]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; AlertWildfire Cameras
;; See API documentation: http://dev-public.nvseismolab.org/doc/api/firecams/
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private wildfire-camera-api-url "https://data.alertwildfire.org/api/firecams/v0")
(def ^:private wildfire-camera-api-key (get-config :cameras :wildfire-camera-api-key))
(def ^:private api-defaults            {:headers {"X-Api-Key" wildfire-camera-api-key}})
(def ^:private cache-max-age           (* 24 60 1000)) ; Once a day

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Camera cache
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^:private camera-cache (atom nil))
(defonce ^:private cached-time  (atom nil))

(defn- reset-cache! [cameras]
  (reset! cached-time (System/currentTimeMillis))
  (reset! camera-cache cameras))

(defn- valid-cache? []
  (and (some? @camera-cache)
       (< (- (System/currentTimeMillis) @cached-time) cache-max-age)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- api-request [path & [opts]]
  (client/get (str wildfire-camera-api-url "/" path) ; URL
              (merge api-defaults opts)))

(defn- api-all-cameras []
  (let [{:keys [status body]} (api-request "cameras")]
    (when (= 200 status)
      (json/read-str body :key-fn keyword))))

(defn- api-get-camera [camera-name]
  (let [{:keys [status body]} (api-request (str "cameras?name=" camera-name))]
    (when (= 200 status)
      (json/read-str body :key-fn keyword))))

(defn- api-current-image
  [camera-name]
  {:pre [(string? camera-name)]}
  (let [{:keys [status body]} (api-request (str "currentimage?name=" camera-name)
                                           {:as :byte-array})]
    (when (= 200 status) body)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- site->feature [{:keys [name site position update_time]}]
  {:type       "Feature"
   :geometry   {:type        "Point"
                :coordinates [(:longitude site) (:latitude site)]}
   :properties {:latitude    (:latitude site)
                :longitude   (:longitude site)
                :name        name
                :pan         (:pan position)
                :tilt        (:tilt position)
                :update-time update_time}})

(defn- ->feature-collection [features]
  {:type     "FeatureCollection"
   :features features})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-cameras
  "Builds a GeoJSON response of the current wildfire cameras."
  []
  (data-response (if (valid-cache?)
                   @camera-cache
                   (let [new-cameras (->> (api-all-cameras)
                                          (pmap site->feature)
                                          (->feature-collection))]
                     (reset-cache! new-cameras)
                     new-cameras))))

(defn get-camera-time
  "Gets the last time a specific camera was updated (in UTC)."
  [camera-name]
  {:pre [(string? camera-name)]}
  (data-response (-> (api-get-camera camera-name)
                     (first)
                     (:update_time))))

(defn get-current-image
  "Builds a response object with current image of a camera.
   Response type is an 'image/jpeg'"
  [camera-name]
  {:pre [(string? camera-name)]}
  (data-response (api-current-image camera-name) {:type "image/jpeg"}))
