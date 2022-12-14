(ns pyregence.utils
  (:import  [java.util TimeZone]
            [java.text SimpleDateFormat])
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

(defn get-email-by-user-id [user-id]
  (if-let [email (sql-primitive (call-sql "get_email_by_user_id" user-id))]
    (data-response email)
    (data-response (str "There is no user with the id " user-id)
                   {:status 403})))
