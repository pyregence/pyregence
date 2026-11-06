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

(def ^:private faa-images-url "https://images.wcams-static.faa.gov/")

;; Bound how long a stalled upstream can hold a request (and the page load
;; waiting on get-cameras), in milliseconds
(def ^:private http-timeouts {:connection-timeout 5000
                              :socket-timeout     5000})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Camera cache
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Per-source (eg faa, alert-west) camera cache
;; Each entry includes the last fetch timestamp,
;; and all the contents relevant to the source

;; Structure:
;; {:faa {:timestamp (System/currentTimeMillis)
;;        :features [vector of Features]
;;        :camera-image-uris {map of proxied image URIs, keyed by camera ID string}}
;;  :alert-west {:timestamp (System/currentTimeMillis)
;;               :features [vector of Features]}}
(defonce ^:private camera-source-cache (atom {}))

;; Per-source cache TTL, in seconds
(def ^:private cache-ttls {:faa 600; 10 minutes
                           :alert-west 1800}); 30 minutes, similar to previous 24 minutes

(defn- source-enabled?
  "FAA needs its proxy URL configured; other sources are always enabled."
  [source-id]
  (case source-id
    :faa (not (str/blank? (get-faa-proxy-url)))
    true))

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

(defn- get-source-features-cached!
  "Returns a camera source's cached values if they're valid; calls fetch-features!
  otherwise. fetch-features! returns a map of :features plus any other values the
  source needs cached. Result is that map plus :cache, a description of the cache
  outcome: \"hit: <timestamp>\", \"miss\", \"stale: <timestamp>\", \"error\", or
  \"disabled\" (with no features) for sources that aren't enabled."
  [source-id fetch-features!]
  (or (when-not (source-enabled? source-id)
        {:features [] :cache "disabled"})
      (when-let [{:keys [timestamp] :as cached} (source-cache-get source-id)]
        (-> (dissoc cached :timestamp)
            (assoc :cache (str "hit: " (Instant/ofEpochMilli timestamp)))))
      (when-let [fetched (try
                           (let [{:keys [features] :as fetched} (fetch-features!)]
                             (when (seq features) fetched))
                           (catch Exception e
                             (log-str "ERROR: Unable to fetch "
                                      (name source-id)
                                      " cameras: "
                                      (ex-message e))
                             nil))]
        (source-cache-reset! source-id fetched)
        (assoc fetched :cache "miss"))
      ;; Fetch failed or returned nothing, fall back to the stale cache
      (when-let [{:keys [timestamp] :as cached} (get @camera-source-cache source-id)]
        (-> (dissoc cached :timestamp)
            (assoc :cache (str "stale: " (Instant/ofEpochMilli timestamp)))))
      {:features [] :cache "error"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- parse-num [v]
  (if (string? v)
    (Double/parseDouble v)
    v))

(defn- uri->path-and-query
  "Strip the scheme and host from a URI, keeping its path and query string."
  [uri]
  (let [uri   (URI. uri)
        path  (.getRawPath uri)
        query (.getRawQuery uri)]
    (if query
      (str path "?" query)
      path)))

(defn- build-camera-image-response
  "Build the response for a camera image from the result of a source's
  fetch-current-image! function. Diagnostic headers tell where a failure came
  from: X-Image-Uri is missing when the camera isn't in the cached features,
  and X-Image-Upstream-Status when the upstream wasn't called."
  [source-id {:keys [image cache uri upstream-status]}]
  (let [response (if image
                   (data-response image {:type "image/jpeg"})
                   (data-response "Camera image not available." {:status 404}))]
    (cond-> (assoc-in response [:headers "X-Features-Cache"] (str (name source-id) "=" cache))
      uri             (assoc-in [:headers "X-Image-Uri"] (uri->path-and-query uri))
      upstream-status (assoc-in [:headers "X-Image-Upstream-Status"] (str upstream-status)))))

;; Timestamp functions

(defn- utc-timestamp->four-hours-old?
  "Returns true if the UTC timestamp is older than four hours."
  [timestamp]
  (let [timestamp      (Instant/parse timestamp)
        now            (Instant/now)
        four-hours-ago (.minus now (Duration/ofHours 4))]
    (.isBefore timestamp four-hours-ago)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ALERTWest Cameras
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- alert-west-make-cameras-api-request!
  "Make a synchronous request to the ALERTWest cameras API,
  and return the response, whatever its status."
  []
  (client/get (str alert-west-api-url "/cameras")
              (merge (get-alert-west-api-defaults)
                     http-timeouts
                     {:throw-exceptions false})))

(defn- alert-west-response->cameras-collection
  "Extract the list of cameras from a successful response,
  or return nil and log on a failed request."
  [{:keys [status body]}]
  (if (= 200 status)
    (try
      (json/read-str body :key-fn keyword)
      (catch Exception _
        (log-str "ERROR: Unable to read JSON response from ALERTWest cameras API.")))
    (log-str "WARN: request to ALERTWest cameras api failed with status " status)))

(defn- alert-west-camera-usable?
  "True for cameras with an update time in the last four hours."
  [{:keys [update_time image]}]
  (let [update-time (or update_time (:time image))]
    (and (some? update-time)
         (not (utc-timestamp->four-hours-old? update-time)))))

(defn- alert-west-prune-cameras
  "Remove unusable cameras."
  [cameras]
  (filter alert-west-camera-usable? cameras))

(defn- alert-west-camera->feature
  "Map a camera item from the ALERTWest api into a map feature."
  [{camera-name :name :keys [site position image update_time]}]
  {:type       "Feature"
   :geometry   {:type        "Point"
                :coordinates [(parse-num (:longitude site))
                              (parse-num (:latitude site))]}
   :properties {:api-name    "alert-west"
                :camera-id   camera-name
                :latitude    (parse-num (:latitude site))
                :longitude   (parse-num (:longitude site))
                :name        camera-name
                :pan         (parse-num (:pan position))
                :state       (:state site)
                :tilt        (parse-num (:tilt position))
                :update-time (or update_time (:time image))
                :image-url   (:url image)}})

(defn- alert-west-cameras->features-collection
  "Extract a collection of Features from a list of ALERTWest cameras."
  [cameras]
  (map alert-west-camera->feature cameras))

(defn- alert-west-get-camera-features!
  "Fetches usable cameras from the ALERTWest api. Returns a map of :features,
  the cameras formatted as features."
  []
  (let [cameras (-> (alert-west-make-cameras-api-request!)
                    (alert-west-response->cameras-collection)
                    (alert-west-prune-cameras))]
    {:features (vec (alert-west-cameras->features-collection cameras))}))

(defn- alert-west-fetch-current-image!
  "Fetches the latest image of an ALERTWest camera from its API, only for
  cameras in the cached features. Returns a map of:
  - :image, the image bytes, or nil for unknown cameras or failed requests
  - :cache, the features cache outcome the camera was looked up in
  - :uri, the image URI, or nil for unknown cameras
  - :upstream-status, the API's response status, or nil if it wasn't called"
  [camera-name]
  (let [{:keys [features cache]} (get-source-features-cached!
                                  :alert-west
                                  alert-west-get-camera-features!)
        known-camera?            (some #(= camera-name (get-in % [:properties :camera-id]))
                                       features)
        image-uri                (when known-camera?
                                   (str alert-west-api-url
                                        "/currentimage?"
                                        (client/generate-query-string {:name camera-name})))
        {:keys [status body]}    (when image-uri
                                   (client/get image-uri
                                               (merge (get-alert-west-api-defaults)
                                                      http-timeouts
                                                      {:as               :byte-array
                                                       :throw-exceptions false})))]
    (when (and image-uri (not= 200 status))
      (log-str "WARN: request for ALERTWest camera "
               camera-name
               " image failed with status "
               status))
    {:image           (when (= 200 status) body)
     :cache           cache
     :uri             image-uri
     :upstream-status status}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FAA Cameras
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- faa-make-sites-api-request!
  "Make a synchronous request to the FAA camera sites API,
  and return the response, whatever its status."
  []
  (client/get (str (get-faa-proxy-url) faa-sites-api-path)
              (merge http-timeouts {:throw-exceptions false})))

(defn- faa-response->sites-collection
  "Extract the list of sites from a successful response,
  or return nil and log on a failed request."
  [{:keys [status body]}]
  (if (= 200 status)
    (let [sites (try
                  (:payload (json/read-str body :key-fn keyword))
                  (catch Exception _
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

(defn- faa-fetch-current-image!
  "Fetches the latest image of an FAA camera through the proxy, using the
  image URI cached alongside the camera features. Returns a map of:
  - :image, the image bytes, or nil for unknown cameras or failed requests
  - :cache, the features cache outcome the image URI came from
  - :uri, the proxied image URI, or nil for unknown cameras
  - :upstream-status, the proxy's response status, or nil if it wasn't called"
  [camera-id]
  (let [{:keys [camera-image-uris cache]} (get-source-features-cached!
                                           :faa
                                           faa-get-camera-features!)
        image-uri                       (get camera-image-uris camera-id)
        {:keys [status body]}             (when image-uri
                                            (client/get image-uri
                                                        (merge http-timeouts
                                                               {:as               :byte-array
                                                                :throw-exceptions false})))]
    (when (and image-uri (not= 200 status))
      (log-str "WARN: request for FAA camera "
               camera-id
               " image failed with status "
               status))
    {:image           (when (= 200 status) body)
     :cache           cache
     :uri             image-uri
     :upstream-status status}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request Handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-cameras
  "Handle a get-cameras request. Return a FeatureCollection that merges
  the cameras from all available sources"
  [_]
  (let [faa        (get-source-features-cached! :faa faa-get-camera-features!)
        alert-west (get-source-features-cached! :alert-west alert-west-get-camera-features!)
        features   (concat (:features alert-west) (:features faa))]
    (-> (data-response {:type     "FeatureCollection"
                        :features features})
        (assoc-in [:headers "X-Features-Cache"]
                  (str "faa=" (:cache faa) ", alert-west=" (:cache alert-west))))))

(defn get-current-image
  "Builds a response object with current image of a camera.
   Response type is an 'image/jpeg'"
  [_ camera-id api-name]
  {:pre [(string? camera-id)]}
  (case api-name
    "alert-west" (build-camera-image-response
                  :alert-west
                  (alert-west-fetch-current-image! camera-id))
    "faa"        (build-camera-image-response :faa (faa-fetch-current-image! camera-id))
    (data-response "Invalid cameras API name." {:status 403})))
