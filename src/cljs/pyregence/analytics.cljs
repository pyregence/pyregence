(ns pyregence.analytics)

(defn gtag
  "Call `gtag` with a given `info` (a clojure map with the info we want to track)
  `gtag` is a function provided by google analytics tool.
   See https://developers.google.com/analytics/devguides/collection/ga4"
  [event-name info]
  (js/gtag "event" event-name (clj->js info)))

(defn gtag-tool-clicked
  "Call `gtag` passing the `tool-usage` event.
   If the tool is toggable, `show?` it the current state."
  ([event-name]
   (gtag "tool-usage" {:tool-clicked event-name}))
  ([show? tool-name]
   (let [event-name (str (if show? "show-" "hide-") tool-name)]
     (gtag-tool-clicked event-name))))
