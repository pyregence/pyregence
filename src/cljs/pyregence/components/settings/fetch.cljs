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

(defn update-org-user!
  "Updates user identified by their `email` to have the `new-name`. Then makes a toast."
  [email new-name]
  (go
    (let [res (<! (u-async/call-clj-async! "update-user-name" email new-name))]
      (if (:success res)
        ;;TODO toast message probably shouldnt be in this ns
        (toast-message! (str "The user " new-name " with the email " email  " has been updated."))
        (toast-message! (:body res))))))

(defn delete-users!
  [users-to-delete]
  (go (<! (u-async/call-clj-async! "delete-users" users-to-delete))))
