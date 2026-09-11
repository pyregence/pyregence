(ns pyregence.datatypes.idle-window
  "How long PyreCast will let a session sit untouched.

   The window belongs to the server -- `pyregence.session/timed-out?` is what
   actually enforces it, and nothing a page believes changes that. This is the
   page's copy, delivered with the page so the two cannot disagree. A literal
   here would be a second source of truth that drifts the first time somebody
   changes the config, drifts silently, and drifts in whichever direction
   happens to be wrong that day.

   The one rule this type exists to hold: **the client must never outlive the
   server.** It gives up a margin early, and the margin is one poll interval, so
   that the latest a poll can notice is the moment the window closes rather than
   some way past it. A page still claiming a session PyreCast has already
   stopped honouring is not a smaller version of PYR1-1623 -- it is PYR1-1623.")

(defrecord IdleWindow [window-ms])

(def poll-interval-ms
  "How often to ask whether the window has run out.

   Fifteen seconds: fine enough that the answer is never long stale, coarse
   enough to cost nothing. It is also the margin `elapsed?` gives up -- one
   number, so the two cannot be changed apart, and the guarantee they make
   together cannot be broken by editing only one of them."
  15000)

(defn ->idle-window
  "The window PyreCast reports, in MINUTES, or nil where it reported none.

   Nil rather than a default. A page that does not know the window cannot kick
   early with any confidence, and guessing is exactly how a client ends up
   outliving the server. The server goes on enforcing either way, so not
   knowing costs the unprompted kick and nothing else -- which is the old
   behaviour, and a fair thing to fall back to."
  [minutes]
  (when (number? minutes)
    (->IdleWindow (* 60000 minutes))))

(defn elapsed?
  "Whether QUIET-MS without user input is long enough that the page should stop
   claiming this session.

   One poll interval early, deliberately. Polls land every `poll-interval-ms`,
   so the first one to see this true is at most that late -- which puts the kick
   at or before the moment the server stops honouring the session, and never
   after it."
  [idle-window quiet-ms]
  (>= quiet-ms (- (:window-ms idle-window) poll-interval-ms)))

^:rct/test
(comment
  ;; PyreCast said nothing, so there is nothing to act on.
  (mapv ->idle-window [nil "15"])
  ;=> [nil nil]

  ;; The kick lands early, never late. Sampling a fifteen-minute window at the
  ;; poll interval: quiet up to one interval short of the window is fine, and
  ;; the moment quiet reaches window-minus-one-interval it is not.
  (let [w      (->idle-window 15)
        window (* 15 60000)]
    [(elapsed? w 0)
     (elapsed? w (- window poll-interval-ms 1))
     (elapsed? w (- window poll-interval-ms))
     (elapsed? w window)])
  ;=> [false false true true]

  ;; The guarantee itself, stated as the thing that must hold rather than as a
  ;; number: whenever the poll before the window closes has already fired, the
  ;; page has already given up. Walking the polls of a two-minute window -- the
  ;; value used to test this by hand -- the last one strictly inside the window
  ;; must be a kick.
  (let [minutes 2
        w       (->idle-window minutes)
        window  (* minutes 60000)
        polls   (range poll-interval-ms window poll-interval-ms)]
    (elapsed? w (last polls)))
  ;=> true

  ;; A window shorter than one poll is degenerate, and the safe reading of it is
  ;; "already elapsed" -- never "wait a bit longer".
  (elapsed? (->idle-window 0.1) 0)
  ;=> true
  )
