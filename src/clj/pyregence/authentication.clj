(ns pyregence.authentication
  (:require [pyregence.email     :as email]
            [pyregence.utils     :refer [nil-on-error]]
            [triangulum.config   :refer [get-config]]
            [triangulum.database :refer [call-sql sql-primitive]]
            [triangulum.response :refer [data-response]]))

(defn log-in [email password]
  (if-let [user (first (call-sql "verify_user_login" {:log? false} email password))]
    (let [user-id (:user_id user)
          settings-str (:settings (first (call-sql "get_user_info" user-id)))
          settings (when settings-str (read-string settings-str))
          two-factor (:two-factor settings)]
      (if (= :email two-factor)
        ;; Email 2FA is enabled
        (do
          (email/send-email! email :2fa)
          (data-response {:email email :require-2fa true}))
        ;; No 2FA required
        (data-response "" {:session (merge {:user-id user-id}
                                           (get-config :app :client-keys))})))
    (data-response "" {:status 403})))

(defn log-out [] (data-response "" {:session nil}))

(defn user-email-taken
  ([email]
   (user-email-taken email -1))
  ([email user-id-to-ignore]
   (if (sql-primitive (call-sql "user_email_taken" email user-id-to-ignore))
     (data-response "")
     (data-response "" {:status 403}))))

(defn set-user-password [email password token]
  (if-let [user (first (call-sql "set_user_password" {:log? false} email password token))]
    (data-response "" {:session {:user-id (:user_id user)}})
    (data-response "" {:status 403})))

(defn verify-user-email [email token]
  (if-let [user (first (call-sql "verify_user_email" email token))]
    (data-response "" {:session {:user-id (:user_id user)}})
    (data-response "" {:status 403})))

(defn add-new-user [email name password & [opts]]
  (let [{:keys [org-id restrict-email?]
         :or   {org-id nil restrict-email? true}} opts
        default-settings (pr-str {:timezone :utc})
        new-user-id      (nil-on-error
                          (sql-primitive (call-sql "add_new_user"
                                                   {:log? false}
                                                   email
                                                   name
                                                   password
                                                   default-settings)))]
    (if new-user-id
      (do (if (and org-id (not restrict-email?))
            (call-sql "add_org_user" org-id new-user-id)
            (call-sql "auto_add_org_user" new-user-id (re-find #"@{1}.+" email)))
          (data-response ""))
      (data-response "" {:status 403}))))

(defn get-email-by-user-id [user-id]
  (if-let [email (sql-primitive (call-sql "get_email_by_user_id" user-id))]
    (data-response email)
    (data-response (str "There is no user with the id " user-id)
                   {:status 403})))

;; TODO hook into UI
(defn get-user-info [user-id]
  (if-let [user-info (first (call-sql "get_user_info" user-id))]
    (data-response user-info)
    (data-response "" {:status 403})))

;; TODO hook into UI add success/failure branches, add auth to route in routing.clj
(defn update-user-info [user-id settings]
  (call-sql "update_user_info" user-id settings)
  (data-response ""))

;; TODO hook into UI and add success/failure branches
(defn update-user-match-drop-access [user-id match-drop-access?]
  (call-sql "update_user_match_drop_access" user-id match-drop-access?)
  (data-response ""))

(defn get-user-match-drop-access [user-id]
  (if (sql-primitive (call-sql "get_user_match_drop_access" user-id))
    (data-response (str "The user with an id of " user-id " has Match Drop access."))
    (let [response-msg (if (nil? user-id)
                         "There is no user logged in. Match Drop will remain disabled."
                         (str "The user with an id of " user-id " does not have Match Drop access."))]
      (data-response response-msg {:status 403}))))

(defn has-match-drop-access? [user-id]
  (sql-primitive (call-sql "get_user_match_drop_access" user-id)))

(defn update-user-name [email new-name]
  (if-let [user-id (sql-primitive (call-sql "get_user_id_by_email" email))]
    (do (call-sql "update_user_name" user-id new-name)
        (data-response ""))
    (data-response (str "There is no user with the email " email)
                   {:status 403})))

(defn get-organizations
  "Given a user's id, returns the list of organizations that they belong to
   and are an admin or a member of."
  [user-id]
  (->> (call-sql "get_organizations" user-id)
       (mapv (fn [{:keys [org_id org_name org_unique_id geoserver_credentials role_id email_domains auto_add auto_accept]}]
               {:org-id                org_id
                :org-name              org_name
                :org-unique-id         org_unique_id
                :geoserver-credentials geoserver_credentials
                :role                  (if (= role_id 1) "admin" "member")
                :email-domains         email_domains
                :auto-add?             auto_add
                :auto-accept?          auto_accept}))
       (data-response)))

(defn is-admin?
  "Returns true if the user is admin of at least one organization."
  [user-id]
  (sql-primitive (call-sql "get_user_admin_access" user-id)))

(defn verify-2fa
  "Verifies a 2FA code"
  [email token]
  (if-let [user (first (call-sql "verify_user_2fa" email token))]
    (data-response "" {:session (merge {:user-id (:user_id user)}
                                       (get-config :app :client-keys))})
    (data-response "" {:status 403})))

(defn get-org-member-users
  "Returns a vector of member users by the given org-id."
  [org-id]
  (->> (call-sql "get_org_member_users" org-id)
       (mapv (fn [{:keys [org_user_id full_name email role_id]}]
               {:org-user-id org_user_id
                :full-name   full_name
                :email       email
                :role-id     role_id}))
       (data-response)))

(defn get-org-non-member-users
  "Returns a vector of non-member users by the given org-id."
  [org-id]
  (data-response (call-sql "get_org_non_member_users" org-id)))

(defn update-org-info [org-id org-name email-domains auto-add? auto-accept?]
  (call-sql "update_org_info" org-id org-name email-domains auto-add? auto-accept?)
  (data-response ""))

(defn add-org-user [org-id email]
  (if-let [user-id (sql-primitive (call-sql "get_user_id_by_email" email))]
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

(defn get-psps-organizations
  "Returns the list of all organizations that have PSPS layers (currently denoted
   by the presence of a value in the `geoserver_credentials` column)."
  []
  (->> (call-sql "get_psps_organizations")
       (mapv #(:org_unique_id %))
       (data-response)))
