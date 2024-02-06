(ns pyregence.secrets
  (:require [triangulum.config   :refer [get-config]]
            [triangulum.response :refer [data-response]]))

(defn get-mapbox-access-token []
  (data-response (get-config :pyregence.secrets/mapbox-access-token)))

(defn get-pyr-auth-token []
  (data-response (get-config :pyregence.secrets/pyr-auth-token)))
