(ns pyregence.jobs
  (:import java.io.File)
  (:require [clojure.java.io        :as io]
            [clojure.string         :as str]
            [runway.simple-sockets  :as runway]
            [triangulum.config      :refer [get-config]]
            [triangulum.logging     :refer [log-str]]
            [pyregence.capabilities :refer [set-all-capabilities!]]
            [pyregence.match-drop   :refer [match-drop-server-msg-handler]]))

;; Set Capabilities

(defn set-all-capabilties-job! []
  (log-str "Calling pyregence.capabilities/set-all-capabilities!")
  (set-all-capabilities!))

;; Clean up service

(def ^:private expiration-time "1 hour in msecs" (* 1000 60 60))

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

(defn start-clean-up-service! []
  (log-str "Starting temp file removal service.")
  (future
    (while true
      (Thread/sleep expiration-time)
      (try (delete-tmp)
           (catch Exception _)))))

(defn stop-clean-up-service! [future-thread]
  (future-cancel future-thread))

;; Match Drop server

(defn start-match-drop-server! []
  (when (get-config :triangulum.views/client-keys :features :match-drop)
    (log-str "Starting Match Drop server on port " (get-config :pyregence.match-drop/match-drop :app-port))
    (runway/start-server! (get-config :pyregence.match-drop/match-drop :app-port)
                          match-drop-server-msg-handler)))

(defn stop-match-drop-server! [_]
  (runway/stop-server!))
