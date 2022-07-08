(ns pyregence.remote-api
  (:require [clojure.data.json :as json]
            [clojure.repl      :refer [demunge]]
            [clojure.string    :as str]
            [triangulum.database :refer [call-sql]]
            [triangulum.logging  :refer [log-str]]
            [triangulum.utils    :refer [kebab->snake]]
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
            [pyregence.cameras        :refer [get-cameras
                                              get-current-image]]
            [pyregence.capabilities   :refer [get-all-layers
                                              get-fire-names
                                              get-layers
                                              get-layer-name
                                              get-user-layers
                                              set-capabilities!
                                              set-all-capabilities!
                                              remove-workspace!]]
            [pyregence.match-drop     :refer [initiate-md! get-md-status get-match-drops]]
            [pyregence.red-flag       :refer [get-red-flag-layer]]
            [pyregence.email          :refer [send-email]]
            [pyregence.views          :refer [data-response]]))

(def name->fn {"add-org-user"         add-org-user
               "add-new-user"         add-new-user
               "get-all-layers"       get-all-layers
               "get-cameras"          get-cameras
               "get-current-image"    get-current-image
               "get-fire-names"       get-fire-names
               "get-layers"           get-layers
               "get-layer-name"       get-layer-name
               "get-match-drops"      get-match-drops
               "get-md-status"        get-md-status
               "get-org-list"         get-org-list
               "get-org-users-list"   get-org-users-list
               "get-user-info"        get-user-info
               "get-user-layers"      get-user-layers
               "get-red-flag-layer"   get-red-flag-layer
               "initiate-md"          initiate-md!
               "log-in"               log-in
               "log-out"              log-out
               "remove-org-user"      remove-org-user
               "send-email"           send-email
               "set-capabilities"     set-capabilities!
               "set-all-capabilities" set-all-capabilities!
               "set-user-password"    set-user-password
               "remove-workspace"     remove-workspace!
               "user-email-taken"     user-email-taken
               "update-org-info"      update-org-info
               "update-org-user-role" update-org-user-role
               "update-user-info"     update-user-info
               "verify-user-email"    verify-user-email})

(defn- fn->sym [f]
  (-> (str f)
      (demunge)
      (str/split #"@")
      (first)
      (symbol)))

;;; Handlers

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
