(ns pyregence.authentication
  (:require [pyregence.utils     :refer [nil-on-error]]
            [triangulum.config   :refer [get-config]]
            [triangulum.database :refer [call-sql sql-primitive]]
            [triangulum.response :refer [data-response]]))

;;; Helper Functions

(defn- is-user-admin-of-org? [user-id org-id]
  (call-sql "is_user_admin_of_org" user-id org-id))

;;; API Routes

(defn add-new-user
  "Creates a new user account and optionally associates them with an organization.

  Arguments:
  - `session`: The current user's session (used to enforce authorization/ability to
               add the user to an organization)
  - `email`, `name`, `password`: User credentials and display name
  - `opts` (optional): A map of options that may include:
    - `:org-id` — The organization to assign the user to (default: nil)

  Behavior:
  - A new user is created and persisted to the database.
  - If `:org-id` is provided, the function checks whether the current user is a
    super admin or has admin privileges for the given org. Given the right permissions,
    the user is associated to the org with the role of 'Member'.

  - If `:org-id` is not provided or the user lacks permissions to assign directly,
    the system falls back to automatic domain-based organization assignment.
    This associates the user with an organization that matches their email domain
    to the email_domains column. The user's assigned role is:
    - 'Member' if the organization has `auto_accept = true` (auto-approved)
    - 'Pending' if `auto_accept = false` (pending approval)

  Returns:
  - A 200 OK response if the user was successfully created and optionally assigned
  - A 403 Forbidden response if user creation fails

  Security:
  - Only users with sufficient privileges (super admins or organization admins) may
    explicitly assign a new user to an organization via `:org-id`.
  - All organization assignments are validated server-side using the session context."
  [session email name password & [opts]]
  (let [{:keys [org-id] :or {org-id nil}} opts
        {:keys [user-id super-admin?]} session
        default-settings (pr-str {:timezone :utc})
        new-user-id      (nil-on-error
                          (sql-primitive (call-sql "add_new_user"
                                                   {:log? false}
                                                   email
                                                   name
                                                   password
                                                   default-settings)))]
    (if-not new-user-id
      (data-response (str "Failed to create the new user with name " name " and email " email)
                     {:status 403})
      (cond
        ;; Explicit org assignment, must be super-admin or org admin
        org-id
        (if (or super-admin? (is-user-admin-of-org? user-id org-id))
          (do
            (call-sql "add_org_user" org-id new-user-id)
            (data-response "User created and added to organization."))
          (data-response "User does not have permission to assign users to this organization."
                         {:status 403}))

        ;; No org-id provided — use domain-based auto-assignment
        :else
        (let [domain (re-find #"@{1}.+" email)]
          (if (call-sql "auto_add_org_user" new-user-id domain)
            (data-response "User created and auto-assigned to all matching organizations by email domain.")
            (data-response "User created successfully but something went wrong when calling auto_add_org_user."
                           {:status 403})))))))

(defn add-org-user [session org-id email]
  (let [user-id (:user-id session)]
    (if-not (is-user-admin-of-org? user-id org-id)
      (data-response "User does not have permission to add members to this organization."
                     {:status 403})
      (if-let [user-id-to-add (sql-primitive (call-sql "get_user_id_by_email" email))]
        (do
          (call-sql "add_org_user" org-id user-id-to-add)
          (data-response ""))
        (data-response (str "There is no user with the email " email)
                       {:status 403})))))

(defn get-current-user-organizations
  "Given the current user by session, returns the list of organizations that
   they belong to and are an admin or a member of."
  [session]
  (if-let [user-id (:user-id session)]
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
         (data-response))
    (data-response "No user is logged in." {:status 403})))

;; TODO hook into UI
(defn get-current-user-settings [session]
  (let [user-id (:user-id session)]
    (if-let [user-info (first (call-sql "get_user_settings" user-id))]
      (data-response user-info)
      (data-response "" {:status 403}))))

;; TODO remove me?
(defn get-org-non-member-users
  "Returns a vector of non-member users by the given org-id, if the user is an
   admin of the given org."
  [session org-id]
  (let [user-id (:user-id session)]
    (if (is-user-admin-of-org? user-id org-id)
      (->> (call-sql "get_org_non_member_users" org-id)
           (mapv (fn [{:keys [user_uid email name]}]
                   {:user-id   user_uid
                    :full-name name
                    :email     email}))
           (data-response))
      (data-response "User does not have permission to access this organization."
                     {:status 403}))))

(defn get-org-member-users
  "Returns a vector of member users for the given org-id, if the user is an
   admin of the given org."
  [session org-id]
  (let [user-id (:user-id session)]
    (if (is-user-admin-of-org? user-id org-id)
      (->> (call-sql "get_org_member_users" org-id)
           (mapv (fn [{:keys [org_user_id full_name email role_id]}]
                   {:org-user-id org_user_id
                    :full-name   full_name
                    :email       email
                    :role-id     role_id}))
           (data-response))
      (data-response "User does not have permission to access this organization."
                     {:status 403}))))

(defn get-psps-organizations
  "Returns the list of all organizations that have PSPS layers (currently denoted
   by the presence of a value in the `geoserver_credentials` column)."
  [_]
  (->> (call-sql "get_psps_organizations")
       (mapv #(:org_unique_id %))
       (data-response)))

(defn get-user-match-drop-access [session]
  (let [{:keys [user-id match-drop-access?]} session]
    (if match-drop-access?
      (data-response (str "The user with an id of " user-id " has Match Drop access."))
      (let [response-msg (if (nil? user-id)
                           "There is no user logged in. Match Drop will remain disabled."
                           (str "The user with an id of " user-id " does not have Match Drop access."))]
        (data-response response-msg {:status 403})))))

(defn log-in [_ email password]
  (if-let [user (first (call-sql "verify_user_login" {:log? false} email password))]
    (data-response "" {:session (merge {:user-id            (:user_id user)
                                        :user-email         (:user_email user)
                                        :match-drop-access? (:match_drop_access user)
                                        :super-admin?       (:super_admin user)}
                                       (get-config :app :client-keys))})
    (data-response "" {:status 403})))

(defn log-out [_] (data-response "" {:session nil}))

(defn remove-org-user [session org-id org-user-id]
  (let [user-id (:user-id session)]
    (if-not (is-user-admin-of-org? user-id org-id)
     (data-response "User does not have permission to remove members from this organization."
                    {:status 403})
     (do
       (call-sql "remove_org_user" org-user-id)
       (data-response "")))))

(defn set-user-password [_ email password reset-key]
  (if-let [user (first (call-sql "set_user_password" {:log? false} email password reset-key))]
    (data-response "" {:session {:user-id            (:user_id user)
                                 :user-email         (:user_email user)
                                 :match-drop-access? (:match_drop_access user)
                                 :super-admin?       (:super_admin user)}})
    (data-response "" {:status 403})))

;; TODO hook into UI
(defn update-current-user-match-drop-access [session match-drop-access?]
  (let [user-id (:user-id session)]
    (if (call-sql "update_user_match_drop_access" user-id match-drop-access?)
      (data-response (str "Match drop access updated to " match-drop-access?))
      (data-response "Match drop access was not able to be updated." {:status 403}))))

;; TODO hook into UI
(defn update-current-user-settings [session new-settings]
  (let [user-id (:user-id session)]
    (if (call-sql "update_user_settings" user-id new-settings)
      (data-response "User settings successfully updated.")
      (data-response "User settings were not able to be updated." {:status 403}))))

(defn update-org-info [session org-id org-name email-domains auto-add? auto-accept?]
  (let [user-id (:user-id session)]
    (if (is-user-admin-of-org? user-id org-id)
      (do
        (call-sql "update_org_info" org-id org-name email-domains auto-add? auto-accept?)
        (data-response ""))
      (data-response "User does not have permission to update this organization."
                     {:status 403}))))

(defn update-org-user-role [session org-id org-user-id role-id]
  (let [user-id (:user-id session)]
    (if-not (is-user-admin-of-org? user-id org-id)
     (data-response "User does not have permission to update user roles in this organization."
                    {:status 403})
     (do
       (call-sql "update_org_user_role" org-user-id role-id)
       (data-response "")))))

(defn update-user-name
  "Allows a super admin to update the name of a user by their email."
  [session email new-name]
  (let [super-admin? (:super-admin? session)]
    (if-not super-admin?
      (data-response "You do not have permission to update user names." {:status 403})
      (if-let [user-id-to-update (sql-primitive (call-sql "get_user_id_by_email" email))]
        (do (call-sql "update_user_name" user-id-to-update new-name)
            (data-response (str "User's name successfully updated to " new-name)))
        (data-response (str "There is no user with the email " email)
                       {:status 403})))))

(defn user-email-taken
  ([_ email]
   (user-email-taken nil email -1))
  ([_ email user-id-to-ignore]
   (if (sql-primitive (call-sql "user_email_taken" email user-id-to-ignore))
     (data-response "")
     (data-response "" {:status 403}))))

(defn verify-user-email [_ email reset-key]
  (if-let [user (first (call-sql "verify_user_email" email reset-key))]
    (data-response "" {:session {:user-id            (:user_id user)
                                 :user-email         (:user_email user)
                                 :match-drop-access? (:match_drop_access user)
                                 :super-admin?       (:super_admin user)}})
    (data-response "" {:status 403})))
