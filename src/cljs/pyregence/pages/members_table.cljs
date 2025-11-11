(ns pyregence.pages.members-table
  (:require
   ["ag-grid-react"     :refer [AgGridReact]]
   ["ag-grid-community" :refer [ModuleRegistry
                                AllCommunityModule
                                colorSchemeDarkBlue
                                themeQuartz]]
   [clojure.core.async          :as async :refer [<! go]]
   [clojure.edn                 :as edn]
   [pyregence.styles            :as $]
   [pyregence.utils.async-utils :as u-async]
   [reagent.core                :as r]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Runs immediately the first time the namespace is loaded, and then never again, even across Figwheel reloads
;; Ensure the AG Grid modules are registered (no-op if already done)
(defonce ag-grid-modules-registered?
  (do
    (.registerModules ModuleRegistry #js [AllCommunityModule])
    true))

(def dark-theme
  (-> themeQuartz
      (.withPart colorSchemeDarkBlue)
      (.withParams #js {:oddRowBackgroundColor "rgb(42, 51, 64, 0.5)"})))

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

(defn- boolean-renderer [params]
  (let [v (aget params "value")]
    (r/as-element
     [:span {:style {:align-items     "center"
                     :display         "flex"
                     :font-size       "30px"
                     :font-weight     "bold"
                     :justify-content "center"
                     :color           (if v "green" "red")}}
      (if v "✓" "✗")])))

(defn- user-role-renderer [params]
  (let [v (aget params "value")]
    (r/as-element
     [:span
      (condp = v
       "super_admin"         "Super Admin"
       "organization_admin"  "Organization Admin"
       "organization_member" "Organization Member"
       "account_manager"     "Account Manager"
       "member"              "Member")])))

(defn- org-membership-status-renderer [params]
  (let [v (aget params "value")]
    (r/as-element
     [:span
      (condp = v
       "none"     "None"
       "pending"  "Pending"
       "accepted" "Accepted")])))

(defn- wrap-text-style [_]
  #js {:whiteSpace "normal" :lineHeight "1.4" :overflow "visible" :textAlign "left"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce grid-api       (r/atom nil))
(defonce table-loading? (r/atom false))

(defonce all-users-table-data
  (r/atom {:row-data []
           :col-defs [{:field "user-id"               :headerName "User ID"               :filter false :width 110}
                      {:field "email"                 :headerName "Email"                 :filter "agTextColumnFilter"}
                      {:field "name"                  :headerName "Full Name"             :filter "agTextColumnFilter" :width 150}
                      {:field "organization-name"     :headerName "Org Name"              :filter "agTextColumnFilter"}
                      {:field "match-drop-access"     :headerName "Match Drop?"           :filter false :width 150 :cellRenderer boolean-renderer}
                      {:field "email-verified"        :headerName "Email Verified?"       :filter false :width 150 :cellRenderer boolean-renderer}
                      {:field "user-role"             :headerName "Role"                  :filter "agTextColumnFilter" :cellRenderer user-role-renderer}
                      {:field "org-membership-status" :headerName "Org Membership Status" :filter false :cellRenderer org-membership-status-renderer}
                      {:field "last-login-date"       :headerName "Last Login Date"       :filter "agDateColumnFilter" :width 300}
                      {:field "settings"              :headerName "Settings"              :filter false :width 200 :autoHeight true :cellStyle wrap-text-style}]}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn api-call
  "A helper functoin to safely call GridApi methods under advanced compilation."
  [^js api method & args]
  (let [f (aget api method)]
    (if (instance? js/Function f)
      (.apply f api (to-array args))
      (js/console.error "GridApi method not found:" method "on" api))))

;; (if (= user-role "super_admin")
;;   "get-all-organizations" ; super admin can see all orgs
;;   "get-current-user-organization")

(defn- get-all-users [user-role]
  (go
    (let [route (if (= user-role "super_admin") "get-all-users" "get-org-member-users")
          resp-chan              (u-async/call-clj-async! route)
          {:keys [body success]} (<! resp-chan)
          users                  (edn/read-string body)]
      (swap! all-users-table-data assoc :row-data (if success users [])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data Processing Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- initialize! [user-role]
  (reset! table-loading? true)
  (get-all-users user-role)
  (reset! table-loading? false))

(defn- today-str []
  (.toISOString (js/Date.)) ; => "2025-05-27T15:26:09.123Z"
  (subs (.toISOString (js/Date.)) 0 10)) ; => "2025-05-27"

(defn- export-button-on-click-fn [file-name]
  (when-let [api @grid-api]
    (api-call api "exportDataAsCsv"
              #js {:fileName (str (today-str) "_" file-name)
                   :processCellCallback
                   (fn [params]
                     (aget params "value"))})))

(defn log-selected-rows! []
  (when-let [api @grid-api]
    (let [rows (api-call api "getSelectedRows")]
      (js/console.log "Selected rows:" rows)
      (js/alert (str "Selected rows:\n" (js/JSON.stringify rows nil 2)))
      rows)))

(defn add-new-user!
  []
  "add-new-user"
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Users UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- users-grid []
  [:div {:style {:height "100%" :width "100%"}}
   [:> AgGridReact
    {:onGridReady                (fn [params]
                                   (reset! grid-api (aget params "api")))
     :theme                      light-theme ; dark-theme
     :rowSelection               #js {:mode "multiRow"}
     :pagination                 true
     :paginationPageSize         25
     :paginationPageSizeSelector #js [25 50 100]
     :defaultColDef              #js {:unSortIcon true} ;; always show sort icons
     :enableCellTextSelection    true
     :rowData                    (clj->js (:row-data @all-users-table-data))
     :columnDefs                 (clj->js (:col-defs @all-users-table-data))}]])

(defn- users-view []
  [:div#users-view
   {:style {:display               "grid"
            :font-family           "Roboto"
            :grid-template-columns "1fr 1fr"
            :grid-template-rows    "auto 1fr"
            :grid-template-areas   "'users-header  users-button'
                                    'users-content users-content'"
            :height                "100%"}}
   [:div {:style {:align-items     "center"
                  :display         "flex"
                  :grid-area       "users-header"
                  :font-size       "36px"
                  :justify-content "flex-start"}}
    "Users Table"]
   [:div {:style {:align-items     "center"
                  :display         "flex"
                  :grid-area       "users-button"
                  :justify-content "flex-end"
                  :z-index         1000}}]
   [:div {:style {:grid-area   "users-content"
                  :padding-top "1rem"}}
    (if @table-loading?
      [:div {:style {:display "flex" :justify-content "center"}}
       [:h1 "Loading..."]]
      [users-grid])]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component
  "The root component for the /users-table page."
  []
  (r/create-class
   {:component-did-mount
    (fn [_]
      ;;TODO fix this so were passing in user-role info
      (initialize! "super_admin"))

    :reagent-render
    (fn [_]
      [:div {:style {:height "100vh"
                     :padding "2rem"}}
       [users-view]])}))
