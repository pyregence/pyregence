(ns pyregence.components.settings.fetch
  (:require
   [clojure.core.async             :refer [<! go]]
   [clojure.edn                    :as edn]
   [pyregence.components.messaging :refer [toast-message!]]
   [pyregence.utils.async-utils    :as u-async]))

(defn get-users! [user-role]
  (go
    (let [route (if (= user-role "super_admin") "get-all-users" "get-org-member-users")
          resp-chan              (u-async/call-clj-async! route)
          {:keys [body success]} (<! resp-chan)
          users                  (edn/read-string body)]
      (if success users []))))

(defn get-orgs!
  [user-role]
  (go
    (let [api-route (if (= user-role "super_admin")
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

(defn get-user-name!
  []
  (go
    ;; TODO is there a function that just assumes the body succeeds and will pull
    ;; data from it? If so use that here.
    (:user-name
     (edn/read-string
      (:body (<! (u-async/call-clj-async! "get-user-name-by-email")))))))
