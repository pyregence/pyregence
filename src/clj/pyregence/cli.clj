(ns pyregence.cli
  (:gen-class)
  (:require
   [pyregence.clock     :as clock]
   [triangulum.build-db :as build-db]
   [triangulum.cli      :refer [get-cli-options]]
   [triangulum.server   :as server]))

(def cli-actions {:server   {:description "Manage web-server"}
                  :build-db {:description "Manage database"}})

(defn run
  "The dispatch, with no opinion about what this process was composed out of.

   Split from -main so that a root other than this one -- the acceptance suite
   has one, on a source path no artifact carries -- can compose differently and
   still reach the same subcommands. Nothing in here installs anything."
  [args]
  (if-let [{:keys [action]}
           (get-cli-options
            (take 1 (filter (set (map name (keys cli-actions))) args))
            {}
            cli-actions
            "cli")]
    (let [subtask-args (remove #{(name action)} args)]
      (case action
        :server   (apply server/-main subtask-args)
        :build-db (apply build-db/-main subtask-args)
        nil))
    (System/exit 0)))

(defn -main
  "The composition root for every launch that ships: the uberjar names this
   namespace as its :main-ns, and the :server and :cli aliases enter here too.

   It builds the wall clock and installs it before dispatching. That is the whole
   of the decision -- `pyregence.clock` holds what it is handed and chooses
   nothing -- and it is why there is no launch flag or config key naming a clock:
   selecting one means having a different root, and only test-support has one."
  [& args]
  (clock/install! (clock/->SystemClock))
  (run args))
