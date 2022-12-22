(ns pyregence.match-drop
  (:import  [java.util UUID])
  (:require [clojure.data.json :as json]
            [clojure.string    :as str]
            [clojure.set       :refer [rename-keys]]
            [triangulum.config          :refer [get-config]]
            [triangulum.database        :refer [call-sql sql-primitive]]
            [triangulum.logging         :refer [log-str]]
            [triangulum.sockets         :refer [send-to-server!]]
            [triangulum.type-conversion :refer [json->clj clj->json]]
            [pyregence.capabilities :refer [set-capabilities!]]
            [pyregence.utils        :refer [nil-on-error convert-date-string]]
            [pyregence.views        :refer [data-response]]))

;;; Helper Functions
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

;;; Static data

(def ^:private host-names
  {(get-md-config :app-host)      (get-md-config :app-name)
   (get-md-config :geosync-host)  (get-md-config :geosync-name)
   (get-md-config :elmfire-host)  (get-md-config :elmfire-name)
   (get-md-config :gridfire-host) (get-md-config :gridfire-name)
   (get-md-config :dps-host)      (get-md-config :dps-name)})

;; SQL fns

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

(defn- send-to-server-wrapper!
  [host port {:keys [match-job-id request]} & [extra-payload]]
  (when-not (send-to-server! host
                             port
                             (-> request
                                 (update :script-args #(merge % extra-payload))
                                 (json/write-str :key-fn kebab->camel)))
    (update-match-job! {:match-job-id match-job-id
                        :md-status    1
                        :message      (str "Connection to " host " failed.")})))

(defn- create-match-job!
  [{:keys [display-name user-id ignition-time _lon _lat] :as params}]
  (let [runway-job-id       (str (UUID/randomUUID))
        match-job-id        (initialize-match-job! user-id)
        model-time          (convert-date-string ignition-time)
        fire-name           (str "match-drop-" match-job-id)
        data-dir            (str (get-md-config :data-dir) "/match-drop-" match-job-id "/" model-time)
        geoserver-workspace (str "fire-spread-forecast_match-drop-" match-job-id "_" model-time)
        ;; TODO: consider different payloads per request instead of one large one.
        request             {:job-id        runway-job-id
                             :response-host (get-md-config :app-host)
                             :response-port (get-md-config :app-port)
                             :script-args   {:common-args  (merge params {:ignition-time ignition-time
                                                                          :fire-name     fire-name})
                                             :dps-args     {:add-to-active-fires "yes"
                                                            :scp-input-deck      "both"
                                                            :south-buffer        24
                                                            :west-buffer         24
                                                            :east-buffer         24
                                                            :north-buffer        24}
                                             :geosync-args {:action              "add"
                                                            :data-dir            data-dir
                                                            :geoserver-url       (get-config :geoserver :match-drop)
                                                            :geoserver-workspace geoserver-workspace}}}
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
    (send-to-server-wrapper! (get-md-config :dps-host)
                             (get-md-config :dps-port)
                             match-job)
    {:match-job-id match-job-id}))

;;; Public API

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
                       :script-args   {:geosync-args {:action "remove" :geoserver-workspace geoserver-workspace}}}
        match-job     {:match-job-id  match-job-id
                       :md-status     3 ; 3 indicates Match Drop is primed for deletion
                       :message       (str "Match Drop " match-job-id " is queued to be deleted.")
                       :request       request
                       :runway-job-id runway-job-id}]
    ; Update match drop job to replace the `runway-job-id` and the `md-status`
    (update-match-job! match-job)
    ; Remove associated workspace from the GeoServer
    (log-str "Initiating the deletion of match drop job #" match-job-id)
    (if (send-to-server! geosync-host
                         geosync-port
                         (-> request (json/write-str :key-fn kebab->camel)))
      (data-response (str "The " geoserver-workspace " workspace is queued to be removed from " geosync-host "."))
      (data-response (str "Connection to " geosync-host " failed.")
                     {:status 403}))))

;;; Job queue progression

(defn get-md-status
  "Returns the current status of the given match drop run."
  [match-job-id]
  (data-response (-> (get-match-job-from-match-job-id match-job-id)
                     (select-keys [:message :md-status :job-log]))))

(defn- get-model [message]
  (second (re-find #".*(elmfire|gridfire).*" (str/lower-case message))))

(defn- process-complete!
  "The function called when a Runway status returns 0, thus indicating a completed
   Runway request. Depending on the `response-host` of the given Runway job,
   we need to initiate an appropriate next request to continue the Match Drop
   pipeline. For example, after a successful data provisioning server (DPS) Runway
   job, we need to initiate calls to both the Elmfire and Gridfire servers."
  [{:keys [match-job-id md-status] :as job} {:keys [response-host message]}]
  (when message
    (update-match-job! {:match-job-id match-job-id :message message}))
  (condp = response-host
    (get-md-config :dps-host) ; After DPS job is completed, let Elmfire and Gridfire servers know
    (do
      (send-to-server-wrapper! (get-md-config :elmfire-host) (get-md-config :elmfire-port) job)
      (send-to-server-wrapper! (get-md-config :gridfire-host) (get-md-config :gridfire-port) job))

    ;; TODO launching two geosync calls for the same directory might break if we switch to image mosaics
    (get-md-config :elmfire-host) ; After Elmfire job is completed, let GeoSync server know
    (send-to-server-wrapper! (get-md-config :geosync-host) (get-md-config :geosync-port) job {:model "elmfire"})

    (get-md-config :gridfire-host) ; After Gridfire job is completed, let GeoSync server know
    (send-to-server-wrapper! (get-md-config :geosync-host) (get-md-config :geosync-port) job {:model "gridfire"})

    (get-md-config :geosync-host) ; After GeoSync job is completed, we either have a deletion request or an addition request
    (if (= md-status 3)
      (do ; Match drop is now safe to be deleted TODO might need to remove from capabilities
        (log-str "Deleting match job " match-job-id " from the database.")
        (call-sql "delete_match_job" match-job-id))
      (let [{:keys [elmfire-done? gridfire-done? request]} job ; Match drop needs to be added to capabilties TODO turn this first part into a helper function
            model     (get-model message)
            elmfire?  (or elmfire-done?  (= "elmfire" model))
            gridfire? (or gridfire-done? (= "gridfire" model))]
        (if (and elmfire? gridfire?)
          (do (update-match-job! {:match-job-id   match-job-id
                                  :md-status      0
                                  :gridfire-done? true
                                  :elmfire-done?  true})
              (set-capabilities! {"geoserver-key"  "match-drop"
                                  "workspace-name" (get-in request [:script-args :geosync-args :geoserver-workspace])}))
          (update-match-job! {:match-job-id   match-job-id
                              :gridfire-done? gridfire?
                              :elmfire-done?  elmfire?}))))))

(defn- process-error! [match-job-id {:keys [message response-host]}]
  (let [db-message (str (get host-names response-host) ": " message)]
    (log-str "Match drop job #" match-job-id " error: " db-message)
    (update-match-job! {:match-job-id match-job-id
                        :md-status    1
                        :message      db-message})))

(defn- process-message! [match-job-id {:keys [message response-host]}]
  (let [db-message (str (get host-names response-host) ": " message)]
    (update-match-job! {:match-job-id match-job-id :message db-message})))

(defn- error-response [request response-host response-port message]
  (when (and response-host response-port) ; TODO: Use spec to validate these fields
    (send-to-server! response-host
                     response-port
                     (json/write-str
                      (assoc-in request [:script-args :error-args] message)
                      :key-fn (comp kebab->camel name)))))

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
  [msg]
  (if-let [{:keys [job-id ; Note that this is the `runway-job-id`
                   status
                   response-host
                   response-port]
            :as   response} (nil-on-error
                             (json/read-str msg :key-fn (comp keyword camel->kebab)))]
    ;; TODO: Use spec to validate the message
    (let [{:keys [request md-status match-job-id] :as job} (get-match-job-from-runway-id job-id)]
      (cond
        (nil? job)
        (error-response {:job-id job-id} response-host response-port
                        (format "The Match Job with runway-job-id %s could not be found in the Pyrecast database." job-id))

        (< md-status 2) ; 0 and 1 indicate the match job has exited (success, failure respectively)
        (error-response request response-host response-port (format "Job %s has exited." job-id))

        (not (contains? #{0 1 2} status)) ; make sure the Runway job has a valid status code
        (error-response request response-host response-port (format "Invalid Runway status code: %s" status))

        :else ; md-status is 2, indicates job still in-progess
        (case status
          0 (process-complete! job response)             ; Runway job DONE
          1 (process-error!    match-job-id response)    ; Runway job ERROR
          2 (process-message!  match-job-id response)))) ; Runway job INFO
    (log-str "Invalid JSON.")))

(defn process-message
  "Accepts a message from the socket server and sends it to be processed."
  [msg]
  (do-processing msg))
