(ns pyregence.remote-api
  (:require [clojure.data.json :as json]
            [clojure.repl :refer [demunge]]
            [clojure.string :as str]
            [triangulum.database :refer [call-sql]]
            [triangulum.logging  :refer [log-str]]
            [triangulum.utils    :refer [kebab->snake data-response]]
            [pyregence.authentication :refer [add-new-user
                                              add-org-user
                                              get-org-list
                                              get-org-users-list
                                              get-user-info
                                              log-in
                                              log-out
                                              remove-org-user
                                              set-user-password
                                              user-email-taken
                                              update-org-info
                                              update-org-user-role
                                              update-user-info
                                              verify-user-email]]
            [pyregence.capabilities   :refer [get-fire-names
                                              get-layers
                                              get-layer-name
                                              get-user-layers
                                              set-capabilities!
                                              remove-workspace!]]
            [pyregence.email          :refer [send-email]]))

(def name->fn {"add-org-user"         add-org-user
               "add-new-user"         add-new-user
               "get-fire-names"       get-fire-names
               "get-layers"           get-layers
               "get-layer-name"       get-layer-name
               "get-user-layers"      get-user-layers
               "get-org-list"         get-org-list
               "get-org-users-list"   get-org-users-list
               "get-user-info"        get-user-info
               "log-in"               log-in
               "log-out"              log-out
               "remove-org-user"      remove-org-user
               "send-email"           send-email
               "set-capabilities"     set-capabilities!
               "set-user-password"    set-user-password
               "remove-workspace"     remove-workspace!
               "user-email-taken"     user-email-taken
               "update-org-info"      update-org-info
               "update-org-user-role" update-org-user-role
               "update-user-info"     update-user-info
               "verify-user-email"    verify-user-email})

(defn fn->sym [f]
  (-> (str f)
      (demunge)
      (str/split #"@")
      (first)
      (symbol)))

(defn clj-handler [{:keys [uri params content-type]}]
  (if-let [function (->> (str/split uri #"/")
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

(defn sql-handler [{:keys [uri params content-type]}]
  (let [[schema function] (->> (str/split uri #"/")
                               (remove str/blank?)
                               (map kebab->snake)
                               (rest))
        sql-args          (if (= content-type "application/edn")
                            (:sql-args params [])
                            (json/read-str (:sql-args params "[]")))
        sql-result        (apply call-sql (str schema "." function) sql-args)]
    (data-response sql-result {:type (if (= content-type "application/edn") :edn :json)})))
