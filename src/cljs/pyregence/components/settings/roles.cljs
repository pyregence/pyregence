(ns pyregence.components.settings.roles)

(def roles
  ["super_admin"
   "organization_admin"
   "organization_member"
   "account_manager"
   ;;TODO handling member would mean we have to kick them out of
   ;; the organization
   #_"member"])

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
