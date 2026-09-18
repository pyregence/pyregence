(ns pyregence.rct-runner
  "The rich-comment-test run's composition root.

   The `^:rct/test` blocks live inside production namespaces and exercise them
   as they stand, so a run that reads the time reaches `pyregence.clock` -- which
   since the install-don't-build inversion holds what a root handed it and throws
   when nothing did. Ten blocks across authentication, capabilities and handlers
   reach it through `session/live?`.

   Installing here rather than in each block is the same argument the inversion
   itself rests on: which clock a process runs on is one decision, made once, at
   the top. The wall clock is the right one -- these blocks assert what PyreCast
   does with real time, and none of them move it."
  (:require [com.mjdowney.rich-comment-tests.test-runner :as rct]
            [pyregence.clock :as clock]))

(defn -main
  "Install the wall clock, then run every rct block under src/."
  [& _]
  (clock/install! (clock/->SystemClock))
  (let [{:keys [fail error]} (rct/run-tests-in-file-tree! :dirs #{"src"})]
    (System/exit (if (pos? (+ (or fail 0) (or error 0))) 1 0))))
