(ns pyregence.logging
  (:import java.text.SimpleDateFormat
           java.util.Date)
  (:require [clojure.pprint :as pp]))

(defonce synchronized-log-writer (agent nil))

(defn max-length [str max-length]
  (subs str 0 (min max-length (count str))))

(defn log [data & {:keys [newline pprint] :or {newline true pprint false}}]
  (let [timestamp (.format (SimpleDateFormat. "MM/dd HH:mm:ss") (Date.))]
    (send-off synchronized-log-writer
              (cond pprint  (fn [_] (print (str timestamp " ")) (pp/pprint (max-length data 1000)))
                    newline (fn [_] (println timestamp (max-length data 1000)))
                    :else   (fn [_] (print timestamp (max-length data 1000))))))
  nil)

(defn log-str [& data]
  (log (apply str data)))
