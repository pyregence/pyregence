(ns pyregence.components.settings.roles
  (:require
   [clojure.string :as str]))


(def roles
  ["super_admin"
   "organization_admin"
   "organization_member"
   "account_manager"
   ;;TODO handling member would mean we have to kick them out of
   ;; the organization
   #_"member"])

;; TODO this is the same as the status display, and all db displays probably,
;; consider a generic solution for displaying.
(defn role->display
  [role]
  (->>
   (str/split role #"_")
   (map str/capitalize)
   (str/join " ")))
