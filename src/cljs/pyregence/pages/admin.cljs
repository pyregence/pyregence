(ns pyregence.pages.admin
  (:require [herb.core          :refer [<class]]
            [clojure.core.async :refer [go <!]]
            [clojure.string     :refer [blank?]]
            [reagent.core     :as r]
            [cljs.reader      :as edn]
            [pyregence.utils  :as u]
            [pyregence.styles :as $]
            [pyregence.components.common    :refer [check-box labeled-input]]
            [pyregence.components.messaging :refer [confirmation-modal set-message-box-content! toast-message!]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def roles [{:opt-id 1 :opt-label "Admin"}
            {:opt-id 2 :opt-label "Member"}
            {:opt-id 3 :opt-label "Pending"}])

(defonce _user-id  (r/atom -1))
(defonce ^{:doc "Vector of organizations the current user is an admin of."} orgs (r/atom []))
(defonce ^{:doc "The `first` organization object from `@orgs`, set as the selected default."} *org (r/atom -1))
(defonce ^{:doc "Vector of the org users associated with the currently selected organization."} org-users (r/atom []))

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
;; Helper Functions - Handle click events and setup modal confirmation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- handle-add-user [_email]
  (when-not (blank? _email)
    (set-message-box-content! {:title  "Add New User"
                               :body   (str "Are you sure that you want to add the user, with email \""
                                            _email "\", to the system?")
                               :action #(add-org-user! _email)})))

(defn- handle-remove-user [_uid _username]
  (set-message-box-content! {:title  "Delete User"
                             :body   (str "Are you sure that you want to permanently remove user \""
                                          _username "\" from the system.")
                             :action #(remove-org-user! _uid)}))

(defn- handle-update-role-id [_rid _uid _username]
  (set-message-box-content! {:title  "Update User Role"
                             :body   (str "Are you sure you want to change the role of user \""
                                          _username "\" to \""
                                          (->> roles
                                               (filter (fn [role] (= _rid (role :opt-id))))
                                               (first)
                                               (:opt-label)) "\"?")
                             :action #(update-org-user-role! _uid _rid)}))

(defn- handle-update-org-settings [_oid _org-name _email-domains _auto-add _auto-accept]
  (set-message-box-content! {:title  "Update Settings"
                             :body   "Saving these changes will overwrite previous setting values."
                             :action #(update-org-info! _oid _org-name _email-domains _auto-add _auto-accept)}))

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
       [:label]
       [check-box "Auto add user to organization" _auto-add?]
       [check-box "Auto accept user as member" _auto-accept?]
       [:input {:class    (<class $/p-form-button :large)
                :style    ($/combine ($/align :block :center) {:margin-top ".5rem"})
                :type     "button"
                :value    "Update"
                :on-click #(handle-update-org-settings
                            opt-id          ; Organization ID
                            @_opt-label     ; Organization name
                            @_email-domains ; Comma delimitted
                            @_auto-add?
                            @_auto-accept?)}]]]]))

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
               :on-click #(handle-remove-user org-user-id opt-label)}]
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
               :on-click #(handle-update-role-id @_role-id org-user-id opt-label)}]]]))

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
                  :on-click #(handle-add-user @new-email) }]]
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
    (cond
      (or (nil? user-id)                ; User is not logged in
          (nil? @*org))                 ; User is not an admin of any org
      (do (u/redirect-to-login! "/admin")
          nil)

      (= @*org -1)                      ; get-org-list has not completed yet
      [:div {:style {:display "flex" :justify-content "center"}}
       [:h1 "Loading..."]]

      :else                             ; Logged-in user and admin of at least one org                                        ;
      [:div {:style {:display "flex" :justify-content "center" :padding "2rem 8rem"}}
       [confirmation-modal]
       [:div {:style {:flex 1 :padding "1rem"}}
        [org-list]]
       [:div {:style {:display        "flex"
                      :flex           2
                      :flex-direction "column"
                      :height         "100%"
                      :padding        "1rem"}}
        [org-settings (some (fn [{:keys [opt-id] :as org}] (when (= opt-id @*org) org)) @orgs)]
        [org-users-list]]])))
