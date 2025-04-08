(ns pyregence.handlers
  (:require [cider.nrepl              :refer [cider-nrepl-handler]]
            [clojure.data.json        :as    json]
            [clojure.repl             :refer [demunge]]
            [clojure.string           :as    str]
            [nrepl.server             :as    nrepl-server]
            [pyregence.authentication :refer [has-match-drop-access? is-admin?]]
            [ring.util.codec          :refer [url-encode]]
            [ring.util.response       :refer [redirect]]
            [triangulum.config        :refer [get-config]]
            [triangulum.handler       :refer [development-app]]
            [triangulum.logging       :refer [log-str set-log-path!]]
            [triangulum.response      :refer [data-response]]
            [triangulum.views         :refer [render-page]]
            [triangulum.worker        :refer [start-workers!]]))

(def not-found-handler (comp #(assoc % :status 404) (render-page "/not-found")))

(defn redirect-handler [{:keys [session query-string uri] :as _request}]
  (let [full-url (url-encode (str uri (when query-string (str "?" query-string))))]
    (if (:user-id session)
      (redirect (str "/?flash_message=You do not have permission to access "
                     full-url))
      (redirect (str "/login?flash_message=You must login to see "
                     full-url)))))

(defn route-authenticator [{:keys [headers session] :as request} auth-type]
  (let [user-id      (:user-id session -1)
        ;; Extract Bearer token from Authorization header
        bearer-token (some->> (get headers "authorization")
                              (re-find #"(?i)^Bearer\s+(.+)$")
                              second)
        valid-token? (= bearer-token (get-config :triangulum.views/client-keys :auth-token))]

    (every? (fn [auth-type]
              (case auth-type
                :admin (is-admin? user-id)
                :match-drop (has-match-drop-access? user-id)
                ;; TODO: token generated per user specifically and validated cryptographically
                :token (do
                         #_(when (and (nil? bearer-token) (contains? (:params request) :auth-token))
                             (log-str "Token in URL params detected"))
                         valid-token?)
                :user  (pos? user-id)
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

(defonce nrepl-server (atom nil))

(defonce nrepl-delay
  (delay
    (let [{:keys [nrepl cider-nrepl nrepl-bind nrepl-port]
           :or   {nrepl-bind "127.0.0.1"
                  nrepl-port 5555}}
          (get-config :server)]
      (cond nrepl
            (do
              (println "Starting nREPL server on" (str nrepl-bind ":" nrepl-port))
              (reset! nrepl-server (nrepl-server/start-server :bind nrepl-bind
                                                              :port nrepl-port)))

            cider-nrepl
            (do
              (println "Starting CIDER nREPL server on" (str nrepl-bind ":" nrepl-port))
              (reset! nrepl-server (nrepl-server/start-server :bind nrepl-bind
                                                              :port nrepl-port
                                                              :handler cider-nrepl-handler)))))))

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
  @nrepl-delay
  @workers-delay
  @log-dir-delay
  (development-app request))
