(ns pyregence.components.settings.unaffilated-members
  (:require
   [pyregence.components.settings.roles :as roles]
   [pyregence.components.settings.users-table :refer [table-with-buttons]]
   [pyregence.components.utils                :refer [card main-styles]]))

(defn main
  [{:keys [user-role]}]
  [:div {:style main-styles}
   [card {:title    "UNAFFILIATED MEMBERS USER-LIST"
          ;; TODO the `update-roles` won't work here because anything higher then
          ;; `member` needs an organization attached.
          :children [table-with-buttons
                     (let [roles (->> user-role roles/role->roles-below (filter roles/none-organization-roles))]
                       {:users-filter                (fn [{:keys [user-role]}]
                                                       (#{"member" "none" "super_admin" "account_manager"} user-role))
                        :default-role-option         (first roles)
                        :role-options                roles
                        :user-role                   user-role
                        :statuses                    ["none"]})]}]])
