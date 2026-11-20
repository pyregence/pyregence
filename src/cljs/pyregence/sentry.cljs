(ns pyregence.sentry
  (:require ["@sentry/browser" :as sentry]
            [clojure.string    :as str]))

(defonce ^:private initialized? (atom false))

(defn init!
  "Initializes the browser Sentry SDK once when :dsn is present."
  [{:keys [dsn environment release]}]
  (when (and (string? dsn) (not (str/blank? dsn)) (compare-and-set! initialized? false true))
    (try
      ;; Never send user emails, IP addresses, or cookies to Sentry.
      (js-invoke sentry "init" (clj->js (cond-> {"dsn"            dsn
                                                 "environment"    (or environment "development")
                                                 "sendDefaultPii" false}
                                          release (assoc "release" release))))
      (catch :default _ (reset! initialized? false)))))

(defn report-exception!
  "Reports error to Sentry when initialized and always returns error unchanged."
  [error context-map]
  (try
    (when @initialized?
      (js-invoke sentry "captureException" error (clj->js {"extra" context-map})))
    (catch :default _ nil))
  error)
