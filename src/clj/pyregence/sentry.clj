(ns pyregence.sentry
  (:require [pyregence.utils   :refer [nil-on-error]]
            [triangulum.config :refer [get-config]])
  (:import [io.sentry IScope ScopeCallback Sentry Sentry$OptionsConfiguration SentryOptions]))

(defonce ^:private initialized? (atom false))

(defn- sentry-config []
  (nil-on-error (get-config :pyregence.sentry/config)))

(defn- release []
  (or (not-empty (System/getenv "SENTRY_RELEASE"))
      (some-> (.getPackage ^Class (class sentry-config))
              (.getImplementationVersion)
              not-empty)))

(defn client-config
  "Returns browser Sentry settings for the page session, or nil when no frontend DSN is set."
  []
  (let [cfg (sentry-config)]
    (when-let [dsn (or (not-empty (System/getenv "SENTRY_FRONTEND_DSN"))
                       (not-empty (:frontend-dsn cfg)))]
      (cond-> {:dsn         dsn
               :environment (or (not-empty (System/getenv "SENTRY_ENVIRONMENT"))
                                (not-empty (:environment cfg))
                                "development")}
        (release) (assoc :release (release))))))

(defn init!
  "Initializes Sentry once per JVM when a backend DSN is configured."
  []
  (when (compare-and-set! initialized? false true)
    (let [cfg (sentry-config)
          dsn (or (not-empty (System/getenv "SENTRY_DSN")) (not-empty (:dsn cfg)))
          env (or (not-empty (System/getenv "SENTRY_ENVIRONMENT")) (not-empty (:environment cfg)) "development")
          rel (release)]
      (when-not (and dsn
                     (nil-on-error
                      (Sentry/init
                       (reify Sentry$OptionsConfiguration
                         (configure [_ options]
                           (let [^SentryOptions opts options]
                             (.setDsn opts ^String dsn)
                             (.setEnvironment opts ^String env)
                             (when rel (.setRelease opts ^String rel))
                             ;; Never send user emails, IP addresses, cookies, or user IDs to Sentry.
                             (.setSendDefaultPii opts false)
                             (doto (.getDataCollection opts)
                               (.forceDataCollection)
                               (.setUserInfo Boolean/FALSE))
                             (.addInAppInclude opts "pyregence")
                             (.addInAppInclude opts "triangulum")))))
                      true))
        (reset! initialized? false)))))

(defn report-exception!
  "Reports throwable to Sentry off-thread when enabled; always returns throwable unchanged."
  ([^Throwable throwable]
   (report-exception! throwable nil))
  ([^Throwable throwable tags]
   (nil-on-error
    (when (Sentry/isEnabled)
      (future
        (nil-on-error
         (if (seq tags)
           (Sentry/captureException throwable
             (reify ScopeCallback
               (run [_ scope]
                 (doseq [[k v] tags :when (some? v)]
                   (.setTag ^IScope scope (name k) (str v))))))
           (Sentry/captureException throwable))))))
   throwable))

(defn wrap-sentry
  "Ring middleware that reports unhandled exceptions to Sentry off-thread and re-throws them.
   Runs zero Sentry code on healthy requests."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable t
        (report-exception! t (nil-on-error
                              (cond-> (select-keys (:session request) [:user-role :subscription-tier])
                                (:request-method request) (assoc :http.method (name (:request-method request)))
                                (:uri request)            (assoc :http.route (:uri request)))))
        (throw t)))))
