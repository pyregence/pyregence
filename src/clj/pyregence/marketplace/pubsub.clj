(ns pyregence.marketplace.pubsub
  (:require [clj-http.client                   :as http]
            [clojure.data.json                 :as json]
            [clojure.string                    :as str]
            [clojure.tools.logging             :as log]
            [pyregence.marketplace.credentials :as creds]
            [pyregence.marketplace.handlers    :as handlers]
            [pyregence.marketplace.util        :as util]
            [triangulum.config                 :refer [get-config]])
  (:import [java.util Base64 Base64$Decoder]))

(def ^:private pubsub-base "https://pubsub.googleapis.com/v1")

(def ^:private default-poll-ms 30000)

(def ^:private max-messages 10)

(def ^:private max-backoff-ms 300000)

(def ^:private max-handler-retries 5)

(defonce ^:private failure-counts (atom {}))

(defn- event-type->keyword [event-type]
  (when (not-empty (str event-type))
    (-> event-type (str/lower-case) (str/replace "_" "-") (keyword))))

(defn- normalize-entitlement-id [raw]
  (when (some? raw)
    (util/strip-resource-prefix "entitlements/" (str raw))))

(defn- extract-event-fields [payload]
  (let [raw-ent  (get payload "entitlement")
        raw-acct (get payload "account")]
    (cond
      (map? raw-ent)
      (let [ent-id (normalize-entitlement-id
                      (or (get raw-ent "id") (get raw-ent "name")))
            plan   (or (get raw-ent "newPlan") (get raw-ent "newPendingPlan"))]
        (cond-> {:entitlement-id ent-id}
          plan (assoc :new-plan plan)))

      (some? raw-ent)                {:entitlement-id (normalize-entitlement-id (str raw-ent))}
      (get payload "entitlementId")  {:entitlement-id (normalize-entitlement-id (get payload "entitlementId"))}
      (map? raw-acct)                {:account-id (or (get raw-acct "id") (get raw-acct "name"))}
      (some? raw-acct)               {:account-id raw-acct}
      :else                          {})))

(defn- decode-message
  "Decodes a Pub/Sub message. Returns event map on success, nil on failure."
  [message]
  (try
    (let [data    (get-in message ["message" "data"])
          ack-id  (get message "ackId")
          payload (-> (Base64$Decoder/.decode (Base64/getDecoder) data)
                      (String.)
                      (json/read-str))]
      (assoc (extract-event-fields payload)
             :ack-id     ack-id
             :event-id   (get payload "eventId")
             :event-type (event-type->keyword (get payload "eventType"))))
    (catch Exception e
      (log/warn e "Failed to decode Pub/Sub message")
      nil)))

(defn- pubsub-post! [subscription-id action body]
  (let [token (creds/access-token :pubsub-key-path)
        url   (str pubsub-base "/" subscription-id ":" action)]
    (http/post url {:as                 :json-string-keys
                    :body               (json/write-str body)
                    :coerce             :always
                    :connection-timeout  10000
                    :headers            {"Authorization" (str "Bearer " token)
                                         "Content-Type"  "application/json"}
                    :socket-timeout     30000
                    :throw-exceptions   false})))

(defn- pull!
  "Pulls messages from Pub/Sub. Throws on non-200 for backoff."
  [subscription-id]
  (let [response (pubsub-post! subscription-id "pull" {"maxMessages" max-messages})]
    (if (= 200 (:status response))
      (get (:body response) "receivedMessages" [])
      (throw (ex-info "Pub/Sub pull failed"
                      {:body   (:body response)
                       :status (:status response)})))))

(defn- acknowledge! [subscription-id ack-ids]
  (when (seq ack-ids)
    (try
      (let [response (pubsub-post! subscription-id "acknowledge" {"ackIds" ack-ids})]
        (when-not (= 200 (:status response))
          (log/warn "Pub/Sub acknowledge returned non-200"
                    {:count  (count ack-ids)
                     :status (:status response)})))
      (catch Exception e
        (when (util/interrupted-cause? e) (.interrupt (Thread/currentThread)))
        (log/error e "Failed to acknowledge Pub/Sub messages")))))

(defn- dispatch-or-exhaust!
  "Dispatches event, returns ack-id on success or after max retries, nil to retry."
  [event]
  (let [retry-key (or (:event-id event) (:ack-id event))]
    (if (handlers/dispatch! event)
      (do (swap! failure-counts dissoc retry-key)
          (:ack-id event))
      (let [n (get (swap! failure-counts update retry-key (fnil inc 0)) retry-key)]
        (when (>= n max-handler-retries)
          (log/error "Handler retries exhausted, acking to prevent infinite retry"
                     (select-keys event [:ack-id :entitlement-id :account-id :event-id :event-type]))
          (swap! failure-counts dissoc retry-key)
          (:ack-id event))))))

(defn- poll! [subscription-id]
  (let [messages  (pull! subscription-id)
        decoded   (mapv (fn [m] {:event (decode-message m) :ack-id (get m "ackId")}) messages)
        bad-acks  (into []
                        (comp (remove :event)
                              (keep (fn [{:keys [ack-id]}]
                                      (when ack-id
                                        (log/warn "Acking malformed Pub/Sub message" {:ack-id ack-id})
                                        ack-id))))
                        decoded)
        good-acks (into []
                        (comp (keep :event)
                              (keep dispatch-or-exhaust!))
                        decoded)]
    (acknowledge! subscription-id good-acks)
    (acknowledge! subscription-id bad-acks)
    (count good-acks)))

^:rct/test
(comment
  (event-type->keyword "ENTITLEMENT_ACTIVE")  ;=> :entitlement-active
  (event-type->keyword "ACCOUNT_DELETED")     ;=> :account-deleted
  (event-type->keyword nil)                   ;=> nil

  (normalize-entitlement-id "providers/P/entitlements/E-1")  ;=> "E-1"
  (normalize-entitlement-id "entitlements/E-1")              ;=> "E-1"
  (normalize-entitlement-id "E-1")                           ;=> "E-1"
  (normalize-entitlement-id nil)                             ;=> nil

  (:entitlement-id (extract-event-fields {"entitlement" "E-1"}))                                   ;=> "E-1"
  (:entitlement-id (extract-event-fields {"entitlement" "providers/P/entitlements/E-1"}))          ;=> "E-1"
  (:entitlement-id (extract-event-fields {"entitlement" {"id" "E-1" "newPlan" "biz"}}))            ;=> "E-1"
  (:new-plan (extract-event-fields {"entitlement" {"id" "E-1" "newPlan" "biz"}}))                  ;=> "biz"
  (:entitlement-id (extract-event-fields {"entitlement" {"name" "providers/P/entitlements/E-1"}})) ;=> "E-1"
  (:entitlement-id (extract-event-fields {"entitlement" {"id" "E-1"}}))                            ;=> "E-1"
  (:entitlement-id (extract-event-fields {"entitlementId" "E-1"}))                                 ;=> "E-1"
  (:account-id (extract-event-fields {"account" {"id" "A-1"}}))                                    ;=> "A-1"
  (:account-id (extract-event-fields {"account" "A-1"}))                                           ;=> "A-1"
  (extract-event-fields {})                                                                        ;=> {}

  (decode-message {})                                          ;=> nil
  (decode-message {"ackId" "a1" "message" {"data" "not-b64"}}) ;=> nil
  )

(defn- next-backoff!
  "Polls once, returns next backoff-ms. Throws InterruptedException to stop."
  [subscription-id backoff-ms]
  (try
    (poll! subscription-id)
    0
    (catch InterruptedException e (throw e))
    (catch Exception e
      (if (util/interrupted-cause? e)
        (throw (InterruptedException.))
        (let [next-ms (min (* 2 (max backoff-ms 1000)) max-backoff-ms)]
          (log/error e "Pub/Sub poll error, backing off" {:backoff-ms next-ms})
          next-ms)))))

(defn- subscriber-loop! [subscription-id poll-interval-ms]
  (reset! failure-counts {})
  (try
    (loop [backoff-ms 0]
      (when-not (Thread/interrupted)
        (let [next-backoff (next-backoff! subscription-id backoff-ms)]
          (Thread/sleep (+ poll-interval-ms next-backoff))
          (recur next-backoff))))
    (catch InterruptedException _))
  (log/info "Marketplace Pub/Sub subscriber stopped"))

(defn start-subscriber!
  "Starts the marketplace Pub/Sub pull loop in a future. No-op if :enabled is false."
  []
  (if-not (get-config :pyregence.marketplace/config :enabled)
    (log/info "Marketplace Pub/Sub subscriber disabled")
    (let [subscription-id (get-config :pyregence.marketplace/config :subscription-id)]
      (when (str/blank? subscription-id)
        (throw (ex-info "Marketplace enabled but :subscription-id not configured"
                        {:type :config-error})))
      (let [poll-interval-ms (or (get-config :pyregence.marketplace/config :poll-interval-ms) default-poll-ms)]
        (log/info "Starting marketplace Pub/Sub subscriber"
                  {:poll-interval-ms poll-interval-ms :subscription-id subscription-id})
        (future (subscriber-loop! subscription-id poll-interval-ms))))))

(defn stop-subscriber!
  "Cancels a subscriber future started by start-subscriber!."
  [subscriber]
  (when subscriber (future-cancel subscriber)))
