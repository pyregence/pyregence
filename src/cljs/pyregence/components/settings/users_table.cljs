(ns pyregence.components.settings.users-table
  (:require
   ["ag-grid-community"                   :refer [AllCommunityModule
                                                  ModuleRegistry themeQuartz]]
   ["ag-grid-react"                       :refer [AgGridReact]]
   [clojure.core.async                    :as async :refer [<! go]]
   [goog.object                           :as goog]
   [pyregence.components.settings.add-user :as add-user]
   [pyregence.components.settings.buttons :as buttons]
   [pyregence.components.settings.roles   :as roles]
   [pyregence.components.settings.status  :as status]
   [pyregence.components.settings.utils   :refer [db->display search-cmpt]]
   [pyregence.styles                      :as $]
   [pyregence.utils.async-utils           :as u-async]
   [reagent.core                          :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Grid functionality
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Runs immediately the first time the namespace is loaded, and then never again, even across Figwheel reloads
;; Ensure the AG Grid modules are registered (no-op if already done)
(defonce ag-grid-modules-registered?
  (do
    (.registerModules ModuleRegistry #js [AllCommunityModule])
    true))

(defn- get-selected-rows
  [grid-api]
  (js->clj
   ((goog/get grid-api "getSelectedRows") grid-api)
   :keywordize-keys true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- update-users-roles
  [role users-to-update]
  (go (<! (u-async/call-clj-async! "update-users-roles" role users-to-update))))

(defn- update-users-status
  [status users-to-update]
  (go (<! (u-async/call-clj-async! "update-users-status" status users-to-update))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;TODO find out why this `api-call` is necessary and use it or remove it.
(defn api-call
  "A helper function to safely call GridApi methods under advanced compilation."
  [^js api method & args]
  (let [f (aget api method)]
    (if (instance? js/Function f)
      (.apply f api (to-array args))
      (js/console.error "GridApi method not found:" method "on" api))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;TODO user-role-renderer and org-render can probably be one function
(defn- user-role-renderer [params]
  (let [role (aget params "value")]
    (r/as-element
     [:span (db->display role)])))

(defn- org-membership-status-renderer [params]
  (let [status (aget params "value")]
    (r/as-element
     [:span (db->display status)])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Table components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def light-theme
  (-> themeQuartz
      (.withParams
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
            :fontWeight                     400})))

(defn table
  [grid-api users users-selected?]
  [:div {:style {:height "100%"
                 :width  "100%"}}
   [:div {:style {:height "100%" :width "100%"}}
    [:> AgGridReact
     {:onGridReady                (fn [params] (reset! grid-api (aget params "api")))
      :rowSelection               #js {:mode "multiRow"}
      :domLayout                  "autoHeight"
      :onRowSelected              #(reset! users-selected? (seq (get-selected-rows @grid-api)))
      :theme                      light-theme
      :pagination                 true
      :paginationPageSize         25
      :paginationPageSizeSelector #js [25 50 100]
      :defaultColDef              #js {:unSortIcon true :flex 1} ;; always show sort icons
      :enableCellTextSelection    true
      :rowData                    (clj->js users)
      :columnDefs
      (clj->js [{:field "name" :headerName "User Name" :filter "agTextColumnFilter" :width 150}
                {:field "email" :headerName "Email Address" :filter "agTextColumnFilter"}
                {:field "user-role" :headerName "User Role" :filter "agTextColumnFilter" :cellRenderer user-role-renderer}
                {:field "org-membership-status" :headerName "Status" :filter false :cellRenderer org-membership-status-renderer}])}]]])

;;TODO consider decoupling this from roles and moving into buttons
(defn drop-down
  [{:keys [options on-click-apply opt->display users-selected? checked]}]
  (let [border-styles (str "1px solid " ($/color-picker :neutral-soft-gray))]
    [:div {:style {:display        "flex"
                   :width          "100%"
                   :border         border-styles
                   :border-radius  "4px"
                   :flex-direction "column"}}
     [:div {:style {:display        "flex"
                    :flex-direction "column"}}
      (doall
       (for [opt  options
             :let [checked? (= @checked opt)]]
         [:div {:key      opt
                :on-click #(reset! checked opt)
                :style    {:display        "flex"
                           :align-items    "center"
                           :gap            "12px"
                           :padding        "14px 12px 14px 14px"
                           :flex-direction "row"}}
          [:input {:type     "checkbox"
                     ;;TODO react complains if i don't have an onChange here.
                   :onChange #(reset! checked opt)
                   :checked  checked?
                   :style
                   (merge
                    {:appearance     "none"
                     :width          "18px"
                     :height         "18px"
                     :border-radius  "50%"
                     :border         "2px solid #555"
                     :vertical-align "middle"
                     :position       "relative"}
                    (if checked?
                      {:background "#555"
                       :boxShadow  "inset 0 0 0 4px white"}
                      {:background "white"
                       :boxShadow  "none"}))}]
            ;;TODO shouldn't have to reset the font stuff why is this coming from the body?
          [:label {:style {:color       "black"
                           :font-weight "normal"}}
           (opt->display opt)]]))]
     [:div {:style {:border-top border-styles
                    :padding    "10px 12px"}}
      [buttons/primary {:text      "Apply"
                        :disabled? (or (not @checked) (not users-selected?))
                        :on-click  (on-click-apply @checked)}]]]))

(defn table-with-buttons
  [{:keys [users on-click-apply-update-users users-selected? user-role]}]
  (def user-role user-role)
  user-role

  ;; TODO Right now, the `search` and `selected-drop-down` persist against side nav changes between orgs
  ;; Do we want that?
  (r/with-let [selected-drop-down (r/atom nil)
               grid-api           (r/atom nil)
               search             (r/atom nil)
               checked            (r/atom nil)]
    (let [update-dd           (fn [to] (reset! selected-drop-down (when-not (= @selected-drop-down to) to)))
          get-selected-emails (fn []
                                (->> @grid-api get-selected-rows (map :email)))
          on-click-apply      (on-click-apply-update-users get-selected-emails)
          on-change-search    (fn [e]
                                (let [s (aget (aget e "target") "value")]
                                  (when @grid-api
                                    (api-call @grid-api "setGridOption" "quickFilterText" s))
                                  (reset! search s)))]
      [:<>
       [:div
        {:style {:min-width "400px"}}
        [search-cmpt {:on-change on-change-search
                      :value     @search}]]
       [:div {:style {:display        "flex"
                      :flex-direction "row"
                      :gap            "16px"}}
        [buttons/ghost-drop-down {:text      "Update User Role"
                                  :selected? (= @selected-drop-down :role)
                                  :on-click  (fn []
                                               (reset! checked nil)
                                               (update-dd :role))}]
        [buttons/ghost-drop-down {:text      "Update User Status"
                                  :selected? (= @selected-drop-down :status)
                                  :on-click  (fn []
                                               (reset! checked nil)
                                               (update-dd :status))}]
        ;; TODO add this back in when we get a more well defined acceptance criteria.
        #_(when (:show-remove-user? m)
            [buttons/ghost-remove-user {:text "Remove User"}])
        [add-user/add-user-dialog {:user-role user-role}]]
       (case @selected-drop-down
         ;; TODO ideally these roles should be queried from the database
         :role   [drop-down {:options         roles/roles
                             :checked         checked
                             :users-selected? @users-selected?
                             :opt->display   db->display
                             :on-click-apply (on-click-apply
                                              update-users-roles
                                              "Role"
                                              db->display)}]
         ;; TODO check if none is a valid option, noting that it would remove them from the org.
         ;; TODO none (as the comment says above implies) doesn't seem to work, look into why.
         :status [drop-down {:options         status/statuses
                             :checked         checked
                             :users-selected? @users-selected?
                             :opt->display   db->display
                             :on-click-apply (on-click-apply
                                              update-users-status
                                              "Status"
                                              db->display)}]
         nil)
       [table grid-api users users-selected?]])))
