(ns pyregence.components.settings.users-table
  (:require
   ["ag-grid-community"                       :refer [AllCommunityModule
                                                   themeQuartz
                                                   ModuleRegistry]]
   ["ag-grid-react"                           :refer [AgGridReact]]
   [clojure.core.async                        :as async :refer [<! go]]
   [goog.object                               :as goog]
   [pyregence.components.buttons              :as buttons]
   [pyregence.components.settings.add-user    :as add-user]
   [pyregence.components.settings.delete-user :as delete-user]
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

;;TODO find out why this `api-call` is necessary and use it or remove it.
(defn api-call
  "A helper function to safely call GridApi methods under advanced compilation."
  [^js api method & args]
  (let [f (aget api method)]
    (if (instance? js/Function f)
      (.apply f api (to-array args))
      (js/console.error "GridApi method not found:" method "on" api))))

(defn- today-str []
  (.toISOString (js/Date.)) ; => "2025-05-27T15:26:09.123Z"
  (subs (.toISOString (js/Date.)) 0 10)) ; => "2025-05-27"

(defn- export-button-on-click-fn [grid-api file-name]
  (when-let [api @grid-api]
    (api-call api "exportDataAsCsv"
              #js {:fileName     (str (today-str) "_" file-name)
                   :onlySelected (some? (seq (get-selected-rows api)))
                   :processCellCallback
                   (fn [params]
                     (aget params "value"))})))

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
  [grid-api users users-selected? columns]
  [:div {:style {:height "100%"
                 :width  "100%"}}
   [:div {:style {:height "100%" :width "100%"}}
    [:> AgGridReact
     {:onGridReady                (fn [params]
                                    (reset! grid-api (goog/get params "api")))
      :onFirstDataRendered        (fn [params]
                                    (let [api (goog/get params "api")]
                                      (api-call api "autoSizeAllColumns")
                                      (doseq [col (api-call api "getAllDisplayedColumns")]
                                        (js-invoke col "setFlex" 1))))
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
      :columnDefs                 (clj->js columns)}]]])

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
  [{:keys [users
           on-click-apply-update-users
           users-selected?
           org-id
           role-options
           default-role-option
           statuses
           columns
           ;;TODO if we get multiple show buttons then we should re-organize.
           show-export-to-csv?
           on-click-delete-users!]}]
  ;; TODO Right now, the `search` and `selected-drop-down` persist against side nav changes between orgs
  ;; Do we want that?
  (r/with-let [selected-drop-down (r/atom nil)
               grid-api           (r/atom nil)
               search             (r/atom nil)
               checked            (r/atom nil)]
    (let [update-dd           (fn [to] (reset! selected-drop-down (when-not (= @selected-drop-down to) to)))
          get-selected-emails (fn []
                                (->> @grid-api get-selected-rows (map :email)))
          get-selected-rows   #(get-selected-rows @grid-api)
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
       [:div {:style {:display         "flex"
                      :width          "100%"
                      :flex-direction  "row"
                      :justify-content "space-between"}}
        [:div {:style {:display "flex"
                       :flex-direction  "row"
                       :gap             "16px"
                       :width           "100%"
                       :justify-content "flex-start"}}
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
                           :on-click #(export-button-on-click-fn grid-api "users-table")}])]]
       (case @selected-drop-down
         ;; TODO ideally these roles should be queried from the database from a sql function.
         :role   [drop-down {:options         role-options
                             :checked         checked
                             :users-selected? @users-selected?
                             :opt->display   db->display
                             :on-click-apply (on-click-apply
                                              update-users-roles
                                              "Role"
                                              db->display)}]
         ;; TODO check if none is a valid option, noting that it would remove them from the org.
         ;; TODO none (as the comment says above implies) doesn't seem to work, look into why.
         :status [drop-down {:options         statuses
                             :checked         checked
                             :users-selected? @users-selected?
                             :opt->display   db->display
                             :on-click-apply (on-click-apply
                                              update-users-status
                                              "Status"
                                              db->display)}]
         nil)
       [table grid-api users users-selected? columns]])))
