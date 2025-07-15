(ns pyregence.analytics
  (:require
   [clojure.data.csv    :as csv]
   [semantic-csv.core   :as sc]
   [triangulum.database :refer [call-sql]])
  (:import
   [java.io StringWriter]))

(defn get-all-users-last-login-dates
  [_]
  (->> (with-open [rows (StringWriter.)]
         (->> "get_all_users_last_login_dates"
              call-sql
              sc/vectorize
              (csv/write-csv rows))
         (str rows))
       (assoc {:status  200 :headers {"Content-Type" "text/csv"}} :body)))
