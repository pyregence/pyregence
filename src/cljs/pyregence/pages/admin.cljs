(ns pyregence.pages.admin
  (:require [cljs.reader                    :as edn]
            [clojure.core.async             :refer [go <!]]
            [clojure.string                 :as str :refer [blank?]]
            [goog.string                    :refer [format]]
            [herb.core                      :refer [<class]]
            [pyregence.components.common    :refer [check-box labeled-input simple-form]]
            [pyregence.components.messaging :refer [confirmation-modal set-message-box-content! toast-message!]]
            [pyregence.styles               :as $]
            [pyregence.utils                :as u]
            [reagent.core                   :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Organization Role Enumeration
(def roles [{:opt-id 1 :opt-label "Admin"}
            {:opt-id 2 :opt-label "Member"}
            {:opt-id 3 :opt-label "Pending"}])

;; Organization Object Properties
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
  orgs (r/atom []))
(defonce ^{:doc "Vector of the org users associated with the currently selected organization."}
  org-users (r/atom []))
(defonce ^{:doc "The email in the Add User form."}
  new-user-email (r/atom ""))
(defonce ^{:doc "The full name in the Add User form."}
  new-user-full-name (r/atom ""))
(defonce ^{:doc "The password in the Add User form."}
  new-user-password (r/atom ""))
(defonce ^{:doc "The confirm password in the Add User form."}
  new-user-re-password (r/atom ""))
(defonce ^{:doc "The pending state of a \"New User\" form submission."}
  pending-new-user-submission? (r/atom false))

;; Current User Selections
(defonce ^{:doc "The user id of the logged in user."}
  _user-id  (r/atom -1))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-org-by-id [id]
  (->> @orgs
       (filter #(= id (:opt-id %)))
       first))

(defn- set-selected-org! [org]
  (reset! *org-id            (:opt-id        org))
  (reset! *org-name          (:opt-label     org))
  (reset! *org-email-domains (:email-domains org))
  (reset! *org-auto-accept?  (:auto-accept?  org))
  (reset! *org-auto-add?     (:auto-add?     org)))

(defn- set-selected-org-by-id! [id]
  (set-selected-org! (get-org-by-id id)))

(defn- reset-add-user-form! []
  (reset! pending-new-user-submission? false)
  (reset! new-user-email "")
  (reset! new-user-full-name "")
  (reset! new-user-password "")
  (reset! new-user-re-password ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-org-users-list [org-id]
  (go
    (reset! org-users
            (edn/read-string (:body (<! (u/call-clj-async! "get-org-users-list" org-id)))))
    (reset-add-user-form!)))

(defn- get-org-list []
  (go
    (reset! orgs (edn/read-string (:body (<! (u/call-clj-async! "get-org-list" @_user-id))))) ; TODO get from session on the back end
    ;; find the current org, by id, in the updated @orgs vector or fallback to the org on the first index
    (set-selected-org! (or (get-org-by-id @*org-id) (first @orgs)))
    (get-org-users-list @*org-id)))

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

(defn- add-new-user! []
  (go
    (if (:success (<! (u/call-clj-async! "add-new-user"
                                         @new-user-email
                                         @new-user-full-name
                                         @new-user-password
                                         {:org-id          @*org-id
                                          :restrict-email? false})))
      (do
        (toast-message! ["Your account has been created successfully."])
        (reset-add-user-form!))
      (toast-message! ["An error occurred while registering."
                       "Please contact support@pyregence.org for help."]))))

(defn- register-new-user! []
  (go
    (reset! pending-new-user-submission? true)
    (let [email-chan (u/call-clj-async! "user-email-taken" @new-user-email)
          errors     (remove nil?
                            [(when (u/missing-data? @new-user-email @new-user-full-name @new-user-password @new-user-re-password)
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
          (add-new-user!)))))

(defn- update-org-user! [email new-name]
  (go
    (<! (u/call-clj-async! "update-user-name" email new-name))
    (toast-message! (str "The user " new-name " with the email " email  " has been updated."))))

(defn- add-existing-user! [email]
  (go
    (let [res (<! (u/call-clj-async! "add-org-user" @*org-id email))]
      (if (:success res)
        (do (get-org-users-list @*org-id)
            (toast-message! (str "User " email " added.")))
        (toast-message! (:body res))))))

(defn- update-org-user-role! [org-user-id role-id]
  (go
    (<! (u/call-clj-async! "update-org-user-role" org-user-id role-id))
    (get-org-users-list @*org-id)
    (toast-message! "User role updated.")))

(defn- remove-org-user! [org-user-id]
  (go
    (<! (u/call-clj-async! "remove-org-user" org-user-id))
    (get-org-users-list @*org-id)
    (toast-message! "User removed.")))

(defn- select-org [org-id]
  (set-selected-org-by-id! org-id)
  (get-org-users-list @*org-id))

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

(defn- handle-add-user []
  (let [message (str "Are you sure that you want to add the following new user\n"
                     "as a Member of the \"%s\" organization?\n\n"
                     "%s <%s>")]
    (set-message-box-content! {:title  "Add New User"
                               :body   (format message @*org-name @new-user-full-name @new-user-email)
                               :action #(register-new-user!)})))

(defn- handle-add-existing-user [email]
  (let [message "Are you sure that you want to add the user with email \"%s\" as a Member of the \"%s\" organization?"]
    (when-not (blank? email)
      (set-message-box-content! {:title  "Add Existing User"
                                 :body   (format message email @*org-name)
                                 :action #(add-existing-user! email)}))))

(defn- handle-edit-user [email prev-name updated-name-state]
  (go
    (let [message (str "Are you sure that you want to update the user's name from %s to %s?")]
     (when-not (blank? @updated-name-state)
       (set-message-box-content! {:title     "Update Username"
                                  :body      (format message prev-name @updated-name-state)
                                  :action    #(go
                                                (do (<! (update-org-user! email @updated-name-state))
                                                    (get-org-users-list @*org-id)))
                                  :cancel-fn #(reset! updated-name-state prev-name)})))))

(defn- handle-remove-user [user-id user-name]
  (let [message "Are you sure that you want to remove user \"%s\" from the \"%s\" organization?"]
    (set-message-box-content! {:title  "Delete User"
                               :body   (format message user-name @*org-name)
                               :action #(remove-org-user! user-id)})))

(defn- handle-update-role-id [rid uid user-name]
  (let [message   "Are you sure you want to change the role of user \"%s\" to \"%s\"?"
        role-name (->> roles
                       (filter (fn [role] (= rid (role :opt-id))))
                       (first)
                       (:opt-label))]
    (set-message-box-content! {:title  "Update User Role"
                               :body   (format message user-name role-name)
                               :action #(update-org-user-role! uid rid)})))

(defn- handle-update-org-settings [oid org-name email-domains auto-add auto-accept]
  (let [message "Are you sure you wish to update the settings for the \"%s\" organization? Saving these changes will overwrite any previous settings."]
    (set-message-box-content! {:title  "Update Settings"
                               :body   (format message @*org-name)
                               :action #(update-org-info! oid org-name email-domains auto-add auto-accept)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- org-item [org-id org-name]
  [:label {:style    ($org-item (= org-id @*org-id))
           :on-click #(select-org org-id)}
   org-name])

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

(defn- user-item [org-user-id user-name email role-id]
  (r/with-let [_role-id          (r/atom role-id)
               updated-name      (r/atom user-name)
               edit-mode-enabled (r/atom false)]
    [:div {:style {:align-items "center" :display "flex" :padding ".25rem"}}
     [:div {:style {:display "flex" :flex-direction "column"}}
      (if @edit-mode-enabled
        [labeled-input "" updated-name {:disabled? (not @edit-mode-enabled)}]
        [:label @updated-name])
      [:label email]]
     [:div {:style ($/combine ($/align :block :right) {:display "flex"})}
      (if @edit-mode-enabled
        [:<>
         [:input {:class    (<class $/p-form-button)
                  :type     "button"
                  :value    "Cancel"
                  :on-click #(do (reset! updated-name user-name)
                                 (reset! edit-mode-enabled false))}]
         [:input {:class    (<class $/p-form-button)
                  :type     "button"
                  :value    "Save"
                  :on-click #(go
                               (do
                                 (<! (handle-edit-user email user-name updated-name))
                                 (reset! edit-mode-enabled false)))}]]
        [:input {:class    (<class $/p-form-button)
                 :type     "button"
                 :value    "Edit User"
                 :on-click #(swap! edit-mode-enabled not)}])
      [:input {:class    (<class $/p-form-button)
               :style    ($/combine ($/align :block :right) {:margin-left "0.5rem"})
               :type     "button"
               :value    "Remove User"
               :on-click #(handle-remove-user org-user-id user-name)}]
      [:select {:class     (<class $/p-bordered-input)
                :style     {:margin "0 .25rem 0 1rem" :height "2rem"}
                :value     @_role-id
                :on-change #(reset! _role-id (u/input-int-value %))}
       (map (fn [{role-id :opt-id role-name :opt-label}]
              [:option {:key role-id :value role-id} role-name])
            roles)]
      [:input {:class    (<class $/p-form-button)
               :style    ($/combine ($/align :block :right) {:margin-left "0.5rem"})
               :type     "button"
               :value    "Update Role"
               :on-click #(handle-update-role-id @_role-id org-user-id user-name)}]]]))

(defn- org-users-list []
  (r/with-let [existing-email (r/atom "")]
    [:div#org-users {:style {:margin-top "2rem"}}
     [:div {:style ($/action-box)}
      [:div {:style ($/action-header)}
       [:label {:style ($/padding "1px" :l)} "Users"]]
      [:div {:style {:overflow "auto"}}
       [:div {:style {:display "flex" :flex-direction "column" :padding "1.5rem"}}
        [:div {:style {:align-items "flex-end" :display "flex"}}
         [labeled-input "Existing User Email Address" existing-email]
         [:input {:class    (<class $/p-form-button)
                  :style    ($/combine ($/align :block :right) {:margin-left "0.5rem"})
                  :type     "button"
                  :value    "Add User"
                  :on-click (fn []
                              (handle-add-existing-user @existing-email)
                              (reset! existing-email ""))}]]
        [:hr]
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
          (nil? @*org-id))              ; User is not an admin of any org
      (do (u/redirect-to-login! "/admin")
          nil)

      (= @*org-id -1)                   ; get-org-list has not completed yet
      [:div {:style {:display "flex" :justify-content "center"}}
       [:h1 "Loading..."]]

      :else                             ; Logged-in user and admin of at least one org
      [:div {:style {:display "flex" :justify-content "center" :padding "2rem 8rem"}}
       [confirmation-modal]
       [:div {:style {:flex 1 :padding "1rem"}}
        [org-list]]
       [:div {:style {:display        "flex"
                      :flex           2
                      :flex-direction "column"
                      :height         "100%"
                      :padding        "1rem"}}
        [org-settings]
        [org-user-add-form]
        [org-users-list]]])))
