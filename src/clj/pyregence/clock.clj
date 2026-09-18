(ns pyregence.clock
  "The system's sense of the present, as an installed capability rather than an
   ambient read.

   Session expiry is decided here, server-side: `pyregence.session` compares the
   stamps in the cookie against what this namespace says the time is. That made
   the fifteen-minute idle timeout untestable -- the only way to observe it was to
   wait fifteen minutes, which is not a test anyone runs.

   Branch By Abstraction: the protocol and the one production implementation
   arrive together, and the second implementation deliberately does not. It lives
   in test-support/, which `:build-uberjar` cannot reach (:src-dirs is
   [\"src/clj\"]).

   Install, don't build. Nothing here decides which clock this process runs on;
   a composition root builds one and hands it over. `pyregence.cli/-main` is that
   root for every production launch and builds a `SystemClock`; figwheel is the
   development one and installs the same clock from `handlers/clock-delay`; the
   acceptance suite has a root of its own, on a source path no artifact carries,
   which builds an advanceable one. An accessor that chose for itself would be a
   service locator -- substitutable without being injected -- and would put the
   decision in the last place a reader looks for it.

   Asked before anything installed one, `clock` throws rather than falling back
   to the wall clock. The failure that guards against is a run which believes it
   can move time, silently gets real time, and reports that nothing expires.")

(defprotocol IClock
  (now-ms [clock] "Milliseconds since the epoch, as this clock reckons it."))

(defrecord SystemClock []
  IClock
  (now-ms [_] (System/currentTimeMillis)))

(defonce ^:private installed
  ;; defonce so that reloading this namespace at a REPL does not quietly
  ;; uninstall the clock the running server was started with.
  (atom nil))

(defn install!
  "Hold CLOCK as the one this process runs on. Called by a composition root,
   before anything serves a request."
  [clock]
  (reset! installed clock))

(defn installed?
  "Whether a root has installed a clock yet."
  []
  (some? @installed))

(defn clock
  "The one clock this process was started with."
  []
  (or @installed
      (throw (ex-info (str "No clock has been installed. A composition root "
                           "calls pyregence.clock/install! before anything reads "
                           "the time; this process reached a read without one.")
                      {}))))

(defn now
  "Shorthand for the present. Call this at a request boundary and pass the value
   down: a function that takes `now` can be reasoned about, and one that reaches
   for it cannot."
  []
  (now-ms (clock)))

^:rct/test
(comment
  ;; The production clock tracks the wall clock.
  (let [before (System/currentTimeMillis)
        t      (now-ms (->SystemClock))
        after  (System/currentTimeMillis)]
    (<= before t after))
  ;=> true

  ;; Reading before a root has installed one is fatal, not a fallback.
  (with-redefs [installed (atom nil)]
    (try (now) :did-not-throw
         (catch clojure.lang.ExceptionInfo _ :threw)))
  ;=> :threw
  )
