(ns pyregence.cameras
  (:import  [java.time Instant Duration])
  (:require [clj-http.client     :as client]
            [clojure.data.json   :as json]
            [clojure.string      :as str]
            [triangulum.config   :refer [get-config]]
            [triangulum.logging  :refer [log-str]]
            [triangulum.response :refer [data-response]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Wildfire Cameras
;; ALERTWest: http://dev-public.nvseismolab.org/doc/api/firecams/
;; FAA WeatherCams: https://weathercams.faa.gov/, reached through an
;; nginx proxy that adds the API token and caches responses.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private alert-west-api-url "https://alertwest.live/api/firecams/v0")

(defn- get-alert-west-api-defaults []
  {:headers {"X-Api-Key" (get-config ::alert-west-api-key)}})

(defn- get-faa-proxy-url []
  (get-config ::faa-proxy-url))
(def ^:private faa-sites-api-path "/api/redistributable/sites")
(def ^:privaate faa-sites-api-url (str (get-faa-proxy-url) faa-sites-api-path))

(def ^:private faa-images-url "https://images.wcams-static.faa.gov/")

(def ^:private cache-max-age            (* 24 60 1000)) ; Once a day

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Camera cache
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Per-source (eg faa, alert-west) camera cache
;; Each entry includes the last fetch timestamp,
;; and all the contents relevant to the source

;; Structure:
;; {:faa {:timestamp (System/currentTimeMillis)
;;        :features [vector of Features]
;;        :cameraImageURIs {map of processed image URIs, keyed by camera ID}}}
(defonce ^:private camera-source-cache (atom {}))

;; Per-source cache TTL, in seconds
(def ^:private cache-ttls {:faa 600; 10 minutes
                           :alert-west 1800}); 30 minutes, similar to previous 24 minutes

(defn- source-cache-valid?
  "Checks if a source cache is valid according to its TTL and timestamp."
  [source-id source-cache]
  (let [source-ttl (get cache-ttls source-id)
        cache-timestamp (:timestamp source-cache)]
    (and (int? source-ttl)
         (int? cache-timestamp)
         (< (- (System/currentTimeMillis) cache-timestamp)
            (* 1000 source-ttl)))))

(defn- source-cache-get
  "Gets the cache map for a camera source keyword, eg :alert-west.
  nil if cache is invalid or does not exist."
  [source-id]
  (let [cache @camera-source-cache
        source-cache (get cache source-id)
        is-valid? (source-cache-valid? source-id source-cache)]
    (if is-valid?
      source-cache
      nil)))

(defn- source-cache-reset!
  "Reset the cache entry for a given source id.
  - source-id: keyword eg :alert-west
  - values: a map of the values to cache"
  [source-id values]
  (when (and values (map? values))
    (let [new-source-cache (conj values
                                 {:timestamp (System/currentTimeMillis)})]
      (swap! camera-source-cache assoc source-id new-source-cache))))




;; TODO - remove once sources are moved to the new cache

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

(defn- faa-api-request [path]
  (let [{:keys [status body]} (api-request path (str (get-faa-proxy-url) "/api") {})]
    (when (= 200 status)
      (:payload (json/read-str body :key-fn keyword)))))

(defn- faa-current-image
  "Fetches the latest image of an FAA camera through the proxy. Only image URLs
   on the FAA static host are fetched, rewritten to the proxy's /images/ path."
  [camera-id]
  (when-let [image-uri (some-> (faa-api-request (str "cameras/" camera-id "/images/last/1"))
                               (first)
                               (:imageUri))]
    (when (str/starts-with? image-uri faa-images-url)
      (let [{:keys [status body]} (client/get (str (get-faa-proxy-url)
                                                   "/images/"
                                                   (subs image-uri (count faa-images-url)))
                                              {:as :byte-array})]
        (when (= 200 status) body)))))

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
                :camera-id   name
                :latitude    (parse-num (:latitude site))
                :longitude   (parse-num (:longitude site))
                :name        name
                :pan         (parse-num (:pan position))
                :state       (:state site)
                :tilt        (parse-num (:tilt position))
                :update-time (or update_time (:time image))
                :image-url   (:url image)}})

(defn- faa-camera->feature
  [{:keys [siteName state]}
   {:keys [cameraId cameraDirection cameraBearing cameraLastSuccess latitude longitude]}]
  {:type       "Feature"
   :geometry   {:type        "Point"
                :coordinates [longitude latitude]}
   :properties {:api-name    "faa"
                :camera-id   (str cameraId)
                :latitude    latitude
                :longitude   longitude
                :name        (str siteName " (" cameraDirection ")")
                :pan         cameraBearing
                :state       state
                :tilt        0
                :update-time cameraLastSuccess}})

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

(defn- get-and-conform-faa-cameras!
  "Fetches FAA WeatherCams cameras and reformats them, skipping inactive sites
   and cameras under maintenance or out of order."
  []
  (some->> (faa-api-request "sites")
           (remove #(or (:siteInMaintenance %) (not (:siteActive %))))
           (mapcat (fn [site]
                     (->> (:cameras site)
                          (remove #(or (:cameraInMaintenance %) (:cameraOutOfOrder %)))
                          (map #(faa-camera->feature site %)))))))

(defn- get-all-cameras!
  "Fetches every configured camera source in parallel. A source that fails
   yields nil instead of taking the others down with it."
  []
  (pmap (fn [[source-name get-cameras!]]
          (try
            (get-cameras!)
            (catch Exception e
              (log-str "Failed to fetch " source-name " cameras: " (ex-message e))
              nil)))
        (cond-> [["ALERTWest" get-and-conform-wildfire-cameras!]]
          (get-faa-proxy-url) (conj ["FAA" get-and-conform-faa-cameras!]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FAA Cameras
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- faa-make-sites-api-request!
  "Make a synchronous request to the FAA camera sites API,
  and return the response."
  []
  (client/get faa-sites-api-url))

(defn- faa-response->sites-collection
  "Extract the list of sites from a successful response,
  or return nil and log on a failed request. Remove sites
  that are inactive or have no working cameras."
  [{:keys [status body] :as response}]
  (if (= 200 status)
    (let [sites (try
                  (:payload (json/read-str body :key-fn keyword))
                  (catch Exception e
                    (log-str "ERROR: Unable to read JSON response from FAA cameras API.")))]
      sites)
    (log-str "WARN: request to FAA cameras api failed with status " status)))

(defn- faa-camera->feature
  "Map a camera item from the FAA api into a map feature."
  [{:keys [siteName state]}
   {:keys [cameraId cameraDirection cameraBearing cameraLastSuccess latitude longitude]}]
  {:type       "Feature"
   :geometry   {:type        "Point"
                :coordinates [longitude latitude]}
   :properties {:api-name    "faa"
                :camera-id   (str cameraId)
                :latitude    latitude
                :longitude   longitude
                :name        (str siteName " (" cameraDirection ")")
                :pan         cameraBearing
                :state       state
                :tilt        0
                :update-time cameraLastSuccess}})

(defn- faa-response->features-collection
  "Extract a collection of Features from a list of FAA camera sites."
  [response]
  (->> (faa-response->sites-collection response)
       (mapcat (fn [site]
                 (map #(faa-camera->feature site %)
                 (:cameras site))))))

(defn- faa-get-camera-features!
  "Fetches cameras from the FAA api and formats them as features."
  []
  (-> (faa-make-sites-api-request!)
      (faa-response->features-collection)))

(defn- faa-get-camera-features-cached!
  "Returns FAA camera features if they're cached; fetches them otherwise.
  Result is a map of :features and :cache (:hit, :miss, :stale or :error)."
  []
  (or (when-let [features (:features (source-cache-get :faa))]
        {:features features :cache :hit})
      (when-let [features (try
                            (not-empty (vec (faa-get-camera-features!)))
                            (catch Exception e
                              (log-str "ERROR: Unable to fetch FAA cameras: " (ex-message e))
                              nil))]
        (source-cache-reset! :faa {:features features})
        {:features features :cache :miss})
      ;; Fetch failed or returned nothing, fall back to the stale cache
      (when-let [features (:features (get @camera-source-cache :faa))]
        {:features features :cache :stale})
      {:features [] :cache :error}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request Handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-cameras
  "Handle a get-cameras request. Return a FeatureCollection that merges
  the cameras from all available sources"
  [_]
  (let [{faa-features :features faa-cache :cache} (faa-get-camera-features-cached!)]
    (data-response {:type     "FeatureCollection"
                    :features faa-features
                    :cache    {:faa faa-cache}})))

(defn get-current-image
  "Builds a response object with current image of a camera.
   Response type is an 'image/jpeg'"
  [_ camera-id api-name]
  {:pre [(string? camera-id)]}
  (case api-name
    "alert-west" (data-response (api-current-image camera-id alert-west-api-url (get-alert-west-api-defaults))
                                {:type "image/jpeg"})
    "faa"        (if (re-matches #"\d+" camera-id)
                   (data-response (faa-current-image camera-id)
                                  {:type "image/jpeg"})
                   (data-response "Invalid FAA camera ID." {:status 403}))
    (data-response "Invalid cameras API name." {:status 403})))
