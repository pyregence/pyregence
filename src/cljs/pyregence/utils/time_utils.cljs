(ns pyregence.utils.time-utils
  (:require [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility Functions - Time Processing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn pad-zero
  "Adds a zero in front of a given number if it's a single digit (eg. turn 2 into 02)."
  [num]
  (let [num-str (str num)]
    (if (= 2 (count num-str))
      num-str
      (str "0" num-str))))

(defn get-time-zone
  "Returns the string code for the local timezone."
  [js-date]
  (-> js-date
      (.toLocaleTimeString "en-us" #js {:timeZoneName "short"})
      (str/split " ")
      (peek)))

(defn get-date-from-js
  "Formats the date portion of JS Date as the date portion of an ISO string."
  [js-date show-utc?]
  (if show-utc?
    (subs (.toISOString js-date) 0 10)
    (str (.getFullYear js-date)
         "-"
         (pad-zero (+ 1 (.getMonth js-date)))
         "-"
         (pad-zero (.getDate js-date)))))

(defn get-time-from-js
  "Formats the time portion of JS Date as the time portion of an ISO string."
  [js-date show-utc?]
  (if show-utc?
    (str (subs (.toISOString js-date) 11 16) " UTC")
    (str (pad-zero (.getHours js-date))
         ":"
         (pad-zero (.getMinutes js-date))
         " "
         (get-time-zone js-date))))

(defn- model-format->js-format
  "Formats date string given from GeoServer to one that can be used by JS Date."
  [date-str]
  (let [minutes (subs date-str 11 13)]
    (str (subs date-str 0 4) "-"
         (subs date-str 4 6) "-"
         (subs date-str 6 8) "T"
         (subs date-str 9 11) ":"
         (if (= 2 (count minutes)) minutes "00")
         ":00.000Z")))

(defn js-date-from-string
  "Converts a date string to a JS Date object."
  [date-str]
  (js/Date. (if (re-matches #"\d{8}_\d{2,6}" date-str)
              (model-format->js-format date-str)
              date-str)))

(defn js-date->iso-string
  "Returns a ISO date-time string for a given JS date object in local or UTC timezone."
  [js-date show-utc?]
  (str (get-date-from-js js-date show-utc?) " " (get-time-from-js js-date show-utc?)))

(defn date-string->iso-string
  "Returns a ISO date-time string for a given date string in local or UTC timezone."
  [date-str show-utc?]
  (js-date->iso-string (js-date-from-string date-str) show-utc?))

(defn iso-string->local-datetime-string
  "Converts an ISO date string to a local datetime string. Note that this will
   use the local time zone of the caller of the function at the time of calling
   (as determined by JavaScript). e.g. '2021-05-27T18:00Z' is converted to
   '2021-05-27T14:00' for a caller in the EDT time zone."
  [iso-string]
  (let [js-date (js/Date. iso-string)
        year    (.getFullYear js-date)
        month   (pad-zero (+ 1 (.getMonth js-date)))
        day     (pad-zero (.getDate js-date))
        hours   (pad-zero (.getHours js-date))
        minutes (pad-zero (.getMinutes js-date))]
    (str year "-" month "-" day "T" hours ":" minutes)))

(defn get-current-local-datetime-string
  "Returns a local-datetime string such as '2023-12-02T16:22' based on the
   current date."
  []
  (-> (js/Date.)
      (.toISOString)
      (iso-string->local-datetime-string)))

(defn ms->hhmmss
  "Converts milliseconds into 'hours:minutes:seconds'."
  [ms]
  (let [sec     (/ ms 1000)
        hours   (js/Math.round (/ sec 3600))
        minutes (js/Math.round (/ (mod sec 3600) 60))
        seconds (js/Math.round (mod (mod sec 3600) 60))]
    (str (pad-zero hours)
         ":"
         (pad-zero minutes)
         ":"
         (pad-zero seconds))))

(defn ms->hr
  "Converts milliseconds to hours."
  [ms]
  (/ ms (* 1000 60 60)))

(defn current-date-ms
  "Returns the current date in milliseconds, with hour/minute/seconds/ms set to 0"
  []
  (-> (js/Date.)
      (.setHours 0 0 0 0)))

(defn current-timezone-shortcode
  "Returns the shortcode for the current timezone (e.g. PDT, EST)"
  []
  (-> (js/Date.)
      (.toLocaleTimeString "en-us" #js{:timeZoneName "short"})
      (.split " ")
      (last)))

(defn format-date
  "Formats a JS Date into MM/DD/YYYY"
  [js-date]
  (str (+ 1 (.getMonth js-date)) "/" (.getDate js-date) "/" (.getFullYear js-date)))

(defn alert-wf-camera-time->js-date
  "Converts a time from the Alert Wildfire cameras API (YYYY-MM-DD HH:MM:SS.MFS) into a JS Date in UTC."
  [camera-time]
  (js/Date. (as-> camera-time %
              (str/split % #" ")
              (interpose "T" %)
              (concat % "Z")
              (apply str %))))

(defn get-time-difference
  "Returns the difference in milliseconds between a JS Date object and the current time.
   Optionally returns the difference between two different JS Date objects."
  [js-date & [js-date-opt]]
  (if js-date-opt
    (- (.getTime js-date) (.getTime js-date-opt))
    (- (.getTime (js/Date.)) (.getTime js-date))))
