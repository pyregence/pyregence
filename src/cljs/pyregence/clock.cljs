(ns pyregence.clock
  "The page's sense of the present, as an installed capability rather than an
   ambient read.

   The mirror of `pyregence.clock` on the server, and here for the same reason.
   PYR1-1623's idle kick is a decision about elapsed time, and a decision about
   elapsed time taken by reading `js/Date.now` where it is needed can only be
   observed by waiting out the real window -- which is not a test anybody runs,
   and so is not a thing anybody checks.

   Install, don't build. Nothing here decides which clock this page runs on; a
   composition root builds one and hands it over. `pyregence.client/init` is
   that root and builds a `SystemClock`.

   Registration rather than resolution, and here that is the only option going.
   The server can name a clock in config and `requiring-resolve` it;
   ClojureScript has no resolver, and `:advanced` munges the names one would
   need to reach anyway.

   The barrier is weaker than the server's, and that is worth saying plainly
   rather than glossing: a page has no private namespace, so `install!` is a
   public mutation that anything running on the page can call, the browser
   console included. What keeps a test clock out of production is not this
   namespace -- it is that the only caller which installs anything other than
   the wall clock lives on a source root no production build lists.

   Asked before a root has installed one, `clock` throws rather than falling
   back to the wall clock. The failure that guards against is a run which
   believes it can move time, silently gets real time, and reports that nothing
   ever expires.")

(defprotocol IClock
  (now-ms [clock] "Milliseconds since the epoch, as this clock reckons it."))

(defrecord SystemClock []
  IClock
  (now-ms [_] (.now js/Date)))

(defonce ^:private installed
  ;; defonce so a figwheel reload of this namespace does not quietly uninstall
  ;; the clock the page was started with.
  (atom nil))

(defn install!
  "Hold CLOCK as the one this page runs on. Called by a composition root, before
   anything reads the time."
  [clock]
  (reset! installed clock))

(defn installed?
  "Whether a root has installed a clock yet."
  []
  (some? @installed))

(defn clock
  "The one clock this page was started with."
  []
  (or @installed
      (throw (ex-info (str "No clock has been installed. A composition root "
                           "calls pyregence.clock/install! before anything reads "
                           "the time; this page reached a read without one.")
                      {}))))

(defn now
  "Shorthand for the present. Call this at an edge and pass the value down: a
   function that takes `now` can be reasoned about, and one that reaches for it
   cannot."
  []
  (now-ms (clock)))
