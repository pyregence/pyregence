(ns pyregence.analytics)

(defn gtag
  "Call `gtag` with a given `info` (a clojure map with the info we want to track)"
  [info]
  (js/gtag "event" "registered-user" (clj->js info)))

(defn gtag-tool-clicked
  "Call `gtag` for a specific togglable tool named `tool-name`
  `gtag` is a function provided by google analytics tool.
  See https://developers.google.com/analytics/devguides/collection/ga4"
  ([event-name]
   (gtag {:tool-clicked event-name}))
  ([show? tool-name]
   (let [event-name (str (if show? "show-" "hide-") tool-name)]
     (gtag-tool-clicked event-name))))
