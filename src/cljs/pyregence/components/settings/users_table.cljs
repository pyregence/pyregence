(ns pyregence.components.settings.users-table
  (:require
   ["ag-grid-community"                       :refer [AllCommunityModule
                                                      ModuleRegistry
                                                      themeQuartz]]
   ["ag-grid-react"                           :refer [AgGridReact]]
   [clojure.core.async                        :as async :refer [<! go]]
   [clojure.edn                               :as edn]
   [clojure.set                               :as set]
   [clojure.string                            :as str]
   [goog.object                               :as goog]
   [pyregence.components.buttons              :as buttons]
   [pyregence.components.messaging            :refer [toast-message!]]
   [pyregence.components.settings.add-user    :as add-user]
   [pyregence.components.settings.delete-user :as delete-user]
   [pyregence.components.svg-icons            :as svg]
   [pyregence.components.utils                :refer [db->display search-cmpt]]
   [pyregence.styles                          :as $]
   [pyregence.utils.async-utils               :as u-async]
   [reagent.core                              :as r]
   [pyregence.components.settings.update-user :as update-user]))

(defn delete-users!
  [users-to-delete]
  (go (<! (u-async/call-clj-async! "delete-users" users-to-delete))))

(defn get-users! [user-role]
  (go
    (let [admin? (#{"super_admin" "account_manager"} user-role)
          route (if admin?
                  "get-all-users"
                  "get-org-member-users")
          resp-chan              (u-async/call-clj-async! route)
          {:keys [body]} (<! resp-chan)]
      (map #(set/rename-keys % {:full-name :name :membership-status :org-membership-status}) (edn/read-string body)))))

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
  [grid-api users users-selected? users-filter columns]
  [:div {:style {:height "100%"
                 :width  "100%"}}
   [:div {:style {:height "100%" :width "100%"}}
    [:> AgGridReact
     {:onGridReady                #(reset! grid-api (goog/get % "api"))
      :onRowDataUpdated           #(api-call @grid-api "autoSizeAllColumns")
      :rowSelection               #js {:mode      "multiRow"
                                       :selectAll "filtered"}
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
      :rowData                    (clj->js (filter users-filter @users))
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
        users-selected?    (r/atom false)
        set-users!         #(go (reset! users (<! (get-users! user-role))))]
    (r/create-class
     {:display-name "users-table"
      :component-did-mount set-users!
      :reagent-render
      (fn [{:keys [org-id
                   org-id->org
                   role-options
                   default-role-option
                   statuses
                   columns
                   show-export-to-csv?
                   choose-org?
                   users-filter] :or {users-filter identity} :as m}]
        (let [org-names           (->> m :organizations (map :org-name))
              tab-selected-org-name   (:org-name (@org-id->org org-id))
              update-drop-down    #(reset! selected-drop-down (when-not (= @selected-drop-down %) %))
              get-selected-emails #(->> @grid-api get-selected-rows (map :email))
              get-selected-rows   #(get-selected-rows @grid-api)
              on-click-apply
              (fn [update-user-info-by-email opt-type opt->display]
                (fn [new-user-info new-org]
                  (let [emails (get-selected-emails)]
                    (update-user-info-by-email new-user-info new-org emails)
                        ;; TODO instead of this hacky sleep i think we have two options,
                        ;; first, we have the update function return the users, this seems ideal. the second is,
                        ;; we get the success from the update function and we then poll the users.
                        ;; TODO this could use the core async timeout instead.
                    (js/setTimeout set-users! 3000)
                    (toast-message!
                         ;;TODO make this handle plural case e.g roles and statues.
                     (str (str/join ", " emails)  " updated " opt-type " to " (opt->display new-user-info) ".")))))
              on-click-delete-users!
              (fn [get-selected-emails]
                (fn []
                  (let [selected-emails (get-selected-emails)]
                    (go
                      (let [{:keys [success]}
                            (<! (delete-users! selected-emails))]
                        (if success
                          (do
                            (js/setTimeout set-users! 3000)
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
             (when (#{"super_admin"} user-role)
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
                                                          #js {:fileName            (str (subs (.toISOString (js/Date.)) 0 10) "_" "users-table")
                                                               :onlySelected        (some? (seq (get-selected-rows)))
                                                               :processCellCallback #(aget % "value")}))}])]]

           (case @selected-drop-down
             ;;TODO this update-user/drop-down is wonky in so many ways.
             :role   [update-user/drop-down
                      (let [org-opt-selected? (and
                                               choose-org?
                                               (#{"super_admin" "account_manager"} user-role)
                                               (#{"organization_admin" "organization_member"} @checked))]
                        (cond->
                         {:options         role-options
                          :organizations   org-names
                          :disabled?       (or (not @checked) (not @users-selected?))
                          :checked         checked
                          ;;TODO opt->display shouldn't be passed in here but we need to move users state to table before it will be easy to re-factor.
                          :opt->display    db->display
                          :on-click-update-users
                          (on-click-apply
                           (fn [role admin-selected-org-name selected-users]
                             (go (<! (u-async/call-clj-async! "update-users-roles"
                                                              role
                                                              (if choose-org?
                                                                admin-selected-org-name
                                                                tab-selected-org-name)
                                                              selected-users))))
                           "Role"
                           db->display)
                          :get-selected-rows get-selected-rows}
                          org-opt-selected?
                          (assoc :select-org-msg (str "Assign Organization for "
                                                      ({"organization_admin"  "Admin"
                                                        "organization_member" "Member"} @checked)
                                                      "."))))]
             :status [update-user/drop-down
                      (let [org-opt-selected?
                            (and
                             choose-org?
                             (#{"super_admin" "account_manager"} user-role)
                             (#{"pending" "accepted"} @checked))]
                        (cond->
                         {:options         statuses
                          :organizations   org-names
                          :disabled?       (or (not @checked) (not @users-selected?))
                          :checked         checked
                          :opt->display    db->display
                          :on-click-update-users
                          (on-click-apply
                           (fn [status admin-selected-org-name selected-users]
                             (go
                               (<! (u-async/call-clj-async! "update-users-status"
                                                            status
                                                            (if choose-org?
                                                              admin-selected-org-name
                                                              tab-selected-org-name)
                                                            selected-users))))
                           "Status"
                           db->display)
                          :get-selected-rows get-selected-rows}
                          org-opt-selected?
                          (assoc :select-org-msg (str "Assign Organization for " (db->display @checked) " Users."))))]
             nil)
           [table grid-api users users-selected? users-filter columns]]))})))
