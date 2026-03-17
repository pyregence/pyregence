(ns pyregence.marketplace.provisioning
  (:require [clojure.string              :as str]
            [clojure.tools.logging       :as log]
            [pyregence.marketplace.util  :as util]
            [triangulum.database         :refer [call-sql sql-primitive]]))

(def plan-id->tier
  "Maps GCP Marketplace plan IDs to subscription_tier values."
  {"basic"               "tier1_free_registered"
   "essential-plan"      "tier1_basic_paid"
   "essential-plan-P1Y"  "tier1_basic_paid"
   "business-plan"       "tier2_pro"
   "business-plan-P1Y"   "tier2_pro"
   "enterprise-plan"     "tier3_enterprise"
   "enterprise-plan-P1Y" "tier3_enterprise"})

(def ^{:doc "Subscription tier used when a plan-id is unknown."}
  default-tier "tier1_free_registered")

(def ^{:doc "Set of valid subscription_tier values recognized by plan-id->tier."}
  valid-tiers (set (vals plan-id->tier)))
(assert (valid-tiers default-tier) "default-tier must be a valid subscription_tier")

(def ^:private secondary-tlds #{"co" "com" "org" "net" "gov" "edu" "ac"})

(defn- email->org-name [email]
  (let [s (or email "")]
    (or (when-let [domain (second (re-find #"@(.+)$" s))]
          (let [parts (vec (str/split domain #"\."))
                n     (count parts)]
            (cond
              (= 1 n)
              (str/capitalize (first parts))
              (and (= 2 n) (secondary-tlds (first parts)))
              nil

              (and (>= n 3) (secondary-tlds (parts (- n 2))))
              (str/capitalize (parts (- n 3)))
              :else
              (str/capitalize (first parts)))))
        (when-let [local (first (str/split s #"@"))]
          (when-not (str/blank? local)
            (str/capitalize local)))
        "Marketplace Organization")))

(defn- ->org-unique-id [acct-id]
  (str "mp-" (last (str/split acct-id #"/"))))

^:rct/test
(comment
  (email->org-name "joe@acme.com")               ;=> "Acme"
  (email->org-name "joe@bbc.co.uk")              ;=> "Bbc"
  (email->org-name "joe@co.uk")                  ;=> "Joe"
  (email->org-name "joe@localhost")               ;=> "Localhost"
  (->org-unique-id "accounts/xyz")               ;=> "mp-xyz"
  (get plan-id->tier "basic")                    ;=> "tier1_free_registered"
  (get plan-id->tier "essential-plan")           ;=> "tier1_basic_paid"
  (get plan-id->tier "essential-plan-P1Y")       ;=> "tier1_basic_paid"
  (get plan-id->tier "business-plan")            ;=> "tier2_pro"
  (get plan-id->tier "business-plan-P1Y")        ;=> "tier2_pro"
  (get plan-id->tier "enterprise-plan")          ;=> "tier3_enterprise"
  (get plan-id->tier "enterprise-plan-P1Y")      ;=> "tier3_enterprise"
  (get plan-id->tier "unknown" default-tier)     ;=> "tier1_free_registered"
  ;; missing :email triggers "Email is required" — all validation errors use :invalid-user-info
  (try (provision! {:procurement-account-id "1"} [])
       (catch Exception e (:type (ex-data e))))  ;=> :invalid-user-info
  )

(defn- provision-result [acct-id org-id user-id]
  {:marketplace-account-id acct-id
   :organization-id        org-id
   :user-id                user-id})

(defn provision!
  "Provisions marketplace org/user."
  [user-info orders]
  (let [{:keys [email google-user-identity org-name procurement-account-id]} user-info
        order-id  (:order-id (first orders))
        acct-id   (util/normalize-account-id procurement-account-id)
        org-label (or (not-empty org-name) (email->org-name email))]
    (when-let [err (cond
                     (str/blank? email)                  "Email is required"
                     (str/blank? procurement-account-id) "Procurement account ID is required"
                     (not (seq orders))                  "No orders provided"
                     (nil? order-id)                     "Order missing order-id")]
      (throw (ex-info err {:type :invalid-user-info})))
    (log/info "Provisioning marketplace account"
              {:email                  email
               :order-id               order-id
               :procurement-account-id procurement-account-id})
    (let [user                          (first (call-sql "get_user_with_org_info" email))
          {:keys [organization_rid user_id]} user]
      (when-not user
        (throw (ex-info "No user found for marketplace provisioning"
                        {:email email :type :user-not-found})))
      (if organization_rid
        (if (sql-primitive (call-sql "provision_marketplace_existing_user_with_org"
                                     organization_rid user_id acct-id order-id
                                     acct-id google-user-identity))
          (do (log/info "Linked marketplace to existing org"
                        {:org-id organization_rid :user-id user_id})
              (provision-result acct-id organization_rid user_id))
          (throw (ex-info "Organization already linked to a different marketplace account"
                          {:org-id organization_rid :type :already-linked})))
        (let [org-unique-id (->org-unique-id acct-id)
              org-id        (sql-primitive
                              (call-sql "provision_marketplace_existing_user_new_org"
                                        org-label org-unique-id acct-id order-id
                                        default-tier user_id acct-id google-user-identity))]
          (log/info "Linked existing user to new marketplace org"
                    {:org-id org-id :user-id user_id})
          (provision-result acct-id org-id user_id))))))
