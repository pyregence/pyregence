(ns pyregence.pages.users-table
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
  (let [v (.-value params)]
    (r/as-element
     [:span {:style {:align-items     "center"
                     :display         "flex"
                     :font-size       "30px"
                     :font-weight     "bold"
                     :justify-content "center"
                     :color           (if v "green" "red")}}
      (if v "✓" "✗")])))

(defn- user-role-renderer [params]
  (let [v (.-value params)]
    (r/as-element
     [:span
      (condp = v
       "super_admin"         "Super Admin"
       "organization_admin"  "Organization Admin"
       "organization_member" "Organization Member"
       "account_manager"     "Account Manager"
       "member"              "Member")])))
    
(defn- org-membership-status-renderer [params]
  (let [v (.-value params)]
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

(defn- get-all-users! []
  (go
    (let [resp-chan              (u-async/call-clj-async! "get-all-users")
          {:keys [body success]} (<! resp-chan)
          users                  (edn/read-string body)]
      (swap! all-users-table-data assoc :row-data (if success users [])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data Processing Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- initialize! []
  (reset! table-loading? true)
  (get-all-users!)
  (reset! table-loading? false))

(defn- today-str []
  (.toISOString (js/Date.)) ; => "2025-05-27T15:26:09.123Z"
  (subs (.toISOString (js/Date.)) 0 10)) ; => "2025-05-27"

(defn- export-button-on-click-fn [file-name]
  (some-> @grid-api
    (.exportDataAsCsv #js {:fileName (str (today-str) "_" file-name)
                           :processCellCallback
                           (fn [params]
                             (.-value params))})))

(defn log-selected-rows! []
  (when-let [api @grid-api]
    (let [rows (.getSelectedRows api)]
      (js/console.log "Selected rows:\n" rows)
      (js/alert (str "Selected rows:\n" (js/JSON.stringify rows nil 2)))
      rows)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Users UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO use new button component
(defn- csv-export-button [file-name]
  [:button
   {:style    {:padding       "0.5rem 1rem"
               :font-size     "1rem"
               :background    "#E5B154"
               :color         "black"
               :border        "none"
               :border-radius "4px"
               :cursor        "pointer"}
    :on-click #(export-button-on-click-fn file-name)}
   "Export to CSV"])

;; TODO remove me
(defn- log-rows-button []
  [:button
   {:style    {:padding       "0.5rem 1rem"
               :font-size     "1rem"
               :background    "#E5B154"
               :color         "black"
               :border        "none"
               :border-radius "4px"
               :cursor        "pointer"
               :margin-right  "1rem"}
    :on-click #(log-selected-rows!)}
   "Log Selected Rows"])

(defn- users-grid []
  [:div {:style {:height "100%" :width "100%"}}
   [:> AgGridReact
    {:onGridReady                (fn [params]
                                   (reset! grid-api (.-api params)))
     :theme                      light-theme ; dark-theme
     :rowSelection               (clj->js {:mode "multiRow"}) ; :enableClickSelection true})
     :pagination                 true
     :paginationPageSize         25
     :paginationPageSizeSelector #js [25 50 100] ;; include 25
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
                  :justify-content "flex-end"}}
    [log-rows-button]
    [csv-export-button "users-table"]]
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
      (initialize!))

    :reagent-render
    (fn [_]
      [:div {:style {:height "100vh"
                     :padding "2rem"}}
       [users-view]])}))
