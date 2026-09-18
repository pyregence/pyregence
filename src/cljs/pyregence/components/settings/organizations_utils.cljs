(ns pyregence.components.settings.organizations-utils
  (:require
   [cljs.reader                 :as edn]
   [clojure.core.async          :refer [<! go]]
   [clojure.string              :as str]
   [pyregence.utils.async-utils :as u-async]))

(defn get-orgs!
  "The same two routes `pyregence.archetypes.directory.over-the-wire` drives,
   and deliberately not behind that protocol yet.

   The directory answers what the forecast page needs -- an id, a name and a
   credential. The settings pages administer organizations rather than read
   them, so they also want :org-uuid, :email-domains, :auto-accept? and
   :auto-add?, and they write back. Widening the directory to carry all of that
   would make one concept out of two: what a member is told they belong to, and
   what an account manager may edit.

   The `{}` below is the conflation that seam exists to remove: a session
   PyreCast has stopped honouring reads here as an account manager who
   administers nothing."
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

(defn orgs->org->uuid
  [orgs]
  (reduce
   (fn [org-uuid->org {:keys [org-uuid org-name email-domains auto-accept? auto-add?] :as org}]
     (assoc org-uuid->org org-uuid
            (assoc org
                   :unsaved-auto-accept? auto-accept?
                   :unsaved-auto-add?    auto-add?
                   :unsaved-org-name     org-name
                   ;; NOTE this mapping is used to keep track of the email.
                   ;; Org domains always start with an @ (e.g. @example.com), so
                   ;; the @ is kept rather than stripped and re-added on save.
                   :og-email->email (reduce
                                     (fn [m e]
                                       (assoc m e {:email e :unsaved-email e}))
                                     {}
                                     (str/split email-domains #",")))))
   {}
   orgs))
