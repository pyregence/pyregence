(ns pyregence.authentication-test
  (:require
   [clojure.test             :refer [deftest is testing use-fixtures]]
   [pyregence.authentication :as authentication]
   [pyregence.marketplace    :as marketplace]
   [pyregence.totp           :as totp]
   [triangulum.config        :as config]
   [triangulum.database      :as database]))

;; -----------------------------------------------------------------------------
;; Failed-attempt throttle on 2FA verification (PYR1-1207 follow-up)
;; -----------------------------------------------------------------------------
;;
;; `log-in` refuses an email after six failed attempts. `verify-2fa` sits behind
;; the same `:auth-type :token` gate, takes an email address plus a code, and
;; returns a session of its own, so it has to spend from the same budget -
;; otherwise the six-digit TOTP codes and the eight-character backup codes can be
;; guessed at whatever rate the network allows.

(def ^:private domain "example.test")

(defn- address
  "Builds a test address so no real mailbox appears in the suite."
  [local-part]
  (str local-part "@" domain))

(def ^:private account       (address "throttle-user"))
(def ^:private other-account (address "someone-else"))

(def ^:private user-id     24)
(def ^:private password    "correct-horse-battery")
(def ^:private totp-secret "JBSWY3DPEHPK3PXP")
(def ^:private backup-code "TESTCODE")

(defn- valid-code
  "The code an authenticator app would be showing right now."
  []
  (str (totp/get-current-totp-code totp-secret)))

(defn- wrong-code
  "A six-digit code that is, by construction, not the current one."
  []
  (format "%06d" (mod (inc (totp/get-current-totp-code totp-secret)) 1000000)))

(def ^:private user-row
  {:user_id user-id :user_email account :user_role "member"})

(defn- fake-call-sql
  "Stands in for the SQL functions the two login paths touch. Scalar-returning
   functions always hand back a single row, as they do from Postgres, so a miss
   is a null column rather than an empty result."
  [sql-fn & args]
  (let [args (if (map? (first args)) (rest args) args)] ; drop the {:log? false} opts
    (case sql-fn
      "get_user_id_by_email" [{:user_id (when (= (first args) account) user-id)}]
      "get_user_settings"    [{:settings (pr-str {:two-factor :totp})}]
      "get_user_with_totp"   [(assoc user-row :secret totp-secret)]
      "use_backup_code"      [{:use_backup_code (= (second args) backup-code)}]
      "verify_user_login"    (when (= (second args) password) [user-row])
      nil)))

(defn- with-stubbed-db
  "Runs `f` with the database, config and marketplace side effects stubbed out."
  [f]
  (with-redefs [database/call-sql            fake-call-sql
                config/get-config            (fn [& _] {})
                marketplace/complete-signup! (fn [& _] nil)]
    (f)))

(use-fixtures :each
  (fn [f]
    (reset! authentication/user-email->failed-login-attempts {})
    (f)))

(defn- verify [code]
  (:status (authentication/verify-2fa nil account code)))

(deftest verify-2fa-locks-out-after-six-wrong-codes
  (with-stubbed-db
    (fn []
      (testing "the first six wrong codes are each refused on their merits"
        (doseq [attempt (range 1 7)]
          (is (= 403 (verify (wrong-code)))
              (str "attempt " attempt " should be rejected, not locked out"))))
      (testing "the seventh is locked out rather than checked"
        (is (= 429 (verify (wrong-code))))))))

(deftest verify-2fa-lockout-refuses-a-code-that-would-otherwise-work
  (with-stubbed-db
    (fn []
      (dotimes [_ 6] (verify (wrong-code)))
      (testing "guessing correctly after the budget is spent does not mint a session"
        (let [response (authentication/verify-2fa nil account (valid-code))]
          (is (= 429 (:status response)))
          (is (nil? (:session response))))))))

(deftest verify-2fa-lockout-covers-backup-codes
  (with-stubbed-db
    (fn []
      (dotimes [_ 6] (verify "AAAAAAAA"))
      (testing "the backup codes are throttled on the same budget"
        (is (= 429 (verify backup-code)))))))

(deftest verify-2fa-shares-the-budget-with-log-in
  (with-stubbed-db
    (fn []
      (testing "an account locked out of log-in cannot keep guessing at verify-2fa"
        (dotimes [_ 6] (authentication/log-in nil account "wrong-password"))
        (is (= 429 (:status (authentication/log-in nil account "wrong-password")))
            "log-in should be locked")
        (is (= 429 (verify (wrong-code)))
            "verify-2fa should be locked too")))))

(deftest verify-2fa-clears-the-count-once-the-code-checks-out
  (with-stubbed-db
    (fn []
      (dotimes [_ 3] (verify (wrong-code)))
      (testing "a correct code is accepted and forgives the earlier fumbles"
        (is (= 200 (:status (authentication/verify-2fa nil account (valid-code)))))
        (is (nil? (@authentication/user-email->failed-login-attempts account))))
      (testing "the budget starts over afterwards"
        (dotimes [_ 6] (verify (wrong-code)))
        (is (= 429 (verify (wrong-code))))))))

(deftest verify-2fa-throttles-per-email
  (with-stubbed-db
    (fn []
      (dotimes [_ 6] (verify (wrong-code)))
      (testing "one locked-out account does not lock out another"
        (is (= 403 (:status (authentication/verify-2fa nil other-account (wrong-code)))))))))

;; -----------------------------------------------------------------------------
;; Behaviour that must not move
;; -----------------------------------------------------------------------------

(deftest verify-2fa-still-accepts-a-valid-code
  (with-stubbed-db
    (fn []
      (testing "TOTP code"
        (is (= 200 (:status (authentication/verify-2fa nil account (valid-code))))))
      (testing "backup code"
        (is (= 200 (:status (authentication/verify-2fa nil account backup-code))))))))

(deftest verify-2fa-refuses-an-unknown-account
  (with-stubbed-db
    (fn []
      (is (= 403 (:status (authentication/verify-2fa nil other-account "123456")))))))

(deftest verify-2fa-without-an-email-is-refused-not-an-error
  (with-stubbed-db
    (fn []
      (testing "a request that omits the email is answered, not thrown"
        (is (= 403 (:status (authentication/verify-2fa nil nil "123456"))))))))

(deftest log-in-still-locks-out-after-six-attempts
  (with-stubbed-db
    (fn []
      (doseq [attempt (range 1 7)]
        (is (= 403 (:status (authentication/log-in nil account "wrong-password")))
            (str "attempt " attempt " should be rejected, not locked out")))
      (is (= 429 (:status (authentication/log-in nil account "wrong-password"))))
      (testing "the count is reported back to the caller"
        (is (= 6 (@authentication/user-email->failed-login-attempts account)))))))

(deftest log-in-without-an-email-is-refused-not-an-error
  (with-stubbed-db
    (fn []
      (is (= 403 (:status (authentication/log-in nil nil "wrong-password")))))))
