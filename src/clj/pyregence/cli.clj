(ns pyregence.cli
  (:gen-class)
  (:require
   [triangulum.cli      :refer [get-cli-options]]
   [triangulum.build-db :as build-db]
   [triangulum.server :as server]))

(defn -main
  [& args]
  (if-let [{:keys [action options]}
           (get-cli-options
             [(first args)]
            {}
            {:server   {:description "manage web-server"}
             :build-db {:description "manage database"}}
            "server or build-db")]
    (case action
      :server    (apply triangulum.server/-main (rest args))
      :build-db (apply build-db/-main (rest args))
      nil)
    (System/exit 0)))
