(ns pyregence.authentication
  (:require [pyregence.views    :refer [data-response]]
            [pyregence.database :refer [call-sql sql-primitive]]))

;;; TODO make a single authentication multimethod

(defn log-in [email password]
  (if-let [user (first (call-sql "pyre.verify_user_login" email password))]
    (data-response "" {:session {:user-id (:user_id user) :org-id (:org_id user)}})
    (data-response "" {:status 401})))

(defn log-out [] (data-response "" {:session nil}))

(defn user-email-exists
  ([email]
   (user-email-exists email -1))
  ([email user-id]
   (if (sql-primitive (call-sql "pyre.user_email_exists" email user-id))
     (data-response "")
     (data-response "" {:status 401}))))

(defn set-user-password [email password reset-key]
  (if-let [user (first (call-sql "pyre.set_user_password" email password reset-key))]
    (data-response "" {:session {:user-id (:user_id user) :org-id (:org_id user)}})
    (data-response "" {:status 401})))

(defn verify-user-email [email reset-key]
  (if-let [user (first (call-sql "pyre.verify_user_email" email reset-key))]
    (data-response "" {:session {:user-id (:user_id user) :org-id (:org_id user)}})
    (data-response "" {:status 401})))

(defn insert-user [email name password]
  (let [settings (pr-str {:theme    :dark
                          :timezone :utc})]
    (if (sql-primitive (call-sql "pyre.insert_user" email name password settings))
      (data-response "")
      (data-response "" {:status 401}))))
