(ns pyregence.api.session-activity
  "Activity shared by every tab belonging to one browser session.")

(defprotocol ISessionActivity
  (last-input-at [activity]
    "When any tab last saw input from the person using this session.")

  (last-reported-input-at [activity]
    "The newest input PyreCast has already received a heartbeat for.")

  (note-input-at! [activity at]
    "Remember input at AT for every tab in this session.")

  (note-reported-input-at! [activity at]
    "Remember that input at AT has been reported to PyreCast."))
