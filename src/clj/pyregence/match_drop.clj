(ns pyregence.match-drop
  (:import  java.util.UUID
            javax.net.ssl.SSLSocket)
  (:require [clojure.core.async :refer [thread]]
            [clojure.data.json :as json]
            [clojure.string    :as str]
            [clojure.set       :refer [rename-keys]]
            [runway.simple-sockets      :as runway]
            [runway.utils               :refer [json-str->edn log-response!]]
            [triangulum.config          :refer [get-config]]
            [triangulum.database        :refer [call-sql sql-primitive]]
            [triangulum.logging         :refer [log-str]]
            [triangulum.type-conversion :refer [json->clj clj->json]]
            [pyregence.capabilities :refer [remove-workspace! set-capabilities!]]
            [pyregence.utils        :as u]
            [pyregence.views        :refer [data-response]]))

;;==============================================================================
;; Helper Functions
;;==============================================================================
;; TODO these will be part of triangulum.utils

(defn- camel->kebab
  "Converts camelString to kebab-string"
  [camel-string]
  (as-> camel-string s
    (str/split s #"(?<=[a-z])(?=[A-Z])")
    (map str/lower-case s)
    (str/join "-" s)))

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

(defn- get-md-config [k]
  (get-config :match-drop k))

;; FIXME this is a bad way to determine the model from a Runway message
(defn- get-model [message]
  (second (re-find #".*(elmfire|gridfire).*" (str/lower-case message))))

;;==============================================================================
;; Static Data
;;==============================================================================

(def ^:private host-names
  {(get-md-config :app-host)      (get-md-config :app-name)
   (get-md-config :dps-host)      (get-md-config :dps-name)
   (get-md-config :elmfire-host)  (get-md-config :elmfire-name)
   (get-md-config :gridfire-host) (get-md-config :gridfire-name)
   (get-md-config :geosync-host)  (get-md-config :geosync-name)})

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
                    :geoserver_workspace   :geoserver-workspace})
      (update :request json->clj)))

(defn- get-match-job-from-runway-id
  "Returns a specific entry in the match_jobs table based on its
   runway-job-id."
  [runway-job-id]
  (when (string? runway-job-id)
    (some-> (call-sql "get_match_job_runway" runway-job-id)
            (first)
            (sql-result->job))))

(defn- get-match-job-from-match-job-id
  "Returns a specific entry in the match_jobs table based on its
   match-job-id."
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
           request
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
            (when request (clj->json request))
            geoserver-workspace))

;;==============================================================================
;; Runway Communication Functions
;;==============================================================================

(defn- send-to-server-wrapper!
  [host port {:keys [match-job-id request]} & [extra-payload]]
  (with-open [client-socket (runway/create-ssl-client-socket host port)]
    (if (runway/send-to-server! client-socket
                                (-> request
                                    (update :script-args merge extra-payload)
                                    (json/write-str :key-fn kebab->camel)))
      ;; TODO do something with this message
      (do
        (let [response (json-str->edn (runway/read-socket! client-socket))]
          (log-str "Send-to-server was true")
          (log-str "response:" response)
          (log-response! response)))
      (update-match-job! {:match-job-id match-job-id
                          :md-status    1
                          :message      (str "Connection to " host " failed.")}))))

;; Think of other ways to handle these error responses
(defn- error-response [request response-host response-port message]
  (when (and response-host response-port) ; TODO: Use spec to validate these fields
    (runway/send-to-server! response-host
                     response-port
                     (json/write-str
                      (assoc-in request [:script-args :error-args] message)
                      :key-fn (comp kebab->camel name)))))

;;==============================================================================
;; Create Match Job Functions
;;==============================================================================

(defn- create-match-job!
  [{:keys [display-name user-id ignition-time lat lon] :as params}]
  (let [runway-job-id       (str (UUID/randomUUID))
        match-job-id        (initialize-match-job! user-id)
        west-buffer         12
        east-buffer         12
        south-buffer        12
        north-buffer        12
        run-hours           24
        model-time          (u/convert-date-string ignition-time)
        wx-start-time       (u/round-down-to-nearest-hour model-time)
        fire-name           (str "match-drop-" match-job-id)
        geoserver-workspace (str "fire-spread-forecast_match-drop-" match-job-id "_" model-time)
        ;; TODO: consider different payloads per request instead of one large one.
        request             {:job-id        runway-job-id
                             :response-host (get-md-config :app-host)
                             :response-port (get-md-config :app-port)
                             :script-args   {:common-args   (merge params {:ignition-time ignition-time
                                                                           :fire-name     fire-name})
                                             :dps-args      {:name                 fire-name
                                                             :outdir               "/mnt/tahoe/pyrecast/fires/datacubes"
                                                             :center-lat           lat
                                                             :center-lon           lon
                                                             :west-buffer          west-buffer
                                                             :east-buffer          east-buffer
                                                             :south-buffer         south-buffer
                                                             :north-buffer         north-buffer
                                                             :do-fuel              true
                                                             :fuel-source          "landfire"
                                                             :fuel-version         "2.2.0"
                                                             :do-wx                true
                                                             :wx-start-time        wx-start-time
                                                             :do-ignition          true
                                                             :point-ignition       true
                                                             :ignition-lat         lat
                                                             :ignition-lon         lon
                                                             :polygon-ignition     false
                                                             :ignition-radius      300}
                                             :elmfire-args  {:datacube            "TODO"
                                                             :west-buffer          west-buffer
                                                             :east-buffer          east-buffer
                                                             :south-buffer         south-buffer
                                                             :north-buffer         north-buffer
                                                             :initialization-type  "points_within_polygon"
                                                             :num-ensemble-members 200
                                                             :ignition-radius      300
                                                             :run-hours            run-hours}
                                             :gridfire-args {:datacube            "TODO"
                                                             :num-ensemble-members 200
                                                             :run-hours            run-hours
                                                             :wx-start-time        wx-start-time
                                                             :initialization-type  "points_within_polygon"}
                                             :geosync-args  {:action         "add"
                                                             :geoserver-name "sierra"
                                                             :elmfire-deck   "TODO"
                                                             :gridfire-deck  "TODO"}}}
        match-job           {:display-name        (or display-name (str "Match Drop " match-job-id))
                             :md-status           2
                             :message             (str "Job " match-job-id " Initiated.")
                             :elmfire-done?       false
                             :gridfire-done?      false
                             :request             request
                             :match-job-id        match-job-id
                             :runway-job-id       runway-job-id
                             :geoserver-workspace geoserver-workspace}]
    (update-match-job! match-job)
    (log-str "Initiating match drop job #" match-job-id)
    ;; The Match Drop pipeline is started by sending a request to the DPS:
    (send-to-server-wrapper! (get-md-config :dps-host)
                             (get-md-config :dps-port)
                             match-job)
    {:match-job-id match-job-id}))

;;==============================================================================
;; Public API
;;==============================================================================

(defn initiate-md!
  "Creates a new match drop run and starts the analysis."
  [{:keys [user-id] :as params}]
  (data-response
   (cond
     (not (get-config :features :match-drop))
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
  "Deletes the specified match drop from the DB and removes it from the GeoServer."
  [match-job-id]
  (let [{:keys [geoserver-workspace]} (get-match-job-from-match-job-id match-job-id)
        runway-job-id (str (UUID/randomUUID))
        geosync-host  (get-md-config :geosync-host)
        geosync-port  (get-md-config :geosync-port)
        request       {:job-id        runway-job-id
                       :response-host (get-md-config :app-host)
                       :response-port (get-md-config :app-port)
                       :script-args   {:geosync-args {:action "remove"}}} ;; TODO include common-args
        match-job     {:match-job-id  match-job-id
                       :md-status     3 ; 3 indicates Match Drop is primed for deletion
                       :message       (str "Match Drop " match-job-id " is queued to be deleted.")
                       :request       request
                       :runway-job-id runway-job-id}]
    ; Update match drop job to replace the `runway-job-id` and the `md-status`
    (update-match-job! match-job)
    ; Remove associated workspace from the GeoServer
    (log-str "Initiating the deletion of match drop job #" match-job-id)
    (if (runway/send-to-server! geosync-host
                         geosync-port
                         (-> request (json/write-str :key-fn kebab->camel)))
      (data-response (str "The " geoserver-workspace " workspace is queued to be removed from " geosync-host "."))
      (data-response (str "Connection to " geosync-host " failed.")
                     {:status 403}))))

(defn get-md-status
  "Returns the current status of the given match drop run."
  [match-job-id]
  (data-response (-> (get-match-job-from-match-job-id match-job-id)
                     (select-keys [:message :md-status :job-log]))))

;;==============================================================================
;; Job Queue Progression
;;==============================================================================

(defn- process-complete!
  "The function called when a Runway status returns 0, thus indicating a completed
   Runway request. Depending on the `response-host` of the given Runway job,
   we need to initiate an appropriate next request to continue the Match Drop
   pipeline. For example, after a successful data provisioning server (DPS) Runway
   job, we need to initiate calls to both the ELMFIRE and GridFire servers."
  [{:keys [match-job-id md-status elmfire-done? gridfire-done? request geoserver-workspace] :as job}
   {:keys [response-host message]}]
  (when message
    (update-match-job! {:match-job-id match-job-id :message message}))
  (condp = response-host
    (get-md-config :dps-host) ; After DPS job is completed, let Elmfire and Gridfire servers know
    (do
      (send-to-server-wrapper! (get-md-config :elmfire-host) (get-md-config :elmfire-port) job)
      (send-to-server-wrapper! (get-md-config :gridfire-host) (get-md-config :gridfire-port) job))

    ;; TODO launching two geosync calls for the same directory might break if we switch to image mosaics
    ;; TODO we only need one call to the GeoSync service now
    ;; (or (get-md-config :elmfire-host) (get-md-config :gridfire-host))
    ;;
    (get-md-config :elmfire-host) ; After ELMFIRE job is completed, let GeoSync server know
    (send-to-server-wrapper! (get-md-config :geosync-host) (get-md-config :geosync-port) job {:model "elmfire"}) ;; FIXME, don't need model

    (get-md-config :gridfire-host) ; After GridFire job is completed, let GeoSync server know
    (send-to-server-wrapper! (get-md-config :geosync-host) (get-md-config :geosync-port) job {:model "gridfire"}) ;; FIXME, don't need model

    (get-md-config :geosync-host) ; After GeoSync job is completed, we either have a deletion request or an addition request
    (condp = md-status
      ;; 2 indicates our Match Drop is still in progress. We check here to see if both
      ;; ELMFIRE and GridFire have finished running ;; TODO they should have already finished running before GeoSync is called at all
      2 (let [model     (get-model message)
              elmfire?  (or elmfire-done?  (= "elmfire" model))
              gridfire? (or gridfire-done? (= "gridfire" model))]
          (if (and elmfire? gridfire?)
            (do (update-match-job! {:match-job-id   match-job-id
                                    :md-status      0
                                    :gridfire-done? true
                                    :elmfire-done?  true})
                (set-capabilities! {"geoserver-key"  "match-drop"
                                    "workspace-name" geoserver-workspace}))
            (update-match-job! {:match-job-id   match-job-id
                                :gridfire-done? gridfire?
                                :elmfire-done?  elmfire?})))

      ;; 3 indicates a deletion - the Match Drop is now safe to be deleted
      3 (do
          (log-str "Deleting match job " match-job-id " from the database.")
          (call-sql "delete_match_job" match-job-id)
          (remove-workspace! {"geoserver-key"  "match-drop"
                              "workspace-name" geoserver-workspace})))))

(defn- process-error! [match-job-id {:keys [message response-host]}]
  (let [db-message (str (get host-names response-host) ": " message)]
    (log-str "Match drop job #" match-job-id " error: " db-message)
    (update-match-job! {:match-job-id match-job-id
                        :md-status    1
                        :message      db-message})))

(defn- process-message! [match-job-id {:keys [message response-host]}]
  (let [db-message (str (get host-names response-host) ": " message)]
    (update-match-job! {:match-job-id match-job-id :message db-message})))

;; This separate function allows reload to work in dev mode for easier development
(defn- do-processing
  "This function is what gets called every time the Pyrecast server recieves a
   message from any one of the Runway servers (e.g. the GridFire Runway server).

   The `msg` argument gets passed in in JSON format and thus needs to be parsed into `response`.
   An example `response` argument can be seen below:
   `{:job-id 'b63c7edc-b53a-4074-b338-eeec0aed33ea', :status 2,
     :message 'You are number 1 in the queue.', :timestamp '12/02 17:42:49',
     :response-host 'geo.localhost', :response-port 31338}`

   The `job-id` corresponds to the `runway-job-id` that the match drop job is
   associated with. The `status` corresponds to the Runway-specific status of the
   job. 0 indicates a completed Runway request, 1 indicates an error, and 2 indicates in-progress.

   From the above Runway `job-id` we can make a request to the SQL database to
   get the info about the match drop job that the initial `msg` is associated with."
  [response-msg]
  (log-str "inside do-processing. the response-msg is: " response-msg)
  (if-let [{:keys [job-id ; Note that this is the `runway-job-id`
                   status
                   response-host
                   response-port]
            :as   response} response-msg]
    ;; TODO: Use spec to validate the message
    (let [{:keys [request md-status match-job-id] :as job} (get-match-job-from-runway-id job-id)]
      (cond
        (nil? job)
        (error-response {:job-id job-id} response-host response-port
                        (format "The Match Job with runway-job-id %s could not be found in the Pyrecast database." job-id))

        (not (contains? #{0 1 2} status)) ; make sure the Runway job has a valid status code
        (error-response request response-host response-port (format "Invalid Runway status code: %s" status))

        (< md-status 2) ; 0 and 1 indicate the match job has exited (success, failure respectively)
        (error-response request response-host response-port (format "Job %s has exited." job-id))

        :else ; md-status is 2 or 3, indicates job is still in-progess or primed to be deleted
        (case status
          0 (process-complete! job response)             ; Runway job DONE
          1 (process-error!    match-job-id response)    ; Runway job ERROR
          2 (process-message!  match-job-id response)))) ; Runway job INFO
    (log-str "Invalid JSON.")))

(defn process-message
  "Accepts a message from the socket server and sends it to be processed.
   The handler function for the :match-drop :app-host and :app-port from `config.edn`."
  [^SSLSocket socket response-msg]
  (try
    (thread
      (loop [response (json-str->edn response-msg)]
        (log-response! (or response
                           {:job-id -1 :status 1 :message "Invalid JSON"}))
        (cond
          (.isClosed socket)          (do
                                        (log-str "socket is closed!!")
                                        (.close socket))
          (not= 3 (:status response)) (do
                                        (log-str "the status is not equal to 3.")
                                        (do-processing (json-str->edn response))) ;; TODO think about what to do for logging in progress (status 2)
          :else                       (do
                                        (log-str "Time to recur!")
                                        (recur (runway/read-socket! socket))))))
    (catch Exception e
      (log-str "Exception on process-message thread: " (.getMessage e)))
    (finally (.close socket))))
