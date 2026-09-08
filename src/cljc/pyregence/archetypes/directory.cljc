(ns pyregence.archetypes.directory
  (:require [pyregence.wiring :as wiring]))

(defn =>Directory
  "The directory concept, answering on behalf of A-SESSION."
  [a-session]
  (wiring/-directory (wiring/current) a-session))
