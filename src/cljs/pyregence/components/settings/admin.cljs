(ns pyregence.components.settings.admin
  (:require
   [pyregence.components.settings.users-table :refer [table-with-buttons]]
   [pyregence.components.utils       :refer [card main-styles]]))

(defn main
  [m]
  [:div {:style main-styles}
   [card {:title    "MEMBER USER-LIST"
          :children [table-with-buttons (assoc m :show-export-to-csv? true
                                                  :on-click-remove-users! true)]}]])
