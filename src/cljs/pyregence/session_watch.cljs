(ns pyregence.session-watch
  "The page noticing, for itself, that PyreCast has stopped honouring its
   session.

   PYR1-1623. Before this, nothing on the page knew time was passing. The
   session died on the server at the appointed minute and the page went on
   showing a Settings button and offering an organization's private layers until
   somebody clicked one and was refused -- and the organization read that
   refusal as its data having disappeared. The refusal path still exists and
   still explains itself, but it only runs when somebody acts, and the complaint
   was about what the screen says while nobody is acting.

   Two halves, and they are opposites on purpose:

   - **The kick.** No input for the length of the window, so stop claiming the
     session and go to the login page saying why.
   - **The heartbeat.** There *was* input, so tell PyreCast, and keep a session
     the person is demonstrably still using.

   Neither is any good alone. Without the heartbeat this is a feature that logs
   out people who are sitting right in front of it: the server's idle timer is
   refreshed by `/clj/*` calls, and reading a map raises none, so a person can
   work for an hour and look idle the whole time. Without the kick the heartbeat
   has nothing to prevent. Worth knowing when reading a test run: a broken
   heartbeat and a working kick look, from the outside, exactly like the fix
   working.

   Nothing here touches the browser or the clock directly. The DOM events that
   mean \"a person is here\" are `browser-utils`' business and the present is
   `clock`'s, so that what is left in this namespace is only the policy: how
   long is too long, and what to do about it."
  (:require [pyregence.clock                  :as clock]
            [pyregence.datatypes.idle-window  :as idle-window]
            [pyregence.utils.async-utils      :as u-async]
            [pyregence.utils.browser-utils    :as u-browser]))

(defonce ^:private ^{:doc "When this page last saw a person. Not when PyreCast last heard from one --
  see `reported-input-at`; keeping the two apart is what makes a beat something
  that can be owed."}
  last-input-at (atom nil))

(defonce ^:private ^{:doc "The moment of input PyreCast has already been told about, so that a page
  nobody is touching stops beating instead of keeping itself alive forever."}
  reported-input-at (atom nil))

(defonce ^:private watching?
  ;; One watch per page. Figwheel re-runs init on every reload, and a second
  ;; watch would mean a second set of listeners and a second interval, quietly
  ;; doubling the beat rate for the rest of the session.
  (atom false))

(defn- note-input!
  "Remember that somebody is here. Runs on every mouse move, so it does one
   thing."
  []
  (reset! last-input-at (clock/now)))

(defn- quiet-ms
  "How long since this page last saw a person."
  []
  (- (clock/now) @last-input-at))

(defn- owes-a-beat?
  "Whether there has been input PyreCast has not been told about.

   The gate that stops this being a bug rather than a feature. An unconditional
   beat would refresh the idle timer of every tab anybody ever left open, which
   is not a heartbeat -- it is a way of turning the timeout off."
  []
  (not= @last-input-at @reported-input-at))

(defn- report-input!
  "Tell PyreCast somebody is still here.

   The reply is not read. Its effect is the refresh `clj-handler` performs on
   any live gated call, and a refusal takes the same route to the login page
   that every other refused call takes -- which is how an open tab whose session
   was revoked elsewhere finds out without being clicked."
  []
  (reset! reported-input-at @last-input-at)
  (u-async/call-clj-async! "note-activity"))

(defn- give-up!
  "Stop claiming this session and go where something can be done about it.

   Nothing is torn down first: the page is going away, and a page that dismantled
   itself and then navigated would be doing the work twice."
  []
  (u-browser/jump-to-url! (str "/login?"
                              u-async/session-ended-param
                              "="
                              u-async/session-ended-reason-idle)))

(defn- check!
  "One look at the clock: give up, beat, or do nothing.

   Giving up wins over beating. They cannot both be right, and beating on the
   tick that decided the session is over would be the page asking PyreCast to
   keep alive a session it has just concluded is dead."
  [an-idle-window]
  (cond
    (idle-window/elapsed? an-idle-window (quiet-ms)) (give-up!)
    (owes-a-beat?)                                   (report-input!)))

(defn watch!
  "Watch AN-IDLE-WINDOW for the moment this session goes quiet too long.

   Does nothing when handed nil -- PyreCast did not say how long the window is,
   so there is nothing to be early for, and the server goes on enforcing it
   regardless. Does nothing on a second call either; see `watching?`.

   Counts from now rather than from nothing: a page has just been loaded, which
   somebody did, and treating that as no input would kick the first person to
   open one and then sit reading it."
  [an-idle-window]
  (when (and an-idle-window (compare-and-set! watching? false true))
    (note-input!)
    (u-browser/when-the-user-does-anything! note-input!)
    (u-browser/every-so-often! idle-window/poll-interval-ms
                               #(check! an-idle-window))
    ;; And again the moment the page is looked at, because the poll above is not
    ;; reliable while it is not. A browser clamps timers in a hidden tab to
    ;; roughly one a minute, which is slower than the margin `idle-window` gives
    ;; up -- so a tab left in the background can pass the window without the
    ;; poll noticing, and the guarantee that the page never outlives the server
    ;; would hold everywhere except the one moment it is observable: when
    ;; somebody comes back to the tab and reads what is on it. Checking on the
    ;; way back is what closes that. Coming back is not itself input -- somebody
    ;; may have arrived here only by closing something else -- so this asks the
    ;; question rather than answering it.
    (u-browser/when-the-page-becomes-visible! #(check! an-idle-window))))
