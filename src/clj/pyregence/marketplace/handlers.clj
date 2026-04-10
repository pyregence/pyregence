(ns pyregence.marketplace.handlers
  (:require [clojure.tools.logging               :as log]
            [pyregence.marketplace.entitlements  :as entitlements]
            [pyregence.marketplace.provisioning  :as provisioning]
            [pyregence.marketplace.util          :as util]
            [triangulum.database                 :refer [call-sql sql-primitive]]))

(defn- entitlement->account-id
  "Extracts the account-id in \"accounts/X\" format from an entitlement map."
  [entitlement]
  (util/normalize-account-id (:account entitlement)))

^:rct/test
(comment
  (entitlement->account-id {:account "providers/p/accounts/A-1"}) ;=> "accounts/A-1"
  (entitlement->account-id {:account ""})                         ;=> nil
  (entitlement->account-id nil)                                   ;=> nil
  )

(defn- handle-creation-requested! [event]
  (:success (entitlements/approve! (:entitlement-id event))))

(defn- entitlement-body
  "Fetches entitlement from Google, returns body map or nil."
  [entitlement-id]
  (let [result (entitlements/fetch entitlement-id)]
    (when (:success result)
      (:body result))))

(defn- handle-tier-update! [sql-fn action extra-keys event]
  (let [body       (entitlement-body (:entitlement-id event))
        account-id (some-> body entitlement->account-id)]
    (cond
      (nil? body)
      (do (log/error "Failed to fetch entitlement"
                     {:action action :entitlement-id (:entitlement-id event)})
          false)

      (nil? account-id)
      (do (log/warn "No account in entitlement"
                    {:action action :entitlement-id (:entitlement-id event)})
          false)

      :else
      (let [plan       (:plan body)
            tier       (get provisioning/plan-id->tier plan provisioning/default-tier)
            extra-vals (mapv body extra-keys)]
        (when (and plan (not (contains? provisioning/plan-id->tier plan)))
          (log/warn "Unknown plan, falling back to default tier"
                    {:action action :plan plan :default-tier provisioning/default-tier}))
        (when (and (seq extra-keys) (some nil? extra-vals))
          (log/warn "Entitlement body missing expected keys"
                    {:action action :expected extra-keys :got-keys (keys body)}))
        (let [org-id (sql-primitive (apply call-sql sql-fn account-id (conj extra-vals tier)))]
          (if org-id
            (log/info "Marketplace entitlement updated"
                      {:action action :account-id account-id :org-id org-id :tier tier})
            (log/warn "No org found for entitlement"
                      {:action action :account-id account-id}))
          (some? org-id))))))

(defn- resolve-account-id
  "Fetches entitlement from Google to extract the account-id."
  [event]
  (some-> (:entitlement-id event)
          (entitlement-body)
          (entitlement->account-id)))

(defn- apply-to-account! [sql-fn action account-id]
  (let [org-id (sql-primitive (call-sql sql-fn account-id))]
    (if org-id
      (log/info "Marketplace account updated" {:action action :account-id account-id :org-id org-id})
      (log/warn "No org found for account" {:action action :account-id account-id}))
    (some? org-id)))

(defn- handle-with-account! [sql-fn action ack-on-missing? event]
  (if-let [account-id (resolve-account-id event)]
    (apply-to-account! sql-fn action account-id)
    (let [ctx {:action action :entitlement-id (:entitlement-id event)}]
      (if ack-on-missing?
        (log/warn  "Cannot resolve account, acking" ctx)
        (log/error "Cannot resolve account"         ctx))
      ack-on-missing?)))

(defn- handle-plan-change-requested! [event]
  (let [new-plan (or (:new-plan event)
                     (some-> (:entitlement-id event) (entitlement-body) :newPendingPlan))]
    (if new-plan
      (:success (entitlements/approve-plan-change! (:entitlement-id event) new-plan))
      (do (log/warn "Cannot determine new plan for plan-change approval"
                    {:entitlement-id (:entitlement-id event)})
          false))))

(defn- handle-account-deleted! [event]
  (if-let [account-id (some-> (:account-id event) util/normalize-account-id)]
    (do (apply-to-account! "delete_marketplace_entitlement_data" :account-deleted account-id)
        true)
    (do (log/warn "Account deletion missing or malformed account-id, acking"
                   (select-keys event [:account-id :event-id :event-type]))
        true)))

(defn- log-and-ack! [msg event]
  (log/info msg (select-keys event [:account-id :entitlement-id :event-id :event-type]))
  true)

(defn- guard-entitlement-id [handler]
  (fn [event]
    (if (:entitlement-id event)
      (handler event)
      (do (log/warn "Entitlement event missing entitlement-id, acking"
                     (select-keys event [:event-id :event-type]))
          true))))

(def ^:private handlers
  "All documented Pub/Sub event types plus defensive ENTITLEMENT_SUSPENDED."
  (merge
    (update-vals
      {:entitlement-active                (partial handle-tier-update! "activate_marketplace_entitlement" :activation [:usageReportingId])
       :entitlement-cancellation-reverted (partial handle-tier-update! "activate_marketplace_entitlement" :cancellation-reverted [:usageReportingId])
       :entitlement-cancelled             (partial handle-with-account! "cancel_marketplace_entitlement" :cancelled true)
       :entitlement-creation-requested    handle-creation-requested!
       :entitlement-deleted               (partial handle-with-account! "delete_marketplace_entitlement_data" :entitlement-deleted true)
       :entitlement-pending-cancellation  (partial handle-with-account! "pending_cancel_marketplace_entitlement" :pending-cancellation false)
       :entitlement-plan-change-requested handle-plan-change-requested!
       :entitlement-plan-changed          (partial handle-tier-update! "update_org_subscription_tier" :plan-change [])
       :entitlement-suspended             (partial handle-with-account! "suspend_marketplace_entitlement" :suspended false)}
      guard-entitlement-id)
    {:account-deleted handle-account-deleted!}
    (update-vals
      {:account-active                    "Account activated"
       :account-creation-requested        "Account creation requested, approved during signup"
       :entitlement-cancelling            "Awaiting final cancellation"
       :entitlement-offer-accepted        "Offer accepted"
       :entitlement-offer-ended           "Offer ended, tier follows plan not offer"
       :entitlement-plan-change-cancelled "Plan change cancelled, never applied locally"
       :entitlement-renewed               "Renewed, no action required"}
      (fn [msg] (partial log-and-ack! msg)))))

(defn dispatch!
  "Runs the handler for an event. Returns true to ack, false to retry."
  [event]
  (try
    (if-let [handler (handlers (:event-type event))]
      (handler event)
      (do (log/info "Unknown event, acking" {:event-type (:event-type event)})
          true))
    (catch Exception e
      (log/error e "Handler exception"
                 (select-keys event [:account-id :entitlement-id :event-id :event-type]))
      false)))

^:rct/test
(comment
  (dispatch! {:account-id "A-1"          :event-type :account-active})             ;=> true
  (dispatch! {:account-id "A-1"          :event-type :account-creation-requested}) ;=> true
  (dispatch! {:account-id "accounts/A-1" :event-type :account-deleted})            ;=> true
  (dispatch! {:entitlement-id "E-1"      :event-type :entitlement-renewed})        ;=> true
  (dispatch! {:entitlement-id "E-1"      :event-type :entitlement-offer-accepted}) ;=> true
  (dispatch! {:event-type :account-deleted})                                       ;=> true
  (dispatch! {:event-type :entitlement-active})                                    ;=> true
  (dispatch! {:entitlement-id "E-1" :event-type :something-unknown})               ;=> true
  )
