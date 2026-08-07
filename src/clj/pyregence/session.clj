(ns pyregence.session
  "Session liveness. Sessions are stateless signed cookies, so one is dead when its stamps
   fall outside the configured timeouts or it predates the user's invalidation cutoff.
   `live?` is what a public route asks, since the auth gate cannot enforce liveness for it."
  (:require [triangulum.config   :refer [get-config]]
            [triangulum.database :refer [call-sql]]))

(def ^:private default-idle-timeout-min     15)  ; NIST 800-63B AAL3 / PCI DSS 8.2.8
(def ^:private default-absolute-timeout-min 420) ; 7 h

(defn- expired?
  "Fail-closed: a session missing either timestamp counts as expired; an unauthenticated one never expires."
  [{:keys [user-id created-at last-active]} now idle-ms absolute-ms]
  (boolean (when user-id
             (or (nil? created-at)
                 (nil? last-active)
                 (> (- now created-at)  absolute-ms)
                 (> (- now last-active) idle-ms)))))

(defn- timeout-ms
  [config-key default-min]
  (* 60000 (or (get-config config-key) default-min)))

(defn timed-out?
  "Whether the session is past its configured idle or absolute timeout as of `now`."
  [session now]
  (expired? session now
            (timeout-ms :pyregence.auth/idle-timeout-min     default-idle-timeout-min)
            (timeout-ms :pyregence.auth/absolute-timeout-min default-absolute-timeout-min)))

(defn- invalidated?
  "Created strictly before the user's invalidation point (set on logout / newer login).
   Strict `<` so a fresh login at the same instant survives; invalidated-at 0 = never."
  [{:keys [user-id created-at]} invalidated-at]
  (boolean (and user-id created-at (pos? invalidated-at) (< created-at invalidated-at))))

(defn revoked?
  "Invalidated server-side (logout / newer login). A missing lookup, nil or no row at all,
   counts as not invalidated rather than crashing."
  [{:keys [user-id] :as session}]
  (boolean
   (and user-id
        (invalidated? session (or (some-> (call-sql "get_user_session_invalidated_at" user-id)
                                          (ffirst)
                                          (val))
                                  0)))))

(defn live?
  "Authenticated and neither timed out nor revoked. A public route should treat a session
   as anonymous unless this is true."
  [session]
  (boolean
   (and (:user-id session)
        (not (timed-out? session (System/currentTimeMillis)))
        (not (revoked? session)))))

^:rct/test
(comment
  ;; idle 15 min = 900000 ms ; absolute 7 h = 25200000 ms (the production defaults)
  (expired? {:user-id 1 :created-at 1000000000000 :last-active 1000000000000} 1000000000000 900000 25200000)
  ;=> false
  (expired? {:user-id 1 :created-at 1000000000000 :last-active 999999000000} 1000000000000 900000 25200000)
  ;=> true
  (expired? {:user-id 1 :created-at 999970000000 :last-active 1000000000000} 1000000000000 900000 25200000)
  ;=> true
  (expired? {:user-id 1} 1000000000000 900000 25200000)
  ;=> true
  (expired? {} 1000000000000 900000 25200000)
  ;=> false

  (invalidated? {:user-id 1 :created-at 1000} 0)
  ;=> false
  (invalidated? {:user-id 1 :created-at 1000} 2000)
  ;=> true
  (invalidated? {:user-id 1 :created-at 3000} 2000)
  ;=> false
  (invalidated? {:user-id 1 :created-at 2000} 2000)
  ;=> false
  (invalidated? {:created-at 1000} 2000)
  ;=> false

  ;; call-sql stubbed to 0 = never logged out.
  (with-redefs [call-sql (fn [& _] [{:get_user_session_invalidated_at 0}])]
    (let [now (System/currentTimeMillis)]
      ;; anonymous, fresh, then timed out
      [(live? {})
       (live? {:user-id 1 :created-at now :last-active now})
       (live? {:user-id 1 :created-at (- now 1000000000) :last-active (- now 1000000000)})]))
  ;=> [false true false]

  ;; Logout revokes without timing out, so the clock alone cannot catch it.
  (let [now (System/currentTimeMillis)]
    (with-redefs [call-sql (fn [& _] [{:get_user_session_invalidated_at now}])]
      (live? {:user-id 1 :created-at (- now 1000) :last-active now})))
  ;=> false
  )
