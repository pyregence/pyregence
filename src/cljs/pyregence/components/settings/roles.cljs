(ns pyregence.components.settings.roles
  (:require
   [clojure.string :as str]))

;;TODO maybe the roles should be reversed? (super_admin = 6)
;;TODO consider having this replace the roles var above
(def role->rank
  {"super_admin" 1
   "account_manager" 2
   "organization_admin" 3
   "organization_member" 4
   "member" 5
   "none" 6})

(defn role->roles-below
  "returns roles below and including"
  [role]
  (let [r (role->rank role)]
    (reduce-kv
     (fn [roles role rank]
       (if (<= r rank)
         (conj roles role)
         roles))
     []
     role->rank)))

(def organization-roles #{"organization_admin" "organization_member"})
;;NOTE this isnt all the non-organization-role-options
(def none-organization-roles #{"super_admin" "account_manager" "member"})

(defn type->label
  [role-type]
  (->> (str/split (str role-type) #"_")
       (map str/capitalize)
       (str/join " ")))
