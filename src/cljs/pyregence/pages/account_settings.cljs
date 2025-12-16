(ns pyregence.pages.account-settings
  (:require
   [clojure.core.async                                  :refer [<! go]]
   [clojure.set                                         :as set]
   [clojure.string                                      :as str]
   [pyregence.components.messaging                      :refer [toast-message!]]
   [pyregence.components.settings.account-settings      :as as]
   [pyregence.components.settings.email                 :as email]
   [pyregence.components.settings.fetch                 :refer [get-orgs!
                                                                get-user-name!
                                                                get-users!
                                                                update-own-user-name!]]
   [pyregence.components.settings.nav-bar               :as nav-bar]
   [pyregence.components.settings.organization-settings :as os]
   [pyregence.components.settings.roles                 :as roles]
   [pyregence.components.settings.unaffilated-members   :as um]
   [pyregence.styles                                    :as $]
   [pyregence.utils.async-utils                         :as u-async]
   [pyregence.utils.dom-utils                           :refer [input-value]]
   [reagent.core                                        :as r]))

(defn orgs->org->id
  [orgs]
  (reduce
   (fn [org-id->org {:keys [org-id org-name email-domains auto-accept? auto-add?] :as org}]
     (assoc org-id->org org-id
            (assoc org
                   :unsaved-auto-accept? auto-accept?
                   :unsaved-auto-add?    auto-add?
                   :unsaved-org-name     org-name
                   ;; NOTE this mapping is used to keep track of the email
                   :og-email->email (reduce
                                     (fn [m e]
                                       (assoc m e {:email e}))
                                     {}
                                     (->>
                                      (str/split email-domains #",")
                                      (map #(str/replace % "@" "")))))))
   {}
   orgs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component
  "The root component of the /account-settings page."
  [{:keys [user-role password-set-date]}]
  (let [org-id->org     (r/atom nil)
        user-name       (r/atom nil)
        users           (r/atom nil)
        selected-log    (r/atom ["Account Settings"])
        users-selected? (r/atom false)]
    (r/create-class
     {:display-name "account-settings"
      :component-did-mount
      #(go
         (reset! users (<! (get-users! user-role)))
         (reset! user-name (<! (get-user-name!)))
         (reset! org-id->org (orgs->org->id (<! (get-orgs! user-role)))))
      :reagent-render
      (fn [{:keys [user-role user-email]}]
        [:div
         {:style {:height         "100%"
                  :display        "flex"
                  :flex-direction "column"
                  :font-family    "Roboto"}}
        ;; TODO this mock `:nav` with actual upper nav bar, this will happen in another PR.
         [:nav  {:style {:display         "flex"
                         :justify-content "center"
                         :align-items     "center"
                         :width           "100%"
                         :height          "33px"
                         :background      ($/color-picker :yellow)}} "mock nav"]
         (let [tabs             (nav-bar/tab-data->tabs
                                 {:selected-log  selected-log
                                  :organizations (vals @org-id->org)
                                  :user-role     user-role})
               selected->tab-id (fn [selected]
                                  (:id (first ((group-by :selected? tabs) selected))))
               selected         (-> @selected-log last)
               selected-page    (selected->tab-id selected)
               on-click-apply-update-users

               (fn [get-selected-users-emails-fn]
                 (fn [update-user-info-by-email opt-type opt->display]
                   (fn [new-user-info]
                     (fn []
                       (let [emails (get-selected-users-emails-fn)]
                         ;; TODO this needs error handling.
                         (update-user-info-by-email new-user-info emails)
                         ;; TODO instead of this hacky sleep i think we have two options,
                         ;; first, we have the update function return the users, this seems ideal. the second is,
                         ;; we get the success from the update function and we then poll the users.
                         ;; TODO this could use the core async timeout instead.
                         (js/setTimeout (fn [] (go (reset! users (<! (get-users! user-role))))) 3000)
                         (toast-message!
                           ;;TODO make this handle plural case e.g roles and statues.
                          (str (str/join ", " emails)  " updated " opt-type " to " (opt->display new-user-info) ".")))))))]
           [:div {:style {:display        "flex"
                          :flex-direction "row"
                          :height         "100%"
                          :background     ($/color-picker :lighter-gray)}}
            [nav-bar/main tabs]
            (case selected-page
              "Account Settings"
              [as/main {:password-set-date password-set-date
                        :role-type         user-role
                        :user-name         @user-name
                        :email-address     user-email
                        :on-change-update-user-name (fn [e]
                                                      (reset! user-name (input-value e)))
                        :on-click-save-user-name (fn []
                                                   (update-own-user-name! @user-name))}]

              "Organization Settings"
              [os/main
               (let [;;TODO this selection should probably be resolved earlier on or happen a different way aka not create org-id->org if only one org
                     selected  (if (= user-role "super_admin")
                                 selected
                                 (-> @org-id->org keys first))
                     {:keys [unsaved-org-name
                             org-id auto-add?
                             org-name
                             og-email->email
                             auto-accept?
                             unsaved-auto-accept?
                             unsaved-auto-add?
                             unsaved-org-name-support-message]} (@org-id->org selected)
                     roles (->> user-role roles/role->roles-below (filter roles/organization-roles))
                     selected-orgs-users
                     (if-not (= user-role "super_admin")
                       ;;TODO check if this shouldn't happen in the db or server instead.
                       (map #(set/rename-keys % {:full-name :name :membership-status :org-membership-status}) @users)
                       (filter (fn [{:keys [user-role organization-name]}]
                                 (and
                                  ;;TODO this conditional should be based on the og-id or org-unique name
                                  (= organization-name org-name)
                                  (#{"organization_admin" "organization_member"} user-role))) @users))]
                 {:org-id                      org-id
                  :default-role-option         (first roles)
                  :og-email->email             og-email->email
                  :users                       selected-orgs-users
                  ;;TODO get selected-rows
                  :users-selected?             users-selected?
                  :unsaved-org-name            unsaved-org-name
                  :unsaved-org-name-support-message
                  unsaved-org-name-support-message
                  :on-click-apply-update-users on-click-apply-update-users
                  :on-click-add-email          (fn [] (swap! org-id->org assoc-in [selected :og-email->email (random-uuid)] {:email ""}))
                  :on-delete-email             (fn [og-email]
                                                 (fn [_]
                                       ;;TODO this means i might have to filter out empty ones.
                                                   (swap! org-id->org update-in [selected :og-email->email] dissoc og-email)))
                  :on-change-email-name
                  (fn [og-email]
                    (fn [e]
                      (let [new-email (.-value (.-target e))]
                        (swap! org-id->org assoc-in [selected :og-email->email og-email :email] new-email))))

                  ;;TODO on "save change" when nothing has changed, it doesn't do anything (it should probably help the user or re-save).
                  :on-click-save-changes
                  (fn []
                  ;; TODO consider adding a toast on success and something if there is a long delay.
                    (let [checked-og-email->email
                          (->> og-email->email
                               (reduce-kv
                                (fn [m id {:keys [email]}]

                                  (assoc m id {:email email :invalid? (not (email/valid-email-domain? email))}))
                                {}))
                          ;;TODO unifiy the ways were collecting support/error messages here.
                          invalid-email-domains? (->> checked-og-email->email vals (some :invalid?))
                          organization-name-support-message (when (str/blank? unsaved-org-name) "Name cannot be blank.")]
                      (swap! org-id->org assoc-in [selected :og-email->email] checked-og-email->email)

                      (cond
                        invalid-email-domains?
                        ;;TODO find a better when then using a cond with nil
                        nil

                        organization-name-support-message
                        (swap! org-id->org assoc-in [selected :unsaved-org-name-support-message] organization-name-support-message)
                        :else
                        (go
                          (let [unsaved-email-domains (->> checked-og-email->email
                                                           vals
                                                           (map :email)
                                                           (map #(str "@" %))
                                                           (str/join ","))

                                {:keys [success]}
                                (<! (u-async/call-clj-async! "update-org-info"
                                                             org-id
                                                             unsaved-org-name
                                                             unsaved-email-domains
                                                             unsaved-auto-add?
                                                             unsaved-auto-accept?))]
                          ;; TODO if not success case.
                            (if success
                              (let [{:keys [org-name email-domains]} (@org-id->org org-id)]
                                ;; TODO Below is a good example of how we have the same relationship in our ratoms, as we do in our db, which leads me to believe
                                ;; we need a client db that can mirror (in query language and reltionship semantics) our backend db.
                                (swap! users #(map
                                               (fn [{:keys [organization-name] :as user}]
                                                 (if (= organization-name org-name)
                                                   (assoc user :organization-name unsaved-org-name)
                                                   user))
                                               %))
                                (swap! org-id->org
                                       (fn [o]
                                         (-> o
                                             (assoc-in [org-id :org-name] unsaved-org-name)
                                             (assoc-in [org-id :email-domains] unsaved-email-domains))))
                                (let [new-name?        (not= org-name unsaved-org-name)
                                      new-email?       (not= email-domains unsaved-email-domains)
                                      new-auto-accept? (not= auto-accept? unsaved-auto-accept?)
                                      new-auto-add?    (not= auto-add?    unsaved-auto-add?)]
                                    ;; TODO instead of three separate toasts maybe it would be better to have one that just said everything?
                                    ;; TODO these messages could probably be improved.
                                    ;; TODO these toasts dont work on the second time, e.g change auto-add save you correctly get a toast, change again, and save and you don't.
                                    ;;      Because, whats supposed to be the original data, e.g auto-add? isn't being updated.
                                  (when new-name? (toast-message! (str "Updated Organization Name : " unsaved-org-name)))
                                  (when new-email? (toast-message! (str "Updated Email Domains: " unsaved-email-domains)))
                                  (when new-auto-accept? (toast-message! (str "Updated Auto Accept: " (if unsaved-auto-accept? "On" "Off"))))
                                  (when new-auto-add? (toast-message! (str "Updated Auto Add: " (if unsaved-auto-add? "On" "Off"))))))))))))

                  :on-change-organization-name
                  (fn [e]
                    (swap! org-id->org
                           assoc-in
                           [selected :unsaved-org-name]
                           (.-value (.-target e))))
                  :auto-accept? auto-accept?
                  :unsaved-auto-accept? unsaved-auto-accept?
                  :on-change-auto-accept-user-as-org-member
                  #(swap! org-id->org update-in [selected :unsaved-auto-accept?] not)
                  :auto-add? auto-add?
                  :unsaved-auto-add? unsaved-auto-add?
                  :on-change-auto-add-user-as-org-member
                  #(swap! org-id->org update-in [selected :unsaved-auto-add?] not)
                  :role-options roles})]

              (let [roles (->> user-role roles/role->roles-below (filter roles/none-organization-roles))]
                [um/main {:users                       (filter (fn [{:keys [user-role]}] (#{"member" "none" "super_admin" "account_manager"} user-role)) @users)
                          :users-selected?             users-selected?
                          :on-click-apply-update-users on-click-apply-update-users
                          ;; TODO Consider renaming `user-role` to something like "default-role" or re-think how this information is passed
                          :default-role-option    (first roles)
                          :role-options           roles}]))])])})))
