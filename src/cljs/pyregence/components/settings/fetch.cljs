(ns pyregence.components.settings.fetch
  (:require
   [clojure.core.async             :refer [<! go]]
   [clojure.edn                    :as edn]
   [pyregence.components.messaging :refer [toast-message!]]
   [pyregence.utils.async-utils    :as u-async]))

(defn get-users! [user-role]
  (go
    (let [route (if (#{"super_admin" "account_manager"} user-role)
                  "get-all-users"
                  "get-org-member-users")
          resp-chan              (u-async/call-clj-async! route)
          {:keys [body success]} (<! resp-chan)
          users                  (edn/read-string body)]
      (if success users []))))

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

(defn update-own-user-name!
  "Updates the currently logged-in user's own name."
  [new-name]
  (go
    (let [res (<! (u-async/call-clj-async! "update-own-user-name" new-name))]
      (if (:success res)
        (toast-message! (str "Your name has been updated to " new-name "."))
        (toast-message! (:body res))))))

(defn get-user-name!
  []
  (go
    ;; TODO is there a function that just assumes the body succeeds and will pull
    ;; data from it? If so use that here.
    (:user-name
     (edn/read-string
      (:body (<! (u-async/call-clj-async! "get-user-name-by-email")))))))

(defn delete-users!
  [users-to-delete]
  (go (<! (u-async/call-clj-async! "delete-users" users-to-delete))))
