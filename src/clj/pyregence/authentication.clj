(ns pyregence.authentication
  (:require [triangulum.config   :refer [get-config]]
            [triangulum.database :refer [call-sql sql-primitive]]
            [pyregence.email     :as email]
            [triangulum.response :refer [data-response]]
            [pyregence.totp      :as totp]
            [pyregence.utils     :refer [nil-on-error]]))

;;; Helper Functions

(defn- create-session-from-user-data
  "Creates a session response from user data returned by SQL functions.
   This is the single source of truth for session structure."
  [user-data]
  (when user-data
    (call-sql "set_users_last_login_date_to_now" (:user_id user-data))
    (data-response "" {:session (merge {:match-drop-access? (:match_drop_access user-data)
                                        :super-admin?       (:super_admin user-data)
                                        :user-email         (:user_email user-data)
                                        :user-id            (:user_id user-data)
                                        :analyst?           (:analyst user-data)}
                                       (get-config :app :client-keys))})))

(defn- is-user-admin-of-org? [user-id org-id]
  (call-sql "is_user_admin_of_org" user-id org-id))

(defn- parse-user-settings
  "Safely parses user settings from EDN string, returning empty map on error."
  [settings-str]
  (or (nil-on-error
       (when settings-str
         (read-string settings-str)))
      {}))

;;; Authentication & Session Management

(defn successful-login
  [{:keys [user-id] :as user}]
  (call-sql "set_users_last_login_date_to_now" user-id)
  (create-session-from-user-data user))

(defn log-in
  "Authenticates user and determines 2FA requirements."
  [_ email password]
  (if-let [user (first (call-sql "verify_user_login" {:log? false} email password))]
    (let [user-id (:user_id user)
          {:keys [settings]} (first (call-sql "get_user_settings" user-id))
          two-factor (:two-factor (parse-user-settings settings))]
      (case two-factor
        :totp  (data-response {:email email :require-2fa true :method "totp"})
        :email (do (email/send-email! nil email :2fa)
                   (data-response {:email email :require-2fa true :method "email"}))
        (successful-login user)))
    (data-response "" {:status 403})))

^:rct/test
(comment
  ;; Test invalid credentials
  (log-in nil "invalid@example.com" "wrongpass")
  ;=>> {:status 403}

  ;; Test valid login without 2FA
  (log-in nil "user@pyr.dev" "user")
  ;=>> {:status 200 :session some?}

  ;; Test login with TOTP 2FA
  (log-in nil "totp-2fa@pyr.dev" "totp2fa")
  ;=>> {:status 200 :body string?}

  ;; Test login with email 2FA
  (log-in nil "email-2fa@pyr.dev" "email2fa"))
  ;=>> {:status 200 :body string?}

(defn log-out [_] (data-response "" {:session nil}))

(defn set-user-password
  "Sets a new password for user with valid reset token."
  [_ email password token]
  (if-let [user (first (call-sql "set_user_password" {:log? false} email password token))]
    (create-session-from-user-data user)
    (data-response "Invalid or expired reset token" {:status 403})))

(defn verify-user-email
  "Verifies user email with the provided token."
  [_ email token]
  (if-let [user (first (call-sql "verify_user_email" email token))]
    (create-session-from-user-data user)
    (data-response "Invalid or expired verification token" {:status 403})))

;;; 2FA Core Functions

(defn verify-2fa
  "Verifies 2FA code for email/TOTP authentication."
  [_ email code]
  (if-let [user-id (sql-primitive (call-sql "get_user_id_by_email" email))]
    (let [{:keys [settings]} (first (call-sql "get_user_settings" user-id))
          two-factor (:two-factor (parse-user-settings settings))]
      (case two-factor
        :totp
        (if-let [{:keys [secret] :as user} (first (call-sql "get_user_with_totp" user-id))]
          (if (or (totp/validate-totp-code secret code)
                  (sql-primitive (call-sql "use_backup_code" user-id code)))
            (successful-login user)
            (data-response "Invalid code" {:status 403}))
          (data-response "TOTP not configured" {:status 403}))

        :email
        (if-let [user (first (call-sql "verify_user_2fa" email code))]
          (successful-login user)
          (data-response "Invalid email verification code" {:status 403}))

        (data-response "2FA not configured for this account" {:status 403})))
    (data-response "User not found" {:status 403})))

^:rct/test
(comment
  ;; Test user not found
  (verify-2fa nil "nonexistent@test.com" "123456")
  ;=>> {:status 403 :body string?}

  ;; Test TOTP with invalid code
  (verify-2fa nil "totp-2fa@pyr.dev" "wrong")
  ;=>> {:status 403 :body string?}

  ;; Test TOTP with valid code
  (let [user-id 24
        ;; Save original settings
        original-settings (:settings (first (call-sql "get_user_settings" user-id)))
        ;; Ensure user has verified TOTP
        _ (call-sql "update_user_settings" user-id (pr-str {:timezone :utc :two-factor :totp}))
        _ (call-sql "delete_totp_setup" user-id)
        _ (call-sql "create_totp_setup" user-id "JBSWY3DPEHPK3PXP")
        _ (call-sql "mark_totp_verified" user-id)
        secret (:secret (first (call-sql "get_totp_setup" user-id)))
        valid-code (str (totp/get-current-totp-code secret))]
    (try
      (verify-2fa nil "totp-2fa@pyr.dev" valid-code)
      (finally
        ;; Restore original state
        (call-sql "update_user_settings" user-id original-settings))))
  ;=>> {:status 200 :session some?}

  ;; Test backup code usage
  (let [user-id 24
        ;; Ensure user has TOTP enabled
        _ (call-sql "update_user_settings" user-id (pr-str {:timezone :utc :two-factor :totp}))
        _ (call-sql "delete_backup_codes" user-id)
        _ (call-sql "create_backup_codes" user-id (into-array ["TESTCODE"]))]
    (try
      (verify-2fa nil "totp-2fa@pyr.dev" "TESTCODE")
      (finally
        ;; Clean up
        (call-sql "delete_backup_codes" user-id))))
  ;=>> {:status 200 :session some?}

  ;; Test email 2FA
  (let [_ (require '[pyregence.email :as email])
        _ (email/mock-send-2fa-code "email-2fa@pyr.dev")
        token (with-out-str
                (email/mock-send-2fa-code "email-2fa@pyr.dev"))
        ;; Extract the code from the printed output
        code (second (re-find #"2FA CODE for .* : (\d+)" token))]
    (verify-2fa nil "email-2fa@pyr.dev" code))
  ;=>> {:status 200 :session some?}

  ;; Test no 2FA configured
  (verify-2fa nil "user@pyr.dev" "123456"))
  ;=>> {:status 403 :body string?}

;;; TOTP Management

(defn begin-totp-setup
  "Initiates TOTP setup for the authenticated user.
   Returns QR URI, secret, and backup codes for authenticator app setup."
  [{:keys [user-id user-email]}]
  (cond
    (nil? user-id)
    (data-response "Not authenticated" {:status 401})

    (sql-primitive (call-sql "has_verified_totp" user-id))
    (data-response "TOTP is already enabled for this account" {:status 400})

    :else
    (if-let [{:keys [secret verified]} (first (call-sql "get_totp_setup" user-id))]
      (when-not verified
        (let [existing-codes (map :code (call-sql "get_backup_codes" user-id))]
          (data-response {:backup-codes existing-codes
                          :qr-uri       (totp/generate-totp-uri user-email secret)
                          :resuming     true
                          :secret       secret})))

      (let [secret (totp/generate-secret)
            backup-codes (totp/generate-backup-codes 10)]
        (call-sql "delete_backup_codes" user-id)
        (call-sql "create_totp_setup" user-id secret)
        (call-sql "create_backup_codes" user-id (into-array String backup-codes))
        (data-response {:backup-codes backup-codes
                        :qr-uri       (totp/generate-totp-uri user-email secret)
                        :resuming     false
                        :secret       secret})))))

^:rct/test
(comment
  ;; Test unauthenticated access
  (begin-totp-setup {})
  ;=>> {:status 401}

  ;; Test user with already verified TOTP
  (begin-totp-setup {:user-id 24 :user-email "totp-2fa@pyr.dev"})
  ;=>> {:status 400}

  ;; First time setup returns all data needed
  (let [_ (call-sql "delete_totp_setup" 1) ; Clean any existing setup first
        response (begin-totp-setup {:user-id 1 :user-email "test@pyregence.com"})]
    (try
      (when (= 200 (:status response))
        (let [body (read-string (:body response))]
          [(set (keys body))
           (:resuming body)
           (= 10 (count (:backup-codes body)))]))
      (finally
        ;; Clean up
        (call-sql "delete_totp_setup" 1)
        (call-sql "delete_backup_codes" 1))))
  ;=> [#{:qr-uri :secret :backup-codes :resuming} false true]

  ;; Resuming returns same data structure
  (let [first-resp (begin-totp-setup {:user-id 1 :user-email "test@pyregence.com"})
        second-resp (begin-totp-setup {:user-id 1 :user-email "test@pyregence.com"})]
    (try
      (when (every? #(= 200 (:status %)) [first-resp second-resp])
        (let [first-body (read-string (:body first-resp))
              second-body (read-string (:body second-resp))]
          [(= (set (keys first-body)) (set (keys second-body)))
           (:resuming second-body)
           (= (:secret first-body) (:secret second-body))]))
      (finally
        ;; Clean up
        (call-sql "delete_totp_setup" 1)
        (call-sql "delete_backup_codes" 1)))))
  ;=> [true true true]

(defn complete-totp-setup
  "Completes TOTP setup by validating the provided code against the unverified setup."
  [{:keys [user-id]} code]
  (cond
    (nil? user-id)
    (data-response "Not authenticated" {:status 401})

    :else
    (if-let [{:keys [secret verified]} (first (call-sql "get_totp_setup" user-id))]
      (cond
        verified
        (data-response "TOTP is already verified" {:status 400})

        (not (totp/validate-totp-code secret code))
        (data-response "Invalid verification code" {:status 400})

        :else
        (do
          (call-sql "mark_totp_verified" user-id)
          (let [settings (-> (call-sql "get_user_settings" user-id)
                             first
                             :settings
                             parse-user-settings
                             (assoc :two-factor :totp))]
            (call-sql "update_user_settings" user-id (pr-str settings)))
          (data-response {:message "TOTP setup completed successfully"})))
      (data-response "No TOTP setup in progress" {:status 400}))))

^:rct/test
(comment
  ;; Test unauthenticated access
  (complete-totp-setup {} "123456")
  ;=>> {:status 401}

  ;; Test no TOTP setup exists
  (complete-totp-setup {:user-id 99} "123456")
  ;=>> {:status 400}

  ;; Test already verified TOTP
  (let [verified-user {:user-id 24}]
    (complete-totp-setup verified-user "123456"))
  ;=>> {:status 400}

  ;; Test invalid code
  (do
    (call-sql "delete_totp_setup" 1)
    (call-sql "create_totp_setup" 1 "TESTSECRET")
    (complete-totp-setup {:user-id 1} "wrong"))
  ;=>> {:status 400}

  ;; Test successful completion
  (do
    (call-sql "delete_totp_setup" 1)
    (call-sql "create_totp_setup" 1 "TESTSECRET")
    (let [valid-code (str (totp/get-current-totp-code "TESTSECRET"))]
      (complete-totp-setup {:user-id 1} valid-code))))
  ;=>> {:status 200}

(defn get-backup-codes
  "Returns the backup codes for the current user if TOTP is enabled."
  [{:keys [user-id]}]
  (cond
    (nil? user-id)
    (data-response "Not authenticated" {:status 401})

    (not (sql-primitive (call-sql "has_verified_totp" user-id)))
    (data-response {:backup-codes []})

    :else
    (->> (call-sql "get_backup_codes" user-id)
         (mapv (fn [{:keys [code used created_at]}]
                 {:code       code
                  :created-at created_at
                  :used?      used}))
         (hash-map :backup-codes)
         (data-response))))

^:rct/test
(comment
  ;; Test unauthenticated access
  (get-backup-codes {})
  ;=>> {:status 401, :body "\"Not authenticated\""}

  ;; Test user without TOTP
  (get-backup-codes {:user-id 2})
  ;=>> {:status 200, :body "{:backup-codes []}"}

  ;; Test user with TOTP
  (get-backup-codes {:user-id 24}))
  ;=>> {:status 200}

(defn regenerate-backup-codes
  "Regenerates backup codes for the current user. Requires current TOTP code for security."
  [{:keys [user-id]} code]
  (cond
    (nil? user-id)
    (data-response "Not authenticated" {:status 401})

    :else
    (if-let [{:keys [secret verified]} (first (call-sql "get_totp_setup" user-id))]
      (cond
        (not verified)
        (data-response "TOTP is not enabled for this account" {:status 400})

        (not (totp/validate-totp-code secret code))
        (data-response "Invalid TOTP code" {:status 403})

        :else
        (do
          (call-sql "delete_backup_codes" user-id)
          (let [new-codes (totp/generate-backup-codes 10)]
            (call-sql "create_backup_codes" user-id (into-array String new-codes))
            (data-response {:backup-codes new-codes}))))
      (data-response "TOTP is not enabled for this account" {:status 400}))))

^:rct/test
(comment
  ;; Test unauthenticated access
  (regenerate-backup-codes {} "123456")
  ;=>> {:status 401, :body "\"Not authenticated\""}

  ;; Test TOTP not enabled
  (regenerate-backup-codes {:user-id 2} "123456")
  ;=>> {:status 400, :body "\"TOTP is not enabled for this account\""}

  ;; Test invalid TOTP code
  (regenerate-backup-codes {:user-id 24} "wrong")
  ;=>> {:status 403, :body "\"Invalid TOTP code\""}

  ;; Test successful regeneration
  (let [user-id 24
        _ (call-sql "update_user_settings" user-id (pr-str {:timezone :utc :two-factor :totp}))
        _ (call-sql "delete_totp_setup" user-id)
        _ (call-sql "create_totp_setup" user-id "JBSWY3DPEHPK3PXP")
        _ (call-sql "mark_totp_verified" user-id)
        secret (:secret (first (call-sql "get_totp_setup" user-id)))
        valid-code (str (totp/get-current-totp-code secret))
        response (regenerate-backup-codes {:user-id user-id} valid-code)]
    (when (= 200 (:status response))
      (seq? (:backup-codes (read-string (:body response)))))))
  ;=> true

(defn remove-totp
  "Removes TOTP authentication for the current user. Requires current TOTP code for security."
  [{:keys [user-id]} code]
  (cond
    (nil? user-id)
    (data-response "Not authenticated" {:status 401})

    :else
    (if-let [{:keys [secret verified]} (first (call-sql "get_totp_setup" user-id))]
      (cond
        (not verified)
        (data-response "TOTP is not enabled for this account" {:status 400})

        (not (totp/validate-totp-code secret code))
        (data-response "Invalid TOTP code" {:status 403})

        :else
        (do
          (call-sql "delete_totp_setup" user-id)
          (call-sql "delete_backup_codes" user-id)
          (let [settings (-> (call-sql "get_user_settings" user-id)
                             first
                             :settings
                             parse-user-settings
                             (dissoc :two-factor))]
            (call-sql "update_user_settings" user-id (pr-str settings)))
          (data-response {:message "TOTP 2FA has been disabled"})))
      (data-response "TOTP is not enabled for this account" {:status 400}))))

^:rct/test
(comment
  ;; Test unauthenticated access
  (remove-totp {} "123456")
  ;=>> {:status 401, :body "\"Not authenticated\""}

  ;; Test TOTP not enabled
  (remove-totp {:user-id 2} "123456")
  ;=>> {:status 400, :body "\"TOTP is not enabled for this account\""}

  ;; Test invalid TOTP code
  (remove-totp {:user-id 24} "wrong")
  ;=>> {:status 403, :body "\"Invalid TOTP code\""}

  ;; Test successful removal
  (let [user-id 24
        secret (:secret (first (call-sql "get_totp_setup" user-id)))
        valid-code (str (totp/get-current-totp-code secret))]
    (remove-totp {:user-id user-id} valid-code)))
  ;=>> {:status 200, :body "{:message \"TOTP 2FA has been disabled\"}"}

;;; User Management

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
        new-user-id (nil-on-error
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

(defn get-current-user-settings
  "Returns settings for the current user."
  [{:keys [user-id]}]
  (if-let [user-info (first (call-sql "get_user_settings" user-id))]
    (data-response user-info)
    (data-response "User not found" {:status 403})))

;; TODO hook into UI
(defn update-current-user-settings [session new-settings]
  (let [user-id (:user-id session)]
    (if (call-sql "update_user_settings" user-id new-settings)
      (data-response "User settings successfully updated.")
      (data-response "User settings were not able to be updated." {:status 403}))))

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

;;; Access Control

(defn get-user-match-drop-access [session]
  (let [{:keys [user-id match-drop-access?]} session]
    (if match-drop-access?
      (data-response (str "The user with an id of " user-id " has Match Drop access."))
      (let [response-msg (if (nil? user-id)
                           "There is no user logged in. Match Drop will remain disabled."
                           (str "The user with an id of " user-id " does not have Match Drop access."))]
        (data-response response-msg {:status 403})))))

;; TODO hook into UI
(defn update-current-user-match-drop-access [session match-drop-access?]
  (let [user-id (:user-id session)]
    (if (call-sql "update_user_match_drop_access" user-id match-drop-access?)
      (data-response (str "Match drop access updated to " match-drop-access?))
      (data-response "Match drop access was not able to be updated." {:status 403}))))

;;; Organization Management

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

(defn get-psps-organizations
  "Returns the list of all organizations that have PSPS layers (currently denoted
   by the presence of a value in the `geoserver_credentials` column)."
  [_]
  (->> (call-sql "get_psps_organizations")
       (mapv #(:org_unique_id %))
       (data-response)))

(defn remove-org-user [session org-id org-user-id]
  (let [user-id (:user-id session)]
    (if-not (is-user-admin-of-org? user-id org-id)
      (data-response "User does not have permission to remove members from this organization."
                     {:status 403})
      (do
        (call-sql "remove_org_user" org-user-id)
        (data-response "")))))

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
