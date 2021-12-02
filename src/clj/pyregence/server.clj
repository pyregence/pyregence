(ns pyregence.server
  (:require [clojure.java.io        :as io]
            [clojure.string         :as str]
            [clojure.tools.cli      :refer [parse-opts]]
            [ring.adapter.jetty     :refer [run-jetty]]
            [pyregence.capabilities :refer [set-all-capabilities!]]
            [pyregence.handler      :refer [create-handler-stack]]
            [pyregence.logging      :refer [log-str set-log-path!]]
            [pyregence.match-drop   :refer [process-message]]
            [pyregence.sockets      :refer [start-socket-server! stop-socket-server!]]))

(defonce server           (atom nil))
(defonce clean-up-service (atom nil))

(def ^:private expiration-time "1 hour in msecs" (* 1000 60 60))
(def ^:private keystore-scan-interval 60)

(defn- expired? [last-mod-time]
  (let [current-time (System/currentTimeMillis)]
    (> (- current-time last-mod-time) expiration-time)))

(defn- delete-tmp []
  (log-str "Removing temp files.")
  (let [tmp-dir (System/getProperty "java.io.tmpdir")
        dirs    (filter #(and (.isDirectory %)
                              (str/includes? (.getPath %) "pyregence-tmp")
                              (expired? (.lastModified %)))
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

(def cli-options
  [["-p" "--http-port PORT" "Port for http, default 8080"
    :default  8080
    :parse-fn #(if (int? %) % (Integer/parseInt %))
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-P" "--https-port PORT" "Port for https (e.g. 8443)"
    :parse-fn #(if (int? %) % (Integer/parseInt %))
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-m" "--mode MODE" "Production (prod) or development (dev) mode, default prod"
    :default  "prod"
    :validate [#{"prod" "dev"} "Must be \"prod\" or \"dev\""]]
   ["-o" "--output-dir DIR" "Output directory for log files. When a directory is not provided, output will be to stdout."
    :default ""]])

(defn start-server! [& args]
  (let [{:keys [options summary errors]} (parse-opts args cli-options)
        {:keys [http-port https-port mode output-dir]} options]
    (if (seq errors)
      (do
        (run! println errors)
        (println (str "Usage:\n" summary)))
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
            (reset! server (run-jetty handler config))
            (reset! clean-up-service (start-clean-up-service!))
            (set-log-path! output-dir)
            (start-socket-server! 31337 process-message)
            (set-all-capabilities!)))))))

(defn stop-server! []
  (set-log-path! "")
  (stop-socket-server!)
  (when @clean-up-service
    (future-cancel @clean-up-service)
    (reset! clean-up-service nil))
  (when @server
    (.stop @server)
    (reset! server nil)))

(def -main start-server!)
