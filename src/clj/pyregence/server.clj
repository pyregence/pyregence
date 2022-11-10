(ns pyregence.server
  (:import java.io.File)
  (:require [clojure.java.io        :as io]
            [clojure.string         :as str]
            [clojure.core.server    :refer [start-server]]
            [ring.adapter.jetty     :refer [run-jetty]]
            [triangulum.cli         :refer [get-cli-options]]
            [triangulum.config      :refer [get-config]]
            [triangulum.notify      :as notify]
            [triangulum.logging     :refer [log-str set-log-path!]]
            [triangulum.sockets     :refer [start-socket-server! stop-socket-server! socket-open? send-to-server!]]
            [pyregence.capabilities :refer [set-all-capabilities!]]
            [pyregence.handler      :refer [create-handler-stack]]
            [pyregence.match-drop   :refer [process-message]]))

(defonce server           (atom nil))
(defonce repl-server      (atom nil))
(defonce clean-up-service (atom nil))

(def ^:private expiration-time "1 hour in msecs" (* 1000 60 60))
(def ^:private keystore-scan-interval 60)

(defn- expired? [last-mod-time]
  (let [current-time (System/currentTimeMillis)]
    (> (- current-time last-mod-time) expiration-time)))

(defn- delete-tmp []
  (log-str "Removing temp files.")
  (let [tmp-dir (System/getProperty "java.io.tmpdir")
        dirs    (filter (fn [^File file]
                          (and (.isDirectory file)
                               (str/includes? (.getPath file) "pyregence-tmp")
                               (expired? (.lastModified file))))
                        (.listFiles (io/file tmp-dir)))]
    (doseq [dir  dirs
            file (reverse (file-seq dir))]
      (io/delete-file file))))

(defn- start-clean-up-service! []
  (log-str "Starting temp file removal service.")
  (future
    (while true
      (Thread/sleep expiration-time)
      (try (delete-tmp)
           (catch Exception _)))))

(def ^:private cli-options
  {:http-port  ["-p" "--http-port PORT"  "Port for http, default 8080"
                :parse-fn #(if (int? %) % (Integer/parseInt %))]
   :https-port ["-P" "--https-port PORT" "Port for https (e.g. 8443)"
                :parse-fn #(if (int? %) % (Integer/parseInt %))]
   :repl       ["-r" "--repl" "Starts a REPL server on port 5555"
                :default false]
   :mode       ["-m" "--mode MODE" "Production (prod) or development (dev) mode, default prod"
                :default "prod"
                :validate [#{"prod" "dev"} "Must be \"prod\" or \"dev\""]]
   :log-dir    ["-l" "--log-dir DIR" "Directory for log files. When a directory is not provided, output will be to stdout."
                :default ""]})

(def ^:private cli-actions
  {:start  {:description "Starts the server."
            :requires    [:http-port]}
   :stop   {:description "Stops the server."}
   :reload {:description "Reloads a running server."}})

(defn start-server! [{:keys [https-port http-port mode log-dir repl]}]
  (let [has-key?   (.exists (io/file "./.key/keystore.pkcs12"))
        ssl?       (and has-key? https-port)
        handler    (create-handler-stack ssl? (= mode "dev"))
        config     (merge
                     {:port  http-port
                      :join? false}
                     (when ssl?
                       {:ssl?          true
                        :ssl-port      https-port
                        :keystore      "./.key/keystore.pkcs12"
                        :keystore-type "pkcs12"
                        :keystore-scan-interval keystore-scan-interval
                        :key-password  "foobar"}))]
    (if (and (not has-key?) https-port)
      (println "ERROR:\n"
               "  An SSL key is required if an HTTPS port is specified.\n"
               "  Create an SSL key for HTTPS or run without the --https-port (-P) option.")
      (do
        (when repl
          (println "Starting REPL server on port 5555")
          (reset! repl-server (start-server {:name :pyr-repl :port 5555 :accept 'clojure.core.server/repl})))
        (reset! server (run-jetty handler config))
        (reset! clean-up-service (start-clean-up-service!))
        (set-log-path! log-dir)
        ;; TODO: Improve by pulling port from config.edn > :match-drop > :app-port
        (start-socket-server! 31337 process-message)
        (when (notify/available?) (notify/ready!))
        (set-all-capabilities!)))))

(defn stop-server! []
  (set-log-path! "")
  (stop-socket-server!)
  (when @clean-up-service
    (future-cancel @clean-up-service)
    (reset! clean-up-service nil))
  (when @server
    (.stop @server)
    (reset! server nil)))

(defn send-to-repl-server! [msg & {:keys [host port] :or {host "127.0.0.1" port 5555}}]
  (if (socket-open? host port)
    (do (send-to-server! host port msg)
        (System/exit 0))
    (do (println (format "Unable to connect to REPL server at %s:%s. Restart the server with the '-r/--repl' flag." host port))
        (System/exit 1))))

(defn stop-running-server! []
  (send-to-repl-server! "(do (require '[pyregence.server :as server]) (server/stop-server!))"))

(defn reload-running-server! []
  (send-to-repl-server! "(require 'pyregence.server :reload-all)"))

(defn -main [& args]
  (let [{:keys [action options]} (get-cli-options args
                                                  cli-options
                                                  cli-actions
                                                  "server"
                                                  (get-config :server))]
    (case action
      :start  (start-server! options)
      :stop   (stop-running-server!)
      :reload (reload-running-server!)
      nil)))
