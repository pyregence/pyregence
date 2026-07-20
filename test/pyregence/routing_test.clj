(ns pyregence.routing-test
  (:require
   [clojure.test         :refer [deftest is testing]]
   [pyregence.handlers   :as    handlers]
   [pyregence.routing    :refer [routes]]
   [triangulum.config    :as    config]))

;; -----------------------------------------------------------------------------
;; Org member-management gate (PYR1-1547)
;; -----------------------------------------------------------------------------
;;
;; Managing org members is intentionally a paid capability: every real
;; organization is on a paid tier (Pro or Enterprise), and tier1/free is
;; reserved for individual members who don't belong to an org. These routes are
;; therefore gated on `#{:organization-admin :tier2-pro :token}`, evaluated with
;; AND semantics - the caller must be an accepted org-admin AND on a paid
;; (>= tier2-pro) tier AND present a valid token. Super-admins and account
;; managers bypass the tier check.

(def ^:private member-management-routes
  [[:post "/clj/get-org-member-users"]
   [:post "/clj/add-org-users"]
   [:post "/clj/update-users-roles"]
   [:post "/clj/update-users-status"]
   [:post "/clj/update-org-info"]])

(deftest member-management-routes-require-admin-and-paid-tier
  (doseq [route member-management-routes]
    (testing (str route " gate")
      (let [auth-type (get-in routes [route :auth-type])]
        (is (= #{:organization-admin :tier2-pro :token} auth-type)
            "should require an org-admin, on a paid tier, with a valid token")))))

;; -----------------------------------------------------------------------------
;; route-authenticator behaviour for the member-management gate
;; -----------------------------------------------------------------------------

(def ^:private gate #{:organization-admin :tier2-pro :token})
(def ^:private valid-token "test-auth-token")

(defn- request
  "Build a minimal Ring request for `route-authenticator`. Omitting :org-id keeps
   current-subscription-tier off the DB and falling back to the session tier."
  [{:keys [role membership tier token]}]
  {:headers (when token {"authorization" (str "Bearer " token)})
   :session (cond-> {}
              role       (assoc :user-role role)
              membership (assoc :org-membership-status membership)
              tier       (assoc :subscription-tier tier))})

(deftest member-management-gate-behaviour
  (with-redefs [config/get-config (fn [& _] valid-token)]
    (testing "org-admin on a paid tier (Pro) passes"
      (is (true? (handlers/route-authenticator
                  (request {:role "organization_admin" :membership "accepted"
                            :tier "tier2_pro" :token valid-token})
                  gate))))

    (testing "org-admin on tier1/free is blocked (the reported ticket scenario)"
      (is (false? (handlers/route-authenticator
                   (request {:role "organization_admin" :membership "accepted"
                             :tier "tier1_free_registered" :token valid-token})
                   gate))))

    (testing "super-admin bypasses the tier check"
      (is (true? (handlers/route-authenticator
                  (request {:role "super_admin" :token valid-token})
                  gate))))

    (testing "account-manager bypasses the tier check"
      (is (true? (handlers/route-authenticator
                  (request {:role "account_manager" :token valid-token})
                  gate))))

    (testing "a plain member (individual, no org) is blocked"
      (is (false? (handlers/route-authenticator
                   (request {:role "member" :token valid-token})
                   gate))))

    (testing "a paid-tier org-admin without a valid token is blocked"
      (is (false? (handlers/route-authenticator
                   (request {:role "organization_admin" :membership "accepted"
                             :tier "tier2_pro" :token "wrong-token"})
                   gate))))))
