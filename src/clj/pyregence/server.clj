(ns pyregence.server
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [pyregence.handler :refer [development-app production-app]]
            [pyregence.logging :refer [log-str]]
            [ring.adapter.jetty :refer [run-jetty]]))

(defonce server           (atom nil))
(defonce clean-up-service (atom nil))

(def expiration-time "1 hour in msecs" (* 1000 60 60))

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

(defn start-server! [& [port mode]]
  (let [mode     (or mode "prod")
        has-key? (.exists (io/file "./.key/keystore.pkcs12"))
        handler  (if (= mode "prod")
                   #'production-app
                   #'development-app)
        config   (merge
                  {:port  (cond
                            (integer? port) port
                            (string? port)  (Integer/parseInt port)
                            (nil? port)     8080
                            :else           8080)
                   :join? false}
                  (when (and has-key? (= mode "prod"))
                    {:ssl?          true
                     :ssl-port      8443
                     :keystore      "./.key/keystore.pkcs12"
                     :keystore-type "pkcs12"
                     :key-password  "foobar"}))]
    (if (and (not has-key?) (= mode "prod"))
      (println "ERROR: there is no SSL key for enabling HTTPS! Create a SSL key for HTTPS or run with the \"dev\" option.")
      (do (reset! server (run-jetty handler config))
          (reset! clean-up-service (start-clean-up-service!))))))

(defn stop-server! []
  (when @clean-up-service
    (future-cancel @clean-up-service)
    (reset! clean-up-service nil))
  (when @server
    (.stop @server)
    (reset! server nil)))

(def -main start-server!)
