(ns pyregence.jobs
  (:import java.io.File)
  (:require [clojure.java.io            :as io]
            [clojure.string             :as str]
            [pyregence.capabilities     :refer [set-all-capabilities!]]
            [pyregence.weather-stations :refer [periodically-get-observation-stations-in-the-background!]]
            [triangulum.config          :refer [get-config]]
            [triangulum.logging         :refer [log-str]]))

;; Set Capabilities

(defn start-set-all-capabilities-job! []
  (future
    (log-str "Calling pyregence.capabilities/set-all-capabilities!")
    (set-all-capabilities! nil)))

(defn stop-set-all-capabilities-job! [fut]
  (future-cancel fut))

;; Clean up service

(def ^:private expiration-time "1 hour in msecs" (* 1000 60 60))

(defn- expired? [last-mod-time]
  (let [current-time (System/currentTimeMillis)]
    (> (- current-time last-mod-time) expiration-time)))

(defn- delete-tmp []
  (log-str "Removing temp files")
  (let [tmp-dir (System/getProperty "java.io.tmpdir")
        dirs    (filter (fn [^File file]
                          (and (.isDirectory file)
                               (str/includes? (.getPath file) "pyregence-tmp")
                               (expired? (.lastModified file))))
                        (.listFiles (io/file tmp-dir)))]
    (doseq [dir  dirs
            file (reverse (file-seq dir))]
      (io/delete-file file)))
  (log-str "Temp files removed"))

(defn start-clean-up-service! []
  (log-str "Starting temp file removal service")
  (future
    (while true
      (Thread/sleep expiration-time)
      (try (delete-tmp)
           (catch Exception _)))))

(defn stop-clean-up-service! [future-thread]
  (log-str "Stopping temp file removal service")
  (future-cancel future-thread))

;; Weather Stations

(defn start-get-weather-stations! []
  (future
    (log-str "Calling pyregence.weather-stations/periodically-get-observation-stations-in-the-background!")
    (periodically-get-observation-stations-in-the-background!)))

(defn stop-get-weather-stations! [fut]
  (future-cancel fut))
