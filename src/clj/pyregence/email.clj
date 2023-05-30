(ns pyregence.email
  (:import java.util.UUID)
  (:require [triangulum.config   :refer [get-config]]
            [triangulum.database :refer [call-sql]]
            [pyregence.views     :refer [data-response]]
            [pyregence.utils     :refer [convert-date-string]]
            [postal.core         :refer [send-message]]))

;; TODO get name for greeting line.
(defn- get-password-reset-message [base-url email reset-key]
  (str "Hi " email ",\n\n"
       "  To reset your password, simply click the following link:\n\n"
       "  " base-url "/reset-password?email=" email "&reset-key=" reset-key "\n\n"
       "  - Pyregence Technical Support"))

(defn- get-new-user-message [base-url email reset-key]
  (str "Hi " email ",\n\n"
       "  You have been registered for Pyregence. Please verify your email by clicking the following link:\n\n"
       "  " base-url "/verify-email?email=" email "&reset-key=" reset-key "\n\n"
       "  - Pyregence Technical Support"))

(defn- get-match-drop-message [base-url email {:keys [match-job-id display-name fire-name ignition-time lat lon]}]
  (str "Hi " email ",\n\n"
       "  Your Match Drop with ID \"" match-job-id "\" and display-name \"" display-name
       "\" has finished running. Please click the following link to view it "
       "on Pyrcast:\n\n" base-url "/forecast?zoom=10&burn-pct=50&forecast=active-fire&fuel="
       "landfire&output=burned&model-init=" (convert-date-string ignition-time)
       "&layer-idx=0&lat=" lat "&weather=hybrid&fire-name=" fire-name "&lng=" lon "&pattern=all&"
       "model=elmfire \n\n"
       "  - Pyregence Technical Support"))

(defn- send-reset-key-email! [mail-config email subject message-fn]
  (let [reset-key (str (UUID/randomUUID))
        result    (send-message
                   (dissoc mail-config :site-url)
                   {:from    (mail-config :user)
                    :to      email
                    :subject subject
                    :body    (message-fn (:site-url mail-config) email reset-key)})]
    (call-sql "set_reset_key" email reset-key)
    (data-response email {:status (when-not (= :SUCCESS (:error result)) 400)})))

(defn- send-match-drop-email! [mail-config email subject message-fn match-drop-args]
  (let [result (send-message
                (dissoc mail-config :site-url)
                {:from    (mail-config :user)
                 :to      email
                 :subject subject
                 :body    (message-fn (:site-url mail-config) email match-drop-args)})]
    (if (= :SUCCESS (:error result))
      (data-response "Match Drop email successfully sent.")
      (data-response "There was an issue sending the Match Drop email." {:status 400}))))

(defn send-email! [email email-type & [match-drop-args]]
  (let [mail-config (get-config :mail)]
    (condp = email-type
      :reset      (send-reset-key-email! mail-config
                                         email
                                         "Pyregence Password Reset"
                                         get-password-reset-message)
      :new-user   (send-reset-key-email! mail-config
                                         email
                                         "Pyregence New User"
                                         get-new-user-message)
      :match-drop (send-match-drop-email! mail-config
                                          email
                                          "Match Drop Finished Running"
                                          get-match-drop-message
                                          match-drop-args)
      (data-response "Invalid email type. Options are `:reset`, `:new-user`, or `:match-drop.`"
                     {:status 400}))))
