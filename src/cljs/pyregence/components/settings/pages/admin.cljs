(ns pyregence.components.settings.pages.admin
  (:require
   [clojure.core.async                        :refer [go <!]]
   [goog.object                               :as goog]
   [pyregence.components.messaging            :refer [toast-message!]]
   [pyregence.components.settings.roles       :as roles]
   [pyregence.components.settings.users-table :refer [table-with-buttons]]
   [pyregence.components.utils                :refer [card main-styles]]
   [pyregence.utils.async-utils               :as u-async]
   [reagent.core                              :as r]))

(defn- update-match-drop-access!
  "Persists a Match Drop access change made via the editable grid toggle."
  [params]
  (let [field (goog/get (goog/get params "colDef") "field")]
    (when (= field "match-drop-access")
      (let [email   (goog/get (goog/get params "data") "email")
            new-val (boolean (goog/get params "newValue"))]
        (go
          (let [{:keys [success]} (<! (u-async/call-clj-async! "update-user-match-drop-access" email new-val))]
            (if success
              (toast-message! (str "Match Drop access for " email " set to " (if new-val "enabled" "disabled") "."))
              (toast-message! (str "Unable to update Match Drop access for " email ".")))))))))

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
                              :choose-org?         true
                              :on-cell-value-changed       update-match-drop-access!
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
                                [{:field "organization-name" :headerName "Org Name" :filter "agTextColumnFilter"}
                                 {:field        "match-drop-access" :headerName "Match Drop?" :filter false :width 150
                                  :editable     true
                                  :cellRenderer "agCheckboxCellRenderer"}
                                 {:field "email-verified" :headerName "Email Verified?" :filter false :width 150 :cellRenderer boolean-renderer}
                                 {:field "last-login-date" :headerName "Last Login Date" :filter "agDateColumnFilter" :width 300}
                                 {:field "settings" :headerName "Settings" :filter false :width 200 :autoHeight true
                                  :cellStyle
                                  #js {:whiteSpace "normal" :lineHeight "1.4" :overflow "visible" :textAlign "left"}}])))]}]])
