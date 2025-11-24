(ns pyregence.authentication
  (:require [pyregence.email     :as email]
            [pyregence.totp      :as totp]
            [pyregence.utils     :refer [nil-on-error]]
            [triangulum.config   :refer [get-config]]
            [triangulum.database :refer [call-sql sql-primitive]]
            [triangulum.response :refer [data-response]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- create-session-from-user-data
  "Creates a session response from user data returned by SQL functions.
   This is the single source of truth for session structure."
  [user-data]
  (when user-data
    (data-response "" {:session (merge {:match-drop-access?    (:match_drop_access user-data)
                                        :user-email            (:user_email user-data)
                                        :user-id               (:user_id user-data)
                                        :user-name             (:user_name user-data)
                                        :user-role             (:user_role user-data)
                                        :organization-id       (:organization_rid user-data)
                                        :org-membership-status (:org_membership_status user-data)}
                                       (get-config :app :client-keys))})))

(defn- parse-user-settings
  "Safely parses user settings from EDN string, returning empty map on error."
  [settings-str]
  (or (nil-on-error
       (when settings-str
         (read-string settings-str)))
      {}))

(defn- get-user-settings
  "Retrieves and parses user settings."
  [user-id]
  (-> (call-sql "get_user_settings" user-id)
      first
      :settings
      parse-user-settings))

(defn- save-user-settings!
  "Saves user settings to the database."
  [user-id settings]
  (call-sql "update_user_settings" user-id (pr-str settings)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Authentication & Session Management
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- successful-login
  [{:keys [user_id] :as user}]
  (call-sql "set_users_last_login_date_to_now" user_id)
  (create-session-from-user-data user))

(defn log-in
  "Authenticates user and determines 2FA requirements."
  [_ email password]
  (if-let [user (first (call-sql "verify_user_login" {:log? false} email password))]
    (let [user-id (:user_id user)
          two-factor (:two-factor (get-user-settings user-id))]
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
  (do
    (save-user-settings! 2 {})
    (log-in nil "user@pyr.dev" "user"))
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; 2FA Core Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn verify-2fa
  "Verifies 2FA code for email/TOTP authentication."
  [_ email code]
  (if-let [user-id (sql-primitive (call-sql "get_user_id_by_email" email))]
    (let [two-factor (:two-factor (get-user-settings user-id))]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; TOTP Management
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
        (let [existing-codes (call-sql "get_backup_codes" user-id)]
          (data-response {:backup-codes (mapv (fn [{:keys [code used]}]
                                                {:code code :used? used})
                                              existing-codes)
                          :qr-uri       (totp/generate-totp-uri user-email secret)
                          :resuming     true
                          :secret       secret})))

      (let [secret (totp/generate-secret)
            backup-codes (totp/generate-backup-codes 10)]
        (call-sql "begin_totp_setup" user-id secret (into-array String backup-codes))
        (data-response {:backup-codes (mapv (fn [code] {:code code :used? false}) backup-codes)
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
          (save-user-settings! user-id
                               (assoc (get-user-settings user-id) :two-factor :totp))
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
  "Regenerates backup codes for the current user. Requires current TOTP code or backup code for security."
  [{:keys [user-id]} code]
  (cond
    (nil? user-id)
    (data-response "Not authenticated" {:status 401})

    :else
    (if-let [{:keys [secret verified]} (first (call-sql "get_totp_setup" user-id))]
      (cond
        (not verified)
        (data-response "TOTP is not enabled for this account" {:status 400})

        (not (or (totp/validate-totp-code secret code)
                 (sql-primitive (call-sql "use_backup_code" user-id code))))
        (data-response "Invalid code" {:status 403})

        :else
        (do
          (let [new-codes (totp/generate-backup-codes 10)]
            (call-sql "regenerate_backup_codes" user-id (into-array String new-codes))
            (data-response {:backup-codes (mapv (fn [code] {:code code :used? false}) new-codes)}))))
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
  ;=>> {:status 403, :body "\"Invalid code\""}

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

(defn enable-email-2fa
  "Enables email-based 2FA after verifying the provided code."
  [{:keys [user-id user-email]} code]
  (cond
    (not user-id)
    (data-response "Not authenticated" {:status 401})

    (not code)
    (data-response "Verification code required" {:status 400})

    :else
    (let [settings (get-user-settings user-id)]
      (cond
        (= :email (:two-factor settings))
        (data-response "Email 2FA is already enabled" {:status 400})

        (empty? (call-sql "verify_user_2fa" user-email code))
        (data-response "Invalid verification code" {:status 403})

        :else
        (do
          (when (= :totp (:two-factor settings))
            (call-sql "cleanup_totp_data" user-id))
          (save-user-settings! user-id (assoc settings :two-factor :email))
          (data-response {:message "Email 2FA has been enabled"}))))))

^:rct/test
(comment
  ;; Test not authenticated
  (enable-email-2fa {} "123456")
  ;=>> {:status 401}

  ;; Test no code provided
  (enable-email-2fa {:user-id 2 :user-email "user@pyr.dev"} nil)
  ;=>> {:status 400}

  ;; Test invalid code
  (do
    (save-user-settings! 2 {})
    (enable-email-2fa {:user-id 2 :user-email "user@pyr.dev"} "wrong"))
  ;=>> {:status 403}

  ;; Test already has email 2FA
  (let [user-id 2
        _ (save-user-settings! user-id {:timezone :utc :two-factor :email})
        result (enable-email-2fa {:user-id user-id :user-email "user@pyr.dev"} "123456")]
    (save-user-settings! user-id {})
    result))
  ;=>> {:status 400}

(defn- remove-totp
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

        (not (or (totp/validate-totp-code secret code)
                 (sql-primitive (call-sql "use_backup_code" user-id code))))
        (data-response "Invalid code" {:status 403})

        :else
        (do
          (call-sql "cleanup_totp_data" user-id)
          (let [settings (dissoc (get-user-settings user-id) :two-factor)]
            (save-user-settings! user-id settings))
          (data-response {:message "Two-factor authentication has been disabled"})))
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
  ;=>> {:status 403, :body "\"Invalid code\""}

  ;; Test successful removal
  (let [user-id 24
        secret (:secret (first (call-sql "get_totp_setup" user-id)))
        valid-code (str (totp/get-current-totp-code secret))]
    (remove-totp {:user-id user-id} valid-code)))
  ;=>> {:status 200, :body "{:message \"Two-factor authentication has been disabled\"}"}

(defn disable-2fa
  "Disables any 2FA method for the current user after verification."
  [{:keys [user-id user-email]} code]
  (if-not user-id
    (data-response "Not authenticated" {:status 401})
    (let [settings (get-user-settings user-id)
          two-factor (:two-factor settings)]
      (cond
        (nil? two-factor)
        (data-response "2FA is not enabled" {:status 400})

        (= two-factor :totp)
        (remove-totp {:user-id user-id} code)

        (= two-factor :email)
        (if (first (call-sql "verify_user_2fa" user-email code))
          (do
            (save-user-settings! user-id (dissoc settings :two-factor))
            (data-response {:message "Two-factor authentication has been disabled"}))
          (data-response "Invalid verification code" {:status 403}))

        :else
        (data-response "Unknown 2FA method" {:status 400})))))

^:rct/test
(comment
  (disable-2fa {} "123456")
  ;=>> {:status 401, :body "\"Not authenticated\""}

  (do
    (save-user-settings! 1 {})
    (disable-2fa {:user-id 1} "123456"))
  ;=>> {:status 400, :body "\"2FA is not enabled\""}

  (let [user-id 14]
    (save-user-settings! user-id {:two-factor :email})
    (disable-2fa {:user-id user-id :user-email "email-2fa@pyr.dev"} "wrong"))
  ;=>> {:status 403, :body "\"Invalid verification code\""}

  (let [user-id 14
        email "email-2fa@pyr.dev"
        _ (require '[pyregence.email :as email])
        output (with-out-str (email/mock-send-2fa-code email))
        code (second (re-find #"2FA CODE for .* : (\d+)" output))]
    (save-user-settings! user-id {:two-factor :email})
    (disable-2fa {:user-id user-id :user-email email} code)))
  ;=>> {:status 200, :body "{:message \"Two-factor authentication has been disabled\"}"}

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; User Management
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
    super admin or an org admin for the given org. Given the right permissions,
    the user is associated to the org with the role of 'organization_member'.

  - If `:org-id` is not provided or the user lacks permissions to assign directly,
    the system falls back to automatic domain-based organization assignment.
    This associates the user with an organization that matches their email domain
    to the `email_domains` column. The user's assigned role is:
    - 'organization_member' if the organization has `auto_accept = true` (auto-approved)
    - 'member' if `auto_accept = false`
    The users membership status is
    - 'accepted' if `auto_accept = true`
    - 'pending' if `auto_accept = false`

  Returns:
  - A 200 OK response if the user was successfully created and optionally assigned
  - A 403 Forbidden response if user creation fails

  Security:
  - Only users with sufficient privileges (super_admin or organization_admin) may
    explicitly assign a new user to an organization via `:org-id`.
  - All organization assignments are validated server-side using the session context."
  [session email user-name password & [opts]]
  (let [{:keys [org-id] :or {org-id nil}} opts
        {:keys [user-role]} session
        default-settings (pr-str {:timezone :utc})
        new-user-id (nil-on-error
                     (sql-primitive (call-sql "add_new_user"
                                              {:log? false}
                                              email
                                              user-name
                                              password
                                              default-settings)))]
    (if-not new-user-id
      (data-response (str "Failed to create the new user with name " user-name " and email " email)
                     {:status 403})
      (cond
        ;; If org-id is provided, we explicitly assign the org (must be super_admin or organization_admin)
        ;; This happens when a super_admin or org_admin is manually adding a user via the admin page
        ;; The new user will have a user_role of organization_member and a user_status of active
        org-id
        (if (or (= user-role "super_admin")
                (= user-role "organization_admin"))
          (do
            (call-sql "add_org_user" org-id new-user-id)
            (data-response "User created and added to organization."))
          (data-response "User does not have permission to assign users to this organization."
                         {:status 403}))

        ;; No org-id provided — use email domain-based auto-assignment (depedent on org auto_add settings)
        :else
        (let [domain (re-find #"@{1}.+" email)]
          (if (call-sql "auto_add_org_user" new-user-id domain)
            (data-response "User created and added to added to an organization by email domain (when auto_add is true for that organization).")
            (data-response "User created successfully but something went wrong when calling auto_add_org_user."
                           {:status 403})))))))

(defn get-current-user-settings
  "Returns settings for the current user."
  [{:keys [user-id user-email]}]
  (if-let [user-info (first (call-sql "get_user_settings" user-id))]
    (data-response (assoc user-info :email user-email))
    (data-response "User not found" {:status 403})))

;; TODO hook into UI
(defn update-current-user-settings [session new-settings]
  (let [user-id (:user-id session)]
    (if (call-sql "update_user_settings" user-id new-settings)
      (data-response "User settings successfully updated.")
      (data-response "User settings were not able to be updated." {:status 403}))))

(defn update-user-name
  "Allows a super admin to update the name of a user by their email."
  [_ email new-name]
  (if-let [user-id-to-update (sql-primitive (call-sql "get_user_id_by_email" email))]
    (do (call-sql "update_user_name" user-id-to-update new-name)
        (data-response (str "User's name successfully updated to " new-name)))
    (data-response (str "There is no user with the email " email)
                   {:status 403})))

;;TODO ideally this would be handled by the database but we should at the very least have a cljc file
;;for the fe and be to share.
(defn- can-upgrade-role?
  "True if `from` role is greater then `to`"
  [from to]
  (let [role->n
        {"member" 0
         "organization_member" 1
         "organization_admin" 2
         "account_manager" 3
         "super_admin" 4}]
    (<= (role->n to) (role->n from))))

;; Consider if status=none implies no organization, what is status=none?
(defn update-users-status
  [{:keys [user-id]} requested-status users-to-update]
  ;; TODO consider using user id's instead of emails.
  (call-sql "update_users_status_by_email" user-id requested-status
            (into-array String users-to-update))
  (data-response "success"))

;;TODO consider the database constraints, or data integrity of some role switches
;; that this fn doesn't try to handle, such as organization_admin to member or super admin, which would require losing the org
(defn update-users-roles
  "Updates users roles"
  [{:keys [user-id user-role]} requested-role users-to-update]
  ;; TODO consider what happens if this sql call fails.
  ;; TODO consider using user id's instead of emails.
  (if (can-upgrade-role? user-role requested-role)
    (do
      (call-sql "update_users_roles_by_email" user-id requested-role
                (into-array String users-to-update))
      (data-response "success"))
    ;; TODO what should this failure message be.
    (data-response "" {:status 400})))

(defn user-email-taken
  ([_ email]
   (user-email-taken nil email -1))
  ([_ email user-id-to-ignore]
   (if (sql-primitive (call-sql "user_email_taken" email user-id-to-ignore))
     (data-response "User email is taken")
     (data-response "User email is not taken" {:status 403}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Access Control
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Organization Management
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-all-organizations
  "Returns the list of all organizations in the database."
  [_]
  (->> (call-sql "get_all_organizations")
       (mapv (fn [{:keys [org_id org_name org_unique_id geoserver_credentials email_domains auto_add auto_accept archived created_date archived_date]}]
               {:org-id                org_id
                :org-name              org_name
                :org-unique-id         org_unique_id
                :geoserver-credentials geoserver_credentials
                :email-domains         email_domains
                :auto-add?             auto_add
                :auto-accept?          auto_accept
                :archived?             archived
                :created-date          created_date
                :archived-date         archived_date}))
       (data-response)))

(defn add-org-user [_ org-id email]
  (if-let [user-id-to-add (sql-primitive (call-sql "get_user_id_by_email" email))]
    (do
      (call-sql "add_org_user" org-id user-id-to-add)
      (data-response ""))
    (data-response (str "There is no user with the email " email)
                   {:status 403})))

(defn get-current-user-organization
  "Given the current user by session, returns the list of organizations that
   they belong to and are an admin or a member of."
  [session]
  (if-let [user-id (:user-id session)]
    (->> (call-sql "get_user_organization" user-id)
         (mapv (fn [{:keys [org_id org_name org_unique_id geoserver_credentials user_role email_domains auto_add auto_accept]}]
                 {:org-id                org_id
                  :org-name              org_name
                  :org-unique-id         org_unique_id
                  :geoserver-credentials geoserver_credentials
                  :role                  user_role
                  :email-domains         email_domains
                  :auto-add?             auto_add
                  :auto-accept?          auto_accept}))
         (data-response))
    (data-response "No user is logged in." {:status 403})))

(defn get-org-member-users
  "Returns a vector of member users for the given org-id, if the user is an
   admin of the given org."
  ([session]
   ;;TODO passing nil here his hacky but its to support admin.cljs which was previously ignoring
   ;; the session and the new setting page which calls get-org-member-users correct with just the session
   ;; in the end will probably need to different api calls.
   (get-org-member-users session nil))
  ([{:keys [organization-id user-role]} org-id]
   (->> (call-sql "get_org_member_users" (if (= user-role "super_admin") org-id organization-id))
        (mapv (fn [{:keys [user_id full_name email user_role org_membership_status]}]
                {:user-id           user_id
                 :full-name         full_name
                 :email             email
                 :user-role         user_role
                 :membership-status org_membership_status}))
        (data-response))))

(defn get-all-users
  "Returns a vector of all users in the DB."
  [_]
  (->> (call-sql "get_all_users")
       (mapv (fn [{:keys [user_uid email name settings match_drop_access email_verified
                          last_login_date user_role org_membership_status organization_name]}]
               {:user-id               user_uid
                :email                 email
                :name                  name
                :settings              settings
                :match-drop-access     match_drop_access
                :email-verified        email_verified
                :last-login-date       last_login_date
                :user-role             user_role
                :org-membership-status org_membership_status
                :organization-name     organization_name}))
       (data-response)))

(defn get-psps-organizations
  "Returns the list of all organizations that have PSPS layers (currently denoted
   by the presence of a value in the `geoserver_credentials` column)."
  [_]
  (->> (call-sql "get_psps_organizations")
       (mapv #(:org_unique_id %))
       (data-response)))

(defn remove-org-user [_ org-user-id]
  (call-sql "remove_org_user" org-user-id)
  (data-response ""))

(defn update-org-info [_ org-id org-name email-domains auto-add? auto-accept?]
  (call-sql "update_org_info" org-id org-name email-domains auto-add? auto-accept?)
  (data-response ""))

(defn update-org-user-role [_ user-id new-role]
  (call-sql "update_org_user_role" user-id new-role)
  (data-response ""))

(defn update-user-org-membership-status [_ user-id new-status]
  (call-sql "update_org_membership_status" user-id new-status)
  (data-response ""))
