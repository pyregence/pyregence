(ns pyregence.marketplace.credentials
  (:require [triangulum.config :refer [get-config]])
  (:import [com.google.auth.oauth2 AccessToken ServiceAccountCredentials]
           [java.io                FileInputStream]))

(def ^:private scope "https://www.googleapis.com/auth/cloud-platform")

(defonce ^:private cache (atom {}))

(defn- path->creds [key-path]
  (-> (FileInputStream. key-path)
      (ServiceAccountCredentials/fromStream)
      (ServiceAccountCredentials/.createScoped [scope])))

(defn credentials [config-key]
  (let [key-path (get-config :pyregence.marketplace/config config-key)]
    (when-not key-path
      (throw (ex-info (str "Key path not configured: " config-key) {:type :config-error})))
    (or (get @cache key-path)
        (let [creds (path->creds key-path)]
          (swap! cache assoc key-path creds)
          creds))))

(defn access-token [config-key]
  (let [creds (credentials config-key)]
    (ServiceAccountCredentials/.refreshIfExpired creds)
    (AccessToken/.getTokenValue (ServiceAccountCredentials/.getAccessToken creds))))
