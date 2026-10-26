(ns pyregence.authentication-test
  "The two-step login gate and the failed-attempt budget, with the database stubbed. These complement
   the rich-comment-tests in `pyregence.authentication`, which need a real database and run under
   `bb test`. This namespace runs under `bb test-clj`, needs no database, and can set up the
   locked-out paths a database-backed test cannot."
  (:require [clojure.string           :as str]
            [clojure.test             :refer [deftest is testing use-fixtures]]
            [pyregence.authentication :as authentication]
            [pyregence.clock          :as clock]
            [pyregence.email          :as email]
            [pyregence.marketplace    :as marketplace]
            [pyregence.session        :as session]
            [pyregence.throttle       :as throttle]
            [pyregence.totp           :as totp]
            [triangulum.config        :as config]
            [triangulum.database      :as database]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private account       "throttle-user@example.test")
(def ^:private other-account "someone-else@example.test")
(def ^:private user-id       24)
(def ^:private other-user-id 25)
(def ^:private password      "correct horse battery")
(def ^:private totp-secret   "JBSWY3DPEHPK3PXP")
(def ^:private backup-code   "TESTCODE")
(def ^:private wrong-code    "wrong") ; never numeric, so never the live TOTP code
(def ^:private email-code    "246810")
(def ^:private password-set-ms 1000) ; when the account's password was last set
(def ^:private link-token    "emailed-token")
(def ^:private new-password  "New password 1A")

(def ^:private user-row
  {:user_id user-id :user_email account :user_role "member"})

(defn- valid-code
  "The code an authenticator app would be showing right now."
  []
  (str (totp/get-current-totp-code totp-secret)))

(defn- fake-call-sql
  "Stands in for the SQL that log-in, the emailed links and the code check touch. Only `user-id` has
   an authenticator, and only the right password or link works, so no stub answer stands in for a
   real one."
  [sql-fn & args]
  (let [args (if (map? (first args)) (rest args) args)] ; drop the {:log? false} opts
    (case sql-fn
      "get_user_settings"  [{:settings (pr-str {:two-factor :totp})}]
      "get_user_with_totp" (when (= (first args) user-id)
                             [(assoc user-row :secret totp-secret)])
      "use_backup_code"    [{:use_backup_code (and (= (first args) user-id)
                                                   (= (second args) backup-code))}]
      "verify_user_login"  (when (= (second args) password) [user-row])
      "get_user_by_link_token" (when (= (second args) link-token) [user-row])
      "set_user_password"  (when (= (nth args 2) link-token) [user-row])
      "verify_user_email"  (when (= (second args) link-token) [user-row])
      "verify_user_2fa"    (when (= (second args) email-code) [user-row])
      "get_password_set_ms" [{:get_password_set_ms password-set-ms}]
      "set_user_password_by_id" (when (= [user-id password-set-ms] [(first args) (nth args 2)]) [user-row])
      nil)))

(use-fixtures :each
  ;; `get-config` answers nil rather than an empty map, because a map is truthy and the configured
  ;; timeouts multiply what it returns. With no response type, `data-response` keeps bodies as data.
  (fn [run]
    (clock/install! (clock/->SystemClock))
    (reset! throttle/attempts {})
    (with-redefs [database/call-sql            fake-call-sql
                  config/get-config            (fn [& _] nil)
                  marketplace/complete-signup! (fn [& _] nil)]
      (run))))

(defn- marker
  "A session as `log-in` leaves it once a password has checked out."
  ([] (marker user-id account))
  ([id email]
   (session/awaiting-2fa {} {:user_id id :user_email email} password-set-ms (clock/now))))

(defn- verify
  "The status `verify-2fa` answers with. The email is always `account`, since the route ignores it."
  [session code]
  (:status (authentication/verify-2fa session account code)))

(defn- spend-the-budget
  "The statuses of six wrong codes, which is the whole allowance."
  [session]
  (mapv (fn [_] (verify session wrong-code)) (range 6)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The gate
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest verify-2fa-refuses-a-caller-who-never-passed-the-password-step
  (testing "a correct code alone mints nothing, which is the whole ticket"
    (doseq [[label session] [["no session"    nil]
                             ["anonymous"     {}]
                             ["stale marker"  (update (marker) :pending-2fa assoc :expires-at 0)]
                             ["no user-email" (update (marker) :pending-2fa dissoc :user-email)]
                             ["no user-id"    (update (marker) :pending-2fa dissoc :user-id)]]]
      (let [response (authentication/verify-2fa session account (valid-code))]
        (is (= 401 (:status response)) label)
        (is (nil? (:session response)) label))))
  (testing "and nothing is charged, so an unauthenticated caller cannot fill the map"
    (is (= {} @throttle/attempts))))

(deftest verify-2fa-reads-the-user-from-the-marker-not-the-request
  (let [response (authentication/verify-2fa (marker other-user-id other-account)
                                            account
                                            backup-code)]
    (testing "a marker for one account cannot verify another, whatever the request says"
      (is (= 403 (:status response)))
      (is (nil? (:session response))))
    (testing "and the charge lands on the marker's user, not the request's"
      (is (= {[:2fa other-user-id] 1} @throttle/attempts)))))

(deftest log-in-leaves-a-marker-when-a-second-factor-is-owed
  (let [response (authentication/log-in {} account password)
        pending  (get-in response [:session :pending-2fa])]
    (testing "the response carries the session the cookie is resealed from"
      (is (= 200 (:status response))))
    (testing "naming the user the second factor is expected from"
      (is (= user-id (:user-id pending)))
      (is (= account (:user-email pending)))
      (is (pos? (:expires-at pending))))
    (testing "and that is what verify-2fa accepts"
      (is (= 200 (verify (:session response) (valid-code)))))))

(deftest marketplace-sso-also-leaves-a-marker
  (with-redefs [marketplace/sso-login (fn [_] {:user    user-row
                                               :session {:marketplace-signup {:org-name "acme"}}})]
    (let [response (authentication/marketplace-sso-login {})
          pending  (get-in response [:session :pending-2fa])]
      (testing "SSO lands on the same 2FA page, so it mints the same marker"
        (is (= 302 (:status response)))
        (is (= user-id (:user-id pending))))
      (testing "and the signup riding on that session survives"
        (is (= {:org-name "acme"} (get-in response [:session :marketplace-signup])))))))

(deftest log-in-starts-a-fresh-pre-authentication-session
  (let [ended-session {:user-id            7
                       :organization-id    11
                       :user-role          "member"
                       :created-at         1
                       :last-active        1
                       :marketplace-signup {:org-name "acme"}}
        response      (authentication/log-in ended-session account password)
        awaiting      (:session response)
        page-session  (session/for-page awaiting)]
    (testing "marketplace signup context rides along until login finishes"
      (is (= {:org-name "acme"} (:marketplace-signup awaiting))))
    (testing "authenticated identity and its lifetime do not cross the password boundary"
      (is (= #{:marketplace-signup :pending-2fa} (set (keys awaiting)))))
    (testing "page rendering preserves the challenge rather than treating it as an ended login"
      (is (false? (:ended? page-session))))
    (testing "the preserved challenge still completes authentication"
      (is (= 200 (verify awaiting (valid-code)))))))

;; Someone else is signed in on this browser, so a session carried over into the reply would show.
(def ^:private signed-in-elsewhere {:user-id other-user-id :user-email other-account})

(def ^:private emailed-links
  {"a password reset"      #(authentication/set-user-password signed-in-elsewhere account new-password %)
   "an email verification" #(authentication/verify-user-email signed-in-elsewhere account %)})

(defn- with-mail
  "Calls `f` with an atom of the mail sent, recorded instead of sent."
  [f]
  (let [sent (atom [])]
    (with-redefs [email/send-email! (fn [_ to kind] (swap! sent conj [to kind]))]
      (f sent))))

(defn- with-second-factor
  "Calls `f` with every account's second factor set to `method` (nil for none), and the SQL calls
   made recorded, each as the function's name and its arguments."
  [method f]
  (let [calls (atom [])]
    (with-redefs [database/call-sql (fn [sql-fn & args]
                                      (swap! calls conj (into [sql-fn] args))
                                      (if (= sql-fn "get_user_settings")
                                        [{:settings (pr-str {:two-factor method})}]
                                        (apply fake-call-sql sql-fn args)))]
      (f calls))))

(defn- cutoffs
  "The session cutoffs stamped for `user-id` among the recorded SQL `calls`."
  [calls]
  (for [[sql-fn id at] calls
        :when (and (= "set_user_session_invalidated_at" sql-fn) (= user-id id))]
    at))

(deftest an-email-verification-owes-the-second-factor
  (let [response (authentication/verify-user-email signed-in-elsewhere account link-token)]
    (testing "it leaves only the marker, as log-in does"
      (is (= 200 (:status response)))
      (is (= #{:pending-2fa} (set (keys (:session response)))))
      (is (= user-id (get-in response [:session :pending-2fa :user-id]))))
    (testing "it sends the browser to the code page, the email encoded"
      (is (= {:second-factor-url "/verify-2fa?email=throttle-user%40example.test&method=totp"} (:body response))))
    (testing "it is completed by the second factor"
      (is (= 200 (verify (:session response) (valid-code)))))))

(deftest an-emailed-link-logs-in-an-account-without-a-second-factor
  ;; A clock that ticks on every read, so a cutoff stamped after the session would postdate it.
  (clock/install! (let [t (atom 0)] (reify clock/IClock (now-ms [_] (swap! t inc)))))
  (try
    (with-second-factor nil
      (fn [calls]
        (doseq [[label follow] emailed-links]
          (let [session (:session (follow link-token))]
            (is (= user-id (:user-id session)) label)
            (is (nil? (:pending-2fa session)) label)
            (is (<= (apply max (cutoffs @calls)) (:created-at session))
                (str label " keeps the session it just made"))))))
    (finally (clock/install! (clock/->SystemClock)))))

(deftest a-wrong-link-mints-nothing
  (doseq [[label follow] emailed-links]
    (let [response (follow "wrong-token")]
      (is (= ({"a password reset" 410} label 403) (:status response)) label)
      (is (nil? (:session response)) label))))

(deftest log-in-and-verify-mail-the-code-to-the-account
  ;; Typed in capitals, so a code sent to the typed address would show.
  (let [log-in (fn [_] (authentication/log-in {} (str/upper-case account) password))]
    (with-mail
      (fn [sent]
        (with-second-factor :email
          (fn [_]
            (doseq [[label follow] {"an email verification" (emailed-links "an email verification")
                                    "a log-in"              log-in}]
              (let [response (follow link-token)]
                (is (= #{:pending-2fa} (set (keys (:session response)))) label)
                (is (= "/verify-2fa?email=throttle-user%40example.test&method=email"
                       (get-in response [:body :second-factor-url]))
                    label)))))
        (is (= (repeat 2 [account :2fa]) @sent))))))

(deftest a-failed-code-email-still-leaves-the-marker
  (with-redefs [email/send-email! (fn [& _] (throw (ex-info "SMTP down" {})))]
    (with-second-factor :email
      (fn [_]
        (testing "for a log-in"
          (is (= user-id (get-in (authentication/log-in {} account password) [:session :pending-2fa :user-id]))))
        (testing "and for a reset, which says the send failed so the page offers it again"
          (let [response (authentication/send-reset-code {} account link-token)]
            (is (= 502 (:status response)))
            (is (= user-id (get-in response [:session :pending-reset :user-id])))))))))

(deftest marketplace-sso-links-the-code-page-with-the-email-encoded
  (with-redefs [marketplace/sso-login (fn [_] {:user    (assoc user-row :user_email "first+last@example.test")
                                               :session {}})]
    (is (= "/verify-2fa?email=first%2Blast%40example.test&method=totp"
           (get-in (authentication/marketplace-sso-login {}) [:headers "Location"])))))

(deftest marketplace-sso-mails-the-code-for-emailed-codes
  (with-mail
    (fn [sent]
      (with-redefs [marketplace/sso-login (fn [_] {:user user-row :session {}})]
        (with-second-factor :email
          (fn [_]
            (is (= "/verify-2fa?email=throttle-user%40example.test&method=email"
                   (get-in (authentication/marketplace-sso-login {}) [:headers "Location"]))))))
      (is (= [[account :2fa]] @sent)))))

(deftest the-code-window-runs-on-the-app-clock
  (let [t (atom 0)]
    (clock/install! (reify clock/IClock (now-ms [_] @t)))
    (try
      (let [marked #(:session (authentication/log-in {} account password))]
        (testing "inside the 15-minute window the code is accepted"
          (is (= 200 (verify (marked) (valid-code)))))
        (testing "once the app clock passes the window, the code is refused"
          (let [session (marked)]
            (swap! t + (* 16 60 1000))
            (is (= 401 (verify session (valid-code)))))))
      (finally (clock/install! (clock/->SystemClock))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; A reset asks for the second factor before the new password
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- check-reset-link
  "The answer to opening the reset link in a browser holding `session`."
  [session]
  (authentication/check-reset-link session account link-token))

(defn- send-code [session] (authentication/send-reset-code session account link-token))

(defn- enter-code [session code] (authentication/verify-reset-code session account link-token code))

(defn- with-link-gone
  "Calls `f` as if a mailed code had replaced the link's token."
  [f]
  (let [call-sql database/call-sql]
    (with-redefs [database/call-sql (fn [sql-fn & args]
                                      (when-not (= sql-fn "get_user_by_link_token") (apply call-sql sql-fn args)))]
      (f))))

(deftest opening-a-reset-link-changes-nothing
  (with-mail
    (fn [sent]
      (doseq [[method step] [[:totp  {:step :code :method :totp :sent? false}]
                             [:email {:step :code :method :email :sent? false}]
                             [nil    {:step :form}]]]
        (with-second-factor method
          (fn [_]
            (let [response (check-reset-link signed-in-elsewhere)]
              (is (= step (:body response)) (str method))
              (is (not (contains? response :session)) (str method " leaves the session alone"))))))
      (is (empty? @sent)))))

(deftest a-wrong-reset-link-says-nothing-about-the-account
  (doseq [[label response] [["opening it"      (authentication/check-reset-link {} account "wrong-token")]
                            ["asking for code" (authentication/send-reset-code {} account "wrong-token")]
                            ["entering a code" (authentication/verify-reset-code {} account "wrong-token" (valid-code))]
                            ["setting it"      (authentication/set-user-password {} account new-password "wrong-token")]]]
    (is (= 410 (:status response)) label)
    (is (nil? (:session response)) label)))

(deftest a-mailed-code-marker-serves-only-its-own-link
  (with-mail
    (fn [_]
      (with-second-factor :email
        (fn [_]
          (let [asked (:session (send-code {}))]
            (with-link-gone
              (fn []
                (is (= 410 (:status (authentication/check-reset-link asked account "another-token"))))
                (is (= 410 (:status (authentication/check-reset-link asked other-account link-token))))))))))))

(deftest resending-keeps-the-first-window
  (with-mail
    (fn [_]
      (with-second-factor :email
        (fn [_]
          (let [first-ask (:session (send-code {}))
                resent    (:session (send-code first-ask))]
            (is (= (get-in first-ask [:pending-reset :expires-at])
                   (get-in resent [:pending-reset :expires-at])))
            (testing "but not once it has closed"
              (let [closed (assoc-in first-ask [:pending-reset :expires-at] 0)]
                (is (< 0 (get-in (:session (send-code closed)) [:pending-reset :expires-at])))))))))))

(deftest an-authenticator-code-opens-the-form-and-keeps-the-login
  (let [response (enter-code signed-in-elsewhere (valid-code))]
    (is (= {:step :form} (:body response)))
    (testing "the login already on this browser stays until the password changes"
      (is (= other-user-id (get-in response [:session :user-id]))))
    (testing "and the link opens the form from then on"
      (is (= {:step :form} (:body (check-reset-link (:session response))))))))

(deftest an-emailed-code-is-mailed-only-when-asked
  (with-mail
    (fn [sent]
      (with-second-factor :email
        (fn [_]
          (let [asked (send-code signed-in-elsewhere)]
            (is (= {:sent? true} (:body asked)))
            (is (= 200 (:status asked)) "a plain 200, or the handler drops the session")
            (is (= [[account :2fa]] @sent))
            (is (= other-user-id (get-in asked [:session :user-id])) "the login stays")
            (with-link-gone
              (fn []
                (testing "re-opening the link, whose token the code replaced, returns to the code step"
                  (is (= {:step :code :method :email :sent? true} (:body (check-reset-link (:session asked))))))
                (testing "a resend still works"
                  (is (= {:sent? true} (:body (send-code (:session asked))))))
                (testing "and the mailed code opens the form"
                  (is (= {:step :form} (:body (enter-code (:session asked) email-code)))))))))))))

(deftest an-authenticator-account-gets-no-mailed-code
  (with-mail
    (fn [sent]
      (is (= 410 (:status (send-code {}))))
      (is (empty? @sent)))))

(deftest a-wrong-reset-code-opens-nothing
  (let [response (enter-code {} wrong-code)]
    (is (= 403 (:status response)))
    (is (nil? (:session response)))))

(deftest reset-codes-spend-the-second-factor-budget
  (is (= (repeat 6 403) (mapv (fn [_] (:status (enter-code {} wrong-code))) (range 6))))
  (is (= 429 (:status (enter-code {} (valid-code))))))

(deftest a-reset-without-the-code-leaves-the-password-alone
  (with-mail
    (fn [_]
      (doseq [method [:totp :email]]
        (with-second-factor method
          (fn [calls]
            (doseq [[label session] [["no code entered"   {}]
                                     ["a code only asked" (:session (send-code {}))]
                                     ["code passed for another account"
                                      (session/cleared-for-reset {} {:user-id other-user-id :user-email other-account}
                                                                 password-set-ms (clock/now))]]]
              (let [response (authentication/set-user-password session account new-password link-token)]
                (is (= 403 (:status response)) (str method " " label))
                (is (nil? (:session response)) (str method " " label))))
            (is (not-any? #(#{"set_user_password" "set_user_password_by_id"} (first %)) @calls)
                (str method ": no password was saved"))))))))

(deftest the-code-lets-the-new-password-log-the-user-in
  (with-second-factor :totp
    (fn [calls]
      (let [before   (clock/now)
            response (authentication/set-user-password (:session (enter-code {} (valid-code))) account new-password link-token)]
        (is (= user-id (get-in response [:session :user-id])))
        (is (some #{["set_user_password_by_id" {:log? false} user-id new-password password-set-ms]} @calls)
            "the database sets it only while the password is the one the code was entered against")
        (is (some #(>= % before) (cutoffs @calls)) "old sessions of the account end")))))

(deftest a-passed-code-lasts-the-code-window
  (let [t (atom 0)]
    (clock/install! (reify clock/IClock (now-ms [_] @t)))
    (try
      (let [cleared (:session (enter-code {} (valid-code)))]
        (swap! t + (* 16 60 1000))
        (is (= 403 (:status (authentication/set-user-password cleared account new-password link-token)))))
      (finally (clock/install! (clock/->SystemClock))))))

(deftest a-code-login-ends-when-the-password-changes
  (let [marked (:session (authentication/log-in {} account password))]
    (with-redefs [database/call-sql (fn [sql-fn & args]
                                      (if (= sql-fn "get_password_set_ms")
                                        [{:get_password_set_ms (inc password-set-ms)}]
                                        (apply fake-call-sql sql-fn args)))]
      (is (= 401 (verify marked (valid-code))) "a reset after the password step ends it"))
    (is (= 200 (verify marked (valid-code))) "otherwise the code finishes it")))

(deftest a-reset-while-the-code-is-mailed-still-ends-the-login
  (let [stamp (atom password-set-ms)]
    (with-redefs [email/send-email! (fn [& _] (swap! stamp inc))]
      (with-second-factor :email
        (fn [_]
          (with-redefs [database/call-sql (let [call-sql database/call-sql]
                                            (fn [sql-fn & args]
                                              (if (= sql-fn "get_password_set_ms")
                                                [{:get_password_set_ms @stamp}]
                                                (apply call-sql sql-fn args))))]
            (is (= 401 (verify (:session (authentication/log-in {} account password)) email-code)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The budget
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest the-second-factor-has-its-own-budget
  (let [marked (marker)]
    (testing "six wrong codes are each checked on their merits"
      (is (= (repeat 6 403) (spend-the-budget marked))))
    (testing "the seventh is refused rather than checked"
      (is (= 429 (verify marked wrong-code))))))

(deftest a-lockout-refuses-a-code-that-would-otherwise-work
  (let [marked (marker)]
    (spend-the-budget marked)
    (testing "guessing right after the allowance is gone still mints no session"
      (let [response (authentication/verify-2fa marked account (valid-code))]
        (is (= 429 (:status response)))
        (is (nil? (:session response)))))))

(deftest the-budget-is-charged-before-the-code-is-checked
  (let [charged (atom nil)]
    (with-redefs [database/call-sql (fn [sql-fn & args]
                                      (when (= "get_user_with_totp" sql-fn)
                                        ;; First sighting only: a second check would paper over a
                                        ;; first one that ran before the charge.
                                        (swap! charged #(or % (get @throttle/attempts [:2fa user-id] 0))))
                                      (apply fake-call-sql sql-fn args))]
      (verify (marker) (valid-code)))
    (testing "the attempt is on the books by the time the credential is consulted"
      (is (= 1 @charged)))))

(deftest backup-codes-spend-the-same-budget
  (let [marked (marker)]
    (is (= 200 (verify marked backup-code)))
    (spend-the-budget marked)
    (is (= 429 (verify marked backup-code)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Who forgives what
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest a-fumbled-password-leaves-the-second-factor-budget-whole
  (dotimes [_ 5] (authentication/log-in {} account "wrong"))
  (let [marked (:session (authentication/log-in {} account password))]
    (testing "the correct password forgives its own count and nothing else"
      (is (= {} @throttle/attempts)))
    (testing "so the whole second-factor allowance is still there"
      (is (= (repeat 6 403) (spend-the-budget marked)))
      (is (= 429 (verify marked wrong-code))))))

(deftest a-correct-code-forgives-both-steps
  (dotimes [_ 3] (authentication/log-in {} account "wrong"))
  (let [marked (marker)]
    (verify marked wrong-code)
    (is (= 2 (count @throttle/attempts)) "both counts standing")
    (is (= 200 (verify marked (valid-code))))
    (is (= {} @throttle/attempts) "a finished authentication forgives both")))

(deftest log-in-still-locks-out-after-six-attempts
  (testing "six wrong passwords are checked"
    (is (= (repeat 6 403)
           (mapv (fn [_] (:status (authentication/log-in {} account "wrong"))) (range 6)))))
  (testing "the seventh is refused, and so is the right password"
    (is (= 429 (:status (authentication/log-in {} account "wrong"))))
    (is (= 429 (:status (authentication/log-in {} account password))))))

(deftest the-budget-is-per-account
  (dotimes [_ 7] (authentication/log-in {} account "wrong"))
  (testing "one locked-out account does not lock out another"
    (is (= 403 (:status (authentication/log-in {} other-account "wrong"))))))
