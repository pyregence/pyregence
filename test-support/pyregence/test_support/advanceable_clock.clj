(ns pyregence.test-support.advanceable-clock
  "A clock that can be pushed forward, so a fifteen-minute timeout can be tested
   in fifteen milliseconds.

   This namespace is not production code and cannot become production code by
   accident. `test-support` is not on :paths and not in :build-uberjar's
   :src-dirs, so it is absent from every artifact; it reaches a running process
   only through the :acceptance alias, which adds the path and names the root
   that installs the clock below.

   The offset is deliberately additive rather than absolute. A test that says
   \"sixteen minutes pass\" means sixteen minutes from wherever the system
   already is; a clock pinned to a fixed instant would also freeze time for
   everything else in the process, and a session created at that instant would
   never age at all."
  (:require [pyregence.clock :as clock]))

(defrecord AdvanceableClock [offset]
  clock/IClock
  (now-ms [_] (+ (System/currentTimeMillis) @offset)))

(defn advanceable-clock
  "Called once, at startup, by `pyregence.test-support.acceptance-main`."
  []
  (->AdvanceableClock (atom 0)))

(defn- installed
  "The running system's clock, if it is one of ours -- and nil rather than a
   throw when no root has installed one at all. `installed?` is asked precisely
   in order to find out, so it must be able to come back false."
  []
  (when (clock/installed?)
    (let [c (clock/clock)]
      (when (instance? AdvanceableClock c) c))))

(defn installed?
  "Whether this process was launched with an advanceable clock. The acceptance
   runner asks before it trusts a single scenario, because the alternative is a
   run that quietly advances nothing and reports that nothing ever expires."
  []
  (some? (installed)))

(defn advance!
  "Move this process forward by MS milliseconds."
  [ms]
  (if-let [c (installed)]
    (swap! (:offset c) + ms)
    (throw (ex-info "This process was not launched with an advanceable clock" {}))))

(defn reset-clock!
  "Return this process to real time."
  []
  (if-let [c (installed)]
    (reset! (:offset c) 0)
    (throw (ex-info "This process was not launched with an advanceable clock" {}))))
