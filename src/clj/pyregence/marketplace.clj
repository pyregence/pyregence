(ns pyregence.marketplace
  (:require [clojure.string                      :as str]
            [clojure.tools.logging               :as log]
            [pyregence.marketplace.entitlements  :as entitlements]
            [pyregence.marketplace.jwt           :as jwt]
            [pyregence.marketplace.provisioning  :as provisioning]
            [pyregence.marketplace.util          :as util]
            [triangulum.database                 :refer [call-sql sql-primitive]]))

(def ^:private signup-ttl-secs (* 24 60 60))

(defn- try-approve-account!
  "Tries to approve account with Google. Returns {:approved? bool}, never throws."
  [procurement-account-id]
  (try
    (let [acct-id (util/normalize-account-id procurement-account-id)
          resp    (entitlements/approve-account! acct-id)]
      (if (:success resp)
        (do (log/info "Early account approval succeeded"
                      {:procurement-id procurement-account-id})
            {:approved? true})
        (do (log/warn "Early account approval failed"
                      {:procurement-id procurement-account-id :error (:error resp)})
            {:approved? false :error (:error resp)})))
    (catch Exception e
      (log/warn e "Early account approval threw"
                {:procurement-id procurement-account-id})
      {:approved? false :error :exception})))

(defn signup
  "Validates JWT, approves account with Google, stores in session,
  redirects to /login or /register."
  [request]
  (if-let [token (jwt/request->token request)]
    (try
      (let [claims               (jwt/validate token)
            procurement-id       (:sub claims)
            google-user-identity (get-in claims [:google :user_identity])
            roles                (get-in claims [:google :roles])
            orders               (jwt/claims->orders claims)
            approval             (try-approve-account! procurement-id)
            user-exists?         (and google-user-identity
                                      (sql-primitive (call-sql "marketplace_gaia_user_exists" google-user-identity)))]
        (log/info "Marketplace JWT validated"
                  {:approved?      (:approved? approval)
                   :procurement-id procurement-id
                   :redirect       (if user-exists? :login :register)
                   :roles          roles})
        {:status  302
         :headers {"Location" (if user-exists?
                                "/login?marketplace=1"
                                "/register?marketplace=1")}
         :session (assoc (:session request)
                         :marketplace-signup
                         {:approved?              (:approved? approval)
                          :google-user-identity   google-user-identity
                          :orders                 orders
                          :procurement-account-id procurement-id
                          :validated-at           (quot (System/currentTimeMillis) 1000)})})
      (catch clojure.lang.ExceptionInfo e
        (log/warn "Marketplace JWT validation failed" {:error (Throwable/.getMessage e)})
        {:status 400 :body "Invalid marketplace token"})
      (catch Exception e
        (log/error e "Unexpected error processing marketplace JWT")
        {:status 500 :body "Internal error"}))
    {:status 400 :body "Missing marketplace token"}))

(defn complete-signup!
  "Post-auth hook. Provisions org/user if marketplace signup is pending, skips if already linked."
  [session user-email]
  (when-let [{:keys [approved? google-user-identity orders org-name
                     procurement-account-id validated-at]}
             (:marketplace-signup session)]
    (let [now (quot (System/currentTimeMillis) 1000)
          age (- now (or validated-at 0))]
      (cond
        (> age signup-ttl-secs)
        (log/warn "Marketplace signup data expired" {:age age :email user-email})

        (and google-user-identity
             (sql-primitive (call-sql "marketplace_gaia_user_exists" google-user-identity)))
        (do (log/info "Returning marketplace user, skipping provisioning"
                      {:procurement-id procurement-account-id :user-email user-email})
            (when-not approved?
              (try-approve-account! procurement-account-id)))

        :else
        (let [user-info {:email                  user-email
                         :google-user-identity   google-user-identity
                         :org-name               org-name
                         :procurement-account-id procurement-account-id}]
          (try
            (let [result (provisioning/provision! user-info orders)]
              (when-not approved?
                (let [resp (entitlements/approve-account! (:marketplace-account-id result))]
                  (if (:success resp)
                    (log/info "Fallback account approval succeeded"
                              {:org-id (:organization-id result)})
                    (log/error "Fallback account approval failed"
                               {:error (:error resp) :org-id (:organization-id result)}))))
              (log/info "Marketplace provisioning complete"
                        {:org-id        (:organization-id result)
                         :pre-approved? (boolean approved?)}))
            (catch Exception e
              (log/error e "Failed to complete marketplace signup"
                         {:user-email user-email}))))))))

(defn sso-login
  "Validates JWT from Google, looks up user by GAIA identity.
  Returns {:user ... :session ...} or nil."
  [request]
  (if-let [token (jwt/request->token request)]
    (try
      (let [claims               (jwt/validate token)
            google-user-identity (get-in claims [:google :user_identity])]
        (if-let [user (and (not (str/blank? google-user-identity))
                           (first (call-sql "get_user_by_gaia_identity" google-user-identity)))]
          (do (log/info "Marketplace SSO login"
                        {:gaia google-user-identity :user-id (:user_id user)})
              {:user    user
               :session (:session request)})
          (do (log/info "Marketplace SSO: user not found, redirecting to login"
                        {:gaia google-user-identity})
              nil)))
      (catch Exception e
        (log/warn "Marketplace SSO JWT validation failed" {:error (Throwable/.getMessage e)})
        nil))
    nil))

^:rct/test
(comment
  (:status (signup {}))                                                           ;=> 400
  (:status (signup {:form-params {"x-gcp-marketplace-token" "bad"}}))             ;=> 400
  (complete-signup! {} "a@b.com")                                                 ;=> nil
  )
