(ns pyregence.weather-stations-new
  (:import  [java.time Instant])
  (:require [triangulum.config   :refer [get-config]]
            [triangulum.logging  :refer [log-str]]
            [triangulum.response :refer [data-response]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Weather Stations
;; US: National Weather Service API, https://www.weather.gov/documentation/services-web-api
;; CA: Environment and Climate Change Canada, REST API at https://api.weather.gc.ca
;;     and SWOB XML files at https://dd.weather.gc.ca (fill-in for observations
;;     the REST API doesn't return).
;;
;; Station lists (map points) take minutes to fetch, so background loops
;; refresh them; request handlers only read the cache. Observations are
;; fetched when a station is clicked, and cached for a short time.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Config
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-source-config
  "The config map of a source (:us or :ca)."
  [source-id]
  (get (get-config :pyregence.weather-stations/sources) source-id))

(defn- get-retry-after-failure-ms
  "How long a refresh loop waits after a failed refresh, instead of its full interval."
  []
  (* 1000 60 (get-config :pyregence.weather-stations/retry-after-failure-minutes)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Source cache
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Per-source station lists and indexes, each replaced wholesale by its
;; refresh loop. A failed refresh keeps the previous value and adds :failed-at.
;;
;; Structure:
;; {:us           {:timestamp ms
;;                 :features  [vector of Features]
;;                 :stations  {station-id {station details}}}
;;  :ca           {:timestamp ms
;;                 :features  [vector of Features]
;;                 :stations  {msc-id {station details, including what the XML path needs}}}
;;  :ca-xml-index {:timestamp ms
;;                 :partner-folders {msc-id {:network :folder}}}}
(defonce ^:private source-cache (atom {}))

(defn- source-cache-status
  "Describes a source cache entry for the diagnostic headers:
  - \"hit: <timestamp>\" when the last refresh succeeded
  - \"stale: <timestamp>\" when the last refresh failed and the previous value is served
  - \"error: <failed-at>\" when the last refresh failed and there's no previous value
  - \"empty\" before the first refresh finishes"
  [{:keys [timestamp failed-at]}]
  (cond
    (and timestamp failed-at) (str "stale: " (Instant/ofEpochMilli timestamp))
    timestamp                 (str "hit: " (Instant/ofEpochMilli timestamp))
    failed-at                 (str "error: " (Instant/ofEpochMilli failed-at))
    :else                     "empty"))

(defn- refresh-source!
  "Calls fetch! and replaces the source's cache entry with its result.
  A result without :features or :partner-folders counts as a failure. On
  failure, keeps the previous value and records :failed-at. Returns true when
  the refresh succeeded."
  [source-id fetch!]
  (try
    (let [values (fetch!)]
      (if (or (seq (:features values)) (seq (:partner-folders values)))
        (do (swap! source-cache assoc source-id (assoc values :timestamp (System/currentTimeMillis)))
            (log-str "Weather stations: refreshed " (name source-id))
            true)
        (do (log-str "WARN: Weather stations: refreshing " (name source-id) " returned nothing")
            (swap! source-cache assoc-in [source-id :failed-at] (System/currentTimeMillis))
            false)))
    ;; Stopping the worker interrupts the loop's thread. Rethrow, so the loop
    ;; ends instead of logging the interruption as a failed refresh.
    (catch InterruptedException e
      (throw e))
    (catch Exception e
      (log-str "ERROR: Weather stations: unable to refresh " (name source-id) ": " (ex-message e))
      (swap! source-cache assoc-in [source-id :failed-at] (System/currentTimeMillis))
      false)))

(defn- start-refresh-loop!
  "Starts a background loop that refreshes a source every refresh-minutes, or
  sooner (retry-after-failure-minutes) after a failure. Returns its future."
  [source-id refresh-minutes fetch!]
  (future
    (loop []
      (let [refreshed? (refresh-source! source-id fetch!)]
        (Thread/sleep (if refreshed?
                        (* 1000 60 refresh-minutes)
                        (get-retry-after-failure-ms)))
        (recur)))))

(defn- get-refresh-loops
  "The background loops: the source cache entry each one fills, how often,
  and the function that fetches its value."
  []
  [])

(defn start-refresh-loops!
  "Starts one refresh loop per source. Returns their futures, for stop-refresh-loops!."
  []
  (mapv (fn [{:keys [source-id refresh-minutes fetch!]}]
          (start-refresh-loop! source-id refresh-minutes fetch!))
        (get-refresh-loops)))

(defn stop-refresh-loops!
  "Stops the refresh loops started by start-refresh-loops!."
  [futures]
  (run! future-cancel futures))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Observation cache
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Latest observations, keyed by [source-id station-id]. The station id is
;; stationIdentifier for :us and msc_id for :ca. Only successful fetches are
;; cached, so the cache is bounded by the number of stations.
;;
;; Structure:
;; {[:us "KSFO"] {:timestamp   ms
;;                :observation {:station {:name :id :timestamp} :observations {...}}
;;                :via         "rest" or "xml"
;;                ...other diagnostics from the source's fetch-observation!}}
(defonce ^:private observation-cache (atom {}))

(defn- observation-fresh?
  "True when a cached observation is younger than ttl-seconds at time now (ms)."
  [{:keys [timestamp]} ttl-seconds now]
  (and (int? timestamp)
       (< (- now timestamp) (* 1000 ttl-seconds))))

(defn- get-observation-cached!
  "Returns the cached observation of a station when it's fresh, and calls
  fetch-observation! otherwise. fetch-observation! returns a map of
  :observation (nil when there's none) plus diagnostics (:via,
  :upstream-status, :xml-path). When the fetch has no observation, falls back
  to the stale cached one. The result is that map plus :cache: \"hit:
  <timestamp>\", \"miss\", or \"stale: <timestamp>\".
  The station id is stationIdentifier for :us and msc_id for :ca."
  [source-id station-id fetch-observation!]
  (let [cache-key   [source-id station-id]
        cached      (get @observation-cache cache-key)
        ttl-seconds (:observation-ttl-seconds (get-source-config source-id))
        now         (System/currentTimeMillis)]
    (if (observation-fresh? cached ttl-seconds now)
      (-> (dissoc cached :timestamp)
          (assoc :cache (str "hit: " (Instant/ofEpochMilli (:timestamp cached)))))
      (let [fetched (try
                      (fetch-observation!)
                      (catch Exception e
                        (log-str "ERROR: Weather stations: unable to fetch the observation of "
                                 (name source-id) " station " station-id ": " (ex-message e))
                        {:upstream-status (str "error: " (ex-message e))}))]
        (cond
          (:observation fetched)
          (do (swap! observation-cache assoc cache-key (assoc fetched :timestamp now))
              (assoc fetched :cache "miss"))

          cached
          (-> (dissoc cached :timestamp)
              (assoc :cache           (str "stale: " (Instant/ofEpochMilli (:timestamp cached)))
                     :upstream-status (:upstream-status fetched)))

          :else
          (assoc fetched :cache "miss"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Responses
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- build-points-cache-header
  "The X-Points-Cache header value, eg \"us=hit: <ts>, ca=stale: <ts>, ca-xml-index=empty\"."
  [source-cache-value]
  (->> [:us :ca :ca-xml-index]
       (map #(str (name %) "=" (source-cache-status (get source-cache-value %))))
       (interpose ", ")
       (apply str)))

(defn- build-observation-response
  "Builds the response for an observation from the result of
  get-observation-cached!. Diagnostic headers tell where the observation came
  from, or why there's none."
  [{:keys [observation cache via upstream-status xml-path]}]
  (let [response (if observation
                   (data-response observation)
                   (data-response "Observation not available." {:status 404}))]
    (cond-> (assoc-in response [:headers "X-Observation-Cache"] cache)
      via             (assoc-in [:headers "X-Observation-Source"] via)
      upstream-status (assoc-in [:headers "X-Upstream-Status"] upstream-status)
      xml-path        (assoc-in [:headers "X-Xml-Path"] xml-path))))
