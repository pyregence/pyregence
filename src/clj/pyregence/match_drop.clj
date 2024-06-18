(ns pyregence.match-drop
  (:import  java.util.UUID
            javax.net.ssl.SSLSocket)
  (:require [clojure.core.async         :refer [thread]]
            [clojure.data.json          :as json]
            [clojure.edn                :as edn]
            [clojure.java.shell         :refer [sh]]
            [clojure.set                :refer [rename-keys]]
            [clojure.string             :as str]
            [pyregence.capabilities     :refer [layers-exist?
                                                remove-workspace!
                                                set-capabilities!]]
            [pyregence.utils            :as u]

            [runway.simple-sockets      :as runway]
            [runway.utils               :refer [json-str->edn log-response!]]
            [triangulum.config          :refer [get-config]]
            [triangulum.database        :refer [call-sql sql-primitive]]
            [triangulum.logging         :refer [log-str]]
            [triangulum.response        :refer [data-response]]
            [triangulum.type-conversion :refer [json->clj clj->json]]))

;;==============================================================================
;; Static Data
;;==============================================================================

(defn- get-md-config [k]
  (get-config :pyregence.match-drop/match-drop k))

(def ^:private runway-server-pretty-names
  {"dps"      (get-md-config :dps-name)
   "elmfire"  (get-md-config :elmfire-name)
   "gridfire" (get-md-config :gridfire-name)
   "geosync"  (get-md-config :geosync-name)})

;;==============================================================================
;; Helper Functions
;;==============================================================================

(defn- kebab->camel
  "Converts kebab-string to camelString."
  [kebab-string]
  (let [words (-> kebab-string
                  (str/lower-case)
                  (str/replace #"^[^a-z_$]|[^\w-]" "")
                  (str/split #"-"))]
    (->> (map str/capitalize (rest words))
         (cons (first words))
         (str/join ""))))

;; FIXME this should eventually be removed -- read the dosctring for more details
(defn- get-server-based-on-job-id
  "Based on a Runway job id that's appended with the name of the service
   (e.g. `elmfire-2d30fbed-0fff-40c1-903e-95b02efd6b16`) returns the name of the
   service. **NOTE** This is a hack that we're using until Runway is extended to
   support/return another key in its repsonse message that identifies the server
   sending the message (something like `server-name`). See a (current) example Runway response below:

  {:timestamp '05/11 16:11:02'
   :job-id    '2d30fbed-0fff-40c1-903e-95b02efd6b16'  ; This portion is what we append the server name to throughout `match_drop.clj`
   :status    2
   :message   '(stdout) Some message for an in progress Runway server here...'}"
  [runway-job-id & [pretty?]]
  (as-> runway-job-id %
        (str/split % #"-")
        (first %)
        (if pretty?
          (get runway-server-pretty-names %)
          %)))

;; FIXME this should eventually be removed -- read the dosctring for more details
(defn- extract-base-runway-job-id
  "Based on a Runway job id that's appended with the name of the service
   (e.g. `elmfire-2d30fbed-0fff-40c1-903e-95b02efd6b16`) returns the job id minus
   the appended service (e.g. `2d30fbed-0fff-40c1-903e-95b02efd6b16`).
   **NOTE** This is a hack that we're using until Runway is extended to
   support/return another key in its repsonse message that identifies the server
   sending the message (something like `server-name`)."
  [full-runway-job-id]
  (as-> full-runway-job-id %
        (str/split % #"-")
        (rest %)
        (str/join "-" %)))

(defn- parse-available-wx-dates
  "Pares the stdout from a call to `fuel_wx_ign.py`. The exact return structure of this
   call (at the time of this writing in June of 2023) is the following:
   ```
   sig-app@goshawk:~$ ssh sig-app@sierra 'cd /mnt/tahoe/cloudfire/microservices/fuel_wx_ign && ./fuel_wx_ign.py --get_available_wx_times=True'
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
                    :runway_job_id         :runway-job-id
                    :user_id               :user-id
                    :created_at            :created-at
                    :updated_at            :updated-at
                    :md_status             :md-status
                    :display_name          :display-name
                    :job_log               :job-log
                    :elmfire_done          :elmfire-done?
                    :gridfire_done         :gridfire-done?
                    :dps_request           :dps-request
                    :elmfire_request       :elmfire-request
                    :gridfire_request      :gridfire-request
                    :geosync_request       :geosync-request
                    :geoserver_workspace   :geoserver-workspace})
      (update :dps-request json->clj)
      (update :elmfire-request json->clj)
      (update :gridfire-request json->clj)
      (update :geosync-request json->clj)))

(defn- get-match-job-from-runway-id
  "Returns a specific entry in the match_jobs table based on its base runway-job-id.
   NOTE: This expects the runway-job-id with the appended prefix stripped. This is needed
   until Runway supports an additional return key (as mentioned in the
   get-server-based-on-job-id function). Example: a passed in `runway-job-id` should be
   `81a01ae5-855f-471c-8b1d-9953df0e4bd6` instead of `dps-81a01ae5-855f-471c-8b1d-9953df0e4bd6`."
  [base-runway-job-id]
  (when (string? base-runway-job-id)
    (some-> (call-sql "get_match_job_runway" base-runway-job-id)
            (first)
            (sql-result->job))))

(defn- get-match-job-from-match-job-id
  "Returns a specific entry in the match_jobs table based on its match-job-id."
  [match-job-id]
  (when (integer? match-job-id)
    (some-> (call-sql "get_match_job" match-job-id)
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
           gridfire-done?
           dps-request
           elmfire-request
           gridfire-request
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
            gridfire-done?
            (when dps-request (clj->json dps-request))
            (when elmfire-request (clj->json elmfire-request))
            (when gridfire-request (clj->json gridfire-request))
            (when geosync-request (clj->json geosync-request))
            geoserver-workspace))

(defn- update-match-drop-on-error!
  "Updates the given match drop in the DB when an error is detected by setting
   the match drop status to 1 (indicating an error) and the corresponding message."
  [match-job-id {:keys [message job-id]}]
  (let [db-message (str "Error from the "
                        (get-server-based-on-job-id job-id true)
                        " server: " message "\n")]
    (log-str "Match drop job #" match-job-id " error: " db-message)
    (update-match-job! {:match-job-id match-job-id
                        :md-status    1
                        :message      db-message})))

(defn- update-match-drop-message!
  "Updates the given match drop in the DB with the latest message."
  [match-job-id {:keys [message job-id]}]
  (let [db-message (str "{"
                        (get-server-based-on-job-id job-id true)
                        "}: " message "\n")]
    (update-match-job! {:match-job-id match-job-id :message db-message})))

;;==============================================================================
;; Runway Communication Functions
;;==============================================================================

(defn- send-to-server-wrapper!
  "A wrapper to send a message to a Runway server. Optionally takes in
   `deletion-request?`, which throws an exception on error so that the
   `delete-match-drop!` function is able to properly inform the front-end
   as to the specific failure."
  [host port match-job-id request & [deletion-request?]]
  (try
    (with-open [client-socket (runway/create-ssl-client-socket host port)]
      (log-str (str "Able to open a client-socket: " client-socket))
      (when-not (runway/send-to-server! client-socket
                                        (-> request
                                            (json/write-str :key-fn kebab->camel)))
        (log-str (str "Was not able to send a request to " host " on port " port "."))
        (update-match-job! {:match-job-id match-job-id
                            :md-status    1
                            :message      (str "Connection to " host ":" port " failed.\n")})
        (when deletion-request? (throw (Exception. "Failed to remove Match Drop.")))))
    (catch Exception e
      (log-str (str "Exception in send-to-server-wrapper!: " e ". "
                    "Was not able to open an SSL client socket to host: " host " on port: " port))
      (update-match-job! {:match-job-id match-job-id
                          :md-status    1
                          :message      (str "Connection to " host ":" port " failed.\n")})
      (when deletion-request? (throw (Exception. "Failed to remove Match Drop."))))))

;;==============================================================================
;; Create Match Job Functions
;;==============================================================================

(defn- create-match-job!
  [{:keys [display-name user-id ignition-time lat lon wx-type] :as params}]
  (let [runway-job-id        (str (UUID/randomUUID))
        match-job-id         (initialize-match-job! user-id)
        west-buffer          12
        east-buffer          12
        south-buffer         12
        north-buffer         12
        num-ensemble-members 200
        ignition-radius      300
        run-hours            72
        model-time           (u/convert-date-string ignition-time) ; e.g. Turns "2022-12-01 18:00 UTC" into "20221201_180000"
        wx-start-time        (u/round-down-to-nearest-hour model-time)
        fire-name            (str (get-md-config :md-prefix) "-match-drop-" match-job-id)
        geoserver-workspace  (str "fire-spread-forecast_" (get-md-config :md-prefix)
                                  "-match-drop-" match-job-id "_" model-time)
        dps-request          {:job-id        (str "dps-" runway-job-id) ; NOTE: see the get-server-based-on-job-id for why we append "dps" -- this is a temporary work-around
                              :response-host (get-md-config :app-host)
                              :response-port (get-md-config :app-port)
                              :async?        false
                              :priority?     false
                              ;; TODO Now that each request is separate, we should get rid of `common-args`. The microservices will have to be updated to reflect this.
                              :script-args   {:common-args (merge params {:ignition-time ignition-time
                                                                          :fire-name     fire-name})
                                              :dps-args    {:name                 fire-name
                                                            :outdir               "/mnt/tahoe/pyrecast/fires/datacubes"
                                                            :center-lat           lat
                                                            :center-lon           lon
                                                            :west-buffer          west-buffer
                                                            :east-buffer          east-buffer
                                                            :south-buffer         south-buffer
                                                            :north-buffer         north-buffer
                                                            :do-fuel              true
                                                            :fuel-source          "landfire"
                                                            :fuel-version         "2.4.0_2.3.0"
                                                            :do-wx                true
                                                            :wx-start-time        wx-start-time
                                                            :wx-type              wx-type
                                                            :do-ignition          true
                                                            :point-ignition       true
                                                            :ignition-lat         lat
                                                            :ignition-lon         lon
                                                            :polygon-ignition     false
                                                            :ignition-radius      ignition-radius}}}
        elmfire-request      {:job-id        (str "elmfire-" runway-job-id) ; NOTE: see the get-server-based-on-job-id for why we append "elmfire" -- this is a temporary work-around
                              :response-host (get-md-config :app-host)
                              :response-port (get-md-config :app-port)
                              :async?        true
                              :priority?     true
                              ;; TODO we should eventually get rid of common-args
                              :script-args   {:common-args  (merge params {:ignition-time ignition-time
                                                                           :fire-name     fire-name})
                                              :elmfire-args {:datacube            "TODO" ; NOTE: this will be filled in at a later point once the DPS has finished running
                                                             :west-buffer          west-buffer
                                                             :east-buffer          east-buffer
                                                             :south-buffer         south-buffer
                                                             :north-buffer         north-buffer
                                                             :initialization-type  "points_within_polygon"
                                                             :num-ensemble-members num-ensemble-members
                                                             :ignition-radius      ignition-radius
                                                             :run-hours            run-hours}}}
        gridfire-request     {:job-id        (str "gridfire-" runway-job-id) ; NOTE: see the get-server-based-on-job-id for why we append "gridfire" -- this is a temporary work-around
                              :response-host (get-md-config :app-host)
                              :response-port (get-md-config :app-port)
                              :async?        true ; FIXME this is not being run asynchronously? perhaps clj->json loses the question mark
                              :priority?     true
                              ;; TODO we should eventually get rid of common-args
                              :script-args   {:common-args   (merge params {:ignition-time ignition-time
                                                                            :fire-name     fire-name})
                                              :gridfire-args {:datacube            "TODO" ; NOTE: this will be filled in at a later point once the DPS has finished running
                                                              :num-ensemble-members num-ensemble-members
                                                              :run-hours            run-hours
                                                              :wx-start-time        wx-start-time
                                                              :initialization-type  "points_within_polygon"}}}
        geosync-request      {:job-id        (str "geosync-" runway-job-id) ; NOTE: see the get-server-based-on-job-id for why we append "geosync" -- this is a temporary work-around
                              :response-host (get-md-config :app-host)
                              :response-port (get-md-config :app-port)
                              :async?        false
                              :priority?     false
                              :script-args   {:geosync-args {:action              "add"
                                                             :geoserver-name      "sierra"
                                                             :geoserver-workspace geoserver-workspace
                                                             :elmfire-deck        "TODO"    ; NOTE: this will be filled in at a later point once ELMFIRE has finished running
                                                             :gridfire-deck       "TODO"}}} ; NOTE: this will be filled in at a later point once GridFire has finished running
        match-job            {:display-name        (or display-name (str "Match Drop " match-job-id))
                              :md-status           2
                              :message             (str "Match Drop #" match-job-id " initiated from Pyrecast.\n")
                              :elmfire-done?       false
                              :gridfire-done?      false
                              :dps-request         dps-request
                              :elmfire-request     elmfire-request
                              :gridfire-request    gridfire-request
                              :geosync-request     geosync-request
                              :match-job-id        match-job-id
                              :runway-job-id       runway-job-id
                              :geoserver-workspace geoserver-workspace}]
    (update-match-job! match-job)
    (log-str "Initiating Match Drop #" match-job-id)
    ;; The Match Drop pipeline is started by sending a request to the DPS:
    (send-to-server-wrapper! (get-md-config :dps-host)
                             (get-md-config :dps-port)
                             (:match-job-id match-job)
                             (:dps-request match-job))
    {:match-job-id match-job-id}))

;;==============================================================================
;; Public API
;;==============================================================================

(defn initiate-md!
  "Creates a new match drop run and starts the analysis."
  [{:keys [user-id] :as params}]
  (data-response
   (cond
     (not (get-config :triangulum.views/client-keys :features :match-drop))
     {:error "Match drop is currently disabled. Please contact your system administrator to enable it."}

     (pos? (count-running-user-match-jobs user-id))
     {:error "Match drop is already running. Please wait until it has completed."}

     (< (get-md-config :max-queue-size) (count-all-running-match-drops))
     {:error "The queue is currently full. Please try again later."}

     :else
     (create-match-job! params))))

(defn get-match-drops
  "Returns the user's match drops"
  [user-id]
  (->> (call-sql "get_user_match_jobs" user-id)
       (mapv sql-result->job)
       (data-response)))

(defn delete-match-drop!
  "Deletes the specified match drop from the DB and removes it from the GeoServer
   via the 'remove' action passed to the GeoSync Runway server."
  [match-job-id]
  (let [{:keys [geosync-request geoserver-workspace]} (get-match-job-from-match-job-id match-job-id)
        updated-geosync-request (-> geosync-request
                                    (assoc-in [:script-args :geosync-args :action] "remove"))
        geosync-host            (get-md-config :geosync-host)
        geosync-port            (get-md-config :geosync-port)]
    (update-match-job! {:match-job-id    match-job-id
                        :md-status       3
                        :geosync-request updated-geosync-request
                        :message         (format "Match Drop #%s is queued to be deleted. Please click the \"Refresh\" button.\n" match-job-id)}) ; TODO test that \" works properly
    (log-str (str "Initiating the deletion of match drop job #" match-job-id "."))
    (if (layers-exist? :match-drop geoserver-workspace)
      ;; If the specified workspace exists in the layer atom, we need to use GeoSync to remove the workspace from the GeoSerer
      (try
        (send-to-server-wrapper! geosync-host
                                 geosync-port
                                 match-job-id
                                 updated-geosync-request
                                 true)
        (data-response (str "The " geoserver-workspace " workspace is queued to be removed from "
                            (get-config :triangulum.views/client-keys :geoserver :match-drop) "."))
        (catch Exception _
          (update-match-job! {:match-job-id match-job-id
                              :md-status    1
                              :message      (format "Something went wrong when trying to delete Match Drop #%s." match-job-id)})
          (data-response (str "Connection to " geosync-host ":" geosync-port " failed.")
                         {:status 403})))
      ;; Otherwise we can just remove the match drop from the database, it doesn't exist anywhere else (normally used for Match Drops that errored out)
      (do (log-str "Deleting Match Job #" match-job-id " from the database.")
          (call-sql "delete_match_job" match-job-id)
          (data-response (str "Match Job #" match-job-id " was deleted from the database."))))))

(defn get-md-status
  "Returns the current status of the given match drop run."
  [match-job-id]
  (data-response (-> (get-match-job-from-match-job-id match-job-id)
                     (select-keys [:display-name :geoserver-workspace :message :md-status :job-log]))))

(defn get-md-available-dates
  "Gets the available dates for Match Drops in UTC. Note that we're shelling out
   to a script on `sierra` as the `sig-app` user.

   Example return:
   {:historical {:min-date-iso-str \"2011-01-30T00:00Z\"
                 :max-date-iso-str \"2022-09-30T23:00Z\"}
    :forecast   {:min-date-iso-str \"2023-06-04T00:00Z\"
                 :max-date-iso-str \"2023-06-07T18:00Z\"}"
  []
  (let [{:keys [out err exit]} (sh "ssh" "sig-app@sierra"
                                   "cd /mnt/tahoe/cloudfire/microservices/fuel_wx_ign && ./fuel_wx_ign.py" "--get_available_wx_times=True")]
    (if (= exit 0)
      (data-response (parse-available-wx-dates out))
      (data-response (str "Something went wrong when calling "
                          "/mnt/tahoe/cloudfire/microservices/fuel_wx_ign/fuel_wx_ign.py:"
                          err)
                     {:status 403}))))

;;==============================================================================
;; Runway Process Complete Functions
;;==============================================================================

(defn- update-fire-requests!
  "Updates the ELMFIRE and GridFire requests in the DB to use the datacube
   returned by the DPS. Returns the updated requests in a map."
  [match-job-id elmfire-request gridfire-request {:keys [datacube]}]
  (let [updated-elmfire-request  (assoc-in elmfire-request [:script-args :elmfire-args :datacube] datacube)
        updated-gridfire-request (assoc-in gridfire-request [:script-args :gridfire-args :datacube] datacube)]
    (update-match-job! {:match-job-id     match-job-id
                        :elmfire-request  updated-elmfire-request
                        :gridfire-request updated-gridfire-request})
    {:updated-elmfire-request  updated-elmfire-request
     :updated-gridfire-request updated-gridfire-request}))

(defn- update-geosync-request!
  "Updates the GeoSync request in the DB to use the output deck paths returned
   by ELMFIRE and GridFire. Returns the updated request."
  [match-job-id job-id geosync-request {:keys [elmfire-deck gridfire-deck]}]
  (if-not (or elmfire-deck gridfire-deck)
    (update-match-drop-on-error! match-job-id
                                 {:job-id  job-id
                                  :message (str "The GeoSync request was not able to be updated. "
                                                "Either ELMFIRE or GridFire didn't return the proper path to an ouput deck.")})
    (let [updated-geosync-request (cond-> geosync-request
                                    elmfire-deck  (assoc-in [:script-args :geosync-args :elmfire-deck] elmfire-deck)
                                    gridfire-deck (assoc-in [:script-args :geosync-args :gridfire-deck] gridfire-deck))]
      (update-match-job! {:match-job-id    match-job-id
                          :geosync-request updated-geosync-request})
      updated-geosync-request)))

(defn- handle-steps-after-dps-finishes!
  "After the DPS job is completed, queue the ELMFIRE and GridFire runs."
  [match-job-id elmfire-request gridfire-request message]
  (log-str "The DPS has finished running!")
  (let [{:keys [updated-elmfire-request
                updated-gridfire-request]} (update-fire-requests! match-job-id
                                                                  elmfire-request
                                                                  gridfire-request
                                                                  (edn/read-string message))]
    (log-str "Initiating ELMFIRE run...")
    (send-to-server-wrapper! (get-md-config :elmfire-host)
                             (get-md-config :elmfire-port)
                             match-job-id
                             updated-elmfire-request)
    (log-str "Initiating GridFire run...")
    (send-to-server-wrapper! (get-md-config :gridfire-host)
                             (get-md-config :gridfire-port)
                             match-job-id
                             updated-gridfire-request)))

(defn- handle-steps-after-elmfire-finishes!
  "After the ELMFIRE job is completed, we need to update the `geosync-request`
   match job parameter such that the `elmfire-deck` key value pair reflects the
   message that was returned from the ELMFIRE Runway server. After we do this,
   we check and see if GridFire has also completed. If it has, we can queue the
   GeoSync request using the aforementioned updated `geosync-request`.
   Otherwise, we return and wait for GridFire to finish. Updates the `elmfire-done?`
   `match-job` parameter to `true` in both cases."
  [match-job-id job-id geosync-request message]
  (log-str "ELMFIRE has finished running!")
  (update-match-job! {:match-job-id  match-job-id
                      :elmfire-done? true})
  (let [updated-geosync-request  (update-geosync-request! match-job-id
                                                          job-id
                                                          geosync-request
                                                          (edn/read-string message))
        {:keys [gridfire-done?]} (get-match-job-from-match-job-id match-job-id)]
    (if gridfire-done?
      (do
        (log-str "Both ELMFIRE and GridFire have finished running. Initiating GeoSync run...")
        (send-to-server-wrapper! (get-md-config :geosync-host)
                                 (get-md-config :geosync-port)
                                 match-job-id
                                 updated-geosync-request))
      (log-str (str "ELMFIRE has finished running, but GridFire has not. "
                    "Waiting until GridFire finishes running to queue GeoSync.")))))

(defn- handle-steps-after-gridfire-finishes!
  "After the GridFire job is completed, we need to update the `geosync-request`
   match job parameter such that the `gridfire-deck` key value pair reflects the
   message that was returned from the GridFire Runway server. After we do this,
   we check and see if ELMFIRE has also completed. If it has, we can queue the
   GeoSync request using the aforementioned updated `geosync-request`.
   Otherwise, we return and wait for ELFIRE to finish. Updates the `gridfire-done?`
   `match-job` parameter to `true` in both cases."
  [match-job-id job-id geosync-request message]
  (log-str "GridFire has finished running!")
  (update-match-job! {:match-job-id   match-job-id
                      :gridfire-done? true})
  (let [updated-geosync-request (update-geosync-request! match-job-id
                                                         job-id
                                                         geosync-request
                                                         (edn/read-string message))
        {:keys [elmfire-done?]} (get-match-job-from-match-job-id match-job-id)]
    (if elmfire-done?
      (do
        (log-str "Both GridFire and ELMFIRE have finished running. Initiating GeoSync run...")
        (send-to-server-wrapper! (get-md-config :geosync-host)
                                 (get-md-config :geosync-port)
                                 match-job-id
                                 updated-geosync-request))
      (log-str (str "GridFire has finished running, but ELMFIRE has not. "
                    "Waiting until ELMFIRE finishes running to queue GeoSync.")))))

(defn- handle-steps-after-geosync-finishes!
  "After GeoSync is finished, we have two possiblities: either the match drop
   status `md-status` is 2 or 3. A `md-status` of 2 indicates that our Match Drop
   job was still in progress at the time that the GeoSync Runway server finished successfully.
   In this case, we can go ahead and `set-capabilities` and update the Match Drop
   job to a `md-status` of 0 (indicating a successfully completed Match Drop).
   A `md-status` of 3 indicates that the Match Drop job was queued to be deleted,
   and the GeoSync Runway server successfully finished removing the Match Drop
   from the GeoServer. We can thus go ahead and delete the Match Drop job from
   the DB and `remove-workspace` so that the layers are removed from Pyrecast."
  [match-job-id job-id md-status geoserver-workspace]
  (condp = md-status
    2       (do
              (log-str (str "Geoserver workspace is " geoserver-workspace))
              ;; NOTE: set-capabilities! isn't handled by the GeoSync action hook since GeoSync is running in CLI mode in the GeoSync microservice (action hooks only work in server mode).
              (set-capabilities! {"geoserver-key"  "match-drop"
                                  "workspace-name" geoserver-workspace})
              (log-str (str "Match Drop layers successfully added to Pyrecast! "
                            "Match Drop job #" match-job-id " is complete."))
              (update-match-job! {:match-job-id match-job-id
                                  :md-status    0
                                  :message      (str "Match Drop job #" match-job-id " is complete!\n")}))

    3     (do
            (log-str "Deleting match job #" match-job-id " from the database.")
            (call-sql "delete_match_job" match-job-id)
            ;; NOTE: remove-workspace! isn't handled by the GeoSync action hook since GeoSync is running in CLI mode in the GeoSync microservice (action hooks only work in server mode)
            ;; TODO in the future we should make sure that the front-end is updated as soon as the workspace is removed.
            ;; we will need to do something similar to the refresh-fire-names! fn in match_drop_tool.cljs, but only call
            ;; it once this section of the code is hit.
            (remove-workspace! {"geoserver-key"  "match-drop"
                                "workspace-name" geoserver-workspace}))

    :else (update-match-drop-on-error! match-job-id
                                       {:job-id  job-id
                                        :message (str "GeoSync finished running, but something went wrong. "
                                                      "The `md-status` was neither 2 nor 3.")})))

(defn- runway-process-complete!
  "The function called when a Runway status returns 0, thus indicating a completed
   Runway request. This function early exits if no message is provided from the
   given Runway server since we depend on these return values to make multiple calls.
   Depending on the server of the given Runway job, we need to initiate an appropriate
   next request to continue the Match Drop pipeline. For example, after a successful
   data provisioning server (DPS) Runway job, we need to initiate calls to both
   the ELMFIRE and GridFire servers. **NOTE** As mentioned in `get-server-based-on-job-id`,
   we are appending the runway job ids with the name of the service that will run.
   Once Runway is extended, this logic can be simplified."
  [{:keys [match-job-id md-status elmfire-request gridfire-request geosync-request geoserver-workspace]}
   {:keys [job-id message]}]
  ;; NOTE: `message` contains our return value from Runway in a map: {:datacube "/some/path/to/datacube.tar"}
  (if-not message
    (update-match-drop-on-error! match-job-id {:job-id  job-id
                                               :message "No return value/message from this server was provided."})
    (let [runway-server-that-finished (get-server-based-on-job-id job-id)]
      (update-match-job! {:match-job-id match-job-id
                          :message      (format "%s has successfully finished running!\n"
                                                (get runway-server-pretty-names runway-server-that-finished))})
      (condp = runway-server-that-finished
        "dps"
        (handle-steps-after-dps-finishes! match-job-id elmfire-request gridfire-request message)

        "elmfire"
        (handle-steps-after-elmfire-finishes! match-job-id job-id geosync-request message)

        "gridfire"
        (handle-steps-after-gridfire-finishes! match-job-id job-id geosync-request message)

        "geosync"
        (handle-steps-after-geosync-finishes! match-job-id job-id md-status geoserver-workspace)

        ;; The runway-server-that-finished wasn't dps, elmfire, gridfire, or geosync
        (update-match-drop-on-error! match-job-id
                                     {:job-id  job-id
                                      :message (str "Something went wrong inside of runway-process-complete!"
                                                    " The Runway server that finished running: \"" runway-server-that-finished
                                                    "\" was not \"dps\", \"elmfire\", \"gridfire\", or \"geosync\".")})))))

;;==============================================================================
;; Job Queue Progression
;;==============================================================================

;; This separate function allows reload to work in dev mode for easier development
(defn- process-response-msg
  "This function is what gets called every time the Pyrecast server recieves a
   message from any one of the Runway servers (e.g. the GridFire Runway server).

   The `msg` argument gets passed in in JSON format and thus needs to be parsed into `response`.
   An example `response-msg-edn` argument can be seen below (would be nice to use spec going forward):

   `{:timestamp '05/11 16:11:02'
     :job-id    '2d30fbed-0fff-40c1-903e-95b02efd6b16'  ; This portion is what we append the server name to throughout `match_drop.clj`
     :status    2
     :message   '(stdout) Some message for an in progress Runway server here...'}`

   The `job-id` corresponds to the `runway-job-id` that the match drop job is
   associated with. The `status` corresponds to the Runway-specific status of the
   job. 0 indicates a completed Runway request, 1 indicates an error, and 2 indicates in-progress.

   From the above Runway `job-id` we can make a request to the SQL database to
   get the info about the match drop job that the initial `response-msg-edn` is associated with."
  [response-msg-edn]
  (if-let [{:keys [job-id status]} response-msg-edn]
    (let [base-runway-job-id (extract-base-runway-job-id job-id)
          {:keys [md-status match-job-id] :as job} (get-match-job-from-runway-id base-runway-job-id)]
      (cond
        (nil? job)
        (update-match-drop-on-error! match-job-id
                                     {:job-id  job-id
                                      :message (format "The Match Job with runway-job-id %s could not be found in the Pyrecast database." base-runway-job-id)})

        (not (contains? #{0 1 2} status)) ; make sure the Runway job has a valid status code
        (update-match-drop-on-error! match-job-id
                                     {:job-id  job-id
                                      :message (format "Invalid Runway status code: %s" status)})

        (#{0 1} md-status) ; a md-status of 0 or 1 indicate the match job has exited (success, failure respectively)
        (update-match-drop-on-error! match-job-id
                                     {:job-id  job-id
                                      :message (format "The Match Job has finished running with a status of: %s" md-status)})

        :else ; md-status is 2 or 3, indicates job is still in-progess or queued to be deleted
        (case status
          ;; Runway job DONE
          0 (runway-process-complete! job response-msg-edn)
          ;; Runway job ERROR
          1 (update-match-drop-on-error! match-job-id response-msg-edn)
          ;; Runway job INFO
          2 (update-match-drop-message! match-job-id response-msg-edn))))
    ;; FIXME We need a clean way to update the match job to indicate an error here. Perhaps we need an atom for the match-job-id so that we can call `update-match-drop-on-error!`?
    (log-str "Something is wrong with the format of the response message from Runway.")))

(defn match-drop-server-msg-handler
  "Accepts a message from the socket server and sends it to be processed.
   The handler function for the Match Drop server started inside of `server.clj`."
  [^SSLSocket socket response-msg-json]
  (thread
    (try
      (loop [response-msg-edn (json-str->edn response-msg-json)]
        (let [status (:status response-msg-edn)]
          (log-response! response-msg-edn)
          (cond
            (.isClosed socket) (do
                                 (log-str "Socket is already closed! Exiting.")
                                 (.close socket))
            (= 2 status)       (do
                                 (process-response-msg response-msg-edn)
                                 (recur (json-str->edn (runway/read-socket! socket))))
            (#{0 1} status)    (process-response-msg response-msg-edn)
            :else              (do
                                 (log-str "Something went wrong. Closing socket!")
                                 (.close socket)))))
      (catch Exception e
        ;; FIXME We need a clean way to update the match job to indicate an error here. Perhaps we need an atom for the match-job-id so that we can call `update-match-drop-on-error!`?
        (log-str (str "Exception in match-drop-server-msg-handler: " e ". "
                      "The " socket " socket will be closed.")))
      (finally (.close socket)))))
