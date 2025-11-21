(ns pyregence.components.settings.roles)

(def roles
  ["super_admin"
   "organization_admin"
   "organization_member"
   "account_manager"
   ;;TODO handling member would mean we have to kick them out of
   ;; the organization
   #_"member"])
