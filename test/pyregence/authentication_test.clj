(ns pyregence.authentication-test
  "The two-step login gate and the failed-attempt budget, with the database stubbed. These complement
   the rich-comment-tests in `pyregence.authentication`, which need a real database and run under
   `bb test`. This namespace runs under `bb test-clj`, needs no database, and can set up the
   locked-out paths a database-backed test cannot."
  (:require [clojure.test             :refer [deftest is testing use-fixtures]]
            [pyregence.authentication :as authentication]
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

(def ^:private user-row
  {:user_id user-id :user_email account :user_role "member"})

(defn- valid-code
  "The code an authenticator app would be showing right now."
  []
  (str (totp/get-current-totp-code totp-secret)))

(defn- fake-call-sql
  "Stands in for the SQL the two login steps touch. Only `user-id` has a second factor and only the
   right password authenticates, so no stub answer stands in for a real one."
  [sql-fn & args]
  (let [args (if (map? (first args)) (rest args) args)] ; drop the {:log? false} opts
    (case sql-fn
      "get_user_settings"  [{:settings (pr-str {:two-factor :totp})}]
      "get_user_with_totp" (when (= (first args) user-id)
                             [(assoc user-row :secret totp-secret)])
      "use_backup_code"    [{:use_backup_code (and (= (first args) user-id)
                                                   (= (second args) backup-code))}]
      "verify_user_login"  (when (= (second args) password) [user-row])
      nil)))

(use-fixtures :each
  ;; `get-config` answers nil rather than an empty map, because a map is truthy and the configured
  ;; timeouts multiply what it returns.
  (fn [run]
    (reset! throttle/attempts {})
    (with-redefs [database/call-sql            fake-call-sql
                  config/get-config            (fn [& _] nil)
                  marketplace/complete-signup! (fn [& _] nil)]
      (run))))

(defn- marker
  "A session as `log-in` leaves it once a password has checked out."
  ([] (marker user-id account))
  ([id email]
   (session/awaiting-2fa {} {:user_id id :user_email email} (System/currentTimeMillis))))

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

(deftest log-in-adds-the-marker-rather-than-replacing-the-session
  (testing "a marketplace signup rides along until the login finishes"
    (let [response (authentication/log-in {:marketplace-signup {:org-name "acme"}} account password)]
      (is (= {:org-name "acme"} (get-in response [:session :marketplace-signup]))))))

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
