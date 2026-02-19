(ns pyregence.components.settings.organizations-utils
  (:require
   [cljs.reader                 :as edn]
   [clojure.core.async          :refer [<! go]]
   [clojure.string              :as str]
   [pyregence.utils.async-utils :as u-async]))

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

(defn orgs->org->id
  [orgs]
  (reduce
   (fn [org-id->org {:keys [org-id org-name email-domains auto-accept? auto-add?] :as org}]
     (assoc org-id->org org-id
            (assoc org
                   :unsaved-auto-accept? auto-accept?
                   :unsaved-auto-add?    auto-add?
                   :unsaved-org-name     org-name
                   ;; NOTE this mapping is used to keep track of the email
                   :og-email->email (reduce
                                     (fn [m e]
                                       (assoc m e {:email e :unsaved-email e}))
                                     {}
                                     (->>
                                      (str/split email-domains #",")
                                      (map #(str/replace % "@" "")))))))
   {}
   orgs))
