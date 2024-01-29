(ns pyregence.cameras
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

(def ^:private alert-wildfire-api-url      "https://data.alertwildfire.org/api/firecams/v0")
(def ^:private alert-wildfire-api-key      (get-config :pyregence.cameras/alert-wildfire-api-key))
(def ^:private alert-wildfire-api-defaults {:headers {"X-Api-Key" alert-wildfire-api-key}})

(def ^:private alert-california-api-url      "https://data.alertcalifornia.org/api/firecams/v0")
(def ^:private alert-california-api-key      (get-config :pyregence.cameras/alert-california-api-key))
(def ^:private alert-california-api-defaults {:headers {"X-Api-Key" alert-california-api-key}})

(def ^:private cache-max-age            (* 24 60 1000)) ; Once a day

(def ^:private california-cameras-to-keep #{"Axis-AlderHill"
                                            "Axis-Alpine"
                                            "Axis-ArmstrongLookout1"
                                            "Axis-ArmstrongLookout2"
                                            "Axis-Babbitt"
                                            "Axis-BaldCA"
                                            "Axis-BaldCA2"
                                            "Axis-BigHill"
                                            "Axis-Bunker"
                                            "Axis-Emerald"
                                            "Axis-CTC"
                                            "Axis-FallenLeaf"
                                            "Axis-FortSage"
                                            "Axis-GoldCountry"
                                            "Axis-HawkinsPeak"
                                            "Axis-Heavenly2"
                                            "Axis-Homewood1"
                                            "Axis-Homewood2"
                                            "Axis-KennedyMine"
                                            "Axis-Leek"
                                            "Axis-Martis"
                                            "Axis-MohawkEsmeralda"
                                            "Axis-Montezuma"
                                            "Axis-MtDanaher"
                                            "Axis-MtZion1"
                                            "Axis-MtZion2"
                                            "Axis-NorthMok"
                                            "Axis-Pepperwood1"
                                            "Axis-QueenBee"
                                            "Axis-RedCorral"
                                            "Axis-RedCorral2"
                                            "Axis-Rockland"
                                            "Axis-Rockpile"
                                            "Axis-Sagehen5"
                                            "Axis-Sierra"
                                            "Axis-TahoeDonner"
                                            "Axis-TVHill"
                                            "Axis-WestPoint"
                                            "Axis-Winters1"
                                            "Axis-Winters2"
                                            "Axis-Konocti"
                                            "Axis-StHelenaNorth"
                                            "Axis-PrattMtn2"
                                            "Axis-PrattMtn1"
                                            "Axis-PierceMtn2"
                                            "Axis-PierceMtn1"})

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

(defn- site->feature [api-name {:keys [name site position image update_time]}]
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
                :update-time update_time
                :image-url   (:url image)}})

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
                                                       ;; The Alert Wildfire API does **not** work for most California cameras so
                                                       ;; we filter out all California Alert Wildfire cameras besides a predefined list
                                                       (filter (fn [camera]
                                                                 (let [camera-properites (:properties camera)]
                                                                   (or (california-cameras-to-keep (:name camera-properites))
                                                                       (not= (:state camera-properites) "CA"))))))
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
