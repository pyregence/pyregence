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

(defn orgs->org-unique-id->org
  ;; Keyed by org-unique-id: the numeric org id is no longer sent to the browser
  ;; (PYR1-1512), so org-unique-id is the org identifier client-side.
  [orgs]
  (reduce
   (fn [org-unique-id->org {:keys [org-unique-id org-name email-domains auto-accept? auto-add?] :as org}]
     (assoc org-unique-id->org org-unique-id
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
