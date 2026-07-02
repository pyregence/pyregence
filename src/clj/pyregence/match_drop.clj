(ns pyregence.match-drop
  (:require
   [clj-http.client            :as client]
   [clojure.data.json          :as json]
   [clojure.pprint             :as pp]
   [clojure.set                :refer [rename-keys]]
   [clojure.string             :as str]
   [pyregence.capabilities     :refer [layers layers-exist?]]
   [pyregence.utils            :as u]
   [triangulum.config          :refer [get-config]]
   [triangulum.database        :refer [call-sql sql-primitive]]
   [triangulum.logging         :refer [log-str]]
   [triangulum.response        :refer [data-response]]
   [triangulum.type-conversion :refer [clj->json json->clj]])
  (:import
   [java.time LocalDateTime ZoneId]
   [java.time.format DateTimeFormatter]))

;;==============================================================================
;; Static Data
;;==============================================================================

(defn- get-md-config [k]
  (get-config ::match-drop k))

(defn- get-md-configs [ks]
  (->> (for [k ks]
         [k (get-md-config k)])
       (into {})))

(def valid-md-fuel-versions
  #{"2.5.0" "2.4.0" "2.3.0" "2.2.0" "2.1.0" "1.4.0" "1.3.0" "1.0.5"})

(def default-fuel-version "2.5.0")

(def ^:private conus-bounds
  {:min-x -125.0 :min-y 25.0 :max-x -66.0 :max-y 50.0})

(defn- intersect-with-conus [min-x min-y max-x max-y]
  [(max min-x (:min-x conus-bounds))
   (max min-y (:min-y conus-bounds))
   (min max-x (:max-x conus-bounds))
   (min max-y (:max-y conus-bounds))])

(defn- fuel-version->workspace
  [fuel-version]
  (str "fuels-and-topography_landfire-" fuel-version))

(defn- get-fuel-layer-extent
  [fuel-version]
  (let [workspace (fuel-version->workspace fuel-version)]
    (->> (:shasta @layers)
         (filter #(= workspace (:workspace %)))
         (first)
         (:extent))))

(defn- point-within-extent?
  [lon lat [min-x min-y max-x max-y]]
  (when (and min-x min-y max-x max-y)
    (let [[min-x min-y max-x max-y] (intersect-with-conus (Double/parseDouble min-x)
                                                          (Double/parseDouble min-y)
                                                          (Double/parseDouble max-x)
                                                          (Double/parseDouble max-y))]
      (and (<= min-x lon max-x)
           (<= min-y lat max-y)))))

;;==============================================================================
;; Helper Functions
;;==============================================================================

(defn- parse-available-wx-dates
  "Pares the stdout from a call to `fuel_wx_ign.py`. The exact return structure of this
   call (at the time of this writing in June of 2023) is the following:

   sig-app@goshawk:~$ ssh sig-app@sierra 'cd /mnt/tahoe/elmfire/cloudfire && ./fuel_wx_ign.py --get_available_wx_times=True'
   Currently available weather times:
   historical- 2011-01-30 00:00 UTC to 2022-09-30 23:00 UTC
   forecast-   2023-06-04 18:00 UTC to 2023-06-21 12:00 UTC
   ```
   Note that even though the above avaialable forecast weather times go out two
   weeks, at the time of writing this function the `fuel_wx_ign.py` script has a
   limitation in that you can only specify a weather start time that is the current
   date. Thus, we manually change the portion of the below response \"2023-06-21\"
   to \"2023-06-07\".

   Example return:
   {:historical {:min-date-iso-str \"2011-01-30T00:00Z\"
                 :max-date-iso-str \"2022-09-30T23:00Z\"}
    :forecast   {:min-date-iso-str \"2023-06-04T00:00Z\"
                 :max-date-iso-str \"2023-06-07T18:00Z\"}

    NOTE: this function is dependent on the exact stdout that `fuel_wx_ign.py` provides.
    In the future, it would be better if the format was more standardized so
    we didn't have to use ugly Regex. For example, we assume that the times are
    always provided in UTC."
  [stdout]
  (try
    (let [historical-regex #"historical-\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}\s+UTC)\s+to\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}\s+UTC)"
          forecast-regex   #"forecast-\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}\s+UTC)\s+to\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}\s+UTC)"
          [_ historical-min historical-max] (re-find historical-regex stdout)
          [_ forecast-min _]                (re-find forecast-regex stdout)
          forecast-max                      (u/get-current-date-time-iso-string)
          ;; Turns a string of the format "2011-01-30 00:00 UTC" into an ISO string: "2011-01-30T00:00Z"
          utc-string->iso-string #(let [[date hours-minutes _] (str/split % #" ")]
                                    (str date "T" hours-minutes "Z"))]
      {:historical {:min-date-iso-str (utc-string->iso-string historical-min)
                    :max-date-iso-str (utc-string->iso-string historical-max)}
       :forecast   {:min-date-iso-str (utc-string->iso-string forecast-min)
                    :max-date-iso-str forecast-max}})
    (catch Exception e
      (log-str (str "Exception in parse-available-wx-dates: " e ". "))
      nil)))

;;==============================================================================
;; SQL Functions
;;==============================================================================

(defn- sql-result->job [result]
  (-> result
      (rename-keys {:match_job_id          :match-job-id
                    :match_job_uuid        :match-job-unique-id
                    :runway_job_id         :runway-job-id
                    :user_id               :user-id
                    :created_at            :created-at
                    :updated_at            :updated-at
                    :md_status             :md-status
                    :display_name          :display-name
                    :job_log               :job-log
                    :elmfire_done          :elmfire-done?
                    :dps_request           :dps-request
                    :elmfire_request       :elmfire-request
                    :geosync_request       :geosync-request
                    :geoserver_workspace   :geoserver-workspace})
      (update :dps-request json->clj)
      (update :elmfire-request json->clj)
      (update :geosync-request json->clj)))

(defn- get-match-job-from-match-job-id!
  "Returns a specific entry in the match_jobs table based on its (internal) match-job-id."
  [match-job-id]
  (when (integer? match-job-id)
    (some-> (call-sql "get_match_job" match-job-id)
            (first)
            (sql-result->job))))

(defn- get-match-job-from-uuid!
  "Returns a specific entry in the match_jobs table based on its public
   match-job-unique-id (uuid)."
  [match-job-unique-id]
  (when-let [uuid (u/->uuid match-job-unique-id)]
    (some-> (call-sql "get_match_job_by_uuid" uuid)
            (first)
            (sql-result->job))))

(defn- count-all-running-match-drops []
  (sql-primitive (call-sql "count_all_running_match_jobs")))

(defn- count-running-user-match-jobs [user-id]
  (sql-primitive (call-sql "count_running_user_match_jobs" user-id)))

(defn- initialize-match-job! [user-id]
  (sql-primitive (call-sql "initialize_match_job" user-id)))

(defn- update-match-job!
  "Updates any of the properties of a match job based on a match-job-id (required).
   Any keys that are not provided will not be updated in the DB."
  [{:keys [match-job-id
           runway-job-id
           md-status
           display-name
           message
           elmfire-done?
           dps-request
           elmfire-request
           geosync-request
           geoserver-workspace]}]
  {:pre [(some? match-job-id)]}
  (call-sql "update_match_job"
            match-job-id
            runway-job-id
            md-status
            display-name
            message
            elmfire-done?
            nil ; gridfire_done (unused, kept for SQL positional compat)
            (when dps-request (clj->json dps-request))
            (when elmfire-request (clj->json elmfire-request))
            nil ; gridfire_request (unused, kept for SQL positional compat)
            (when geosync-request (clj->json geosync-request))
            geoserver-workspace))

;;==============================================================================
;; Create Match Job Functions
;;==============================================================================

(defn- utc-date->epoch-s [s]
  (let [only-date-time (str/trim (first (str/split s #"UTC")))
        fmt            (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm")
        dt             (LocalDateTime/parse only-date-time fmt)]
    (/ (.toEpochMilli (.toInstant (.atZone dt  (ZoneId/of "UTC"))))
       1000)))

(defn- match-drop-args->body
  [match-job-id
   {:keys [ignition-time lat lon wx-type fuel-version]}
   {:keys [sig3-env]}]
  (let [model-time    (u/convert-date-string ignition-time) ; e.g. Turns "2022-12-01 18:00 UTC" into "20221201_180000"
        wx-start-time (u/round-down-to-nearest-hour model-time)
        fire-name     (str "md-" match-job-id)]
    {:network   :match-drop
     :arguments {:env                  sig3-env
                 :pyrc_fire_name       fire-name
                 :geoserver-workspace  (str "match-drop-forecast_" fire-name "_" model-time)
                 :pyrc_simulation_span {:pyrc_simspan_center_lon    lon
                                        :pyrc_simspan_center_lat    lat
                                        :pyrc_simspan_start_epoch_s (utc-date->epoch-s wx-start-time)}
                 :pyrc_ignition        {:pyrc_ignition_lon     lon
                                        :pyrc_ignition_lat     lat
                                        :pyrc_ignition_epoch_s (utc-date->epoch-s ignition-time)}
                 :pyrc_inputs          {:pyrc_fuel_source  "landfire"
                                        :pyrc_wx_type      wx-type
                                        :pyrc_fuel_version (or fuel-version default-fuel-version)}
                 :pyrc_sim_params      {:pyrc_sim_num_ensemble_members 200}}}))

(defn- submit-match-drop-job!
  "Requests a match-drop job from kubernetes"
  [params sig3-endpoint match-job-id]
  (let [match-drop-config                  (get-md-configs [:sig3-env])
        request                            (match-drop-args->body match-job-id params match-drop-config)
        api-url                            (format "%s/api/submit-job" sig3-endpoint)
        http-request                       {:body         (json/write-str request)
                                            :headers      {"sig-auth" (get-md-config :sig3-auth)}
                                            :content-type :json
                                            :accept       :json}
        _                                  (println "POST" api-url request)
        {:keys [body status] :as response} (client/post api-url http-request)]
    (if (= 200 status)
      {:match-drop-inputs (:arguments request)
       :job-id            (:job-id (json/read-str body :key-fn keyword))}
      (throw (ex-info (format "match-drop request failed with status %d" status)
                      {:request http-request :response response})))))

(defn- poll-job!
  [sig3-endpoint job-id]
  (let [{:keys [status body]} (client/get (format "%s/api/poll/%s" sig3-endpoint job-id)
                                          {:headers          {"sig-auth" (get-md-config :sig3-auth)}
                                           :socket-timeout   30000
                                           :conn-timeout     30000
                                           :throw-exceptions false})]
    (when-not (= 200 status)
      (throw (ex-info (format "poll-job! returned status %d" status) {:status status :body body})))
    (json/read-str body)))

(defn- prettify
  [edn]
  (with-out-str (pp/pprint edn)))

(defn calculate-transitions
  "Return a vector of state changes when compared with the current job-state.
   `order` is for sorting: control order of events.
   `step` is the step name in the match-drop network in sig3.
   When a step skips the pending phase (e.g. polling interval > step duration),
   a synthetic pending transition is emitted so STARTED always precedes DONE/FAILED."
  [state job-state match-job-id]
  (mapcat (fn build-transitions [[step {:strs [order pending success failure]}]]
            (let [{:strs [result job-status]} (get-in job-state ["steps" step])
                  {:strs [message]}           result
                  missed-pending?             (and (false? pending) (#{"success" "failure"} job-status))
                  pending-transition          [order step "pending" {} match-job-id]]
              (case job-status
                "pending" (when (false? pending)
                            [pending-transition])
                "failure" (cond-> []
                            missed-pending?    (conj pending-transition)
                            (false? failure)   (conj [order step "failure" message match-job-id]))
                "success" (cond-> []
                            missed-pending?    (conj pending-transition)
                            (false? success)   (conj [order step "success" (prettify result) match-job-id]))
                nil)))
          @state))

(defn- poll-with-retries!
  "Polls in a future with retry-on-error and timeout. `poll-fn` is called each
   iteration and should return false when polling is complete, true to continue.
   `on-error` is called with the exception. `on-timeout` is called when the
   deadline is exceeded."
  [{:keys [poll-fn on-error on-timeout]}]
  (let [poll-interval-ms (* 10 1000)
        poll-timeout-ms  (* 36000 1000)
        end-time         (+ (System/currentTimeMillis) poll-timeout-ms)]
    (future
      (loop []
        (let [continue? (try
                          (poll-fn)
                          (catch Exception e
                            (on-error e)
                            true))]
          (when continue?
            (Thread/sleep poll-interval-ms)
            (if (< (System/currentTimeMillis) end-time)
              (recur)
              (on-timeout))))))))

;; https://mikerowecode.com/2013/02/clojure-polling-function.html
(defn- start-polling-results!
  [sig3-endpoint job-id match-job-id]
  (let [state (atom {"mdrop-dps"          {"pending" false "success" false "failure" false "order" 1}
                     "mdrop-elmfire"      {"pending" false "success" false "failure" false "order" 2} ;; `2` is not a typo: the models run in parallel
                     "mdrop-pyretechnics" {"pending" false "success" false "failure" false "order" 2} ;; `2` is not a typo: the models run in parallel
                     "mdrop-geosync"      {"pending" false "success" false "failure" false "order" 3}})]
    (poll-with-retries!
     {:poll-fn    (fn poll-and-record-transitions []
                    (let [job-state     (poll-job! sig3-endpoint job-id)
                          transitions   (calculate-transitions state job-state match-job-id)
                          job-succeded? (= (get job-state "status") "success")
                          job-failed?   (= (get job-state "status") "failure")
                          job-done?     (or job-succeded? job-failed?)]
                      (doseq [[_ step status result match-job-id] (sort-by first transitions)]
                        (update-match-job! (cond-> {:match-job-id   match-job-id
                                                    :message        (case status
                                                                      "pending" (str "Step " step " STARTED")
                                                                      "failure" (str "Step " step " FAILED.\nResult:\n" result)
                                                                      "success" (str "Step " step " DONE.\nResult:\n" result))}
                                             (and (= step "mdrop-elmfire") (= "success" status))
                                             (assoc :elmfire-done? true))))
                      (doseq [[_ step status] transitions]
                        (swap! state assoc-in [step status] true))
                      (if job-done?
                        (do (update-match-job! {:match-job-id match-job-id
                                                :md-status    (if job-succeded? 0 1)})
                            false)
                        true)))
      :on-error   (fn [e] (log-str "ERROR polling match-drop job-id=" job-id " match-job-id=" match-job-id ": " (.getMessage e)))
      :on-timeout (fn []
                    (log-str "Timeout while waiting for job " job-id " results. Stopping progress recording.")
                    (update-match-job! {:match-job-id match-job-id
                                        :md-status    1
                                        :message      (str "Match Drop #" match-job-id " timed out.")}))})))

(defn- create-match-job-using-kubernetes!
  [{:keys [user-id display-name] :as params} sig3-endpoint]
  (let [match-job-id                       (initialize-match-job! user-id)
        {:keys [job-id match-drop-inputs]} (submit-match-drop-job! params sig3-endpoint match-job-id)
        {:keys [geoserver-workspace]}      match-drop-inputs]
    (update-match-job! {:display-name        (or display-name (str "Match Drop " match-job-id))
                        :md-status           2
                        :message             (str "Match Drop #" match-job-id " initiated from Pyrecast.")
                        :elmfire-done?       false
                        :dps-request         match-drop-inputs
                        :elmfire-request     {}
                        :geosync-request     {}
                        :match-job-id        match-job-id
                        :runway-job-id       job-id ;; NOTE: `k8s-job-id` actually
                        :geoserver-workspace geoserver-workspace})
    (start-polling-results! sig3-endpoint job-id match-job-id)
    ;; Return the unpredictable public id; the browser never sees the sequential PK.
    {:match-job-unique-id (:match-job-unique-id (get-match-job-from-match-job-id! match-job-id))}))

(defn- create-match-job!
  [{:keys [user-id] :as params}]
  {:pre [(integer? user-id)]}
  (let [sig3-endpoint (get-config :triangulum.views/client-keys :features :sig3-endpoint)]
    (create-match-job-using-kubernetes! params sig3-endpoint)))

;;==============================================================================
;; Public API
;;==============================================================================

(defn initiate-md!
  "Creates a new match drop run and starts the analysis."
  [session match-drop-job-params]
  (let [{:keys [user-id match-drop-access?]} session
        {:keys [fuel-version lat lon]}       match-drop-job-params
        fuel-version                         (or fuel-version default-fuel-version)
        match-drop-job-params                (assoc match-drop-job-params :fuel-version fuel-version)]
    (if-not match-drop-access?
      (data-response "You do not have access to the Match Drop tool."
                     {:status 403})
      (data-response
       (cond
         (not (get-config :triangulum.views/client-keys :features :match-drop))
         {:error "Match drop is currently disabled. Please contact your system administrator to enable it."}

         (not (valid-md-fuel-versions fuel-version))
         {:error (str "Invalid fuel version: " fuel-version ". Valid versions are: " (str/join ", " (sort valid-md-fuel-versions)))}

         (let [extent (get-fuel-layer-extent fuel-version)]
           (and extent (not (point-within-extent? lon lat extent))))
         {:error (str "The ignition point is outside the geographic boundary of LANDFIRE " fuel-version ". Please select a different fuel version or location.")}

         (pos? (count-running-user-match-jobs user-id))
         {:error "Match drop is already running. Please wait until it has completed."}

         (<= (get-md-config :max-queue-size) (count-all-running-match-drops))
         {:error "The queue is currently full. Please try again later."}

         :else
         (create-match-job! (assoc match-drop-job-params :user-id user-id)))))))

(defn get-match-drops
  "Returns the user's match drops."
  [session]
  (let [{:keys [user-id match-drop-access?]} session]
    (if-not match-drop-access?
      (data-response "You do not have access to the Match Drop tool."
                     {:status 403})
      (->> (call-sql "get_user_match_jobs" user-id)
           ;; Expose the unpredictable public id, never the sequential PK.
           (mapv #(-> % sql-result->job (dissoc :match-job-id)))
           (data-response)))))

(defn- submit-match-drop-removal-job!
  "Requests a match-drop job from kubernetes"
  [sig3-endpoint {:keys [geosync-host geosync-port geoserver-workspace] :as _original-request}]
  (let [request                            {:network   :remove-match-drop
                                            :arguments {:geosync-host        geosync-host
                                                        :geosync-port        geosync-port
                                                        :geoserver-workspace geoserver-workspace}}
        api-url                            (format "%s/api/submit-job" sig3-endpoint)
        http-request                       {:body         (json/write-str request)
                                            :headers      {"sig-auth" (get-md-config :sig3-auth)}
                                            :content-type :json
                                            :accept       :json}
        _                                  (println "POST" api-url request)
        {:keys [body status] :as response} (client/post api-url http-request)]
    (if (= 200 status)
      {:job-id (:job-id (json/read-str body :key-fn keyword))}
      (throw (ex-info (format "match-drop removal request failed with status %d" status)
                      {:request http-request :response response})))))

(defn- poll-delete-match-drop-results-then-remove-from-db!
  [{:keys [job-id]}
   sig3-endpoint
   match-job-id]
  (poll-with-retries!
   {:poll-fn    (fn poll-and-delete-when-done []
                  (let [job-state     (poll-job! sig3-endpoint job-id)
                        status        (get job-state "status")
                        job-succeded? (= status "success")
                        job-failed?   (= status "failure")
                        job-done?     (or job-succeded? job-failed?)]
                    (if job-done?
                      (do (if job-succeded?
                            (do (log-str "Deleting Match Job #" match-job-id " from the database.")
                                (call-sql "delete_match_job" match-job-id))
                            (log-str "ERROR deleting match-drop '" match-job-id "'\n" job-state))
                          false)
                      true)))
    :on-error   (fn [e] (log-str "ERROR polling delete match-drop job-id=" job-id " match-job-id=" match-job-id ": " (.getMessage e)))
    :on-timeout (fn [] (log-str "Timeout while waiting for delete job " job-id " results. Aborting."))}))

(defn- delete-match-drop-using-kubernetes! [sig3-endpoint match-job-id]
  (let [{:keys [dps-request geoserver-workspace]} (get-match-job-from-match-job-id! match-job-id)
        original-request                          dps-request ; TODO https://sig-gis.atlassian.net/browse/PYR1-1317
        geosync-host                              (:geosync-host dps-request)
        geosync-port                              (:geosync-port dps-request)]
    (if (layers-exist? :match-drop geoserver-workspace)
      ;; If the specified workspace exists in the layer atom, we need to use GeoSync to remove the workspace from the GeoServer
      (try
        (-> (submit-match-drop-removal-job! sig3-endpoint original-request)
            (poll-delete-match-drop-results-then-remove-from-db! sig3-endpoint match-job-id))
        (data-response (str "The " geoserver-workspace " workspace is queued to be removed from "
                            (get-config :triangulum.views/client-keys :geoserver :match-drop) "."))
        (catch Exception _
          (update-match-job! {:match-job-id match-job-id
                              :md-status    1
                              :message      (format "Something went wrong when trying to delete Match Drop #%s." match-job-id)})
          (data-response (str "Connection to " geosync-host ":" geosync-port " failed.")
                         {:status 500})))
      ;; Otherwise we can just remove the match drop from the database, it doesn't exist anywhere else (normally used for Match Drops that errored out)
      (do (log-str "Deleting Match Job #" match-job-id " from the database.")
          (call-sql "delete_match_job" match-job-id)
          (data-response (str "Match Job #" match-job-id " was deleted from the database."))))))

(defn delete-match-drop!
  "Deletes the specified match drop from the DB and removes it from the GeoServer
   via the 'remove' action passed to the GeoSync Microservice.
   Only the match drop's owner may delete it."
  [{:keys [user-id]} match-job-unique-id]
  (let [job (get-match-job-from-uuid! match-job-unique-id)]
    (if (and job (= user-id (:user-id job)))
      (let [sig3-endpoint (get-config :triangulum.views/client-keys :features :sig3-endpoint)]
        (delete-match-drop-using-kubernetes! sig3-endpoint (:match-job-id job)))
      (data-response "You are not authorized to delete this match drop." {:status 403}))))

(defn get-md-status
  "Returns the current status of the given match drop run.
   Only the match drop's owner may view it."
  [{:keys [user-id]} match-job-unique-id]
  (let [job (get-match-job-from-uuid! match-job-unique-id)]
    (if (and job (= user-id (:user-id job)))
      (data-response (select-keys job [:display-name :geoserver-workspace :message :md-status :job-log]))
      (data-response "You are not authorized to view this match drop." {:status 403}))))

(defn get-md-available-dates
  "Gets the available dates for Match Drops in UTC.
   Example return:
   {:historical {:min-date-iso-str \"2011-01-30T00:00Z\"
                 :max-date-iso-str \"2022-09-30T23:00Z\"}
    :forecast   {:min-date-iso-str \"2023-06-04T00:00Z\"
                 :max-date-iso-str \"2023-06-07T18:00Z\"}"
  [_]
  (let [sig3-endpoint (get-config :triangulum.views/client-keys :features :sig3-endpoint)
        api-url       (format "%s/api/get-available-wx-times" sig3-endpoint)
        http-request  {:headers {"sig-auth" (get-md-config :sig3-auth)}}
        _             (println "GET" api-url)
        response      (client/get api-url http-request)]
    (data-response (parse-available-wx-dates (:body response)))))

(defn get-fuel-extent
  "Returns the geographic bounding box of a LANDFIRE fuel version as GeoJSON."
  [_ fuel-version]
  (if-not (valid-md-fuel-versions fuel-version)
    (data-response {:error (str "Invalid fuel version: " fuel-version)}
                   {:status 400})
    (if-let [extent (get-fuel-layer-extent fuel-version)]
      (let [[min-x min-y max-x max-y] (intersect-with-conus (Double/parseDouble (nth extent 0))
                                                            (Double/parseDouble (nth extent 1))
                                                            (Double/parseDouble (nth extent 2))
                                                            (Double/parseDouble (nth extent 3)))]
        (data-response {:type     "FeatureCollection"
                        :features [{:type       "Feature"
                                    :properties {:fuel-version fuel-version}
                                    :geometry   {:type        "Polygon"
                                                 :coordinates [[[min-x min-y]
                                                                [max-x min-y]
                                                                [max-x max-y]
                                                                [min-x max-y]
                                                                [min-x min-y]]]}}]}
                       {:type :json}))
      (do (println "WARN: No extent found for fuel version" fuel-version
                   "- :shasta layers may not be loaded")
          (data-response {:type "FeatureCollection" :features []}
                         {:type :json})))))

^:rct/test
(comment
  ;; --- Match drop ownership guard (PYR1-1512) ---
  ;; A match drop the session user does not own (here a non-existent uuid) must
  ;; not be viewable or deletable; both return 403 without revealing existence.
  (get-md-status {:user-id 1} "00000000-0000-0000-0000-000000000000")
  ;=>> {:status 403}

  (delete-match-drop! {:user-id 1} "00000000-0000-0000-0000-000000000000")
  ;=>> {:status 403}

  ;; A malformed (non-uuid) id is rejected the same way -- no 500.
  (get-md-status {:user-id 1} "not-a-uuid")
  ;=>> {:status 403}
  )
