(ns pyregence.handlers
  (:require [cider.nrepl         :refer [cider-nrepl-handler]]
            [clojure.data.json   :as    json]
            [clojure.repl        :refer [demunge]]
            [clojure.string      :as    str]
            [nrepl.server        :as    nrepl-server]
            [ring.util.codec     :refer [url-encode]]
            [ring.util.response  :refer [redirect]]
            [triangulum.config   :refer [get-config]]
            [triangulum.database :refer [call-sql sql-primitive]]
            [triangulum.handler  :refer [development-app]]
            [triangulum.logging  :refer [log-str set-log-path!]]
            [triangulum.response :refer [data-response]]
            [triangulum.views    :refer [render-page]]
            [triangulum.worker   :refer [start-workers!]]))

(def not-found-handler (comp #(assoc % :status 404) (render-page "/not-found")))

(defn redirect-handler [{:keys [session query-string uri] :as _request}]
  (let [full-url (url-encode (str uri (when query-string (str "?" query-string))))]
    (if (:user-id session)
      (redirect (str "/?flash_message=You do not have permission to access "
                     full-url))
      (redirect (str "/login?flash_message=You must login to see "
                     full-url)))))

(def role-hierarchy
  (-> (make-hierarchy)
      (derive :super-admin         :organization-admin)
      (derive :super-admin         :account-manager)
      (derive :super-admin         :organization-member)
      (derive :super-admin         :member)
      (derive :organization-admin  :organization-member)
      (derive :organization-admin  :member)
      (derive :account-manager     :member)
      (derive :organization-member :member)))

(defn route-authenticator [{:keys [headers session]} auth-type]
  (let [org-membership-status  (:org-membership-status session nil)
        has-match-drop-access? (:match-drop-access? session)
        user-role              (some-> session
                                       (:user-role)
                                       (str/replace "_" "-")
                                       (keyword)) ; e.g. turns "super_admin" into :super-admin so that we can use role-hierarchy
        ;; Extract Bearer token from Authorization header
        bearer-token           (some->> (get headers "authorization")
                                        (re-find #"(?i)^Bearer\s+(.+)$")
                                        second)
        valid-token?           (= bearer-token (get-config :triangulum.views/client-keys :auth-token))]
    (every? (fn [auth-type]
              (case auth-type
                :token           valid-token? ; TODO: generate token per user and validate it cryptographically
                :match-drop      has-match-drop-access?
                :super-admin     (isa? role-hierarchy user-role :super-admin)
                :account-manager (isa? role-hierarchy user-role :account-manager)
                :org-admin       (and (isa? role-hierarchy user-role :organization-admin)
                                      (= org-membership-status "accepted"))
                :org-member      (and (isa? role-hierarchy user-role :organization-member)
                                      (= org-membership-status "accepted"))
                :member          (isa? role-hierarchy user-role :member)
                true))
            (if (keyword? auth-type) [auth-type] auth-type))))

(defn- fn->sym [f]
  (-> (str f)
      (demunge)
      (str/split #"@")
      (first)
      (symbol)))

(defn clj-handler
  "Wraps a backend Clojure function for use in the CLJ HTTP API routing table (defined in `routing.clj`).

     This handler extracts `:clj-args` from the incoming request (either EDN or JSON),
     and injects the user's session as the first argument when calling the target function.
     It supports both user-facing routes (which rely on session-based IAM) and machine-triggered
     calls (e.g. action hooks) by preserving `:clj-args` for non-auth data.

     Arguments:
     - `function`: A Clojure function that must accept the session as its first argument,
       followed by any optional arguments extracted from `:clj-args`.

     Returns a Ring-compatible handler function that:
     - Calls the wrapped function with `(apply function session clj-args)`
     - Logs the call and args
     - Returns the result as a Ring response (with optional `:status` handling and response type conversion)

     Example usage:
       (clj-handler my-fn)

     Where `my-fn` has a function signature such as:
      (defn my-fn [_ arg1 arg2 arg3 ...]) ; when session is unused
      (defn my-fn [session arg1 arg2 arg3 ...])
      (defn my-fn [_])
      (defn my-fn [session])"
  [function]
  (fn [{:keys [params content-type session]}]
    (let [clj-args   (if (= content-type "application/edn")
                       (:clj-args params [])
                       (json/read-str (:clj-args params "[]")))
          clj-result (apply function session clj-args)]
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
