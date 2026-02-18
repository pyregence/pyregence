(ns pyregence.marketplace.approval
  (:require [clj-http.client                   :as http]
            [clojure.data.json                 :as json]
            [clojure.string                    :as str]
            [clojure.tools.logging             :as log]
            [pyregence.marketplace.credentials :as creds]
            [triangulum.config                 :refer [get-config]]))

(def ^:private api-base "https://cloudcommerceprocurement.googleapis.com")

(defn- ->account-id [id]
  (if (str/starts-with? id "accounts/")
    (subs id 9)
    id))

^:rct/test
(comment
  (->account-id "accounts/123") ;=> "123"
  (->account-id "123")          ;=> "123"
  )

(defn approve! [acct-id]
  (let [partner-id (get-config :pyregence.marketplace/config :partner-id)
        account-id (->account-id acct-id)
        url        (str api-base "/v1/providers/" partner-id "/accounts/" account-id ":approve")]
    (log/info "Approving marketplace account" {:account-id account-id})
    (try
      (let [token    (creds/access-token :procurement-key-path)
            response (http/post url {:as               :json
                                     :body             (json/write-str {:approvalName "signup"})
                                     :headers          {"Authorization" (str "Bearer " token)
                                                        "Content-Type"  "application/json"}
                                     :throw-exceptions false})]
        (if (#{200 201} (:status response))
          (do
            (log/info "Account approved" {:account-id account-id})
            {:body    (:body response)
             :success true})
          (do
            (log/error "Account approval failed"
                       {:account-id account-id
                        :status     (:status response)})
            {:body    (:body response)
             :error   (str "API returned " (:status response))
             :success false})))
      (catch Exception e
        (log/error e "Failed to call approval API" {:account-id account-id})
        {:error   (Throwable/.getMessage e)
         :success false}))))
