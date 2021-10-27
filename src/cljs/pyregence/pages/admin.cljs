(ns pyregence.pages.admin
  (:require [herb.core          :refer [<class]]
            [clojure.core.async :refer [go <!]]
            [reagent.core     :as r]
            [cljs.reader      :as edn]
            [pyregence.utils  :as u]
            [pyregence.styles :as $]
            [pyregence.components.common    :refer [check-box labeled-input]]
            [pyregence.components.messaging :refer [toast-message!]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def roles [{:opt-id 1 :opt-label "Admin"}
            {:opt-id 2 :opt-label "Member"}
            {:opt-id 3 :opt-label "Pending"}])

(defonce _user-id  (r/atom -1))
(defonce orgs      (r/atom []))
(defonce *org      (r/atom -1))
(defonce org-users (r/atom []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-org-users-list [org-id]
  (go
    (reset! org-users
            (edn/read-string (:body (<! (u/call-clj-async! "get-org-users-list" org-id)))))))

(defn- get-org-list []
  (go
    (reset! orgs (edn/read-string (:body (<! (u/call-clj-async! "get-org-list" @_user-id))))) ; TODO get from session on the back end
    (reset! *org (:opt-id (first @orgs)))
    (get-org-users-list @*org)))

(defn- update-org-info! [opt-id org-name email-domains auto-add? auto-accept?]
  (go
    (<! (u/call-clj-async! "update-org-info"
                           opt-id
                           org-name
                           email-domains
                           auto-add?
                           auto-accept?))
    (get-org-list)
    (toast-message! "Organization info updated.")))

(defn- add-org-user! [email]
  (go
    (let [res (<! (u/call-clj-async! "add-org-user" @*org email))]
      (if (:success res)
        (do (get-org-users-list @*org)
            (toast-message! (str "User " email " added.")))
        (toast-message! (:body res))))))

(defn- update-org-user-role! [org-user-id role-id]
  (go
    (<! (u/call-clj-async! "update-org-user-role" org-user-id role-id))
    (get-org-users-list @*org)
    (toast-message! "User role updated.")))

(defn- remove-org-user! [org-user-id]
  (go
    (<! (u/call-clj-async! "remove-org-user" org-user-id))
    (get-org-users-list @*org)
    (toast-message! "User removed.")))

(defn- select-org [org-id]
  (reset! *org org-id)
  (get-org-users-list @*org))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $org-item [selected?]
  (merge {:border-bottom (str "1px solid " ($/color-picker :brown))
          :padding       ".75rem"}
         (when selected? {:background-color ($/color-picker :yellow 0.3)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- org-item [org-id name]
  [:label {:style    ($org-item (= org-id @*org))
           :on-click #(select-org org-id)}
   name])

(defn- org-list []
  [:div#org-list
   [:div {:style ($/action-box)}
    [:div {:style ($/action-header)}
     [:label {:style ($/padding "1px" :l)} "Organization List"]]
    [:div {:style {:overflow "auto"}}
     [:div {:style {:display "flex" :flex-direction "column"}}
      (doall (map (fn [{:keys [opt-id opt-label]}]
                    ^{:key opt-id} [org-item opt-id opt-label])
                  @orgs))]]]])

(defn- org-settings [{:keys [opt-id opt-label email-domains auto-add? auto-accept?]}]
  (r/with-let [_opt-label     (r/atom opt-label)
               _email-domains (r/atom email-domains)
               _auto-add?     (r/atom auto-add?)
               _auto-accept?  (r/atom auto-accept?)]
    [:div#org-settings {:style ($/action-box)}
     [:div {:style ($/action-header)}
      [:label {:style ($/padding "1px" :l)} "Settings"]]
     [:div {:style {:overflow "auto"}}
      [:div {:style {:display "flex" :flex-direction "column" :padding "1.5rem"}}
       [labeled-input "Name" _opt-label]
       [labeled-input "Email Domains (comma separated)" _email-domains]
       [check-box "Auto add user to organization" _auto-add?]
       [check-box "Auto accept user as member" _auto-accept?]
       [:input {:class    (<class $/p-form-button :large)
                :style    ($/combine ($/align :block :center) {:margin-top ".5rem"})
                :type     "button"
                :value    "Save"
                :on-click #(update-org-info! opt-id @_opt-label @_email-domains @_auto-add? @_auto-accept?)}]]]]))

(defn- user-item [org-user-id opt-label email role-id]
  (r/with-let [_role-id (r/atom role-id)]
    [:div {:style {:align-items "center" :display "flex" :padding ".25rem"}}
     [:div {:style {:display "flex" :flex-direction "column"}}
      [:label opt-label]
      [:label email]]
     [:span {:style ($/combine ($/align :block :right) {:display "flex"})}
      [:input {:class    (<class $/p-form-button)
               :style    ($/combine ($/align :block :right) {:margin-left "0.5rem"})
               :type     "button"
               :value    "Remove User"
               :on-click #(remove-org-user! org-user-id)}]
      [:select {:class     (<class $/p-bordered-input)
                :style     {:margin "0 .25rem 0 1rem" :height "2rem"}
                :value     @_role-id
                :on-change #(reset! _role-id (u/input-int-value %))}
       (map (fn [{:keys [opt-id opt-label]}]
              [:option {:key opt-id :value opt-id} opt-label])
            roles)]
      [:input {:class    (<class $/p-form-button)
               :style    ($/combine ($/align :block :right) {:margin-left "0.5rem"})
               :type     "button"
               :value    "Update Role"
               :on-click #(update-org-user-role! org-user-id @_role-id)}]]]))

(defn- org-users-list []
  (r/with-let [new-email (r/atom "")]
    [:div#org-users {:style {:margin-top "2rem"}}
     [:div {:style ($/action-box)}
      [:div {:style ($/action-header)}
       [:label {:style ($/padding "1px" :l)} "Users"]]
      [:div {:style {:overflow "auto"}}
       [:div {:style {:display "flex" :flex-direction "column" :padding "1.5rem"}}
        [:div {:style {:align-items "flex-end" :display "flex"}}
         [labeled-input "New User" new-email]
         [:input {:class    (<class $/p-form-button)
                  :style    ($/combine ($/align :block :right) {:margin-left "0.5rem"})
                  :type     "button"
                  :value    "Add User"
                  :on-click #(add-org-user! @new-email)}]]
        (doall (map (fn [{:keys [opt-id opt-label email role-id]}]
                      ^{:key opt-id}
                      [user-item opt-id opt-label email role-id])
                    @org-users))]]]]))

(defn root-component
  "The root component for the /admin page.
   Displays the organization list, settings, and users."
  [{:keys [user-id]}]
  (reset! _user-id user-id)
  (get-org-list)
  (fn [_]
    [:<>
     [:div {:style {:display "flex" :justify-content "center" :padding "2rem 8rem"}}
      [:div {:style {:flex 1 :padding "1rem"}}
       [org-list]]
      ^{:key @*org}
      [:div {:style {:display        "flex"
                     :flex           2
                     :flex-direction "column"
                     :height         "100%"
                     :padding        "1rem"}}
       [org-settings (some (fn [{:keys [opt-id] :as org}] (when (= opt-id @*org) org)) @orgs)]
       [org-users-list]]]]))
