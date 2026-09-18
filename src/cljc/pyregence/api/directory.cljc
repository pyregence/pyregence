(ns pyregence.api.directory
  "What PyreCast has told this page about who it belongs to.")

(defprotocol IDirectory
  (refresh! [directory]
    "Ask PyreCast again, and hold what it says.

     Asking and reading are separate because a refusal is not an answer. What
     PyreCast last said stays said until it says something else -- emptying the
     page on a refusal is the disappearance PYR1-1623 is about.

     Returns a channel that closes once PyreCast has answered, for the one
     caller that cannot read a stale answer: `select-forecast!` rebuilds the
     whole option panel out of what is held here, so it must ask before it
     reads. Everything else ignores the channel and reads whenever it renders.")

  (organizations [directory]
    "The organizations PyreCast reported for this session, or nil where it has
     reported none.

     Nil and empty are different claims. Empty says this session belongs to no
     organization; nil says PyreCast has not been asked, or would not say. NV
     Energy read the second as the first.")

  (psps-backed-ids [directory]
    "Ids of every organization PyreCast holds PSPS data for; nil before it has
     been asked.

     A fact about the world and not about a person -- PyreCast answers it on the
     shared client token -- so it outlives the session that does not. Which of
     them are this session's is asked of `organizations`, not of PyreCast."))

(defn directory?
  "Whether X answers as a directory."
  [x]
  (satisfies? IDirectory x))
