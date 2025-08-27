(ns pyregence.pages.admin
  (:require [cljs.reader                    :as edn]
            [clojure.core.async             :refer [go <!]]
            [clojure.string                 :refer [blank?]]
            [goog.string                    :refer [format]]
            [herb.core                      :refer [<class]]
            [pyregence.components.common    :refer [check-box labeled-input simple-form]]
            [pyregence.components.messaging :refer [message-box-modal set-message-box-content! toast-message!]]
            [pyregence.styles               :as $]
            [pyregence.utils.async-utils    :as u-async]
            [pyregence.utils.data-utils     :as u-data]
            [pyregence.utils.dom-utils      :as u-dom]
            [reagent.core                   :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Update role id designiations
;; Organization Role Enumeration
(def ^:private roles [{:opt-id 1 :opt-label "Super Admin"         :user-role "super_admin"}
                      {:opt-id 2 :opt-label "Organization Admin"  :user-role "organization_admin"}
                      {:opt-id 3 :opt-label "Organization Member" :user-role "organization_member"}
                      {:opt-id 4 :opt-label "Account Manager"     :user-role "account_manager"}
                      {:opt-id 5 :opt-label "Member"              :user-role "member"}])

(def ^:private membership-statuses [{:opt-id 1 :opt-label "None"     :status "none"}
                                    {:opt-id 2 :opt-label "Pending"  :status "pending"}
                                    {:opt-id 3 :opt-label "Accepted" :status "accepted"}])

;; Organization Object Properties
(defonce ^{:doc "The currently selected organization."}
  *org (r/atom nil))
(defonce ^{:doc "The id of the currently selected organization."}
  *org-id (r/atom -1))
(defonce ^{:doc "The display name of the currently selected organization."}
  *org-name (r/atom ""))
(defonce ^{:doc "The comma separated email domains of the selected organization."}
  *org-email-domains (r/atom ""))
(defonce ^{:doc "A boolean indicating if a user should be auto accepted as a member of the selected organization."}
  *org-auto-accept? (r/atom false))
(defonce ^{:doc "A boolean indicating if a user should be auto added to the selected organization."}
  *org-auto-add? (r/atom false))

;; Current Organization Selections
(defonce ^{:doc "Vector of organizations where the current user is an admin."}
  *orgs (r/atom []))
(defonce ^{:doc "Vector of the org users associated with the currently selected organization."}
  *org-members (r/atom []))
(defonce ^{:doc "Vector of non-member users available to link with the currently selected organization."}
  *org-non-members (r/atom []))

;; Current User Selections
(defonce ^{:doc "The user id of the logged in user."}
  _user-id (r/atom -1))
(defonce ^{:doc "The user role of the logged in user."}
  _user-role (r/atom -1))

;; Add User form state
(defonce ^{:doc "The email in the Add User form."}
  new-user-email (r/atom ""))
(defonce ^{:doc "The full name in the Add User form."}
  new-user-full-name (r/atom ""))
(defonce ^{:doc "The password in the Add User form."}
  new-user-password (r/atom ""))
(defonce ^{:doc "The confirm password in the Add User form."}
  new-user-re-password (r/atom ""))
(defonce ^{:doc "The pending state of a organization list request."}
  pending-get-organizations? (r/atom false))
(defonce ^{:doc "The pending state of a \"New User\" form submission."}
  pending-new-user-submission? (r/atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-org-by-id [org-list org-id]
  (->> org-list
       (filter #(= org-id (:org-id %)))
       first))

(defn- set-selected-org! [org]
  (reset! *org               org)
  (reset! *org-id            (:org-id        org))
  (reset! *org-name          (:org-name      org))
  (reset! *org-email-domains (:email-domains org))
  (reset! *org-auto-accept?  (:auto-accept?  org))
  (reset! *org-auto-add?     (:auto-add?     org)))

(defn- reset-add-user-form! []
  (reset! pending-new-user-submission? false)
  (reset! new-user-email "")
  (reset! new-user-full-name "")
  (reset! new-user-password "")
  (reset! new-user-re-password ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-org-member-users [org-id]
  (go
    (let [response (<! (u-async/call-clj-async! "get-org-member-users" org-id))]
      (reset! *org-members (if (:success response)
                             (edn/read-string (:body response))
                             [])))))

(defn- get-organizations [user-role]
  (reset! pending-get-organizations? true)
  (go
   (let [api-route (if (= user-role "super_admin")
                     "get-all-organizations" ; super admin can see all orgs
                     "get-current-user-organization") ; org admin can just see their org
         response  (<! (u-async/call-clj-async! api-route))]
      (reset! *orgs (if (:success response)
                      (->> (:body response)
                           (edn/read-string))
                      []))
      (reset! pending-get-organizations? false)

      ;; Skip on returned zero-length list
      (when (> (count @*orgs) 0)
        (set-selected-org! (or (get-org-by-id @*orgs @*org-id) (first @*orgs)))
        (get-org-member-users @*org-id)))))

(defn- update-org-info! [opt-id org-name email-domains auto-add? auto-accept?]
  (go
    (<! (u-async/call-clj-async! "update-org-info"
                                 opt-id
                                 org-name
                                 email-domains
                                 auto-add?
                                 auto-accept?))
    (get-organizations @_user-role)
    (toast-message! "Organization info updated.")))

(defn- add-new-user-and-assign-to-*org! []
  (go
    (toast-message! "Creating new account. This may take a moment...")
    (if (and (:success (<! (u-async/call-clj-async! "add-new-user"
                                                    @new-user-email
                                                    @new-user-full-name
                                                    @new-user-password
                                                    {:org-id @*org-id})))
             (:success (<! (u-async/call-clj-async! "send-email" @new-user-email :new-user))))
      (do
        (toast-message! ["Your account has been created successfully."
                         "Please check your email for a link to complete registration."])
        (reset-add-user-form!)
        (get-org-member-users @*org-id))
      (toast-message! ["An error occurred while registering."
                       "Please contact support@pyregence.org for help."]))))

(defn- register-new-user! []
  (go
    (reset! pending-new-user-submission? true)
    (let [email-chan (u-async/call-clj-async! "user-email-taken" @new-user-email)
          errors     (remove nil?
                             [(when (u-data/missing-data? @new-user-email @new-user-full-name @new-user-password @new-user-re-password)
                                "You must fill in all required information to continue.")

                              (when (< (count @new-user-password) 8)
                                "Your password must be at least 8 charactors long.")

                              (when-not (= @new-user-password @new-user-re-password)
                                "The passwords you have entered do not match.")

                              (when (:success (<! email-chan))
                                (str "A user with the email '" @new-user-email "' has already been created."))])]
      (if (pos? (count errors))
        (do (toast-message! errors)
            (reset! pending-new-user-submission? false))
        (add-new-user-and-assign-to-*org!)))))

(defn- update-org-user! [email new-name]
  (go
    (let [res (<! (u-async/call-clj-async! "update-user-name" email new-name))]
      (if (:success res)
        (toast-message! (str "The user " new-name " with the email " email  " has been updated."))
        (toast-message! (:body res))))))

(defn- add-existing-user! [email]
  (go
    (let [res (<! (u-async/call-clj-async! "add-org-user" @*org-id email))]
      (if (:success res)
        (do (get-org-member-users @*org-id)
            (toast-message! (str "User " email " added.")))
        (toast-message! (:body res))))))

(defn- update-org-user-role! [user-id new-role]
  (go
    (let [res (<! (u-async/call-clj-async! "update-org-user-role" user-id new-role))]
      (if (:success res)
        (do (get-org-member-users @*org-id)
            (toast-message! "User role updated."))
        (toast-message! (:body res))))))

(defn- update-org-user-membership-status! [user-id new-status]
  (go
    (let [res (<! (u-async/call-clj-async! "update-user-org-membership-status" user-id new-status))]
      (if (:success res)
        (do (get-org-member-users @*org-id)
            (toast-message! "User membership status updated."))
        (toast-message! (:body res))))))

(defn- remove-org-user! [user-id]
  (go
    (let [res (<! (u-async/call-clj-async! "remove-org-user" user-id))]
      (if (:success res)
        (do (get-org-member-users @*org-id)
            (toast-message! "User removed from organization."))
        (toast-message! (:body res))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $org-item [selected?]
  (merge {:border-bottom (str "1px solid " ($/color-picker :brown))
          :padding       ".75rem"}
         (when selected? {:background-color ($/color-picker :yellow 0.3)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Click Event Handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- handle-select-org! [org-id]
  (set-selected-org! (get-org-by-id @*orgs org-id))
  (get-org-member-users org-id)
  (reset-add-user-form!))

(defn- handle-add-user []
  (let [message (str "Are you sure that you want to add the following new user\n"
                     "as a Member of the \"%s\" organization?\n\n"
                     "%s <%s>")]
    (set-message-box-content! {:mode   :confirm
                               :title  "Add New User"
                               :body   (format message @*org-name @new-user-full-name @new-user-email)
                               :action #(register-new-user!)})))

(defn- handle-add-existing-user [org-id email]
  (let [message "Are you sure that you want to add the user with email \"%s\" as a Member of the \"%s\" organization?"]
    (when-not (blank? email)
      (set-message-box-content! {:mode   :confirm
                                 :title  "Add Existing User"
                                 :body   (format message email @*org-name)
                                 :action #(do
                                            (add-existing-user! email)
                                            (get-org-member-users org-id))}))))

(defn- handle-edit-user [email prev-name updated-name-state]
  (go
    (let [message (str "Are you sure that you want to update the user's name from %s to %s?")]
      (when-not (blank? @updated-name-state)
        (set-message-box-content! {:mode      :confirm
                                   :title     "Update Username"
                                   :body      (format message prev-name @updated-name-state)
                                   :action    #(go
                                                 (<! (update-org-user! email @updated-name-state)))
                                   :cancel-fn #(reset! updated-name-state prev-name)})))))

(defn- handle-remove-user [user-id user-name]
  (let [message "Are you sure that you want to remove user \"%s\" from the \"%s\" organization?"]
    (set-message-box-content! {:mode   :confirm
                               :title  "Delete User"
                               :body   (format message user-name @*org-name)
                               :action #(remove-org-user! user-id)})))

(defn- handle-update-role-id [role-id user-id user-name]
  (let [message              "Are you sure you want to change the role of user \"%s\" to \"%s\"?"
        new-role             (->> roles
                                  (filter (fn [role] (= role-id (:opt-id role))))
                                  (first))
        new-role-db-format   (:user-role new-role)
        new-role-pretty-text (:opt-label new-role)]
    (set-message-box-content! {:mode   :confirm
                               :title  "Update User Role"
                               :body   (format message user-name new-role-pretty-text)
                               :action #(update-org-user-role! user-id new-role-db-format)})))

(defn- handle-update-membership-status [status-id user-id user-name]
  (let [message                "Are you sure you want to change the membership status of user \"%s\" to \"%s\"?"
        new-status             (->> membership-statuses
                                    (filter (fn [m] (= status-id (:opt-id m))))
                                    (first))
        new-status-db-format   (:status new-status)
        new-status-pretty-text (:opt-label new-status)]
    (set-message-box-content! {:mode   :confirm
                               :title  "Update User Membership Status"
                               :body   (format message user-name new-status-pretty-text)
                               :action #(update-org-user-membership-status! user-id new-status-db-format)})))

(defn- handle-update-org-settings [oid org-name email-domains auto-add auto-accept]
  (let [message "Are you sure you wish to update the settings for the \"%s\" organization? Saving these changes will overwrite any previous settings."]
    (set-message-box-content! {:mode   :confirm
                               :title  "Update Settings"
                               :body   (format message @*org-name)
                               :action #(update-org-info! oid org-name email-domains auto-add auto-accept)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- org-item [org-id org-name]
  [:label {:style    ($org-item (= org-id @*org-id))
           :on-click #(handle-select-org! org-id)}
   org-name])

(defn- org-list [orgs]
  [:div#org-list
   [:div {:style ($/action-box)}
    [:div {:style ($/action-header)}
     [:label {:style ($/padding "1px" :l)} "Organization List"]]
    [:div {:style {:overflow "auto"}}
     [:div {:style {:display "flex" :flex-direction "column"}}
      (doall (map (fn [{:keys [org-id org-name]}]
                    ^{:key org-id} [org-item org-id org-name])
                  orgs))]]]])

(defn- org-settings [_]
  [:div#org-settings {:style ($/action-box)}
   [:div {:style ($/action-header)}
    [:label {:style ($/padding "1px" :l)} "Settings"]]
   [:div {:style {:overflow "auto"}}
    [:div {:style {:display "flex" :flex-direction "column" :padding "1.5rem"}}
     [labeled-input "Name" *org-name]
     [labeled-input "Email Domains (comma separated)" *org-email-domains]
     [check-box "Auto add user to organization" *org-auto-add?]
     [check-box "Auto accept user as member" *org-auto-accept?]
     [:input {:class    (<class $/p-form-button :large)
              :style    ($/combine ($/align :block :center) {:margin-top ".5rem"})
              :type     "button"
              :value    "Update"
              :on-click #(handle-update-org-settings
                          @*org-id
                          @*org-name
                          @*org-email-domains
                          @*org-auto-add?
                          @*org-auto-accept?)}]]]])

(defn- org-user-add-form []
  [:div {:style ($/combine ($/disabled-group @pending-new-user-submission?)
                           {:margin-top "2rem"})}
   [simple-form
    "Add User"
    "Add New User"
    [["Email"            new-user-email       "email"    "email"]
     ["Full Name"        new-user-full-name   "text"     "name"]
     ["Password"         new-user-password    "password" "new-password"]
     ["Confirm Password" new-user-re-password "password" "confirm-password"]]
    handle-add-user]])

(defn- user-item [user-id full-name email user-role user-membership-status]
  (r/with-let [_role-id              (r/atom (->> roles
                                                  (filter (fn [role-entry] (= user-role (:user-role role-entry))))
                                                  (first)
                                                  (:opt-id)))
               _membership-status-id (r/atom (->> membership-statuses
                                                  (filter (fn [m] (= user-membership-status (:status m))))
                                                  (first)
                                                  (:opt-id)))
               full-name-update      (r/atom full-name)
               edit-mode-enabled     (r/atom false)]
    [:div {:style {:align-items "center" :display "flex" :padding ".25rem"}}
     [:div {:style {:display "flex" :flex-direction "column"}}
      (if @edit-mode-enabled
        [labeled-input "" full-name-update {:disabled? (not @edit-mode-enabled)}]
        [:label @full-name-update])
      [:label email]]
     [:div {:style ($/combine ($/align :block :right) {:display "flex"})}
      (if @edit-mode-enabled
        [:<>
         [:input {:class    (<class $/p-form-button)
                  :type     "button"
                  :value    "Cancel"
                  :on-click #(do (reset! full-name-update full-name)
                                 (reset! edit-mode-enabled false))}]
         [:input {:class    (<class $/p-form-button)
                  :type     "button"
                  :value    "Save"
                  :on-click #(go
                               (do
                                 (<! (handle-edit-user email full-name full-name-update))
                                 (reset! edit-mode-enabled false)))}]]
        [:input {:class    (<class $/p-form-button)
                 :type     "button"
                 :value    "Edit User"
                 :on-click #(swap! edit-mode-enabled not)}])
      ;; Remove User button
      [:input {:class    (<class $/p-form-button)
               :style    ($/combine ($/align :block :right) {:margin-left "0.5rem"})
               :type     "button"
               :value    "Remove User"
               :on-click #(handle-remove-user user-id full-name)}]
      ;; User Role dropdown
      [:select {:class     (<class $/p-bordered-input)
                :style     {:margin "0 .25rem 0 1rem" :height "2rem"}
                :value     @_role-id
                :on-change #(reset! _role-id (u-dom/input-int-value %))}
       (map (fn [{role-id :opt-id role-name :opt-label}]
              [:option {:key role-id :value role-id} role-name])
            roles)]
      ;; Update Role button
      [:input {:class    (<class $/p-form-button)
               :style    ($/combine ($/align :block :right) {:margin-left "0.5rem"})
               :type     "button"
               :value    "Update Role"
               :on-click #(handle-update-role-id @_role-id user-id full-name)}]
      ;; Membership Status dropdown
      [:select {:class     (<class $/p-bordered-input)
                :style     {:margin "0 .25rem 0 1rem" :height "2rem"}
                :value     @_membership-status-id
                :on-change #(reset! _membership-status-id (u-dom/input-int-value %))}
       (map (fn [{membership-status-id :opt-id status-name :opt-label}]
              [:option {:key membership-status-id :value membership-status-id} status-name])
            membership-statuses)]
      ;; Update Role button
      [:input {:class    (<class $/p-form-button)
               :style    ($/combine ($/align :block :right) {:margin-left "0.5rem"})
               :type     "button"
               :value    "Update Status"
               :on-click #(handle-update-membership-status @_membership-status-id user-id full-name)}]]]))

(defn- org-users-list [org-member-users]
  [:div#org-users {:style {:margin-top "2rem"}}
   [:div {:style ($/action-box)}
    [:div {:style ($/action-header)}
     [:label {:style ($/padding "1px" :l)} "Member User-List"]]
    [:div {:style {:overflow "auto"}}
     [:div {:style {:display "flex" :flex-direction "column" :padding "1.5rem"}}
      (doall (map (fn [{:keys [user-id full-name email user-role membership-status]}]
                    ^{:key user-id}
                    [user-item user-id full-name email user-role membership-status])
                  org-member-users))]]]])

;; TODO add in a UI component to show a user's membership_status
(defn root-component
  "The root component for the /admin page.
   Displays the organization list, settings, and users."
  [{:keys [user-id user-role]}]
  (reset! _user-id user-id)
  (reset! _user-role user-role)
  (get-organizations user-role)
  (fn [_]
    (if @pending-get-organizations?
      ;; Organizations are still loading
      [:div {:style {:display "flex" :justify-content "center"}}
       [:h1 "Loading..."]]
      ;; Organizations have been loaded
      [:div {:style {:display "flex" :justify-content "center" :padding "2rem 8rem"}}
       [message-box-modal]
       [:div {:style {:flex 1 :padding "1rem"}}
        [org-list @*orgs]]
       [:div {:style {:display        "flex"
                      :flex           3
                      :flex-direction "column"
                      :height         "100%"
                      :padding        "1rem"}}
        [org-settings]
        [org-user-add-form]
        [org-users-list @*org-members]]])))
