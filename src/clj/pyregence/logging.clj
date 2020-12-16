(ns pyregence.logging
  (:import java.text.SimpleDateFormat
           java.util.Date)
  (:require [clojure.java.io :as io]
            [clojure.pprint  :as pp]
            [clojure.string  :as str]))

(defonce synchronized-log-writer (agent nil))

(defonce output-path (atom ""))

(defn max-length [string length]
  (subs string 0 (min length (count string))))

(defn log [data & {:keys [newline? pprint?] :or {newline? true pprint? false}}]
  (let [timestamp    (.format (SimpleDateFormat. "MM/dd HH:mm:ss") (Date.))
        log-filename (str (.format (SimpleDateFormat. "YYYY-MM-dd") (Date.)) ".log")
        max-data     (max-length data 500)
        line         (str timestamp
                          " "
                          (if pprint? (pp/pprint max-data) max-data)
                          (when newline? "\n"))]
    (send-off synchronized-log-writer
              (if (pos? (count @output-path))
                (fn [_] (spit (str @output-path log-filename) line :append true))
                (fn [_] (print line)))))
  nil)

(defn log-str [& data]
  (log (apply str data)))

(defn set-output-path! [path]
  (let [path (str path (when-not (str/ends-with? path "/") "/"))]
    (try
      (io/make-parents (str path "dummy.log"))
      (log-str "Logging to: " path)
      (reset! output-path path)
      (catch Exception _
        (log-str "Error setting log path to " path ". Check that you supplied a valid path.")))))
