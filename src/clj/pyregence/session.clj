(ns pyregence.session
  "Sessions: whether PyreCast still honours one, the marker that sits between the
   two login steps, and what a page may know of either.

   Sessions are stateless signed cookies, so one is dead when its stamps fall
   outside the configured timeouts or it predates the user's invalidation cutoff.
   `live?` is what a public route asks, since the auth gate cannot enforce
   liveness for it.

   This is also the only namespace that knows a session is a map. A caller that
   `assoc`s or `dissoc`s one is working two levels at once -- the session as a
   domain concept and the hashmap that happens to carry it -- so the shaping
   belongs here, where the map is the local vocabulary rather than a foreign one."
  (:require [pyregence.clock     :as    clock]
            [pyregence.utils     :refer [->uuid]]
            [triangulum.config   :refer [get-config]]
            [triangulum.database :refer [call-sql]]))

(def ^:private default-idle-timeout-min     15)  ; NIST 800-63B AAL3 / PCI DSS 8.2.8
(def ^:private default-absolute-timeout-min 420) ; 7 h
(def ^:private default-two-factor-window-min 15) ; the emailed code's own lifetime

(defn authenticated?
  "Whether this session belongs to a user at all.

   False for an anonymous visitor and for a machine caller presenting only a
   bearer token: neither holds a session that could time out, be revoked, or
   end. Spelled out here rather than as a `:user-id` lookup at each call site --
   every one of those was asking this, and \"was there ever a session\" is a
   question about a person, not about the shape of a map."
  [session]
  (some? (:user-id session)))

(defn- expired?
  "Fail-closed: a session missing either timestamp counts as expired; an unauthenticated one never expires."
  [{:keys [created-at last-active] :as session} now idle-ms absolute-ms]
  (boolean (when (authenticated? session)
             (or (nil? created-at)
                 (nil? last-active)
                 (> (- now created-at)  absolute-ms)
                 (> (- now last-active) idle-ms)))))

(defn- timeout-min
  [config-key default-min]
  (or (get-config config-key) default-min))

(defn- timeout-ms
  [config-key default-min]
  (* 60000 (timeout-min config-key default-min)))

(defn- idle-timeout-min
  "How many minutes of quiet PyreCast allows before it stops honouring a session.

   `timed-out?` is what enforces the window; this is that same window said out
   loud, so that `as-a-page-may-see-it` hands the page the one number rather
   than a second copy of it."
  []
  (timeout-min :pyregence.auth/idle-timeout-min default-idle-timeout-min))

(defn timed-out?
  "Whether the session is past its configured idle or absolute timeout as of `now`."
  [session now]
  (expired? session now
            (timeout-ms :pyregence.auth/idle-timeout-min     default-idle-timeout-min)
            (timeout-ms :pyregence.auth/absolute-timeout-min default-absolute-timeout-min)))

(defn- invalidated?
  "Created strictly before the user's invalidation point (set on logout / newer login).
   Strict `<` so a fresh login at the same instant survives; invalidated-at 0 = never."
  [{:keys [created-at] :as session} invalidated-at]
  (boolean (and (authenticated? session) created-at (pos? invalidated-at) (< created-at invalidated-at))))

(defn active-login
  "The account's server-authoritative device session, or nil."
  [{:keys [user-id]}]
  (when user-id (first (call-sql "get_active_user_session" user-id))))

(defn active-login-live?
  "Whether the registry row still represents an active login at NOW."
  [{:keys [created_at last_active_at revoked_at]} now]
  (boolean
   (and created_at last_active_at (nil? revoked_at)
        (not (expired? {:user-id 1
                        :created-at created_at
                        :last-active last_active_at}
                       now
                       (timeout-ms :pyregence.auth/idle-timeout-min default-idle-timeout-min)
                       (timeout-ms :pyregence.auth/absolute-timeout-min default-absolute-timeout-min))))))

(defn same-device?
  "Whether ACTIVE belongs to the device presenting this request."
  [active request-session]
  (= (str (:device_id active)) (str (:request-device-id request-session))))

(defn with-request-device
  "Attach the validated origin-device identifier at the HTTP boundary."
  [stored-session header-value]
  (cond-> (or stored-session {})
    (->uuid header-value) (assoc :request-device-id (str (->uuid header-value)))))

(defn- registered-row-live?
  [session active now]
  (and active
       (= (str (:session-generation session))
          (str (:session_generation active)))
       (= (str (:device-id session)) (str (:device_id active)))
       (nil? (:revoked_at active))
       (not (expired? (assoc session
                             :created-at (:created_at active)
                             :last-active (:last_active_at active))
                      now
                      (timeout-ms :pyregence.auth/idle-timeout-min default-idle-timeout-min)
                      (timeout-ms :pyregence.auth/absolute-timeout-min default-absolute-timeout-min)))))

(defn- registered-live?
  [session now]
  (registered-row-live? session (active-login session) now))

(defn revoked?
  "Whether shared persistence has stopped honouring this login. Legacy cookies
   retain the per-user cutoff during rollout."
  [{:keys [user-id session-generation] :as session}]
  (boolean
   (and (authenticated? session)
        (if session-generation
          (not (registered-live? session (clock/now)))
          (invalidated? session (or (some-> (call-sql "get_user_session_invalidated_at" user-id)
                                            (ffirst) (val))
                                    0))))))

(defn live?
  "Authenticated and neither timed out nor revoked. A public route should treat a session
   as anonymous unless this is true.

   The two-argument form takes `now` and reads no clock at all, which is what makes
   liveness testable without waiting for it. The one-argument form is the same
   question asked of the clock this system was built with."
  ([session] (live? session (clock/now)))
  ([session now]
   (boolean
    (and (authenticated? session)
         (if (:session-generation session)
           (boolean (registered-live? session now))
           (and (not (timed-out? session now))
                (not (revoked? session))))))))

(defn request-device-current?
  "Whether this request comes from the device named by its signed session and
   by the active registry row. Tabs in that device intentionally compare equal."
  [{:keys [user-id session-generation device-id request-device-id]}]
  (boolean
   (and user-id session-generation device-id request-device-id
        (= (str device-id) (str request-device-id))
        (some-> (call-sql "active_device_session_matches"
                          user-id session-generation device-id)
                first :active_device_session_matches))))

(defn logout-disposition
  "Classify a qualified logout without mutating its session."
  [stored-session {:keys [expected-generation scope]}]
  (cond
    (not= :current scope) :unsupported-scope
    (not= (:session-generation stored-session) expected-generation) :superseded
    (not (request-device-current? stored-session)) :superseded
    :else :current))

(defn cleanup-disposition
  "Classify whether an ended generation may clear the cookie presented now."
  [stored-session {:keys [expected-generation]}]
  (cond
    (not= (:session-generation stored-session) expected-generation) :superseded
    (not (authenticated? stored-session)) :already-clean
    (live? stored-session) :still-live
    :else :clean-up))

(def ^:private pre-authentication-keys
  "Context allowed to cross from an unauthenticated first step into the second.
  Authenticated identity and its timestamps deliberately do not: an ended login
  carried into this state would make page rendering discard the 2FA challenge."
  [:marketplace-signup])

(defn awaiting-2fa
  "A fresh pre-authentication session owing a second factor from `user`, with its
  own fuse. Marketplace signup context rides along until login finishes; a prior
  authenticated session does not."
  [session {:keys [user_id user_email]} now]
  (assoc (select-keys session pre-authentication-keys) :pending-2fa
         {:user-id    user_id
          :user-email user_email
          :expires-at (+ now (timeout-ms :pyregence.auth/two-factor-window-min
                                         default-two-factor-window-min))}))

(defn pending-user
  "Who this session is still owed a second factor for, or nil. Absent, incomplete and stale markers
   all read alike, so what comes back is a whole user or nothing."
  [session now]
  (let [{:keys [user-id user-email expires-at]} (:pending-2fa session)]
    (when (and user-id user-email expires-at (< now expires-at))
      {:user-id user-id :user-email user-email})))

(def ^:private takeover-user-keys
  [:match_drop_access :user_email :user_id :user_name :user_role
   :organization_rid :org_membership_status :subscription_tier
   :marketplace_status])

(defn awaiting-takeover
  "Stage an authenticated request to move an account to another device."
  [session user active candidate-device candidate-generation now]
  (assoc (select-keys session pre-authentication-keys)
         :pending-takeover
         {:user-data (select-keys user takeover-user-keys)
          :candidate-device candidate-device
          :candidate-generation candidate-generation
          :observed-generation (str (:session_generation active))
          :observed-epoch (:session_epoch active)
          :expires-at (+ now (timeout-ms :pyregence.auth/two-factor-window-min
                                         default-two-factor-window-min))}))

(defn pending-takeover
  "Return a complete, unexpired device-transfer challenge, or nil."
  [session now]
  (let [{:keys [user-data candidate-device candidate-generation
                observed-generation observed-epoch expires-at] :as challenge}
        (:pending-takeover session)]
    (when (and (:user_id user-data) (->uuid candidate-device)
               (->uuid candidate-generation) (->uuid observed-generation)
               (some? observed-epoch) expires-at (< now expires-at))
      challenge)))

(defn takeover-required?
  "Whether SESSION carries a live, authenticated request to move the account
   to this browser device. This is the one fact a page needs; the signed
   challenge itself remains server-side."
  [session now]
  (boolean (pending-takeover session now)))

(defn ended?
  "Whether a session that once existed is one PyreCast no longer honours -- idled
   out, aged out, or revoked by a logout or a newer login elsewhere.

   Note what this is not: it is not \"logged out\". A request that never carried a
   session cannot have had one end, and answers false here. That distinction is
   the whole value of this predicate: it separates \"you were logged in and are
   not any more\" from \"you were never logged in\", and only the first of those
   has an explanation worth giving.

   Exactly `live?` negated over an authenticated session, and written that way on
   purpose. Restating the two timeouts and the revocation here as a disjunction
   was the same question asked in a second vocabulary, and the two would drift
   the first time a third way to die is added."
  ([session]
   ;; Not `(ended? session (clock/now))`: an anonymous request has no session to
   ;; ask about and must not reach the clock to find that out. Token-only routes
   ;; are served by processes that never installed one.
   (and (authenticated? session)
        (not (live? session))))
  ([session now]
   (and (authenticated? session)
        (not (live? session now)))))

(def ^:private internal-keys
  "The sequential PKs PyreCast addresses its own rows by, and the one thing in a
   session that must never reach a browser (PYR1-1512 enumeration hardening).
   Named rather than spelled out at the call site, so \"remove the internal PKs\"
   and \"remove these two keywords\" stop being the same sentence: adding a third
   is then a change to this list.

   `:pending-2fa` is on it for the same reason and not a second one: the marker
   carries a user id of its own (PYR1-1615), so a page told nothing about
   `:user-id` while being handed `:pending-2fa` would have been told anyway."
  [:user-id :organization-id :pending-2fa :pending-takeover
   :device-id :request-device-id])

(def ^:private ended-page-keys
  "The facts an ended page needs in order to clean up the exact generation it
   rendered. Account claims are deliberately absent: a page PyreCast calls a
   guest must not retain an authenticated role, organization, or token in its
   own model."
  [:session-generation :last-active])

(defn- page-facing
  "A session shaped the way a page receives one: PyreCast's own PKs out, and in
   their place whether the session is LOGGED-IN? and the IDLE-TIMEOUT-MIN the
   page should act on.

   Told both rather than asking for either, so that this is map-shaping and
   nothing else. `live?` takes `now` for the same reason a few lines up: a
  function that is handed its facts stays at one altitude, and one that reaches
  for them does not."
  [session is-logged-in? ended-session? idle-min requires-takeover?]
  (let [visible-session (if is-logged-in? session (select-keys session ended-page-keys))
        page-device    (or (:device-id session)
                           (get-in session [:pending-takeover :candidate-device]))]
    (-> (apply dissoc visible-session internal-keys)
        (assoc :logged-in?       is-logged-in?
               :ended-session?   ended-session?
               :device-session-id page-device
               :idle-timeout-min idle-min
               :device-transfer-required? requires-takeover?))))

(defn for-page
  "The session view a page may receive, and whether its login had already ended."
  [stored-session]
  (let [now          (clock/now)
        active       (when (:session-generation stored-session)
                       (active-login stored-session))
        is-live?     (if active
                       (boolean (registered-row-live? stored-session active now))
                       (live? stored-session now))
        page-session (cond-> stored-session
                       (and is-live? active)
                       (assoc :last-active (:last_active_at active)))]
    {:visible (page-facing page-session is-live?
                           (and (authenticated? stored-session) (not is-live?))
                           (when is-live? (idle-timeout-min))
                           (takeover-required? stored-session now))
     :ended?  (and (authenticated? stored-session) (not is-live?))}))

(defn as-a-page-may-see-it
  "This session as a page is allowed to know it: PyreCast's own PKs taken out,
   the fact of a user replaced by whether that user's session is still live, and
   the idle window said out loud.

   `:logged-in?` asks `live?` rather than whether a `:user-id` is present.
   Sessions are stateless signed cookies, so nothing strips the `:user-id` when
   one goes stale and its presence stays true forever; a page reading that claims
   a session every gated route is already correctly refusing. That is PYR1-1623
   -- an organization read the resulting missing layers as lost data rather than
   as an expired session -- so this asks the same question the auth gate asks, at
   the cost of the same revocation lookup the gate already pays for.

   The window travels with the page because the page acts on it: the client-side
   kick gives up shortly before it, and the alternative is a literal in the
   ClojureScript that agrees with the server only until somebody changes the
   config.

   Reported only to a session that has one to lose. An anonymous visitor is told
   nothing, not because the number is a secret -- knowing how long PyreCast
   tolerates quiet gets a caller nothing it does not already get by waiting --
   but because a page that is sent a window it must not act on has to be told
   separately not to act on it, and that second sentence is a conditional in the
   ClojureScript whose only job is to undo this one."
  [session]
  (:visible (for-page session)))

(defn note-activity
  "Answer a heartbeat: the page saying the person is still here.

   This is the only ordinary route that advances authoritative activity. Page
   rendering and background polling do not prove a person is present.

   It is a route of its own because the client needs exactly one call that means
   nothing but \"still here\". Every other route means something, and sending one
   of those on a timer to keep a session alive would be a request PyreCast has
   to actually serve.

   Answering at all is the useful half in the other direction too: a heartbeat
   from a session that was revoked elsewhere is refused like any other gated
   call, so somebody sitting in front of an open tab finds out within one beat
   rather than at their next click."
  [{:keys [user-id session-generation device-id] :as session}]
  (if session-generation
    (let [last-active-at
          (when (request-device-current? session)
            (some-> (call-sql "note_active_device_activity"
                              user-id session-generation device-id (clock/now))
                    first :note_active_device_activity))]
      (cond-> {:accepted? (some? last-active-at)}
        last-active-at (assoc :last-active-at last-active-at)))
    {:accepted? (live? session)}))

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

  ;; 15 min = 900000 ms, and the rest of the session rides along.
  (awaiting-2fa {:marketplace-signup {:org "acme"}} {:user_id 1 :user_email "a@b.c"} 1000)
  ;=> {:marketplace-signup {:org "acme"} :pending-2fa {:user-id 1 :user-email "a@b.c" :expires-at 901000}}

  ;; Fresh, then exactly at the fuse, then each field missing in turn, then nothing at all.
  (let [marked {:pending-2fa {:user-id 1 :user-email "a@b.c" :expires-at 2000}}]
    [(pending-user marked 1999)
     (pending-user marked 2000)
     (pending-user (update marked :pending-2fa dissoc :expires-at) 1999)
     (pending-user (update marked :pending-2fa dissoc :user-email) 1999)
     (pending-user (update marked :pending-2fa dissoc :user-id) 1999)
     (pending-user {} 1999)
     (pending-user nil 1999)])
  ;=> [{:user-id 1 :user-email "a@b.c"} nil nil nil nil nil nil]

  ;; Logout revokes without timing out, so the clock alone cannot catch it.
  (let [now (System/currentTimeMillis)]
    (with-redefs [call-sql (fn [& _] [{:get_user_session_invalidated_at now}])]
      (live? {:user-id 1 :created-at (- now 1000) :last-active now})))
  ;=> false

  (mapv authenticated? [{} {:user-id nil} {:user-id 1}])
  ;=> [false false true]

  ;; The window the page is told is the window this namespace enforces. Read off
  ;; `as-a-page-may-see-it` rather than the private helper, because that is the
  ;; number that actually ships, and asserted against `timed-out?` rather than
  ;; against the config, because the failure worth catching is the two
  ;; disagreeing -- a client kicking on one number while the server honours
  ;; another is PYR1-1623 with a shorter fuse.
  (with-redefs [call-sql (fn [& _] [{:get_user_session_invalidated_at 0}])]
    (let [now    (System/currentTimeMillis)
          quiet  (fn [ms] {:user-id 1 :created-at now :last-active (- now ms)})
          window (* 60000 (:idle-timeout-min (as-a-page-may-see-it (quiet 0))))]
      [(timed-out? (quiet (dec window)) now)
       (timed-out? (quiet (inc window)) now)]))
  ;=> [false true]

  ;; Liveness, not the presence of a :user-id: a stale cookie carries one forever,
  ;; and a page that reads it claims a session every gated route is refusing.
  (with-redefs [call-sql (fn [& _] [{:get_user_session_invalidated_at 0}])]
    (let [now           (System/currentTimeMillis)
          fresh         {:user-id 1 :organization-id 7 :created-at now :last-active now}
          idle-past     (inc (timeout-ms :pyregence.auth/idle-timeout-min default-idle-timeout-min))
          absolute-past (inc (timeout-ms :pyregence.auth/absolute-timeout-min default-absolute-timeout-min))]
      (mapv (comp :logged-in? as-a-page-may-see-it)
            [fresh
             (assoc fresh :last-active (- now idle-past))
             (assoc fresh :created-at (- now absolute-past))
             {}])))
  ;=> [true false false false]

  ;; An ended login is an actual guest at the page boundary. Its generation and
  ;; device survive only so qualified cleanup can target the cookie it rendered.
  (with-redefs [call-sql (fn [& _] [])]
    (let [visible (as-a-page-may-see-it
                   {:user-id 1 :user-role "organization_member"
                    :user-email "member@example.test" :auth-token "secret"
                    :session-generation "11111111-1111-1111-1111-111111111111"
                    :device-id "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
                    :created-at 1 :last-active 1})]
      [(select-keys visible [:user-role :user-email :auth-token])
       (:session-generation visible)
       (:device-session-id visible)]))
  ;=> [{} "11111111-1111-1111-1111-111111111111" "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"]

  ;; Somebody with no session is told no window: there is nothing for them to be
  ;; early for, and a number they must not act on is a number the page then has
  ;; to be told to ignore.
  (with-redefs [call-sql (fn [& _] [{:get_user_session_invalidated_at 0}])]
    (let [now       (System/currentTimeMillis)
          idle-past (inc (timeout-ms :pyregence.auth/idle-timeout-min default-idle-timeout-min))]
      (mapv (comp some? :idle-timeout-min as-a-page-may-see-it)
            [{:user-id 1 :created-at now :last-active now}
             {:user-id 1 :created-at now :last-active (- now idle-past)}
             {}])))
  ;=> [true false false]

  ;; The PKs never reach a browser (PYR1-1512 enumeration hardening), and
  ;; neither does the 2FA marker, which carries one of its own (PYR1-1615).
  (with-redefs [call-sql (fn [& _] [{:get_user_session_invalidated_at 0}])]
    (let [now (System/currentTimeMillis)]
      (-> {:user-id 1 :organization-id 7 :created-at now :last-active now
           :pending-2fa {:user-id 1 :user-email "a@b.c" :expires-at (+ now 1000)}}
          (as-a-page-may-see-it)
          (select-keys internal-keys))))
  ;=> {}

  ;; `ended?` is not `live?` negated: a caller who never had a session is neither.
  (with-redefs [call-sql (fn [& _] [{:get_user_session_invalidated_at 0}])]
    (let [now       (System/currentTimeMillis)
          idle-past (inc (timeout-ms :pyregence.auth/idle-timeout-min default-idle-timeout-min))]
      ;; anonymous, fresh, then idled out
      [(ended? {})
       (ended? {:user-id 1 :created-at now :last-active now})
       (ended? {:user-id 1 :created-at now :last-active (- now idle-past)})]))
  ;=> [false false true]
  )
