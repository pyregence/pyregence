(ns pyregence.cameras
  (:import  [java.time Instant Duration])
  (:require [clj-http.client     :as client]
            [clojure.data.json   :as json]
            [triangulum.config   :refer [get-config]]
            [triangulum.response :refer [data-response]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; AlertWildfire Cameras
;; See API documentation: http://dev-public.nvseismolab.org/doc/api/firecams/
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private alert-west-api-url "https://alertwest.live/api/firecams/v0")

(defn- get-alert-west-api-defaults []
  {:headers {"X-Api-Key" (get-config ::alert-west-api-key)}})

(def ^:private cache-max-age            (* 24 60 1000)) ; Once a day

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Camera cache
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^:private camera-cache (atom nil))
(defonce ^:private cached-time  (atom nil))

(defn- reset-cache! [cameras]
  (reset! cached-time (System/currentTimeMillis))
  (reset! camera-cache cameras))

(defn- valid-cache? []
  (and @camera-cache
       (< (- (System/currentTimeMillis) @cached-time) cache-max-age)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- api-request [path api-url api-defaults & [opts]]
  (client/get (str api-url "/" path) ; URL
              (merge api-defaults opts)))

(defn- api-all-cameras [api-url api-defaults]
  (let [{:keys [status body]} (api-request "cameras" api-url api-defaults)]
    (when (= 200 status)
      (json/read-str body :key-fn keyword))))

(defn- api-current-image [camera-name api-url api-defaults]
  (let [{:keys [status body]} (api-request (str "currentimage?name=" camera-name)
                                           api-url
                                           api-defaults
                                           {:as :byte-array})]
    (when (= 200 status) body)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- parse-num [v]
  (if (string? v)
    (Double/parseDouble v)
    v))

(defn- site->feature [api-name {:keys [name site position image update_time]}]
  {:type       "Feature"
   :geometry   {:type        "Point"
                :coordinates [(parse-num (:longitude site))
                              (parse-num (:latitude site))]}
   :properties {:api-name    api-name
                :latitude    (parse-num (:latitude site))
                :longitude   (parse-num (:longitude site))
                :name        name
                :pan         (parse-num (:pan position))
                :state       (:state site)
                :tilt        (parse-num (:tilt position))
                :update-time (or update_time (:time image))
                :image-url   (:url image)}})

(defn- ->feature-collection [features]
  {:type     "FeatureCollection"
   :features features})

;; Timestamp functions

(defn- utc-timestamp->four-hours-old?
  "Returns true if the UTC timestamp is older than four hours."
  [timestamp]
  (let [timestamp      (Instant/parse timestamp)
        now            (Instant/now)
        four-hours-ago (.minus now (Duration/ofHours 4))]
    (.isBefore timestamp four-hours-ago)))

;; Camera functions

(defn- get-wildfire-cameras!
  []
  (api-all-cameras alert-west-api-url (get-alert-west-api-defaults)))

(defn- get-and-conform-wildfire-cameras!
  "Fetches ALERTWest wildfire cameras and reformats them."
  []
  (some->> (get-wildfire-cameras!)
           (pmap #(site->feature "alert-west" %))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-cameras
  "Builds a GeoJSON response of the current wildfire cameras."
  [_]
  (data-response
   (if (valid-cache?)
     @camera-cache
     (let [new-cameras (->> (get-and-conform-wildfire-cameras!)
                            ;; remove inactive cameras
                            (remove (fn [{{timestamp :update-time} :properties}]
                                      (or (nil? timestamp)
                                          (utc-timestamp->four-hours-old? timestamp))))
                            (->feature-collection))]
       (reset-cache! new-cameras)
       new-cameras))))

(defn get-current-image
  "Builds a response object with current image of a camera.
   Response type is an 'image/jpeg'"
  [_ camera-name api-name]
  {:pre [(string? camera-name)]}
  (let [[api-url api-defaults] (case api-name
                                 "alert-west" [alert-west-api-url (get-alert-west-api-defaults)]
                                 nil)]
    (if (and api-url api-defaults)
      (data-response (api-current-image camera-name api-url api-defaults)
                     {:type "image/jpeg"})
      (data-response (str "Invalid cameras API name.")
                     {:status 403}))))
