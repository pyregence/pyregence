(ns pyregence.components.settings.fetch
  (:require
   [clojure.core.async             :refer [<! go]]
   [clojure.edn                    :as edn]
   [clojure.set :as set]
   [pyregence.components.messaging :refer [toast-message!]]
   [pyregence.utils.async-utils    :as u-async]))

(defn get-users! [user-role]
  (go
    (let [admin? (#{"super_admin" "account_manager"} user-role)
          route (if admin?
                  "get-all-users"
                  "get-org-member-users")
          resp-chan              (u-async/call-clj-async! route)
          {:keys [body]} (<! resp-chan)]
      (map #(set/rename-keys % {:full-name :name :membership-status :org-membership-status}) (edn/read-string body)))))

(defn get-orgs!
  [user-role]
  (go
    (let [api-route (if (#{"super_admin" "account_manager"} user-role)
                      "get-all-organizations"
                      "get-current-user-organization")
          response  (<! (u-async/call-clj-async! api-route))]
      (if (:success response)
        (->> (:body response)
             (edn/read-string))
        {}))))

(defn delete-users!
  [users-to-delete]
  (go (<! (u-async/call-clj-async! "delete-users" users-to-delete))))
