(ns firesage.database
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [firesage.logging :refer [log-str]]
            [firesage.views :refer [data-response]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

;;; Helper Functions

(defn kebab->snake [kebab-str]
  (str/replace kebab-str "-" "_"))

(defn format-simple
  "Use any char after % for format."
  [f-str & args]
  (apply format (str/replace f-str #"(%[^ ])" "%s") args))

(defn str-places
  "Creates a string with the pattern '(?, ?), (?, ?)'"
  [rows]
  (str/join ", " (repeat (count rows)
                         (str "("
                              (str/join ", " (repeat (count (first rows)) "?"))
                              ")"))))

(defn pg-partition [fields rows]
  (partition-all (quot 32767 (count fields)) rows))

(def sql-primitive (comp val first first))

;;; Static Data

(def pg-db {:dbtype                "postgresql"
            :dbname                "firesage"
            :user                  "firesage"
            :password              "firesage"
            :reWriteBatchedInserts true})

;;; Select Queries

(defn run-call-sql [use-vec? sql-fn-name & args]
  (let [query           (format-simple "SELECT * FROM %1(%2)"
                                       sql-fn-name (str/join "," (repeat (count args) "?")))
        query-with-args (format-simple "SELECT * FROM %1(%2)"
                                       sql-fn-name (str/join "," (map pr-str args)))]
    (log-str "SQL Call: " query-with-args)
    (jdbc/execute! (jdbc/get-datasource pg-db)
                   (into [query] (map #(condp = (type %)
                                         java.lang.Long (int %)
                                         java.lang.Double (float %)
                                         %)
                                      args))
                   {:builder-fn (if use-vec?
                                  rs/as-unqualified-lower-arrays
                                  rs/as-unqualified-lower-maps)})))

(defn call-sql [sql-fn-name & args]
  (apply run-call-sql false sql-fn-name args))

(defn call-sql-vec [sql-fn-name & args]
  (apply run-call-sql true sql-fn-name args))

(defn sql-handler [{:keys [uri params content-type]}]
  (let [[schema function] (->> (str/split uri #"/")
                               (remove str/blank?)
                               (map kebab->snake)
                               (rest))
        sql-args          (if (= content-type "application/edn")
                            (:sql-args params [])
                            (json/read-str (:sql-args params "[]")))
        sql-result        (apply call-sql (str schema "." function) sql-args)]
    (data-response 200 sql-result (= content-type "application/edn"))))

;;; Insert Queries

(defn for-insert-multi!
  [table cols rows]
  (into [(format-simple "INSERT INTO %1 (%2) VALUES %3"
                        table
                        (str/join ", " (map name cols))
                        (str-places rows))]
        cat
        rows))

(defn insert-rows!
  ([table rows]
   (insert-rows! table rows (keys (first rows))))
  ([table rows fields]
   (let [get-fields (apply juxt fields)]
     (doseq [sm-rows (pg-partition fields rows)]
       (jdbc/execute-one! (jdbc/get-datasource pg-db)
                          (for-insert-multi! table fields (map get-fields sm-rows))
                          {})))))

(defn p-insert-rows! [table rows fields]
  (doall (pmap (fn [row-group] (insert-rows! table row-group fields))
               (pg-partition fields rows))))

;;; Update Queries

(defn for-update-multi!
  [table cols where-col rows]
  (let [col-names  (map name cols)
        where-name (name where-col)
        set-pairs  (->> col-names
                        (remove #(= % where-name))
                        (map #(str % " = b." %))
                        (str/join ", "))
        params     (str/join ", " col-names)]
    (into [(format-simple "UPDATE %1 AS t SET %2 FROM (VALUES %3) AS b (%4) WHERE t.%5 = b.%6"
                          table set-pairs (str-places rows) params where-name where-name)]
          cat
          rows)))

(defn update-rows!
  ([table rows id-key]
   (update-rows! table rows id-key (keys (first rows))))
  ([table rows id-key fields]
   (let [get-fields (apply juxt fields)]
     (doseq [sm-rows (pg-partition fields rows)]
       (jdbc/execute-one! (jdbc/get-datasource pg-db)
                          (for-update-multi! table fields id-key (map get-fields sm-rows))
                          {})))))

(defn p-update-rows! [table rows id-key fields]
  (doall (pmap (fn [row-group] (update-rows! table row-group id-key fields))
               (pg-partition fields rows))))
