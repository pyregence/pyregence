(ns pyregence.marketplace.entitlements
  (:require [clj-http.client                   :as http]
            [clojure.data.json                 :as json]
            [clojure.string                    :as str]
            [clojure.tools.logging             :as log]
            [pyregence.marketplace.credentials :as creds]
            [pyregence.marketplace.util        :as util]
            [triangulum.config                 :refer [get-config]]))

(def ^:private api-base "https://cloudcommerceprocurement.googleapis.com")

(def ^:private action->path
  {:approve             "approve"
   :approve-plan-change "approvePlanChange"})

(defn- ->url [resource-type resource-id action]
  (let [partner-id (get-config :pyregence.marketplace/config :partner-id)]
    (cond-> (str api-base "/v1/providers/" partner-id "/" (name resource-type) "/" resource-id)
      action (str ":" (or (action->path action)
                          (throw (ex-info "Unknown API action" {:action action})))))))

(defn- request! [method url body-map]
  (try
    (let [token    (creds/access-token :procurement-key-path)
          options  (cond-> {:as                 :json
                            :coerce             :always
                            :connection-timeout 10000
                            :headers            {"Authorization" (str "Bearer " token)
                                                 "Content-Type"  "application/json"}
                            :socket-timeout    30000
                            :throw-exceptions  false}
                     body-map (assoc :body (json/write-str body-map)))
          response (case method
                     :get   (http/get url options)
                     :post  (http/post url options)
                     :patch (http/patch url options))]
      (cond
        (#{200 201} (:status response))
        {:body    (:body response)
         :success true}

        (= 409 (:status response))
        (do
          (log/info "Entitlement operation returned 409 (idempotent)" {:url url})
          {:body    (:body response)
           :success true})

        :else
        (do
          (log/error "Entitlement API failed" {:status (:status response)
                                               :url    url})
          {:body    (:body response)
           :error   :api-error
           :success false})))
    (catch Exception e
      (when (util/interrupted-cause? e) (.interrupt (Thread/currentThread)))
      (log/error e "Failed to call entitlement API" {:url url})
      {:error   :request-exception
       :success false})))

^:rct/test
(comment
  (let [url (->url :entitlements "ent-123" nil)]
    (str/ends-with? url "/entitlements/ent-123"))                 ;=> true
  (let [url (->url :entitlements "ent-123" :approve)]
    (str/ends-with? url "/entitlements/ent-123:approve"))         ;=> true
  (let [url (->url :accounts "A-1" :approve)]
    (str/ends-with? url "/accounts/A-1:approve"))                 ;=> true
  (let [url (->url :entitlements "ent-1" :approve-plan-change)]
    (str/ends-with? url "/entitlements/ent-1:approvePlanChange")) ;=> true
  (try (->url :entitlements "ent-1" :bogus)
       (catch Exception e (:action (ex-data e))))                 ;=> :bogus
  )

(defn fetch
  "Fetches an entitlement from the Procurement API."
  [entitlement-id]
  (let [url (->url :entitlements entitlement-id nil)]
    (log/info "Fetching entitlement" {:entitlement-id entitlement-id})
    (request! :get url nil)))

(defn approve!
  "Approves an entitlement via the Procurement API."
  [entitlement-id]
  (let [url (->url :entitlements entitlement-id :approve)]
    (log/info "Approving entitlement" {:entitlement-id entitlement-id})
    (request! :post url {})))

(defn approve-plan-change!
  "Approves a pending plan change on an entitlement."
  [entitlement-id pending-plan]
  (let [url (->url :entitlements entitlement-id :approve-plan-change)]
    (log/info "Approving plan change" {:entitlement-id entitlement-id :pending-plan pending-plan})
    (request! :post url {"pendingPlanName" pending-plan})))

(defn approve-account!
  "Approves a marketplace account. Accepts bare or \"accounts/X\" form."
  [account-id]
  (if-let [bare-id (util/bare-account-id account-id)]
    (let [url (->url :accounts bare-id :approve)]
      (log/info "Approving marketplace account" {:account-id bare-id})
      (request! :post url {"approvalName" "signup"}))
    (throw (ex-info "Cannot approve account: blank account-id"
                    {:raw account-id :type :invalid-argument}))))
