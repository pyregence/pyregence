(ns pyregence.authentication
  (:require [pyregence.views    :refer [data-response]]
            [pyregence.database :refer [call-sql sql-primitive]]))

;;; TODO make a single authentication multimethod

(defn log-in [email password]
  (if-let [user (first (call-sql "verify_user_login" email password))]
    (data-response "" {:session {:user-id (:user_id user)}})
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
    (data-response "" {:session {:user-id (:user_id user)}})
    (data-response "" {:status 403})))

(defn verify-user-email [email reset-key]
  (if-let [user (first (call-sql "verify_user_email" email reset-key))]
    (data-response "" {:session {:user-id (:user_id user)}})
    (data-response "" {:status 403})))

(defn add-new-user [email name password]
  (let [default-settings (pr-str {:theme    :dark
                                  :timezone :utc})
        new-user-id      (sql-primitive (call-sql "add_new_user"
                                                  email
                                                  name
                                                  password
                                                  default-settings))]
    (if new-user-id
      (do (call-sql "auto_add_org_user" new-user-id (re-find #"@{1}.+" email))
          (data-response ""))
      (data-response "" {:status 403}))))

;; TODO hook into UI
(defn get-user-info [user-id]
  (if-let [user-info (first (call-sql "get_user_info" user-id))]
    (data-response user-info)
    (data-response "" {:status 403})))

;; TODO hook into UI
(defn update-user-info [user-id settings]
  (call-sql "update_user_info" user-id settings)
  (data-response ""))

(defn get-org-list [user-id]
  (->> (call-sql "get_org_list" user-id)
       (mapv (fn [{:keys [org_id org_name email_domains auto_add auto_accept]}]
               {:opt-id        org_id
                :opt-label     org_name
                :email-domains email_domains
                :auto-add?     auto_add
                :auto-accept?  auto_accept}))
       (data-response)))

(defn get-org-users-list [org-id]
  (->> (call-sql "get_org_users_list" org-id)
       (mapv (fn [{:keys [org_user_id name email role_id]}]
               {:opt-id    org_user_id
                :opt-label name
                :email     email
                :role-id   role_id}))
       (data-response)))

(defn update-org-info [org-id org-name email-domains auto-add? auto-accept?]
  (call-sql "update_org_info" org-id org-name email-domains auto-add? auto-accept?)
  (data-response ""))

(defn add-org-user [org-id email]
  (if-let [user-id (sql-primitive (call-sql "user_email_exists" email -1))]
    (do (call-sql "add_org_user" org-id user-id)
        (data-response ""))
    (data-response (str "There is no user with the email " email)
                   {:status 403})))

(defn update-org-user-role [org-user-id role-id]
  (call-sql "update_org_user_role" org-user-id role-id)
  (data-response ""))

(defn remove-org-user [org-user-id]
  (call-sql "remove_org_user" org-user-id)
  (data-response ""))
