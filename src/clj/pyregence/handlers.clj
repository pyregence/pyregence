(ns pyregence.handlers
  (:require [clojure.data.json        :as    json]
            [clojure.repl             :refer [demunge]]
            [clojure.string           :as    str]
            [pyregence.authentication :refer [has-match-drop-access? is-admin?]]
            [ring.util.codec          :refer [url-encode]]
            [ring.util.response       :refer [redirect]]
            [triangulum.config        :refer [get-config]]
            [triangulum.handler       :refer [development-app]]
            [triangulum.logging       :refer [log-str set-log-path!]]
            [triangulum.response      :refer [data-response]]
            [triangulum.views         :refer [render-page]]
            [triangulum.worker        :refer [start-workers!]]))

(def not-found-handler (render-page "/not-found"))

(defn redirect-handler [{:keys [session query-string uri] :as _request}]
  (let [full-url (url-encode (str uri (when query-string (str "?" query-string))))]
    (if (:user-id session)
      (redirect (str "/?flash_message=You do not have permission to access "
                     full-url))
      (redirect (str "/login?flash_message=You must login to see "
                     full-url)))))

(defn route-authenticator [{:keys [session params] :as _request} auth-type]
  (let [user-id (:user-id session -1)]
    (every? (fn [auth-type]
              (case auth-type
                :admin      (is-admin? user-id)
                :match-drop (has-match-drop-access? user-id)
                :token      (= (:auth-token params)
                               (get-config :pyregence.secrets/pyr-auth-token))
                :user       (pos? user-id)
                true))
            (if (keyword? auth-type) [auth-type] auth-type))))

(defn- fn->sym [f]
  (-> (str f)
      (demunge)
      (str/split #"@")
      (first)
      (symbol)))

(defn clj-handler [function]
  (fn [{:keys [params content-type]}]
    (let [clj-args   (if (= content-type "application/edn")
                       (:clj-args params [])
                       (json/read-str (:clj-args params "[]")))
          clj-result (apply function clj-args)]
      (log-str "CLJ Call: " (cons (fn->sym function) clj-args))
      (if (:status clj-result)
        clj-result
        (data-response clj-result {:type (if (= content-type "application/edn") :edn :json)})))))

;; Figwheel

(defonce workers-delay
  (delay
   (let [workers (get-config :triangulum.worker/workers)]
     (when (seq workers)
       (println "Starting worker jobs")
       (start-workers! workers)))))

(defonce log-dir-delay
  (delay
    (let [log-dir (get-config :triangulum.server/log-dir)]
      (set-log-path! (or log-dir "")))))

(defn development-app-wrapper
  "Funky wrap-a-doodler"
  [request]
  @workers-delay
  @log-dir-delay
  (development-app request))
