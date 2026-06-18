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
      (derive :account-manager     :organization-admin)
      (derive :account-manager     :organization-member)
      (derive :account-manager     :member)
      (derive :organization-admin  :organization-member)
      (derive :organization-admin  :member)
      (derive :organization-member :member)))

(def subscription-hierarchy
  (-> (make-hierarchy)
      (derive :tier3-enterprise      :tier2-pro)
      (derive :tier3-enterprise      :tier1-basic-paid)
      (derive :tier3-enterprise      :tier1-free-registered)
      (derive :tier2-pro             :tier1-basic-paid)
      (derive :tier2-pro             :tier1-free-registered)
      (derive :tier1-basic-paid      :tier1-free-registered)
      (derive :tier1-free-registered :tier0-guest)))

(defn- sql->kw
  "Turns a SQL string like super_admin into :super-admin  or tier2_pro into :tier2-pro
   so that we can use the role and subscription hierarchies."
  [sql-str]
  (some-> sql-str
          (str/replace "_" "-")
          (keyword)))

(defn- session-user-role
  "Returns the user role from the session, defaults to :member."
  [session]
  (or (some-> session :user-role sql->kw)
      :member))

(defn- current-subscription-tier
  "Current subscription tier from DB, falling back to session, then :tier0-guest."
  [session]
  (or (try
        (when-let [org-id (:organization-id session)]
          (sql->kw (sql-primitive (call-sql "get_org_subscription_tier" org-id))))
        (catch Exception _ nil))
      (some-> session :subscription-tier sql->kw)
      :tier0-guest))

(defn shell-tier?
  "Returns true if the subscription tier is a single-user (shell) tier, i.e. tier1 or below."
  [tier]
  (not (isa? subscription-hierarchy tier :tier2-pro)))

;; Session liveness (PYR1-1513 / PYR1-1515)

(def ^:private default-idle-timeout-min     15)  ; NIST 800-63B AAL3 / PCI DSS 8.2.8
(def ^:private default-absolute-timeout-min 420) ; 7 h (AC: absolute timeout < 8 h)

(defn- session-expired?
  "True when an authenticated session has passed its idle or absolute timeout. Fail-closed:
   a session missing either timestamp counts as expired; an unauthenticated one never expires."
  [{:keys [user-id created-at last-active]} now idle-ms absolute-ms]
  (boolean (when user-id
             (or (nil? created-at)
                 (nil? last-active)
                 (> (- now created-at)  absolute-ms)
                 (> (- now last-active) idle-ms)))))

(defn- timeout-ms
  "A configured timeout in ms, from a minutes config key with a fallback default."
  [config-key default-min]
  (* 60000 (or (get-config config-key) default-min)))

(defn- session-timed-out?
  "Whether `session` is past its configured idle/absolute timeout as of `now` (ms)."
  [session now]
  (session-expired? session now
                    (timeout-ms :pyregence.auth/idle-timeout-min     default-idle-timeout-min)
                    (timeout-ms :pyregence.auth/absolute-timeout-min default-absolute-timeout-min)))

(defn- session-invalidated?
  "True when the session was created strictly before the user's invalidation point (set on
   logout or a newer login; PYR1-1513/1519). `invalidated-at` is epoch ms; 0 = never. Strict
   `<` so a session created in the same instant it set the point (a fresh login) survives."
  [{:keys [user-id created-at]} invalidated-at]
  (boolean (and user-id created-at (pos? invalidated-at) (< created-at invalidated-at))))

(defn- session-revoked?
  "Whether this authenticated session predates the user's last logout (PYR1-1513).
   A nil lookup (e.g. a deleted user) counts as not invalidated rather than crashing."
  [{:keys [user-id] :as session}]
  (boolean
   (and user-id
        (session-invalidated? session (or (sql-primitive (call-sql "get_user_session_invalidated_at" user-id)) 0)))))

^:rct/test
(comment
  ;; idle 15 min = 900000 ms ; absolute 7 h = 25200000 ms (the production defaults)
  (session-expired? {:user-id 1 :created-at 1000000000000 :last-active 1000000000000} 1000000000000 900000 25200000)
  ;=> false
  (session-expired? {:user-id 1 :created-at 1000000000000 :last-active 999999000000} 1000000000000 900000 25200000)
  ;=> true
  (session-expired? {:user-id 1 :created-at 999970000000 :last-active 1000000000000} 1000000000000 900000 25200000)
  ;=> true
  (session-expired? {:user-id 1} 1000000000000 900000 25200000)
  ;=> true
  (session-expired? {} 1000000000000 900000 25200000)
  ;=> false

  (session-invalidated? {:user-id 1 :created-at 1000} 0)
  ;=> false
  (session-invalidated? {:user-id 1 :created-at 1000} 2000)
  ;=> true
  (session-invalidated? {:user-id 1 :created-at 3000} 2000)
  ;=> false
  ;; created at the epoch instant (a fresh login) -> survives (PYR1-1519)
  (session-invalidated? {:user-id 1 :created-at 2000} 2000)
  ;=> false
  (session-invalidated? {:created-at 1000} 2000)
  ;=> false
  )

;; TODO add (some? (:user-id session)) check to all routes to ensure user is logged in?
(defn- authorized?
  "True when the session satisfies every required auth-type (role, tier, token,
   match-drop). Pure authorization; session liveness is checked separately."
  [{:keys [headers session]} auth-type]
  (let [org-membership-status  (:org-membership-status session nil)
        has-match-drop-access? (:match-drop-access? session)
        user-role              (session-user-role session)
        subscription-tier      (delay (current-subscription-tier session))
        ;; Extract Bearer token from Authorization header
        bearer-token           (some->> (get headers "authorization")
                                        (re-find #"(?i)^Bearer\s+(.+)$")
                                        second)
        valid-token?           (= bearer-token (get-config :triangulum.views/client-keys :auth-token))
        super-admin?           (isa? role-hierarchy user-role :super-admin)
        account-manager?       (isa? role-hierarchy user-role :account-manager)]
    (every? (fn [one-auth-type]
              (case one-auth-type
                :token               valid-token? ; TODO: generate token per user and validate it cryptographically
                :match-drop          has-match-drop-access?
                ;; Roles
                :super-admin         super-admin?
                :account-manager     account-manager?
                :organization-admin  (or super-admin? ; super-admins have no org, so org-membership-status is none
                                         account-manager?
                                         (and (isa? role-hierarchy user-role :organization-admin)
                                              (= org-membership-status "accepted")))
                :organization-member (or super-admin? ; super-admins have no org, so org-membership-status is none
                                         account-manager?
                                         (and (isa? role-hierarchy user-role :organization-member)
                                              (= org-membership-status "accepted")))
                :member              (isa? role-hierarchy user-role :member)
                ;; super-admins / account-managers have access to all tiers
                (:tier3-enterprise :tier2-pro :tier1-basic-paid :tier1-free-registered)
                (or super-admin?
                    account-manager?
                    (isa? subscription-hierarchy @subscription-tier one-auth-type))
                true))
            (if (keyword? auth-type) [auth-type] auth-type))))

(defn- requires-live-session?
  "True when authorizing `auth-type` consults the session (a role, tier, or match-drop grant).
   Token-only routes (log-in/out, password reset, email/2FA verify) authorize on the request
   token alone, so they stay reachable with a stale cookie - a user must be able to log out or
   re-login even after the session times out or is revoked (PYR1-1513/1515/1519)."
  [auth-type]
  (boolean (some #(not= :token %) (if (keyword? auth-type) [auth-type] auth-type))))

(defn route-authenticator
  "Authorizes a request for `auth-type`. For session-authorizing routes it first rejects any
   session that has timed out (PYR1-1515) or was invalidated on logout / by a newer login
   (PYR1-1513/1519); token-only routes skip that check so logout and re-login stay reachable."
  [{:keys [session] :as request} auth-type]
  (if (and (requires-live-session? auth-type)
           (:user-id session)
           (or (session-timed-out? session (System/currentTimeMillis))
               (session-revoked? session)))
    false
    (authorized? request auth-type)))

^:rct/test
(comment
  ;; fresh authenticated session authorizes; the same session past its idle timeout is
  ;; rejected. call-sql stubbed so the invalidation lookup returns "never" (0).
  (with-redefs [call-sql (fn [& _] [{:get_user_session_invalidated_at 0}])]
    (let [now     (System/currentTimeMillis)
          fresh   {:user-id 1 :user-role "organization_member" :org-membership-status "accepted"
                   :created-at now :last-active now}
          expired (assoc fresh :last-active (- now 1000000000))]
      [(route-authenticator {:session fresh}   :member)
       (route-authenticator {:session expired} :member)]))
  ;=> [true false]

  ;; a since-deleted user's lingering session: the lookup returns nil -> treated as
  ;; not invalidated, no NullPointerException.
  (with-redefs [call-sql (fn [& _] [{:get_user_session_invalidated_at nil}])]
    (let [now (System/currentTimeMillis)]
      (route-authenticator {:session {:user-id 999 :user-role "member" :created-at now :last-active now}} :member)))
  ;=> true

  ;; subscription-tier auth resolves the requested tier (no fall-through to the `true`
  ;; default): a non-paying member does not satisfy :tier2-pro.
  (with-redefs [call-sql (fn [& _] [{:get_user_session_invalidated_at 0}])]
    (let [now (System/currentTimeMillis)]
      (route-authenticator {:session {:user-id 1 :user-role "member" :created-at now :last-active now}} :tier2-pro)))
  ;=> false

  ;; a stale (timed-out) session still reaches a token-only route (logout/login/reset stay usable),
  ;; but the same session is rejected on a session-authorizing route (PYR1-1513/1515/1519).
  (with-redefs [call-sql   (fn [& _] [{:get_user_session_invalidated_at 0}])
                get-config (fn [k & _] (when (= k :triangulum.views/client-keys) "tok"))]
    (let [now     (System/currentTimeMillis)
          expired {:user-id 1 :user-role "member" :created-at (- now 1000000000) :last-active (- now 1000000000)}
          req     {:session expired :headers {"authorization" "Bearer tok"}}]
      [(route-authenticator req :token)
       (route-authenticator req :member)]))
  ;=> [true false]

  (requires-live-session? :token)
  ;=> false
  (requires-live-session? :member)
  ;=> true
  (requires-live-session? #{:member :token})
  ;=> true

  ;; a set auth-type requires every element: #{:token :super-admin} denies a plain member even with a
  ;; valid token (a map literal would fall through to authorized?'s `true` default and admit anyone).
  (with-redefs [call-sql   (fn [& _] [{:get_user_session_invalidated_at 0}])
                get-config (fn [k & _] (when (= k :triangulum.views/client-keys) "tok"))]
    (let [now    (System/currentTimeMillis)
          member {:session {:user-id 5 :user-role "member" :created-at now :last-active now}
                  :headers {"authorization" "Bearer tok"}}]
      (route-authenticator member #{:token :super-admin})))
  ;=> false
  )

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
          clj-result (apply function session clj-args)
          response   (if (:status clj-result)
                       clj-result
                       (data-response clj-result {:type (if (= content-type "application/edn") :edn :json)}))]
      (log-str "CLJ Call: " (cons (fn->sym function) clj-args))
      ;; PYR1-1515: refresh idle timer, unless the wrapped fn already set :session (log-in/log-out)
      (if (and (:user-id session) (not (contains? response :session)))
        (assoc response :session (assoc session :last-active (System/currentTimeMillis)))
        response))))

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
