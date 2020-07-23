(ns pyregence.authentication
  (:require [pyregence.views    :refer [data-response]]
            [pyregence.database :refer [call-sql sql-primitive]]))

;;; TODO make a single authentication multimethod

(defn log-in [email password]
  (if-let [user (first (call-sql "verify_user_login" email password))]
    (data-response "" {:session {:user-id (:user_id user) :org-id (:org_id user)}})
    (data-response "" {:status 403})))

(defn log-out [] (data-response "" {:session nil}))

(defn user-email-exists
  ([email]
   (user-email-exists email -1))
  ([email user-id]
   (if (sql-primitive (call-sql "user_email_exists" email user-id))
     (data-response "")
     (data-response "" {:status 403}))))

(defn set-user-password [email password reset-key]
  (if-let [user (first (call-sql "set_user_password" email password reset-key))]
    (data-response "" {:session {:user-id (:user_id user) :org-id (:org_id user)}})
    (data-response "" {:status 403})))

(defn verify-user-email [email reset-key]
  (if-let [user (first (call-sql "verify_user_email" email reset-key))]
    (data-response "" {:session {:user-id (:user_id user) :org-id (:org_id user)}})
    (data-response "" {:status 403})))

(defn insert-user [email name password]
  (let [settings (pr-str {:theme    :dark
                          :timezone :utc})]
    (if (sql-primitive (call-sql "insert_user" email name password settings))
      (data-response "")
      (data-response "" {:status 403}))))
