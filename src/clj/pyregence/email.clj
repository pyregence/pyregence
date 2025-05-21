(ns pyregence.email
  (:import java.util.UUID)
  (:require [pyregence.utils     :refer [convert-date-string]]
            [triangulum.config   :refer [get-config] :as config]
            [triangulum.database :refer [call-sql]]
            [triangulum.email    :refer [send-mail]]
            [triangulum.response :refer [data-response]]))

;; TODO get name for greeting line.
(defn- get-password-reset-message [base-url email verification-token]
  (str "Hi " email ",\n\n"
       "  To reset your password, simply click the following link:\n\n"
       "  " base-url "/reset-password?email=" email "&verification-token=" verification-token "\n\n"
       "  - Pyregence Technical Support"))

(defn- get-new-user-message [base-url email verification-token]
  (str "Hi " email ",\n\n"
       "  You have been registered for Pyregence. Please verify your email by clicking the following link:\n\n"
       "  " base-url "/verify-email?email=" email "&verification-token=" verification-token "\n\n"
       "  - Pyregence Technical Support"))

(defn- get-match-drop-message [base-url email {:keys [match-job-id display-name fire-name ignition-time lat lon]}]
  (str "Hi " email ",\n\n"
       "  Your Match Drop with ID \"" match-job-id "\" and display name \"" display-name
       "\" has finished running. Please click the following link to view it "
       "on Pyrecast:\n\n" base-url "/forecast?zoom=10&burn-pct=50&forecast=active-fire&fuel="
       "landfire&output=burned&model-init=" (convert-date-string ignition-time)
       "&layer-idx=0&lat=" lat "&weather=hybrid&fire-name=" fire-name "&lng=" lon "&pattern=all&"
       "model=elmfire \n\n"
       "  - Pyregence Technical Support"))

(defn- generate-numeric-token
  "Generate a 6-digit numeric token for verification purposes"
  []
  (format "%06d" (rand-int 1000000)))

(defn- send-verification-email!
  "Send verification email with a token."
  [email subject message-fn]
  (let [verification-token (str (UUID/randomUUID))
        body               (message-fn (get-config :triangulum.email/base-url) email verification-token)
        result             (send-mail email nil nil subject body :text)]
    (call-sql "set_verification_token" email verification-token nil)
    (data-response email {:status (when-not (= :SUCCESS (:error result)) 400)})))

(defn- get-2fa-message
  "Generate message for 2FA verification"
  [_ email token expiry-mins]
  (str "Hi " email ",\n\n"
       "  Please use the following verification code to complete your Pyregence login:\n\n"
       "  " token "\n\n"
       "  This code will expire in " expiry-mins " minutes.\n\n"
       "  - Pyregence Technical Support"))

(defn send-2fa-code
  "Sends a time-limited 2FA code to the user's email"
  [email]
  (let [expiry-mins   (or (config/get-config ::verification-token-expiry-minutes) 15)
        token         (generate-numeric-token)
        expiry-ms     (* expiry-mins 60 1000) ;; Convert minutes to milliseconds
        current-time  (System/currentTimeMillis)
        expiration    (java.sql.Timestamp. (+ current-time expiry-ms))
        body          (get-2fa-message nil email token expiry-mins)
        result        (send-mail email nil nil "Pyregence Login Verification Code" body :text)]
    (call-sql "set_verification_token" email token expiration)
    (data-response email {:status (when-not (= :SUCCESS (:error result)) 400)})))

(defn- send-match-drop-email! [email subject message-fn match-drop-args]
  (let [body   (message-fn (get-config :triangulum.email/base-url) email match-drop-args)
        result (send-mail email nil nil subject body :text)]
    (if (= :SUCCESS (:error result))
      (data-response "Match Drop email successfully sent.")
      (data-response "There was an issue sending the Match Drop email." {:status 400}))))

;; Testing version of send-2fa-code that just prints the code
(defn mock-send-2fa-code
  "For testing only: generates a 2FA code and stores it, but doesn't send an email"
  [email]
  (let [expiry-mins   (or (config/get-config ::verification-token-expiry-minutes) 15)
        token         (generate-numeric-token)
        expiry-ms     (* expiry-mins 60 1000) ;; Convert minutes to milliseconds
        current-time  (System/currentTimeMillis)
        expiration    (java.sql.Timestamp. (+ current-time expiry-ms))
        body          (get-2fa-message nil email token expiry-mins)]

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

(defn send-email! [_ email email-type & [match-drop-args]]
  (condp = email-type
    :reset      (send-verification-email! email
                                          "Pyregence Password Reset"
                                          get-password-reset-message)
    :new-user   (send-verification-email! email
                                          "Pyregence New User"
                                          get-new-user-message)
    :2fa        (send-2fa-code email) ; For testing without email: (mock-send-2fa-code email)
    :match-drop (send-match-drop-email! email
                                        "Match Drop Finished Running"
                                        get-match-drop-message
                                        match-drop-args)
    (data-response "Invalid email type. Options are `:reset`, `:new-user`, `:2fa`, or `:match-drop.`"
                   {:status 400})))
