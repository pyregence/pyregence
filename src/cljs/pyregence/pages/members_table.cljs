(ns pyregence.pages.members-table
  (:require
   ["ag-grid-react"     :refer [AgGridReact]]
   ["ag-grid-community" :refer [ModuleRegistry
                                AllCommunityModule]]
   [clojure.core.async          :as async :refer [<! go]]
   [clojure.edn                 :as edn]
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

(defonce table-loading? (r/atom false))

(defonce all-users-table-data
  (r/atom {:row-data []
           :col-defs [{:field "name"                  :headerName "User Name"             :filter "agTextColumnFilter" :width 150}
                      {:field "email"                 :headerName "Email Address"                 :filter "agTextColumnFilter"}
                      {:field "user-role"             :headerName "User Role"                  :filter "agTextColumnFilter" :cellRenderer user-role-renderer}
                      {:field "org-membership-status" :headerName "Status" :filter false :cellRenderer org-membership-status-renderer}
                      #_{:field "settings"              :headerName "Settings"              :filter false :width 200 :autoHeight false :cellStyle wrap-text-style}]}))

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


(defn add-new-user!
  []
  "add-new-user"
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Users UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- users-grid [user-info grid-api]
  [:div {:style {:height "100%" :width "100%"}}
   [:> AgGridReact
    {:onGridReady                (fn [params] (reset! grid-api (aget params "api")))
     :rowSelection               #js {:mode "multiRow"}
     :pagination                 true
     :paginationPageSize         25
     :paginationPageSizeSelector #js [25 50 100]
     :defaultColDef              #js {:unSortIcon true} ;; always show sort icons
     :enableCellTextSelection    true
     :rowData                    (clj->js (:row-data @all-users-table-data))
     :columnDefs                 (clj->js (:col-defs @all-users-table-data))}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component
  "The root component for the /users-table page."
  [{:keys [user-role]} grid-api]
  (r/create-class
   {:component-did-mount
    (fn [_]
      (initialize! user-role))
    :reagent-render
    (fn [user-info]
      ;;TODO figure out why height is vh not 100%?
      [:div {:style {:height "700px"
                     :width  "100%"}}
       [users-grid user-info grid-api]])}))
