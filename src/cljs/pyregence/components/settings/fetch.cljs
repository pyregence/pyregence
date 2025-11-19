(ns pyregence.components.settings.fetch
  (:require
   [clojure.core.async          :refer [<! go]]
   [clojure.edn                 :as edn]
   [pyregence.utils.async-utils :as u-async]))

;; TODO consider making 1 api call to get all the data...
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
