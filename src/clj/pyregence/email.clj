(ns pyregence.email
  (:import java.util.UUID)
  (:require [pyregence.email.messages :as messages]
            [triangulum.config        :refer [get-config]]
            [triangulum.database      :refer [call-sql sql-primitive]]
            [triangulum.email         :refer [send-mail]]
            [triangulum.response      :refer [data-response]]))

;; Helper to get security email config
(defn- get-security-email-config
  "Returns email config map for security emails, or nil if not configured."
  []
  (let [{:keys [security-host security-user security-pass
                security-tls security-port]} (get-config :mail)]
    (when (and security-host security-user security-pass)
      {:host security-host
       :user security-user
       :pass security-pass
       :tls  security-tls
       :port security-port})))

(defn- get-email-format
  "Returns the email format preference for a user.
   Checks user settings first, then system config, then defaults to :html"
  [user-email]
  (let [user-settings (when user-email
                        (try
                          (read-string
                           (or (sql-primitive
                                (call-sql "get_user_settings_by_email" user-email))
                               "{}"))
                          (catch Exception _ {})))]
    (or (:email-format user-settings)
        (get-config :pyregence.email/default-format)
        :html)))

;; Message generation functions are now in pyregence.email.messages namespace
;; using multimethod dispatch on format type (:text or :html)

(defn- generate-numeric-token
  "Generate a 6-digit numeric token for verification purposes"
  []
  (format "%06d" (rand-int 1000000)))

(defn- send-verification-email!
  "Send verification email with a token.
   config-type can be :security (for password resets) or :support (for new users)."
  [email subject message-fn config-type]
  (let [user-name          (sql-primitive (call-sql "get_user_name_by_email" email))
        verification-token (str (UUID/randomUUID))
        base-url           (get-config :triangulum.email/base-url)
        fmt                (get-email-format email)
        body               (message-fn fmt base-url email user-name verification-token)
        ;; Use security config only for :security type, otherwise use default
        email-config       (when (= config-type :security)
                             (get-security-email-config))
        from-name          (if (= config-type :security) "PyreCast Security" "PyreCast Support")
        result             (send-mail email nil from-name subject body fmt email-config)]
    (call-sql "set_verification_token" email verification-token nil)
    (data-response email {:status (when-not (= :SUCCESS (:error result)) 400)})))

(defn send-2fa-code
  "Sends a time-limited 2FA code to the user's email"
  [email]
  (let [user-name     (sql-primitive (call-sql "get_user_name_by_email" email))
        expiry-mins   (or (get-config ::verification-token-expiry-minutes) 15)
        token         (generate-numeric-token)
        expiry-ms     (* expiry-mins 60 1000) ;; Convert minutes to milliseconds
        current-time  (System/currentTimeMillis)
        expiration    (java.sql.Timestamp. (+ current-time expiry-ms))
        fmt           (get-email-format email)
        body          (messages/two-fa-email fmt email user-name token expiry-mins)
        email-config  (get-security-email-config)  ; Get security config
        ;; Call send-mail with security config
        result        (send-mail email nil "PyreCast Security" "PyreCast Login Verification Code" body fmt email-config)]
    (call-sql "set_verification_token" email token expiration)
    (data-response email {:status (when-not (= :SUCCESS (:error result)) 400)})))

(defn- send-match-drop-email! [email subject match-drop-args]
  (let [user-name (sql-primitive (call-sql "get_user_name_by_email" email))
        base-url  (get-config :triangulum.email/base-url)
        fmt       (get-email-format email)
        body      (messages/match-drop-email fmt base-url email user-name
                                             (:job-id match-drop-args)
                                             (:display-name match-drop-args)
                                             (:status match-drop-args))
        result    (send-mail email nil "PyreCast Support" subject body fmt)]
    (if (= :SUCCESS (:error result))
      (data-response "Match Drop email successfully sent.")
      (data-response "There was an issue sending the Match Drop email." {:status 400}))))

;; Testing version of send-2fa-code that just prints the code
(defn mock-send-2fa-code
  "For testing only: generates a 2FA code and stores it, but doesn't send an email"
  [email]
  (let [user-name     (sql-primitive (call-sql "get_user_name_by_email" email))
        expiry-mins   (or (get-config ::verification-token-expiry-minutes) 15)
        token         (generate-numeric-token)
        expiry-ms     (* expiry-mins 60 1000) ;; Convert minutes to milliseconds
        current-time  (System/currentTimeMillis)
        expiration    (java.sql.Timestamp. (+ current-time expiry-ms))
        ;; Generate test message using the messages namespace
        body          (messages/two-fa-email :text email user-name token expiry-mins)]

    (println "=====================================")
    (println "TESTING MODE: NO EMAIL SENT")
    (println "2FA CODE for" email ":" token)
    (println "Expires at:" expiration)
    (println "Code will expire in" expiry-mins "minutes (from config)")
    (println "Email body would be:")
    (println body)
    (println "=====================================")

    (call-sql "set_verification_token" email token expiration)
    (data-response email)))

(defn send-email!
  "Send an email of the specified type to the user.
   Automatically selects HTML or text format based on user preferences."
  [_ email email-type & [match-drop-args]]
  (condp = email-type
    :reset      (send-verification-email! email
                                          "PyreCast Password Reset"
                                          messages/password-reset-email
                                          :security)
    :new-user   (send-verification-email! email
                                          "Welcome to PyreCast!"
                                          messages/welcome-email
                                          :support)
    :2fa        (send-2fa-code email) ; For testing without email: (mock-send-2fa-code email)
    :match-drop (send-match-drop-email! email
                                        "Match Drop Ready"
                                        match-drop-args)
    (data-response "Invalid email type. Options are `:reset`, `:new-user`, `:2fa`, or `:match-drop.`"
                   {:status 400})))
