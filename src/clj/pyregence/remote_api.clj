(ns pyregence.remote-api
  (:require [clojure.data.json :as json]
            [clojure.repl :refer [demunge]]
            [clojure.string :as str]
            [pyregence.capabilities :refer [get-capabilities set-capabilities!]]
            [pyregence.email :refer [send-email]]
            [pyregence.logging :refer [log-str]]
            [pyregence.views :refer [data-response]]))

(def name->fn {"send-email"       send-email
               "get-capabilities" get-capabilities
               "set-capabilities" set-capabilities!})

(defn fn->sym [f]
  (-> (str f)
      (demunge)
      (str/split #"@")
      (first)
      (symbol)))

(defn clj-handler [{:keys [uri params content-type]}]
  (let [function   (->> (str/split uri #"/")
                        (remove str/blank?)
                        (second)
                        (name->fn))
        clj-args   (if (= content-type "application/edn")
                     (:clj-args params [])
                     (json/read-str (:clj-args params "[]")))
        clj-result (apply function clj-args)]
    (log-str "CLJ Call: " (cons (fn->sym function) clj-args))
    (if (:status clj-result)
      clj-result
      (data-response {:body clj-result :type "application/edn"}))))
