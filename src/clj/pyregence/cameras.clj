(ns pyregence.cameras
  (:import  [java.net URI]
            [java.time Instant Duration])
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
(def ^:private faa-sites-api-url (str (get-faa-proxy-url) faa-sites-api-path))

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
;;        :camera-image-uris {map of proxied image URIs, keyed by camera ID string}}}
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

;; TODO - remove once get-and-conform-faa-cameras! is replaced by the FAA Cameras section
(declare faa-camera->feature)

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
  and return the response, whatever its status."
  []
  (client/get faa-sites-api-url {:throw-exceptions false}))

(defn- faa-response->sites-collection
  "Extract the list of sites from a successful response,
  or return nil and log on a failed request."
  [{:keys [status body] :as response}]
  (if (= 200 status)
    (let [sites (try
                  (:payload (json/read-str body :key-fn keyword))
                  (catch Exception e
                    (log-str "ERROR: Unable to read JSON response from FAA cameras API.")))]
      sites)
    (log-str "WARN: request to FAA cameras api failed with status " status)))

(defn- faa-site-usable?
  "True for sites that are active and not under maintenance."
  [{:keys [siteActive siteInMaintenance]}]
  (and siteActive (not siteInMaintenance)))

(defn- faa-camera-usable?
  "True for cameras that are in service and have a current image on the FAA static host."
  [{:keys [cameraInMaintenance cameraOutOfOrder currentImageUri]}]
  (and (not cameraInMaintenance)
       (not cameraOutOfOrder)
       (string? currentImageUri)
       (str/starts-with? currentImageUri faa-images-url)))

(defn- faa-prune-sites
  "Remove unusable sites, and unusable cameras from the remaining sites."
  [sites]
  (->> sites
       (filter faa-site-usable?)
       (map #(update % :cameras (partial filter faa-camera-usable?)))))

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

(defn- faa-sites->features-collection
  "Extract a collection of Features from a list of FAA camera sites."
  [sites]
  (mapcat (fn [site]
            (map #(faa-camera->feature site %)
                 (:cameras site)))
          sites))

(defn- faa-image-uri->proxy-uri
  "Rewrite an FAA static image URI to the proxy's /images/ path."
  [image-uri]
  (str (get-faa-proxy-url) "/images/" (subs image-uri (count faa-images-url))))

(defn- faa-sites->camera-image-uris
  "Map each FAA camera ID (as a string) to its current image URI on the proxy."
  [sites]
  (into {}
        (for [{:keys [cameras]}                  sites
              {:keys [cameraId currentImageUri]} cameras]
          [(str cameraId) (faa-image-uri->proxy-uri currentImageUri)])))

(defn- faa-get-camera-features!
  "Fetches usable cameras from the FAA api. Returns a map of :features, the cameras
  formatted as features, and :camera-image-uris, their proxied image URIs."
  []
  (let [sites (-> (faa-make-sites-api-request!)
                  (faa-response->sites-collection)
                  (faa-prune-sites))]
    {:features          (vec (faa-sites->features-collection sites))
     :camera-image-uris (faa-sites->camera-image-uris sites)}))

(defn- faa-get-camera-features-cached!
  "Returns FAA camera features and image URIs if they're cached; fetches them otherwise.
  Result is a map of :features, :camera-image-uris and :cache, a description of
  the cache outcome: \"hit: <timestamp>\", \"miss\", \"stale: <timestamp>\" or \"error\"."
  []
  (or (when-let [{:keys [timestamp] :as cached} (source-cache-get :faa)]
        (-> (select-keys cached [:features :camera-image-uris])
            (assoc :cache (str "hit: " (Instant/ofEpochMilli timestamp)))))
      (when-let [fetched (try
                           (let [{:keys [features] :as fetched} (faa-get-camera-features!)]
                             (when (seq features) fetched))
                           (catch Exception e
                             (log-str "ERROR: Unable to fetch FAA cameras: " (ex-message e))
                             nil))]
        (source-cache-reset! :faa fetched)
        (assoc fetched :cache "miss"))
      ;; Fetch failed or returned nothing, fall back to the stale cache
      (when-let [{:keys [timestamp] :as cached} (get @camera-source-cache :faa)]
        (-> (select-keys cached [:features :camera-image-uris])
            (assoc :cache (str "stale: " (Instant/ofEpochMilli timestamp)))))
      {:features [] :camera-image-uris {} :cache "error"}))

(defn- faa-fetch-current-image!
  "Fetches the latest image of an FAA camera through the proxy, using the
  image URI cached alongside the camera features. Returns a map of:
  - :image, the image bytes, or nil for unknown cameras or failed requests
  - :cache, the features cache outcome the image URI came from
  - :uri, the proxied image URI, or nil for unknown cameras
  - :proxy-status, the proxy's response status, or nil if it wasn't called"
  [camera-id]
  (let [{:keys [camera-image-uris cache]} (faa-get-camera-features-cached!)
        image-uri                         (get camera-image-uris camera-id)
        {:keys [status body]}             (when image-uri
                                            (client/get image-uri
                                                        {:as :byte-array
                                                         :throw-exceptions false}))]
    (when (and image-uri (not= 200 status))
      (log-str "WARN: request for FAA camera "
               camera-id
               " image failed with status "
               status))
    {:image        (when (= 200 status) body)
     :cache        cache
     :uri          image-uri
     :proxy-status status}))

(defn- faa-build-image-response
  "Build the response for an FAA camera image from the result of
  faa-fetch-current-image!. Diagnostic headers tell where a failure came from:
  X-Image-Uri is missing when the camera isn't in the cached features, and
  X-Image-Proxy-Status when the proxy wasn't called."
  [{:keys [image cache uri proxy-status]}]
  (let [response (if image
                   (data-response image {:type "image/jpeg"})
                   (data-response "FAA camera image not available." {:status 404}))]
    (cond-> (assoc-in response [:headers "X-Features-Cache"] (str "faa=" cache))
      uri          (assoc-in [:headers "X-Image-Uri"] (.getPath (URI. uri)))
      proxy-status (assoc-in [:headers "X-Image-Proxy-Status"] (str proxy-status)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request Handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-cameras
  "Handle a get-cameras request. Return a FeatureCollection that merges
  the cameras from all available sources"
  [_]
  (let [{faa-features :features faa-cache :cache} (faa-get-camera-features-cached!)]
    (-> (data-response {:type     "FeatureCollection"
                        :features faa-features})
        (assoc-in [:headers "X-Features-Cache"] (str "faa=" faa-cache)))))

(defn get-current-image
  "Builds a response object with current image of a camera.
   Response type is an 'image/jpeg'"
  [_ camera-id api-name]
  {:pre [(string? camera-id)]}
  (case api-name
    "alert-west" (data-response (api-current-image camera-id alert-west-api-url (get-alert-west-api-defaults))
                                {:type "image/jpeg"})
    "faa"        (if-not (re-matches #"\d+" camera-id)
                   (data-response "Invalid FAA camera ID." {:status 403})
                   (faa-build-image-response (faa-fetch-current-image! camera-id)))
    (data-response "Invalid cameras API name." {:status 403})))
