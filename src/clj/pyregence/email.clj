(ns pyregence.email
  (:import java.util.UUID)
  (:require [clojure.edn :as edn]
            [pyregence.database :refer [call-sql sql-primitive]]
            [pyregence.views :refer [data-response]]
            [postal.core :refer [send-message]]))

(defn get-mail-config []
  (edn/read-string (slurp "email-server.edn")))

(defn get-password-reset-message [url username reset-key]
  (str "Hi " username ",\n\n"
       "  To reset your password, simply click the following link:\n\n"
       "  " url "/password/reset?username=" username "&key=" reset-key "\n\n"
       "  - Pyregence Technical Support"))

(defn get-new-user-message [url username reset-key]
  (str "Hi " username ",\n\n"
       "  You have been registered for Pyregence. To complete the registration process and create a password, simply click the following link:\n\n"
       "  " url "/password/reset?username=" username "&key=" reset-key "\n\n"
       "  - Pyregence Technical Support"))

;; FIXME: Need to define contact.get_user_email and contact.set_reset_key or equivalent SQL functions.
(defn send-reset-key-email! [mail-config username subject message-fn]
  (let [reset-key (str (UUID/randomUUID))
        email     (sql-primitive (call-sql "contact.get_user_email" username))
        result    (send-message
                   (dissoc mail-config :site-url)
                   {:from    (mail-config :user)
                    :to      email
                    :subject subject
                    :body    (message-fn (:site-url mail-config) username reset-key)})]
    (call-sql "contact.set_reset_key" username reset-key)
    (data-response username {:status (when-not (= :SUCCESS (:error result)) 400)})))

(defn send-email [username email-type]
  (let [mail-config (get-mail-config)]
    (condp = email-type
      :reset    (send-reset-key-email! mail-config
                                       username
                                       "Pyregence Password Reset"
                                       get-password-reset-message)
      :new-user (send-reset-key-email! mail-config
                                       username
                                       "Pyregence New User"
                                       get-new-user-message)
      (data-response "Invalid email type." {:status 400}))))
