(ns pyregence.match-drop
  (:import  [java.util TimeZone Date]
            [java.text SimpleDateFormat])
  (:require [clojure.data.json :as json]
            [clojure.string    :as str]
            [pyregence.capabilities :refer [set-capabilities!]]
            [pyregence.logging      :refer [log-str]]
            [pyregence.sockets      :refer [send-to-server!]]
            [pyregence.views        :refer [data-response]]))

(defonce ^:private job-queue (atom {}))

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

(defn- val->long
  [v & [default]]
  (cond
    (instance? Long v) v
    (number? v)        (long v)
    :else              (try
                         (Long/parseLong v)
                         (catch Exception _ (long (or default -1))))))

(defn- convert-date-string [date-str]
  (let [in-format  (SimpleDateFormat. "yyyy-MM-dd HH:mm z")
        out-format (doto (SimpleDateFormat. "yyyyMMdd_HHmmss")
                     (.setTimeZone (TimeZone/getTimeZone "UTC")))]
    (->> date-str
         (.parse in-format)
         (.format out-format))))

(defn timestamp []
  (.format (SimpleDateFormat. "MM/dd HH:mm:ss") (Date.)))

(defmacro nil-on-error
  [& body]
  (let [_ (gensym)]
    `(try ~@body (catch Exception ~_ nil))))

;;; Static data

(def ^:private host-names
  {"elmfire.pyregence.org"  "ELMFIRE"
   "gridfire.pyregence.org" "GridFire"
   "wx.pyregence.org"       "Weather"
   "data.pyregence.org"     "GeoServer"})

(defn append-log [job {:keys [message] :as m}]
  (cond-> (merge job m)
    message (assoc :log (str (:log job) (format "\n%s - %s" (timestamp) message)))))

(defn- set-job-keys! [job-id m]
  (swap! job-queue update job-id append-log m))

(defn- send-to-server-wrapper!
  [host port job-id & [extra-payload]]
  (when-not (send-to-server! host
                             port
                             (json/write-str
                              (-> @job-queue
                                  (get-in [job-id :request])
                                  (merge extra-payload))
                              :key-fn kebab->camel))
    (set-job-keys! job-id
                   {:md-status 1
                    :message   (str "Connection to " host " failed.")})))
(defn initiate-md!
  "Creates a new match drop run and starts the analysis."
  [{:keys [user-id ignition-time] :as params}]
  (let [job-id        (if (seq @job-queue) ; TODO get from SQL
                        (->> @job-queue
                             (keys)
                             (apply max)
                             (inc))
                        (quot (System/currentTimeMillis) 100000)) ; This is temporary until we get job-id from PG
        model-time    (convert-date-string ignition-time)
        request       (merge params
                             ;; TODO consider different payloads per request instead of one large one.
                             {:response-host       "pyregence-dev.sig-gis.com"
                              :response-port       31337
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
                              :action              "add"})]
    (swap! job-queue
           assoc
           job-id
           {:user-id        user-id
            :md-status      2
            :message        (str "Job " job-id " Initiated.")
            :log            (format "%s - Job %d Initiated" (timestamp) job-id)
            :elmfire-done?  false
            :gridfire-done? false
            :request        request})
    (log-str "Initiating match drop job #" job-id)
    (send-to-server-wrapper! "wx.pyregence.org" 31337 job-id)
    job-id))

;;; Job queue progression

(defn get-md-status
  "Returns the current status of the given match drop run."
  [job-id]
  (data-response (-> @job-queue
                     (get job-id)
                     (select-keys [:message :md-status :log]))))

(defn- process-complete! [job-id {:keys [response-host message model]}]
  (when message
    (set-job-keys! job-id {:message message}))
  (case response-host
    "wx.pyregence.org"
    (do
      (send-to-server-wrapper! "elmfire.pyregence.org" 31338 job-id)
      (send-to-server-wrapper! "gridfire.pyregence.org" 31337 job-id))

    ;; TODO launching two geosync calls for the same directory might break if we switch to image mosaics
    "elmfire.pyregence.org"
    (send-to-server-wrapper! "data.pyregence.org" 31337 job-id {:model "elmfire"})

    "gridfire.pyregence.org"
    (send-to-server-wrapper! "data.pyregence.org" 31337 job-id {:model "gridfire"})

    "data.pyregence.org"
    (let [{:keys [elmfire-done? gridfire-done?]} (get @job-queue job-id)
          elmfire?  (or elmfire-done?  (= "elmfire" model))
          gridfire? (or gridfire-done? (= "gridfire" model))]
      (if (and elmfire? gridfire?)
        (do (set-job-keys! job-id {:md-status      0
                                   :gridfire-done? true
                                   :elmfire-done?  true})
            (set-capabilities! (get-in @job-queue [job-id :request :geoserver-workspace])))
        (set-job-keys! job-id {:gridfire-done? gridfire?
                               :elmfire-done?  elmfire?})))))

(defn- process-error! [job-id {:keys [message]}]
  (log-str "Match drop job #" job-id " error: " message)
  (set-job-keys! job-id {:md-status 1 :message message}))

(defn- process-message! [job-id {:keys [message response-host]}]
  (set-job-keys! job-id {:message (str (get host-names response-host) ": " message)}))

;; This separate function allows reload to work in dev mode for easier development
(defn- do-processing [msg]
  (let [{:keys [fire-name status]
         :or {fire-name ""}
         :as response} (nil-on-error (json/read-str msg :key-fn (comp keyword camel->kebab)))
        job-id (-> fire-name (str/split #"-") (last) (val->long))]
    (when (and (pos? job-id)
               (= 2 (get-in @job-queue [job-id :md-status])))
      (case status
        0 (process-complete! job-id response)
        1 (process-error!    job-id response)
        2 (process-message!  job-id response)
        nil))))

(defn process-message
  "Accepts a message from the socket server and sends it to be processed."
  [msg]
  (do-processing msg))
