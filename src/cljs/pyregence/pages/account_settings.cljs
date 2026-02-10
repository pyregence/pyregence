(ns pyregence.pages.account-settings
  (:require
   [clojure.core.async                                  :refer [<! go]]
   [clojure.string                                      :as str]
   [pyregence.components.messaging                      :refer [toast-message!]]
   [pyregence.components.nav-bar                        :refer [nav-bar]]
   [pyregence.components.settings.admin                 :as admin]
   [pyregence.components.settings.fetch                 :refer [delete-users!
                                                                get-orgs!
                                                                get-user-name!]]
   [pyregence.components.settings.nav-bar               :as side-nav-bar]
   [pyregence.components.settings.roles                 :as roles]
   [pyregence.state                                     :as !]
   [pyregence.styles                                    :as $]
   [pyregence.utils.browser-utils                       :as u-browser]
   [reagent.core                                        :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
                                       (assoc m e {:email e :unsaved-email e}))
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
  (let [org-id->org       (r/atom nil)
        user-name         (r/atom nil)
        unsaved-user-name (r/atom nil)
        selected-tab-log  (r/atom ["Admin"])
        users-selected?   (r/atom false)]
    (r/create-class
     {:display-name "account-settings"
      :component-did-mount
      #(go
         (let [update-fn (fn [& _]
                           (-> js/window (.scrollTo 0 0))
                           (reset! !/mobile? (> 800.0 (.-innerWidth js/window))))]
           (-> js/window (.addEventListener "touchend" update-fn))
           (-> js/window (.addEventListener "resize"   update-fn))
           (reset! user-name (<! (get-user-name!)))
           (reset! unsaved-user-name @user-name)
           (reset! org-id->org (orgs->org->id (<! (get-orgs! user-role))))
           (update-fn)))
      :reagent-render
      (fn [{:keys [user-role user-email]}]
        [:div
         {:style {:height         "100vh"
                  :margin-bottom  "40px"
                  :display        "flex"
                  :flex-direction "column"
                  :font-family    "Roboto"
                  ;;NOTE this padding-bottom is to account for the header, there is probably a better way.
                  :padding-bottom "60px"}}
         [nav-bar {:logged-in?         true
                   :mobile?            @!/mobile?
                   :on-forecast-select (fn [forecast]
                                         (u-browser/jump-to-url!
                                          (str "/?forecast=" (name forecast))))
                   :user-role          user-role}]
         (let [tabs             (side-nav-bar/tab-data->tabs
                                  {:selected-log  selected-tab-log
                                   :organizations (vals @org-id->org)
                                   :user-role     user-role})]
           [:div {:style {:display        "flex"
                          :flex-direction "row"
                          :height         "100%"
                          :background     ($/color-picker :lighter-gray)}}
            [side-nav-bar/main tabs]
            (case (side-nav-bar/get-page-from-selected-log tabs @selected-tab-log)
              #_#_"Account Settings"
                [as/main {:columns                        columns
                          :password-set-date              password-set-date
                          :role-type                      user-role
                          :user-name                      @unsaved-user-name
                          :email-address                  user-email
                          :account-details-save-disabled? (= @user-name @unsaved-user-name)
                          :on-change-update-user-name     (fn [e]
                                                            (reset! unsaved-user-name (input-value e)))
                          :on-click-save-user-name        (fn []
                                                          ;;TODO should this be on success?
                                                            (update-own-user-name! @unsaved-user-name)
                                                            (reset! user-name @unsaved-user-name))}]
              "Organization Settings"
              [:p "hi"]
                #_[os/main
                 (let [;;TODO this selection should probably be resolved earlier on or happen a different way aka not create org-id->org if only one org
                       selected (if (= user-role "super_admin")
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
                   {:columns                columns
                    :statuses                ["accepted" "pending"]
                    :save-changes-disabled? (and (= unsaved-org-name org-name)
                                                 (= unsaved-auto-accept? auto-accept?)
                                                 (= unsaved-auto-add? auto-add?)
                                                 (not (some (fn [[_ {:keys [email unsaved-email]}]] (not= email unsaved-email)) og-email->email)))
                    :org-id                      org-id
                    :default-role-option         (first roles)
                    :og-email->email             og-email->email
                    :users                       selected-orgs-users
                    :users-selected?             users-selected?
                    :unsaved-org-name            unsaved-org-name
                    :unsaved-org-name-support-message
                    unsaved-org-name-support-message
                    :on-click-apply-update-users on-click-apply-update-users
                    :on-click-add-email          (fn [] (swap! org-id->org assoc-in [selected :og-email->email (random-uuid)] {:email "" :unsaved-email ""}))
                    :on-delete-email             (fn [og-email]
                                                   (fn [e]
                                                     (let [new-email (.-value (.-target e))]
                                                       (swap! org-id->org assoc-in [selected :og-email->email og-email :unsaved-email] new-email))))
                    :on-change-email-name
                    (fn [og-email]
                      (fn [e]
                        (let [new-email (.-value (.-target e))]
                          (swap! org-id->org assoc-in [selected :og-email->email og-email :unsaved-email] new-email))))

                  ;;TODO on "save change" when nothing has changed, it doesn't do anything (it should probably help the user or re-save).
                    :on-click-save-changes
                    (fn []
                  ;; TODO consider adding a toast on success and something if there is a long delay.
                      (let [checked-og-email->email
                            (->> og-email->email
                                 (reduce-kv
                                  (fn [m id {:keys [unsaved-email email]}]

                                    (assoc m id {:email email :unsaved-email unsaved-email :invalid? (not (email/valid-email-domain? unsaved-email))}))
                                  {}))
                          ;;TODO unifiy the ways were collecting support/error messages here.
                            invalid-email-domains?            (->> checked-og-email->email vals (some :invalid?))
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
                                                             (map :unsaved-email)
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
                                             ;; TODO find a way to save unsaved state better then having to know that you have to come here to always deal with it.
                                               (assoc-in [org-id :org-name] unsaved-org-name)
                                               (assoc-in [org-id :email-domains] unsaved-email-domains)
                                               (assoc-in [org-id :og-email->email] (reduce-kv (fn [m id {:keys [unsaved-email]}]
                                                                                                (assoc m id {:email unsaved-email :unsaved-email unsaved-email}))
                                                                                              {}
                                                                                              og-email->email))
                                               (assoc-in [org-id :auto-accept?] unsaved-auto-accept?)
                                               (assoc-in [org-id :auto-add?] unsaved-auto-add?))))
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
                    :auto-accept?         auto-accept?
                    :unsaved-auto-accept? unsaved-auto-accept?
                    :on-change-auto-accept-user-as-org-member
                    #(swap! org-id->org update-in [selected :unsaved-auto-accept?] not)
                    :auto-add?            auto-add?
                    :unsaved-auto-add?    unsaved-auto-add?
                    :on-change-auto-add-user-as-org-member
                    #(swap! org-id->org update-in [selected :unsaved-auto-add?] not)
                    :role-options         roles})]
              "Admin"
              [admin/main
               (let [roles (->> user-role roles/role->roles-below)]
                 {:user-role user-role
                  ;; TODO Consider renaming `user-role` to something like "default-role" or re-think how this information is passed
                  :default-role-option         (first roles)
                  :role-options                roles})]
              #_[um/main
                 (let [roles (->> user-role roles/role->roles-below (filter roles/none-organization-roles))]
                   {:columns                     columns
                    :users                       (filter (fn [{:keys [user-role]}] (#{"member" "none" "super_admin" "account_manager"} user-role)) @users)
                    :users-selected?             users-selected?
                    :on-click-apply-update-users on-click-apply-update-users
                          ;; TODO Consider renaming `user-role` to something like "default-role" or re-think how this information is passed
                    :default-role-option         (first roles)
                    :role-options                roles
                    :statuses                    ["none"]})])])])})))
