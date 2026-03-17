(ns pyregence.marketplace.credentials
  (:require [triangulum.config :refer [get-config]])
  (:import [com.google.auth.oauth2 AccessToken ServiceAccountCredentials]
           [java.io                FileInputStream]))

(def ^:private scope "https://www.googleapis.com/auth/cloud-platform")

(defonce ^:private cache (atom {}))

(defn- path->creds [key-path]
  (with-open [is (FileInputStream. key-path)]
    (-> (ServiceAccountCredentials/fromStream is)
        (ServiceAccountCredentials/.createScoped [scope]))))

(defn credentials
  "Returns cached ServiceAccountCredentials for the given config key."
  [config-key]
  (let [key-path (get-config :pyregence.marketplace/config config-key)]
    (when-not key-path
      (throw (ex-info "Key path not configured" {:config-key config-key :type :config-error})))
    @(-> (swap! cache update key-path #(or % (delay (path->creds key-path))))
         (get key-path))))

(defn access-token
  "Returns a fresh access token string, refreshing if expired."
  [config-key]
  (-> (credentials config-key)
      (doto (ServiceAccountCredentials/.refreshIfExpired))
      (ServiceAccountCredentials/.getAccessToken)
      (AccessToken/.getTokenValue)))

^:rct/test
(comment
  (try (credentials :nonexistent-key)
       (catch Exception e (:type (ex-data e)))) ;=> :config-error
  )
