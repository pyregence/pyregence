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
  {"elmfire.pyregence.org"  "ELMFIRE"
   "gridfire.pyregence.org" "GridFire"
   "wx.pyregence.org"       "Weather"
   "data.pyregence.org"     "GeoServer"})

;; SQL fns

(defn- sql-result->job [result]
  (-> result
      (rename-keys {:job_id        :job-id
                    :user_id       :user-id
                    :created_at    :created-at
                    :updated_at    :updated-at
                    :md_status     :md-status
                    :display_name  :display-name
                    :job_log       :log ; TODO: rename log to job-log
                    :elmfire_done  :elmfire-done?
                    :gridfire_done :gridfire-done?})
      (update :request json->clj)))

(defn- get-match-job [job-id]
  (-> (call-sql "get_match_job" job-id)
      (first)
      (sql-result->job)))

(defn- count-all-running-match-drops []
  (sql-primitive (call-sql "count_all_running_match_jobs")))

(defn- count-running-user-match-jobs [user-id]
  (sql-primitive (call-sql "count_running_user_match_jobs" user-id)))

(defn- initialize-match-job! [user-id]
  (sql-primitive (call-sql "initialize_match_job" user-id)))

(defn- update-match-job! [job-id {:keys [md-status display-name message elmfire-done? gridfire-done? request]}]
  (call-sql "update_match_job" job-id md-status display-name message elmfire-done? gridfire-done? (when request (clj->json request))))

(defn- send-to-server-wrapper!
  [host port {:keys [job-id request] :as job} & [extra-payload]]
  (when-not (send-to-server! host
                             port
                             (-> request
                                 (update :script-args #(merge % extra-payload))
                                 (json/write-str :key-fn kebab->camel)))
    (update-match-job! job-id {:md-status 1
                               :message   (str "Connection to " host " failed.")})))

(defn- create-match-job!
  [{:keys [display-name user-id ignition-time] :as params}]
  (let [job-id              (initialize-match-job! user-id)
        model-time          (convert-date-string ignition-time)
        ;; TODO: do we still need the fire-name
        fire-name           (str "match-drop-" job-id)
        ;; TODO /var/www/html should not be hardcoded.
        data-dir            (str "/var/www/html/fire_spread_forecast/match-drop-" job-id "/" model-time)
        geoserver-workspace (str "fire-spread-forecast_match-drop-" job-id "_" model-time)
        ;; TODO consider different payloads per request instead of one large one.
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
    (send-to-server-wrapper! (get-md-config :wx-host)
                             (get-md-config :wx-port)
                             job)
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

     (< 5 (count-all-running-match-drops))
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

(defn- get-model [message]
  (second (re-find #".*(elmfire|gridfire).*" (str/lower-case message))))
  
(defn- process-complete! [{:keys [job-id] :as job} {:keys [response-host message]}]
  (when message
    (update-match-job! job-id {:message message}))
  (condp = response-host
    (get-md-config :wx-host)
    (do
      (send-to-server-wrapper! (get-md-config :elmfire-host) (get-md-config :elmfire-port) job)
      (send-to-server-wrapper! (get-md-config :gridfire-host) (get-md-config :gridfire-port) job))

    ;; TODO launching two geosync calls for the same directory might break if we switch to image mosaics
    (get-md-config :elmfire-host)
    (send-to-server-wrapper! (get-md-config :data-host) (get-md-config :data-port) job {:model "elmfire"})

    (get-md-config :gridfire-host)
    (send-to-server-wrapper! (get-md-config :data-host) (get-md-config :data-port) job {:model "gridfire"})

    (get-md-config :data-host)
    (let [{:keys [elmfire-done? gridfire-done? request]} job
          model     (get-model message)
          elmfire?  (or elmfire-done?  (= "elmfire" model))
          gridfire? (or gridfire-done? (= "gridfire" model))]
      (if (and elmfire? gridfire?)
        (do (update-match-job! job-id {:md-status      0
                                       :gridfire-done? true
                                       :elmfire-done?  true})
            (set-capabilities! (get-in request [:script-args :geosync-args :geoserver-workspace])))
        (update-match-job! job-id {:gridfire-done? gridfire?
                                   :elmfire-done?  elmfire?})))))

(defn- process-error! [job-id {:keys [message response-host]}]
  (let [db-message (str (get host-names response-host) ": " message)]
    (log-str "Match drop job #" job-id " error: " db-message)
    (update-match-job! job-id {:md-status 1 :message db-message})))

(defn- process-message! [job-id {:keys [message response-host]}]
  (let [db-message (str (get host-names response-host) ": " message)]
    (update-match-job! job-id {:message db-message})))

(defn- error-response [request response-host response-port message]
  (when (and response-host response-port) ; TODO: Use spec to validate these fields
    (send-to-server! response-host
                     response-port
                     (json/write-str
                      (assoc-in request [:script-args :error-args]
                                (clj->json {:status  1
                                            :message (str "Bad Request: " message)}))
                      :key-fn (comp kebab->camel name)))))

;; This separate function allows reload to work in dev mode for easier development
(defn- do-processing [msg]
  (let [{:keys [job-id status response-host response-port]
         :as   response}                     (nil-on-error (json/read-str msg :key-fn (comp keyword camel->kebab)))
        {:keys [request md-status] :as job} (get-match-job job-id)]
    (if (and (pos? job-id)
             (= 2 md-status))
      (case status
        0 (process-complete! job response)
        1 (process-error!    job-id response)
        2 (process-message!  job-id response)
        (error-response request response-host response-port "Invalid status code."))
      (error-response request response-host response-port "Invalid or missing job-id."))))

(defn process-message
  "Accepts a message from the socket server and sends it to be processed."
  [msg]
  (do-processing msg))
