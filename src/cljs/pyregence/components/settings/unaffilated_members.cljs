(ns pyregence.components.settings.unaffilated-members
  (:require
   [pyregence.components.settings.users-table :refer [table-with-buttons]]
   [pyregence.components.settings.utils       :refer [card main-styles]]))

(defn main
  [m]
  [:div {:style main-styles}
   [card {:title    "UNAFFILIATED MEMBERS USER-LIST"
          ;; TODO the `update-roles` won't work here because anything higher then
          ;; `member` needs an organization attached.
          :children [table-with-buttons m]}]])
