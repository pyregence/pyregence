(ns pyregence.marketplace.provisioning
  (:require [clojure.string        :as str]
            [clojure.tools.logging :as log]
            [pyregence.email       :as email]
            [triangulum.database   :refer [call-sql sql-primitive]])
  (:import [java.security SecureRandom]
           [java.util    Base64 Base64$Encoder]))

(defn- random-password []
  (let [bytes (byte-array 32)
        _     (SecureRandom/.nextBytes (SecureRandom.) bytes)]
    (Base64$Encoder/.encodeToString (Base64/getEncoder) bytes)))

(def ^:private plan-id->tier
  "Maps GCP Marketplace plan IDs to internal subscription_tier enum values."
  {"basic"           "tier1_free_registered"
   "essential-plan"  "tier1_basic_paid"
   "business-plan"   "tier2_pro"
   "enterprise-plan" "tier3_enterprise"})

(def ^:private default-tier "tier1_basic_paid")

(def ^:private secondary-tlds #{"co" "com" "org" "net" "gov" "edu" "ac"})

(defn- email->org-name [email]
  (let [s (or email "")]
    (or (when-let [domain (second (re-find #"@(.+)$" s))]
          (let [parts (str/split domain #"\.")]
            (cond
              (= 1 (count parts))            (str/capitalize (first parts))
              (secondary-tlds (first parts)) (str/capitalize (second parts))
              :else                          (str/capitalize (first parts)))))
        (when-let [local (first (str/split s #"@"))]
          (when-not (str/blank? local)
            (str/capitalize local)))
        "Marketplace Organization")))

(defn- ->org-unique-id [acct-id]
  (str "mp-" (last (str/split acct-id #"/"))))

^:rct/test
(comment
  (email->org-name "joe@acme.com")               ;=> "Acme"
  (email->org-name "joe@co.uk")                  ;=> "Uk"
  (email->org-name "joe@localhost")              ;=> "Localhost"
  (->org-unique-id "accounts/xyz")               ;=> "mp-xyz"
  (= 44 (count (random-password)))               ;=> true
  (get plan-id->tier "essential-plan")           ;=> "tier1_basic_paid"
  (get plan-id->tier "business-plan")            ;=> "tier2_pro"
  (get plan-id->tier "enterprise-plan")          ;=> "tier3_enterprise"
  (get plan-id->tier "basic")                    ;=> "tier1_free_registered"
  (get plan-id->tier "unknown" default-tier)     ;=> "tier1_basic_paid"
  (try (provision! {:procurement-account-id "1"} [])
       (catch Exception e (:type (ex-data e))))  ;=> :invalid-user-info
  )

(defn provision!
  "Provisions marketplace org/user via atomic SQL."
  [user-info orders]
  (let [{:keys [procurement-account-id google-user-identity email org-name plan-id]} user-info
        tier (get plan-id->tier plan-id default-tier)]
    (when (str/blank? email)
      (throw (ex-info "Email is required" {:type :invalid-user-info})))
    (when (str/blank? procurement-account-id)
      (throw (ex-info "Procurement account ID is required" {:type :invalid-user-info})))

    (when (empty? orders)
      (throw (ex-info "No orders provided" {:type :invalid-user-info})))
    (let [order-id  (:order-id (first orders))
          acct-id   (str "accounts/" procurement-account-id)
          org-label (if (seq org-name) org-name (email->org-name email))]
      (when-not order-id
        (throw (ex-info "Order missing order-id" {:type :invalid-user-info})))
      (log/info "Provisioning marketplace account"
                {:email                  email
                 :order-id               order-id
                 :procurement-account-id procurement-account-id})
      (try
        (if-let [user (first (call-sql "get_user_with_org_info" email))]
          (let [{:keys [user_id organization_rid]} user]
            (if organization_rid
              (do
                (call-sql "provision_marketplace_existing_user_with_org"
                          organization_rid user_id acct-id order-id
                          procurement-account-id google-user-identity)
                (log/info "Linked marketplace to existing org"
                          {:org-id organization_rid :user-id user_id})
                {:email                  email
                 :existing?              true
                 :marketplace-account-id acct-id
                 :organization-id        organization_rid
                 :success                true
                 :user-id                user_id})
              (let [org-unique-id (->org-unique-id acct-id)
                    org-id        (sql-primitive
                                   (call-sql "provision_marketplace_existing_user_new_org"
                                             org-label org-unique-id acct-id order-id
                                             tier user_id procurement-account-id google-user-identity))]
                (log/info "Linked existing user to new marketplace org"
                          {:org-id org-id :user-id user_id})
                {:email                  email
                 :existing?              true
                 :marketplace-account-id acct-id
                 :organization-id        org-id
                 :success                true
                 :user-id                user_id})))
          (let [org-unique-id (->org-unique-id acct-id)
                local         (first (str/split email #"@"))
                uname         (if (str/blank? local) "Marketplace User" local)
                password      (random-password)
                settings      (pr-str {:timezone :utc})
                result        (first (call-sql "provision_marketplace_new_user"
                                               org-label org-unique-id acct-id order-id
                                               tier email uname password
                                               settings procurement-account-id google-user-identity))
                org-id        (:org_id result)
                user-id       (:user_id result)]
            (log/info "Created new marketplace user and org"
                      {:email email :org-id org-id :user-id user-id})
            (try
              (email/send-email! nil email :reset-password)
              (log/info "Sent welcome email" {:email email})
              (catch Exception e
                (log/error e "Failed to send welcome email" {:email email})))
            {:email                  email
             :existing?              false
             :marketplace-account-id acct-id
             :organization-id        org-id
             :success                true
             :user-id                user-id}))
        (catch Exception e
          (log/error e "Failed to provision marketplace account" {:email email})
          {:error   "provisioning_failed"
           :message (Throwable/.getMessage e)
           :success false})))))
