(ns pyregence.match-drop
  (:import  [java.util TimeZone]
            [java.text SimpleDateFormat])
  (:require [clojure.data.json :as json]
            [clojure.string    :as str]
            [clojure.set       :refer [rename-keys]]
            [triangulum.type-conversion :refer [val->long json->clj clj->json]]
            [pyregence.capabilities :refer [set-capabilities!]]
            [pyregence.config       :refer [get-config]]
            [pyregence.logging      :refer [log-str]]
            [pyregence.sockets      :refer [send-to-server!]]
            [pyregence.views        :refer [data-response]]
            [pyregence.database     :refer [call-sql sql-primitive]]))

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

(defmacro nil-on-error
  [& body]
  (let [_ (gensym)]
    `(try ~@body (catch Exception ~_ nil))))

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
      (rename-keys {:created_at    :created-at
                    :elmfire_done  :elmfire-done?
                    :fire_name     :fire-name
                    :gridfire_done :gridfire-done?
                    :job_id        :job-id
                    :user_id       :user-id
                    :job_log       :log
                    :md_status     :md-status
                    :updated_at    :updated-at})
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

(defn- update-match-job! [job-id {:keys [md-status fire-name message elmfire-done? gridfire-done? request]}]
  (call-sql "update_match_job" job-id md-status fire-name message elmfire-done? gridfire-done? (when (some? request) (clj->json request))))

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
  [{:keys [fire-name user-id ignition-time] :as params}]
  (let [job-id        (initialize-match-job! user-id)
        model-time    (convert-date-string ignition-time)
        request       (merge params
                             ;; TODO consider different payloads per request instead of one large one.
                             {:response-host       (get-md-config :app-host)
                              :response-port       (get-md-config :app-port)
                              :fire-name           (str "match-drop-" job-id)
                              :ignition-time       ignition-time
                              ;; Data Provisioning
                              :add-to-active-fires "yes"
                              :scp-input-deck      "both"
                              :south-buffer        24
                              :west-buffer         24
                              :east-buffer         24
                              :north-buffer        24
                              ;; GeoSync
                              :data-dir            (str "/var/www/html/fire_spread_forecast/match-drop-" job-id "/" model-time)
                              :geoserver-workspace (str "fire-spread-forecast_match-drop-" job-id "_" model-time)
                              :action              "add"})
        job           {:user-id        user-id
                       :fire-name      fire-name
                       :md-status      2
                       :message        (str "Job " job-id " Initiated.")
                       :elmfire-done?  false
                       :gridfire-done? false
                       :request        request}]
    (update-match-job! job-id job)
    (log-str "Initiating match drop job #" job-id)
    (send-to-server-wrapper! (get-md-config :wx-host)
                             (get-md-config :wx-port)
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

(defn- process-complete! [job-id {:keys [response-host message model]}]
  (when message
    (update-match-job! job-id {:message message}))
  (condp = response-host
    (get-md-config :wx-host)
    (do
      (send-to-server-wrapper! (get-md-config :elmfire-host) (get-md-config :elmfire-port) job-id)
      (send-to-server-wrapper! (get-md-config :gridfire-host) (get-md-config :gridfire-port) job-id))

    ;; TODO launching two geosync calls for the same directory might break if we switch to image mosaics
    (get-md-config :elmfire-host)
    (send-to-server-wrapper! (get-md-config :data-host) (get-md-config :data-port) job-id {:model "elmfire"})

    (get-md-config :gridfire-host)
    (send-to-server-wrapper! (get-md-config :data-host) (get-md-config :data-port) job-id {:model "gridfire"})

    (get-md-config :data-host)
    (let [{:keys [elmfire-done? gridfire-done? request]} (get-match-job job-id)
          elmfire?  (or elmfire-done?  (= "elmfire" model))
          gridfire? (or gridfire-done? (= "gridfire" model))]
      (if (and elmfire? gridfire?)
        (do (update-match-job! job-id {:md-status      0
                                       :gridfire-done? true
                                       :elmfire-done?  true})
            (set-capabilities! (:geoserver-workspace request)))
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
  (let [{:keys [fire-name status response-host response-port]
         :or {fire-name ""}
         :as response} (nil-on-error (json/read-str msg :key-fn (comp keyword camel->kebab)))
        job-id (-> fire-name (str/split #"-") (last) (val->long))]
    (if (and (pos? job-id)
             (= 2 (:md-status (get-match-job job-id))))
      (case status
        0 (process-complete! job-id response)
        1 (process-error!    job-id response)
        2 (process-message!  job-id response)
        (error-response response-host response-port "Invalid status code."))
      (error-response response-host response-port "Invalid or missing fire-name."))))

(defn process-message
  "Accepts a message from the socket server and sends it to be processed."
  [msg]
  (do-processing msg))
