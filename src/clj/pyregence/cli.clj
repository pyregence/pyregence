(ns pyregence.cli
  (:gen-class)
  (:require
   [triangulum.build-db :as build-db]
   [triangulum.cli      :refer [get-cli-options]]
   [triangulum.server   :as server]))

(def cli-actions {:server   {:description "Manage web-server"}
                  :build-db {:description "Manage database"}})

(defn -main
  [& args]
  (if-let [{:keys [action]}
           (get-cli-options
            (take 1 (filter (set (map name (keys cli-actions))) args))
            {}
            cli-actions
            "cli")]
    (let [subtask-args (remove #{(name action)} args)]
      (case action
        :server   (apply triangulum.server/-main subtask-args)
        :build-db (apply build-db/-main subtask-args)
        nil))
    (System/exit 0)))
