(ns pyregence.archetypes.session-activity
  (:require [pyregence.wiring :as wiring]))

(defn =>SessionActivity
  "The activity shared by every tab in this browser session."
  []
  (wiring/-session-activity (wiring/current)))
