(ns pyregence.match-drop
  (:import  [java.util TimeZone]
            [java.text SimpleDateFormat])
  (:require [clojure.data.json :as json]
            [clojure.string    :as str]
            [clojure.set       :refer [rename-keys]]
            [triangulum.config          :refer [get-config]]
            [triangulum.database        :refer [call-sql sql-primitive]]
            [triangulum.logging         :refer [log-str]]
            [triangulum.sockets         :refer [send-to-server!]]
            [triangulum.type-conversion :refer [val->long json->clj clj->json]]
            [pyregence.capabilities :refer [set-capabilities!]]
            [pyregence.utils        :refer [nil-on-error]]
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

(defn- convert-date-string [date-str]
  (let [in-format  (SimpleDateFormat. "yyyy-MM-dd HH:mm z")
        out-format (doto (SimpleDateFormat. "yyyyMMdd_HHmmss")
                     (.setTimeZone (TimeZone/getTimeZone "UTC")))]
    (->> date-str
         (.parse in-format)
         (.format out-format))))

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
      (rename-keys {:created_at    :created-at
                    :elmfire_done  :elmfire-done?
                    :display_name  :display-name
                    :gridfire_done :gridfire-done?
                    :job_id        :job-id
                    :user_id       :user-id
                    :job_log       :log
                    :md_status     :md-status
                    :updated_at    :updated-at})
      (update :request json->clj)))

(defn- get-match-job [job-id]
  (when (integer? job-id)
    (some-> (call-sql "get_match_job" job-id)
            (first)
            (sql-result->job))))

(defn- count-all-running-match-drops []
  (sql-primitive (call-sql "count_all_running_match_jobs")))

(defn- count-running-user-match-jobs [user-id]
  (sql-primitive (call-sql "count_running_user_match_jobs" user-id)))

(defn- initialize-match-job! [user-id]
  (sql-primitive (call-sql "initialize_match_job" user-id)))

(defn- update-match-job! [job-id {:keys [md-status display-name message elmfire-done? gridfire-done? request]}]
  (call-sql "update_match_job" job-id md-status display-name message elmfire-done? gridfire-done? (when request (clj->json request))))

(defn- send-to-server-wrapper!
  [host port job-id & [extra-payload]]
  (when-not (send-to-server! host
                             port
                             (json/write-str
                              (-> (get-match-job job-id)
                                  (:request)
                                  (merge extra-payload))
                              :key-fn kebab->camel))
    (update-match-job! job-id {:md-status 1
                               :message   (str "Connection to " host " failed.")})))

(defn- create-match-job!
  [{:keys [display-name user-id ignition-time] :as params}]
  (let [job-id              (initialize-match-job! user-id)
        model-time          (convert-date-string ignition-time)
        ;; TODO: do we still need the fire-name
        fire-name           (str "match-drop-" job-id)
        ;; TODO: /var/www/html should not be hardcoded.
        data-dir            (str (get-md-config :data-dir) "/match-drop-" job-id "/" model-time)
        geoserver-workspace (str "fire-spread-forecast_match-drop-" job-id "_" model-time)
        ;; TODO: consider different payloads per request instead of one large one.
        request             {:job-id        job-id
                             :response-host (get-md-config :app-host)
                             :response-port (get-md-config :app-port)
                             :script-args   (-> {:common-args  (merge params
                                                                      {:fire-name     fire-name
                                                                       :ignition-time ignition-time})
                                                 :dps-args     {:add-to-active-fires "yes"
                                                                :scp-input-deck      "both"
                                                                :south-buffer        24
                                                                :west-buffer         24
                                                                :east-buffer         24
                                                                :north-buffer        24}
                                                 :geosync-args {:action              "add"
                                                                :data-dir            data-dir
                                                                :geoserver-workspace geoserver-workspace}}
                                                (update-vals clj->json))}
        job                 {:display-name   (or display-name (str "Match Drop " job-id))
                             :md-status      2
                             :message        (str "Job " job-id " Initiated.")
                             :elmfire-done?  false
                             :gridfire-done? false
                             :request        request}]
    (update-match-job! job-id job)
    (log-str "Initiating match drop job #" job-id)
    (send-to-server-wrapper! (get-md-config :dps-host)
                             (get-md-config :dps-port)
                             job-id)
    {:job-id job-id}))

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

;;; Job queue progression

(defn get-md-status
  "Returns the current status of the given match drop run."
  [job-id]
  (data-response (-> (get-match-job job-id)
                     (select-keys [:message :md-status :log]))))

(defn- process-complete! [job-id {:keys [response-host message model]}]
  (when message
    (update-match-job! job-id {:message message}))
  (condp = response-host
    (get-md-config :dps-host)
    (do
      (send-to-server-wrapper! (get-md-config :elmfire-host) (get-md-config :elmfire-port) job-id)
      (send-to-server-wrapper! (get-md-config :gridfire-host) (get-md-config :gridfire-port) job-id))

    ;; TODO launching two geosync calls for the same directory might break if we switch to image mosaics
    (get-md-config :elmfire-host)
    (send-to-server-wrapper! (get-md-config :geosync-host) (get-md-config :geosync-port) job-id {:model "elmfire"})

    (get-md-config :gridfire-host)
    (send-to-server-wrapper! (get-md-config :geosync-host) (get-md-config :geosync-port) job-id {:model "gridfire"})

    (get-md-config :geosync-host)
    (let [{:keys [elmfire-done? gridfire-done? request]} (get-match-job job-id)
          elmfire?  (or elmfire-done?  (= "elmfire" model))
          gridfire? (or gridfire-done? (= "gridfire" model))]
      (if (and elmfire? gridfire?)
        (do (update-match-job! job-id {:md-status      0
                                       :gridfire-done? true
                                       :elmfire-done?  true})
            (set-capabilities! {"geoserver-key"  "match-drop"
                                "workspace-name" (:geoserver-workspace request)}))
        (update-match-job! job-id {:gridfire-done? gridfire?
                                   :elmfire-done?  elmfire?})))))

(defn- process-error! [job-id {:keys [message]}]
  (log-str "Match drop job #" job-id " error: " message)
  (update-match-job! job-id {:md-status 1 :message message}))

(defn- process-message! [job-id {:keys [message response-host]}]
  (update-match-job! job-id {:message (str (get host-names response-host) ": " message)}))

(defn- error-response [response-host response-port message]
  (when (and response-host response-port) ; TODO: Use spec to validate these fields
    (send-to-server! response-host
                     response-port
                     (json/write-str
                      {:status        1
                       :message       (str "Bad Request: " message)
                       :response-host (get-md-config :app-host)
                       :response-port (get-md-config :app-port)}
                      :key-fn (comp kebab->camel name)))))

;; This separate function allows reload to work in dev mode for easier development
(defn- do-processing [msg]
  (if-let [{:keys [job-id
                   status
                   response-host
                   response-port]
            :as   response} (nil-on-error
                             (json/read-str msg :key-fn (comp keyword camel->kebab)))]
    ;; TODO: Use spec to validate the message
    (if-let [job (get-match-job job-id)]
      (if (= 2 (:md-status job))        ; 2 indicates that the job has not yet completed.
        (case status
          0 (process-complete! job-id response) ; DONE
          1 (process-error!    job-id response) ; ERROR
          2 (process-message!  job-id response) ; INFO
          (error-response response-host response-port (format "Invalid status code: %s" status)))
        (error-response response-host response-port (format "Job %s has exited." job-id)))
      (error-response response-host response-port (format "Invalid job-id: %s" job-id)))
    (log-str "Invalid JSON.")))

(defn process-message
  "Accepts a message from the socket server and sends it to be processed."
  [msg]
  (do-processing msg))
