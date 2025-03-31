(ns pyregence.analytics)

(defn gtag-tool
  "Call `gtag` for a specific togglable tool named `tool-name`
  `gtag` is a function provided by google analytics tool.
  See https://developers.google.com/analytics/devguides/collection/ga4"
  [show? tool-name]
  (let [event-name (str (if show? "show-" "hide-") tool-name)]
    (js/gtag "event" "registered-user" (clj->js {:tool-clicked event-name}))))
