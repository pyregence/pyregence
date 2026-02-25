(ns pyregence.components.settings.pages.admin
  (:require
   [pyregence.components.settings.roles       :as roles]
   [pyregence.components.settings.users-table :refer [table-with-buttons]]
   [pyregence.components.utils                :refer [card main-styles]]
   [reagent.core                              :as r]))

(defn main
  [{:keys [user-role] :as m}]
  [:div {:style main-styles}
   [card {:title    "MEMBER USER-LIST"
          :children [table-with-buttons
                     (let [roles (->> user-role roles/role->roles-below)]
                       (assoc m
                              :role-options                roles
                              :default-role-option         (first roles)
                              :statuses                    ["accepted" "pending" "none"]
                              :show-export-to-csv? true
                              :columns
                              (let [boolean-renderer
                                    (fn [params]
                                      (let [v (aget params "value")]
                                        (r/as-element
                                         [:span {:style {:align-items     "center"
                                                         :display         "flex"
                                                         :font-size       "30px"
                                                         :font-weight     "bold"
                                                         :justify-content "center"
                                                         :color           (if v "green" "red")}}
                                          (if v "✓" "✗")])))]
                                [{:field "user-id"               :headerName "User ID"               :filter false :width 110}
                                 {:field "organization-name"     :headerName "Org Name"              :filter "agTextColumnFilter"}
                                 {:field "match-drop-access"     :headerName "Match Drop?"           :filter false :width 150 :cellRenderer boolean-renderer}
                                 {:field "email-verified"        :headerName "Email Verified?"       :filter false :width 150 :cellRenderer boolean-renderer}
                                 {:field "last-login-date"       :headerName "Last Login Date"       :filter "agDateColumnFilter" :width 300}
                                 {:field "settings"              :headerName "Settings"              :filter false :width 200 :autoHeight true
                                  :cellStyle
                                  #js {:whiteSpace "normal" :lineHeight "1.4" :overflow "visible" :textAlign "left"}}])))]}]])
