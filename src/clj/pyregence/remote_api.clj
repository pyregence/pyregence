(ns pyregence.remote-api
  (:require [clojure.data.json :as json]
            [clojure.repl :refer [demunge]]
            [clojure.string :as str]
            [pyregence.authentication :refer [insert-user
                                              log-in
                                              log-out
                                              set-user-password
                                              user-email-exists
                                              verify-user-email]]
            [pyregence.capabilities :refer [get-capabilities set-capabilities!]]
            [pyregence.email :refer [send-email]]
            [pyregence.logging :refer [log-str]]
            [pyregence.views :refer [data-response]]))

(def name->fn {"get-capabilities"  get-capabilities
               "insert-user"       insert-user
               "log-in"            log-in
               "log-out"           log-out
               "send-email"        send-email
               "set-capabilities"  set-capabilities!
               "set-user-password" set-user-password
               "user-email-exists" user-email-exists
               "verify-user-email" verify-user-email})

(defn fn->sym [f]
  (-> (str f)
      (demunge)
      (str/split #"@")
      (first)
      (symbol)))

(defn clj-handler [{:keys [uri params content-type]}]
  (if-let [function   (->> (str/split uri #"/")
                           (remove str/blank?)
                           (second)
                           (name->fn))]
    (let [clj-args   (if (= content-type "application/edn")
                       (:clj-args params [])
                       (json/read-str (:clj-args params "[]")))
          clj-result (apply function clj-args)]
      (log-str "CLJ Call: " (cons (fn->sym function) clj-args))
      (if (:status clj-result)
        clj-result
        (data-response clj-result {:type (if (= content-type "application/edn") :edn :json)})))
    (data-response "There is no valid function with this name." {:status 400})))
