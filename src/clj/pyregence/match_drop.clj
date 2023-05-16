(ns pyregence.match-drop
  (:import  java.util.UUID
            javax.net.ssl.SSLSocket)
  (:require [clojure.pprint     :refer [pprint]]
            [clojure.core.async :refer [thread]]
            [clojure.data.json :as json]
            [clojure.edn       :as edn]
            [clojure.string    :as str]
            [clojure.set       :refer [rename-keys]]
            [runway.simple-sockets      :as runway]
            [runway.utils               :refer [json-str->edn log-response!]]
            [triangulum.config          :refer [get-config]]
            [triangulum.database        :refer [call-sql sql-primitive]]
            [triangulum.logging         :refer [log log-str]]
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

(def ^:private runway-server-pretty-names
  {"dps"      (get-md-config :dps-name)
   "elmfire"  (get-md-config :elmfire-name)
   "gridfire" (get-md-config :gridfire-name)
   "geosync"  (get-md-config :geosync-name)})

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
  (let [db-message (str "Message from the "
                        (get-server-based-on-job-id job-id true)
                        " server: " message "\n")]
    (update-match-job! {:match-job-id match-job-id :message db-message})))

;;==============================================================================
;; Runway Communication Functions
;;==============================================================================

(defn- send-to-server-wrapper!
  [host port {:keys [match-job-id request]} & [extra-payload]]
  (log-str "Inside send-to-server-wrapper!")
  (with-open [client-socket (runway/create-ssl-client-socket host port)]
    (if (runway/send-to-server! client-socket
                                (-> request
                                    (update :script-args merge extra-payload)
                                    (json/write-str :key-fn kebab->camel)))
      ;; TODO do something with this message
      ;; TODO we need to keep reading off of this socket....
      ;; Consider making the loop thread in process-message a helper function...
      (do
        (log-str "send-to-server was true"))
        ; (let [response (json-str->edn (runway/read-socket! client-socket))]
        ;   (log-str "Send-to-server was true")
        ;   (log-str "response:" response)
        ;   (log-response! response)))
      (do
        (log-str "send-to-server returned false, updating the match job to match...")
        (update-match-job! {:match-job-id match-job-id
                            :md-status    1
                            :message      (str "Connection to " host " failed.")})))))

;; Think of other ways to handle these error responses
(defn- error-response [request response-host response-port message]
  (log-str "Error-response!")
  (log-str "Request: " request)
  (log-str "response-host: " response-host)
  (log-str "response-port: " response-port)
  (log-str "message: " message))
  ; (when (and response-host response-port) ; TODO: Use spec to validate these fields
  ;   (runway/send-to-server! response-host
  ;                           response-port
  ;                           (json/write-str
  ;                            (assoc-in request [:script-args :error-args] message)
  ;                            :key-fn (comp kebab->camel name)))))

;;==============================================================================
;; Create Match Job Functions
;;==============================================================================

(defn- create-match-job!
  [{:keys [display-name user-id ignition-time lat lon] :as params}]
  (let [runway-job-id       (str "dps-" (UUID/randomUUID)) ; NOTE see the get-server-based-on-job-id for why we append "dps" -- this is a temporary work-around
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
                             :async?        true ; TODO confirm this
                             :priority?     true ; TODO confirm this
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
                             :message             (str "Match Drop #" match-job-id " initiated from Pyrecast.\n")
                             :elmfire-done?       false
                             :gridfire-done?      false
                             ;; TODO have seperate requests for each service: :dps-request, :elmfire-request, :gridfire-request, :geosync-request
                             :request             request
                             :match-job-id        match-job-id
                             :runway-job-id       runway-job-id
                             :geoserver-workspace geoserver-workspace}]
    (update-match-job! match-job)
    (log-str "Initiating match drop job #" match-job-id)
    ; (log-str "Match Drop massive request is: " (pprint request))
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
                       ;; TODO we need to include either common-args or the geoserver workspace so that the geosync server knows what to delete
                       :script-args   {:geosync-args {:action "remove"}}}
        match-job     {:match-job-id  match-job-id
                       :md-status     3 ; 3 indicates Match Drop is primed for deletion
                       :message       (str "Match Drop " match-job-id " is queued to be deleted.\n")
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

;; TODO test me and the log-str
;; TODO think about return type here
(defn update-elmfire-gridfire-requests!
  "Updates the match drop request in the DB (specifically the ELMFIRE and GridFire portions)
   to use the datacube returned by the DPS and returns the updated request."
  [match-job-id request {:keys [datacube]}]
  (let [updated-request (-> request
                            (assoc-in [:script-args :elmfire-args :datacube] datacube)
                            (assoc-in [:script-args :gridfire-args :datacube] datacube))]
    (log-str (str "Inside update-elmfire-gridfire-requests! New request is: " updated-request))
    (update-match-job! {:match-job-id match-job-id :request updated-request})
    updated-request))

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
  [{:keys [match-job-id md-status elmfire-done? gridfire-done? request geoserver-workspace] :as job}
   {:keys [job-id message] :as response-msg-edn}]
  (log-str "Inside of runway-process-complete!")
  ;; The result of this message will be our return value from Runway
  (if-not message
    (update-match-drop-on-error! match-job-id {:job-id  job-id
                                               :message "No return value from this server was provided."})
    (do
      ; (update-match-job! {:match-job-id match-job-id :message message}))
      (condp = (get-server-based-on-job-id job-id)
        ;; After the DPS job is completed, queue the ELMFIRE and GridFire servers
        "dps"
        (do
          ;; Update the ELMFIRE and GridFire request params to use the provided {:datacube "/some/path/to/tarball.tar"}
          (let [updated-request (update-elmfire-gridfire-requests! match-job-id request (edn/read-string message))]
            (log-str "Initiating ELMFIRE run...")
            ;; TODO we need to get the new job with the new request in there, right now we just send the job
            ;; we can probably just (let [{:keys [request md-status match-job-id] :as job} (get-match-job-from-runway-id job-id) and do away with returning the request from the above function, just update the database
            ; need to think if there's a race case of updating the database and then immediately query the data base to get the job
            ;; we could also maybe sql-request->job after calling the SQL update function -- figure out if the update SQL command will return what we want -- doesn't look like it will unles we add "RETURNING"
            ; (send-to-server-wrapper! (get-md-config :elmfire-host) (get-md-config :elmfire-port) job)
            (log-str "Initiating GridFire run...")))
            ; (send-to-server-wrapper! (get-md-config :gridfire-host) (get-md-config :gridfire-port) job))

        ;; TODO launching two geosync calls for the same directory might break if we switch to image mosaics
        ;; TODO we only need one call to the GeoSync service now
        ;; (or (get-md-config :elmfire-host) (get-md-config :gridfire-host))
        ;; After the ELMFIRE job is completed, queue the GeoSync server
        "elmfire"
        (send-to-server-wrapper! (get-md-config :geosync-host) (get-md-config :geosync-port) job {:model "elmfire"}) ;; FIXME, don't need model

        ;; After the GridFire job is completed, queue the GeoSync server
        "gridfire"
        (send-to-server-wrapper! (get-md-config :geosync-host) (get-md-config :geosync-port) job {:model "gridfire"}) ;; FIXME, don't need model

        "geosync" ; After GeoSync job is completed, we either have a deletion request or an addition request
        (condp = md-status ;; TODO make everything below this into a helper fn
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
                                  "workspace-name" geoserver-workspace})))

        (do
          (log-str "Inside the 'else' branch runway-process-complete!")
          (update-match-drop-on-error! match-job-id response-msg-edn))))))

;; This separate function allows reload to work in dev mode for easier development
(defn- process-response-msg
  "This function is what gets called every time the Pyrecast server recieves a
   message from any one of the Runway servers (e.g. the GridFire Runway server).

   The `msg` argument gets passed in in JSON format and thus needs to be parsed into `response`.
   An example `response-msg-edn` argument can be seen below:

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
  ; (log-str "Inside of `process-response-msg`. the response-msg-edn is: " response-msg-edn)
  (if-let [{:keys [job-id ; Note that this is the `runway-job-id`
                   status
                   response-host ;;TODO remove me and response-port
                   response-port]} response-msg-edn]
    ;; TODO: Use spec to validate the message
    (let [{:keys [request md-status match-job-id] :as job} (get-match-job-from-runway-id job-id)]
      ; (log-str "The response was parsed properly: " response)
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
          0 (runway-process-complete! job response-msg-edn)             ; Runway job DONE
          1 (update-match-drop-on-error! match-job-id response-msg-edn)    ; Runway job ERROR
          2 (do
              (log-str "Inside of the update-match-drop-message! branch in `process-response-msg` response is: " response-msg-edn)
              (update-match-drop-message! match-job-id response-msg-edn))))) ; Runway job INFO
    (log-str "Invalid JSON.")))

(defn match-drop-server-msg-handler
  "Accepts a message from the socket server and sends it to be processed.
   The handler function for the Match Drop server started inside of `server.clj`."
  [^SSLSocket socket response-msg-json]
  (log-str "BACK INSIDE OF PROCESS MESSAGE")
  (thread
    (try
      (loop [response-msg-edn (json-str->edn response-msg-json)]
        (let [status (:status response-msg-edn)]
          (log-response! response-msg-edn) ; TODO do we need this?
          (cond
            (.isClosed socket) (do
                                 (log-str "socket is closed!!")
                                 (.close socket))
            (= 2 status)       (do
                                 (process-response-msg response-msg-edn)
                                 (recur (json-str->edn (runway/read-socket! socket)))) ;; TODO think about what to do for logging in progress (status 2)
            (#{0 1} status)    (do
                                 (log-str "The status is " status ", we can stop recurring after one more call to do-processing.")
                                 (process-response-msg response-msg-edn))
                                 ;; Do we keep reading the socket for the next request???
                                 ; (recur (json-str->edn (runway/read-socket! socket)))) ;; TODO think about what to do for logging in progress (status 2)
            :else              (do
                                 (log-str "Something went wrong. Closing socket!")
                                 (.close socket)))))
      (catch Exception e
        ;; TODO update the match job with an error code and exit gracefully here
        ;; call update-match-drop-on-error! or something
        (log-str (str "Exception in match-drop-server-msg-handler: " e "."
                      "The " socket " will be closed.")))
      (finally (.close socket)))))
