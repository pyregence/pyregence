(ns pyregence.marketplace
  (:require [clojure.string                     :as str]
            [clojure.tools.logging              :as log]
            [pyregence.marketplace.approval     :as approval]
            [pyregence.marketplace.jwt          :as jwt]
            [pyregence.marketplace.provisioning :as provisioning]
            [triangulum.config                  :refer [get-config]]
            [triangulum.database                :refer [call-sql]]
            [triangulum.response                :refer [data-response]])
  (:import [java.net URLEncoder]))

(defn health [_]
  (data-response {:enabled (get-config ::config :enabled)
                  :status  "ok"}))

(defn- set-status! [organization-id status]
  (try
    (call-sql "update_marketplace_status" organization-id status)
    true
    (catch Exception e
      (log/error e "Failed to update marketplace status" {:org-id organization-id :status status})
      false)))

(defn signup
  "Validates JWT, stores in session, redirects to /register."
  [request]
  (let [token (jwt/request->token request)]
    (if-not token
      {:status 400 :body "Missing marketplace token"}
      (try
        (let [claims               (jwt/validate token)
              procurement-id       (:sub claims)
              google-user-identity (or (get-in claims [:google :user_identity])
                                       (:email claims))
              roles                (get-in claims [:google :roles])
              orders               (jwt/->orders claims)
              default-org          (or (when-let [domain (second (re-find #"@([^.]+)" (or google-user-identity "")))]
                                         (str/capitalize domain))
                                       "")]
          (log/info "Marketplace JWT validated, redirecting to /register"
                    {:procurement-id procurement-id :roles roles})
          {:status  302
           :headers {"Location" (str "/register?marketplace=1"
                                     (when (seq default-org)
                                       (str "&org=" (URLEncoder/encode default-org "UTF-8"))))}
           :session (assoc (:session request)
                           :marketplace-signup
                           {:google-user-identity   google-user-identity
                            :marketplace-roles      roles
                            :orders                 orders
                            :procurement-account-id procurement-id
                            :validated-at           (quot (System/currentTimeMillis) 1000)})})
        (catch clojure.lang.ExceptionInfo e
          (log/warn "Marketplace JWT validation failed" {:error (Throwable/.getMessage e)})
          {:status 400 :body "Invalid marketplace token"})
        (catch Exception e
          (log/error e "Unexpected error processing marketplace JWT")
          {:status 500 :body "Internal error"})))))

(def ^:private signup-ttl-secs (* 24 60 60))

(defn complete-signup!
  "Post-auth hook: provisions org, approves account, clears session."
  [session _user-id user-email]
  (when-let [{:keys [procurement-account-id google-user-identity orders marketplace-roles validated-at org-name]}
             (:marketplace-signup session)]
    (let [now (quot (System/currentTimeMillis) 1000)
          age (- now (or validated-at 0))]
      (if (> age signup-ttl-secs)
        (do
          (log/warn "Marketplace signup data expired" {:age age :email user-email})
          (dissoc session :marketplace-signup))
        (do
          (log/info "Completing marketplace signup after auth"
                    {:marketplace-roles marketplace-roles
                     :procurement-id    procurement-account-id
                     :user-email        user-email})
          (try
            ;; Phase 3 (Pub/Sub) will gate provisioning on ENTITLEMENT_ACTIVE.
            (let [user-info {:email                  user-email
                             :google-user-identity   google-user-identity
                             :org-name               org-name
                             :procurement-account-id procurement-account-id}
                  result    (provisioning/provision! user-info orders)]
              (when (:success result)
                (let [acct-id (:marketplace-account-id result)
                      resp    (approval/approve! acct-id)]
                  (if (:success resp)
                    (do
                      (set-status! (:organization-id result) "active")
                      (log/info "Marketplace signup complete" {:org-id (:organization-id result)}))
                    (log/error "Provisioned but Google approval failed"
                               {:org-id (:organization-id result) :error (:error resp)}))))
              (dissoc session :marketplace-signup))
            (catch Exception e
              (log/error e "Failed to complete marketplace signup" {:user-email user-email})
              (dissoc session :marketplace-signup))))))))

^:rct/test
(comment
  (:status (health nil))                                                          ;=> 200
  (:status (signup {}))                                                           ;=> 400
  (:status (signup {:form-params {"x-gcp-marketplace-token" "bad"}}))             ;=> 400
  (nil? (complete-signup! {} 1 "a@b.com"))                                        ;=> true
  )
