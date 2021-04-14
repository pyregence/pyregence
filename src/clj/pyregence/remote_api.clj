(ns pyregence.remote-api
  (:require [clojure.data.json :as json]
            [clojure.repl :refer [demunge]]
            [clojure.string :as str]
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
            [pyregence.capabilities   :refer [get-fire-names
                                              get-layers
                                              get-layer-name
                                              get-user-layers
                                              set-capabilities!
                                              remove-workspace!]]
            [pyregence.match-drop     :refer [initiate-md! get-md-status]]
            [pyregence.email          :refer [send-email]]
            [pyregence.logging        :refer [log-str]]
            [pyregence.views          :refer [data-response]]))

(def name->fn {"add-org-user"         add-org-user
               "add-new-user"         add-new-user
               "get-cameras"          get-cameras
               "get-current-image"    get-current-image
               "get-fire-names"       get-fire-names
               "get-layers"           get-layers
               "get-layer-name"       get-layer-name
               "get-md-status"        get-md-status
               "get-org-list"         get-org-list
               "get-org-users-list"   get-org-users-list
               "get-user-info"        get-user-info
               "get-user-layers"      get-user-layers
               "initiate-md"          initiate-md!
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
