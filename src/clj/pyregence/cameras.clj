(ns pyregence.cameras
  (:require [clj-http.client    :as client]
            [clojure.data.json  :as json]
            [pyregence.views    :refer [data-response]]
            [triangulum.config  :refer [get-config]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; AlertWildfire Cameras
;; See API documentation: http://dev-public.nvseismolab.org/doc/api/firecams/
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private alert-wildfire-api-url      "https://data.alertwildfire.org/api/firecams/v0")
(def ^:private alert-wildfire-api-key      (get-config :cameras :alert-wildfire-api-key))
(def ^:private alert-wildfire-api-defaults {:headers {"X-Api-Key" alert-wildfire-api-key}})

(def ^:private alert-california-api-url      "https://data.alertcalifornia.org/api/firecams/v0")
(def ^:private alert-california-api-key      (get-config :cameras :alert-california-api-key))
(def ^:private alert-california-api-defaults {:headers {"X-Api-Key" alert-california-api-key}})

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

(defn- site->feature [api-name {:keys [name site position update_time]}]
  {:type       "Feature"
   :geometry   {:type        "Point"
                :coordinates [(:longitude site) (:latitude site)]}
   :properties {:api-name    api-name
                :latitude    (:latitude site)
                :longitude   (:longitude site)
                :name        name
                :pan         (:pan position)
                :state       (:state site)
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
                   (let [alert-wildfire-cameras   (->> (api-all-cameras alert-wildfire-api-url
                                                                        alert-wildfire-api-defaults)
                                                       (pmap #(site->feature "alert-wildfire" %))
                                                       ;; Alert Wildfire API does **not** work for California cameras
                                                       (filter #(not= (get-in % [:properties :state]) "CA")))
                         alert-california-cameras (->> (api-all-cameras alert-california-api-url
                                                                        alert-california-api-defaults)
                                                       (pmap #(site->feature "alert-california" %)))
                         new-cameras              (-> alert-wildfire-cameras
                                                      (concat alert-california-cameras)
                                                      (->feature-collection))]
                     (reset-cache! new-cameras)
                     new-cameras))))

(defn get-current-image
  "Builds a response object with current image of a camera.
   Response type is an 'image/jpeg'"
  [camera-name api-name]
  {:pre [(string? camera-name)]}
  (let [[api-url api-defaults] (case api-name
                                 "alert-wildfire"   [alert-wildfire-api-url alert-wildfire-api-defaults]
                                 "alert-california" [alert-california-api-url alert-california-api-defaults]
                                 nil)]
    (if (and api-url api-defaults)
      (data-response (api-current-image camera-name api-url api-defaults)
                     {:type "image/jpeg"})
      (data-response (str "Invalid cameras API name.")
                     {:status 403}))))
