(ns pyregence.test-support.acceptance-main
  "The acceptance suite's composition root: the same PyreCast as `pyregence.cli`,
   composed with a clock that can be pushed forward and with settings the run
   names rather than the ones its working directory happens to hold.

   This is what replaced -Dpyregence.clock.constructor. A flag naming a
   constructor left the choice with the accessor, which then had to resolve a
   symbol at first read; a second root makes the choice at the only place a
   choice belongs, and there is nothing left to resolve.

   The barrier is the classpath and not a conditional. `test-support` is not on
   :paths and not in :build-uberjar's :src-dirs, so no artifact contains this
   namespace and no production launch can name it -- see
   `pyregence.packaging-test`, which reads deps.edn and says so."
  (:gen-class)
  (:require [pyregence.cli      :as cli]
            [pyregence.clock    :as clock]
            [pyregence.settings :as settings]
            [pyregence.test-support.advanceable-clock :as advanceable]))

(defn -main
  "Settle the settings this run names, install the advanceable clock, and hand
   what is left of the arguments to the dispatch."
  [& args]
  (clock/install! (advanceable/advanceable-clock))
  (cli/run (settings/in-force-from-args! args)))
