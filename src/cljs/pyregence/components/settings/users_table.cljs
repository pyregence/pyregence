(ns pyregence.components.settings.users-table
  (:require
   ["ag-grid-community"                       :refer [AllCommunityModule
                                                      ModuleRegistry
                                                      themeQuartz]]
   ["ag-grid-react"                           :refer [AgGridReact]]
   [clojure.core.async                        :as async :refer [<! go]]
   [clojure.string                            :as str]
   [goog.object                               :as goog]
   [pyregence.components.buttons              :as buttons]
   [pyregence.components.messaging            :refer [toast-message!]]
   [pyregence.components.settings.add-user    :as add-user]
   [pyregence.components.settings.delete-user :as delete-user]
   [pyregence.components.settings.fetch       :refer [delete-users! get-users!]]
   [pyregence.components.svg-icons            :as svg]
   [pyregence.components.utils                :refer [db->display search-cmpt]]
   [pyregence.styles                          :as $]
   [pyregence.utils.async-utils               :as u-async]
   [reagent.core                              :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Grid functionality
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Runs immediately the first time the namespace is loaded, and then never again, even across Figwheel reloads
;; Ensure the AG Grid modules are registered (no-op if already done)
(defonce ag-grid-modules-registered?
  (do
    (.registerModules ModuleRegistry #js [AllCommunityModule])
    true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;TODO find out if we can replace api-call is "safer" then other more concise alternatives.
(defn api-call
  "A helper function to safely call GridApi methods under advanced compilation."
  [^js api method & args]
  (let [f (aget api method)]
    (if (instance? js/Function f)
      (.apply f api (to-array args))
      (js/console.error "GridApi method not found:" method "on" api))))

;;TODO find out why this can't be inlined, it seems to break the interaction with the onRowSelected.
(defn- get-selected-rows
  [grid-api]
  (js->clj (api-call grid-api "getSelectedRows") :keywordize-keys true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Table components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;TODO figure out why we can't inline this it seems to have an interaction with getselectedrows.
(defn table
  [grid-api users users-selected? columns]
  [:div {:style {:height "100%"
                 :width  "100%"}}
   [:div {:style {:height "100%" :width "100%"}}
    [:> AgGridReact
     {:onGridReady                #(reset! grid-api (goog/get % "api"))
      :onRowDataUpdated           #(api-call @grid-api "autoSizeAllColumns")
      :rowSelection               #js {:mode "multiRow"}
      :domLayout                  "autoHeight"
      :onRowSelected              #(reset! users-selected? (seq (get-selected-rows @grid-api)))
      :theme                      (.withParams themeQuartz
                                               #js {:backgroundColor                ($/color-picker :white)
                                                    :headerBackgroundColor          ($/color-picker :neutral-light-gray)
                                                    :headerTextColor                ($/color-picker :black)
                                                    :headerFontWeight               600
                                                    :rowHoverColor                  ($/color-picker :light-orange)
                                                    :selectedRowBackgroundColor     ($/color-picker :soft-orange)
                                                    :checkboxCheckedBackgroundColor ($/color-picker :primary-main-orange)
                                                    :checkboxCheckedBorderColor     ($/color-picker :light-orange)
                                                    :focusShadow                    "none"
                                                    :cellHorizontalPadding          12
                                                    :rowHeight                      62
                                                    :fontFamily                     "Roboto"
                                                    :fontSize                       14
                                                    :fontWeight                     400})
      :pagination                 true
      :paginationPageSize         25
      :paginationPageSizeSelector #js [25 50 100]
      :defaultColDef              #js {:unSortIcon true :flex 1}
      :enableCellTextSelection    true
      :rowData                    (clj->js @users)
      :columnDefs
      (->> columns
           (concat
            [{:field "name"                  :headerName "User Name"     :filter "agTextColumnFilter"  :width 150}
             {:field "email"                 :headerName "Email Address" :filter "agTextColumnFilter"}
             {:field "user-role"             :headerName "User Role"     :filter "agTextColumnFilter"  :cellRenderer #(r/as-element [:span (db->display (aget % "value"))])}
             {:field "org-membership-status" :headerName "Status"        :filter false                 :cellRenderer #(r/as-element [:span (db->display (aget % "value"))])}])
           clj->js)}]]])

(defn table-with-buttons
  [{:keys [user-role]}]
  (let [selected-drop-down (r/atom nil)
        grid-api           (r/atom nil)
        search             (r/atom nil)
        checked            (r/atom nil)
        users              (r/atom nil)
        users-selected?    (r/atom false)]
    (r/create-class
     {:display-name "users-table"
      :component-did-mount #(go (reset! users (<! (get-users! user-role))))
      :reagent-render
      (fn [{:keys [user-role
                   org-id
                   role-options
                   default-role-option
                   statuses
                   columns
                   show-export-to-csv?]}]
        (let [update-drop-down    #(reset! selected-drop-down (when-not (= @selected-drop-down %) %))
              get-selected-emails #(->> @grid-api get-selected-rows (map :email))
              get-selected-rows   #(get-selected-rows @grid-api)
              on-click-apply
              (fn [update-user-info-by-email opt-type opt->display]
                (fn [new-user-info]
                  (fn []
                    (let [emails (get-selected-emails)]
                        ;; TODO this needs error handling.
                      (update-user-info-by-email new-user-info emails)
                        ;; TODO instead of this hacky sleep i think we have two options,
                        ;; first, we have the update function return the users, this seems ideal. the second is,
                        ;; we get the success from the update function and we then poll the users.
                        ;; TODO this could use the core async timeout instead.
                      (js/setTimeout #(go (reset! users (<! (get-users! user-role)))) 3000)
                      (toast-message!
                         ;;TODO make this handle plural case e.g roles and statues.
                       (str (str/join ", " emails)  " updated " opt-type " to " (opt->display new-user-info) "."))))))
              on-click-delete-users!

              (fn [get-selected-emails]
                (fn []
                  (let [selected-emails (get-selected-emails)]
                    (go
                      (let [{:keys [success]}
                            (<! (delete-users! selected-emails))]
                        (if success
                          (do
                            (js/setTimeout (fn [] (go (reset! users (<! (get-users! user-role))))) 3000)
                            (toast-message! (str "Users Deleted: " (str/join ", " selected-emails))))
                                        ;;TODO it's unclear what would help here...
                          (toast-message! "Something went wrong!")))))))]
          [:<>
           [:div
            {:style {:min-width "400px"}}
            [search-cmpt {:on-change #(let [new-search (-> % .-target .-value)]
                                        (api-call @grid-api "setGridOption" "quickFilterText" new-search)
                                        (reset! search new-search))
                          :value     @search}]]
           [:div {:style {:display         "flex"
                          :width           "100%"
                          :flex-direction  "row"
                          :justify-content "space-between"}}
            [:div {:style {:display         "flex"
                           :flex-direction  "row"
                           :gap             "16px"
                           :width           "100%"
                           :justify-content "flex-start"}}
             [buttons/ghost-drop-down {:text      "Update User Role"
                                       :selected? (= @selected-drop-down :role)
                                       :on-click  (fn []
                                                    (reset! checked nil)
                                                    (update-drop-down :role))}]
             [buttons/ghost-drop-down {:text      "Update User Status"
                                       :selected? (= @selected-drop-down :status)
                                       :on-click  (fn []
                                                    (reset! checked nil)
                                                    (update-drop-down :status))}]
             (when on-click-delete-users!
               [delete-user/confirm-dialog
                {:on-click-delete-users! (on-click-delete-users! get-selected-emails)
                 :get-selected-rows      get-selected-rows}])]
            [:div {:style {:display        "flex"
                           :flex-direction "column"
                           :gap            "10px"}}
             [add-user/add-user-dialog {:org-id org-id :role-options role-options :default-role-option default-role-option}]
             (when show-export-to-csv?
               [buttons/ghost {:text     "Export"
                               :icon     [svg/export :height "24px" :width "24px"]
                               :on-click (fn [] (api-call @grid-api "exportDataAsCsv"
                                                          #js {:fileName            (str (subs (.toISOString (js/Date.)) 0 10) "_" "users-taqle")
                                                               :onlySelected        (some? (seq (get-selected-rows)))
                                                               :processCellCallback #(aget % "value")}))}])]]
           (case @selected-drop-down
         ;; TODO ideally these roles should be queried from the database from a sql function.
             :role   [buttons/drop-down {:options         role-options
                                         :disabled?       (or (not @checked) (not @users-selected?))
                                         :checked         checked
                                     ;;TODO opt->display should be passed in here but we need to move users state to table before it will be easy to re-factor.
                                         :opt->display    db->display
                                         :on-click  (on-click-apply
                                                     (fn [role users]
                                                       (go (<! (u-async/call-clj-async! "update-users-roles" role users))))
                                                     "Role"
                                                     db->display)}]
         ;; TODO check if none is a valid option, noting that it would remove them from the org.
         ;; TODO none (as the comment says above implies) doesn't seem to work, look into why.
             :status [buttons/drop-down {:options         statuses
                                         :disabled?       (or (not @checked) (not @users-selected?))
                                         :checked         checked
                                         :opt->display    db->display
                                         :on-click  (on-click-apply
                                                     (fn [status users]
                                                       (go (<! (u-async/call-clj-async! "update-users-status" status users))))
                                                     "Status"
                                                     db->display)}]
             nil)
           [table grid-api users users-selected? columns]]))})))
