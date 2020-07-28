(ns pyregence.email
  (:import java.util.UUID)
  (:require [clojure.edn :as edn]
            [pyregence.database :refer [call-sql]]
            [pyregence.views :refer [data-response]]
            [postal.core :refer [send-message]]))

(defn get-mail-config []
  (edn/read-string (slurp "email-server.edn")))

;; TODO get name for greeting line.
(defn get-password-reset-message [url email reset-key]
  (str "Hi " email ",\n\n"
       "  To reset your password, simply click the following link:\n\n"
       "  " url "/reset-password?email=" email "&reset-key=" reset-key "\n\n"
       "  - Pyregence Technical Support"))

(defn get-new-user-message [url email reset-key]
  (str "Hi " email ",\n\n"
       "  You have been registered for Pyregence. Please verify your email by clicking the following link:\n\n"
       "  " url "/verify-email?email=" email "&reset-key=" reset-key "\n\n"
       "  - Pyregence Technical Support"))

(defn send-reset-key-email! [mail-config email subject message-fn]
  (let [reset-key (str (UUID/randomUUID))
        result    (send-message
                   (dissoc mail-config :site-url)
                   {:from    (mail-config :user)
                    :to      email
                    :subject subject
                    :body    (message-fn (:site-url mail-config) email reset-key)})]
    (call-sql "set_reset_key" email reset-key)
    (data-response email {:status (when-not (= :SUCCESS (:error result)) 400)})))

(defn send-email [email email-type]
  (let [mail-config (get-mail-config)]
    (condp = email-type
      :reset    (send-reset-key-email! mail-config
                                       email
                                       "Pyregence Password Reset"
                                       get-password-reset-message)
      :new-user (send-reset-key-email! mail-config
                                       email
                                       "Pyregence New User"
                                       get-new-user-message)
      (data-response "Invalid email type." {:status 400}))))
