(ns pyregence.utils
  (:import  [java.util TimeZone]
            [java.text SimpleDateFormat]
            [java.time LocalDateTime ZonedDateTime]
            [java.time.format DateTimeFormatter]
            [java.time.temporal ChronoUnit])
  (:require [triangulum.database :refer [call-sql sql-primitive]]
            [pyregence.views     :refer [data-response]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro nil-on-error
  "A macro that encloses the given `body` in a try-catch that returns `nil` on exception."
  [& body]
  (let [_ (gensym)]
    `(try ~@body (catch Exception ~_ nil))))

(defn convert-date-string
  "Converts a date str in the format of '2022-12-01 18:00 UTC' to '20221201_180000'."
  [date-str]
  (let [in-format  (SimpleDateFormat. "yyyy-MM-dd HH:mm z")
        out-format (doto (SimpleDateFormat. "yyyyMMdd_HHmmss")
                     (.setTimeZone (TimeZone/getTimeZone "UTC")))]
    (->> date-str
         (.parse in-format)
         (.format out-format))))

(defn dir-name-string->local-date-time
  "Converts a string in the format '20221201_180000' to a Java LocalDateTime object."
  [date]
  (let [year    (Integer/parseInt (subs date 0 4))
        month   (Integer/parseInt (subs date 4 6))
        day     (Integer/parseInt (subs date 6 8))
        hours   (Integer/parseInt (subs date 9 11))
        minutes (Integer/parseInt (subs date 11 13))
        seconds (Integer/parseInt (subs date 13 15))]
    (LocalDateTime/of year month day hours minutes seconds)))

(defn pad-zero
  "Converts a number to a string and adds a leading zero, if necessary."
  [num]
  (if (< num 10) (str "0" num) (str num)))

(defn round-down-to-nearest-hour
  "Rounds down a date string in the format '20221201_185900' to the nearest hour,
   returning a date string (e.g. '2022-12-01 18:00 UTC')."
  [date-str]
  (let [local-date-time   (dir-name-string->local-date-time date-str)
        initial-minutes   (.getMinute local-date-time)
        minutes-remainder (mod initial-minutes 60)
        rounded-date-time (.minusMinutes local-date-time minutes-remainder)
        year              (.getYear rounded-date-time)
        month             (.getMonthValue rounded-date-time)
        day               (.getDayOfMonth rounded-date-time)
        hour              (.getHour rounded-date-time)
        minute            (.getMinute rounded-date-time)]
    (str year "-" (pad-zero month) "-" (pad-zero day) " "
         (pad-zero hour) ":" (pad-zero minute) " UTC")))

(defn date-24-hours-behind
  "Subtracts 24 hours from a date string in the format '20221201_185900'
   and then rounds down to the nearest day, returning a directory name date string
   (e.g. '20221130')."
  [date-str]
  (let [local-date-time (dir-name-string->local-date-time date-str)
        sub-date        (.minusDays local-date-time 1)
        year            (.getYear sub-date)
        month           (.getMonthValue sub-date)
        day             (.getDayOfMonth sub-date)]
    (str year (pad-zero month) (pad-zero day))))

(defn get-current-date-time-iso-string
  "Returns the current date and time as an ISO string rounded down to the nearest
   hour. e.g. \"2023-06-07T15:00Z\""
  []
  (let [current-datetime (ZonedDateTime/now)
        rounded-datetime (-> current-datetime
                             (.truncatedTo ChronoUnit/HOURS))]
    (.format (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm'Z'")
             rounded-datetime)))

(defn get-email-by-user-id [user-id]
  (if-let [email (sql-primitive (call-sql "get_email_by_user_id" user-id))]
    (data-response email)
    (data-response (str "There is no user with the id " user-id)
                   {:status 403})))
