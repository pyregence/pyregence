(ns pyregence.pages.account-settings
  (:require
   [clojure.core.async                                  :refer [<! go]]
   [clojure.set                                         :as set]
   [clojure.string                                      :as str]
   [pyregence.components.messaging                      :refer [toast-message!]]
   [pyregence.components.settings.account-settings      :as as]
   [pyregence.components.settings.email                 :as email]
   [pyregence.components.settings.fetch                 :refer [get-orgs!
                                                                get-users!
                                                                get-user-name!
                                                                update-org-user!]]
   [pyregence.components.settings.nav-bar               :as nav-bar]
   [pyregence.components.settings.organization-settings :as os]
   [pyregence.components.settings.unaffilated-members   :as um]
   [pyregence.styles                                    :as $]
   [pyregence.utils.async-utils                         :as u-async]
   [pyregence.utils.dom-utils :refer [input-value]]
   [reagent.core                                        :as r]))

(defn orgs->org->id
  [orgs]
  (reduce
   (fn [org-id->org {:keys [org-id org-name email-domains] :as org}]
     (assoc org-id->org org-id
            (assoc org
                   :unsaved-org-name org-name
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
  [{:keys [user-role]}]
  (let [org-id->org  (r/atom nil)
        user-name    (r/atom nil)
        users        (r/atom nil)
        selected-log (r/atom ["Account Settings"])]
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
                 (fn [update-user-info-by-email]
                   (fn [new-user-info]
                     (fn []
                       (let [emails (get-selected-users-emails-fn)]
                         ;; TODO this needs error handling.
                         (update-user-info-by-email new-user-info emails)
                         ;; TODO instead of this hacky sleep i think we have two options,
                         ;; first, we have the update function return the users, this seems ideal. the second is,
                         ;; we get the success from the update function and we then poll the users.
                         (js/setTimeout (fn [] (go (reset! users (<! (get-users! user-role))))) 3000)
                         (toast-message! (str (str/join ", " emails)  " updated!")))))))]
           [:div {:style {:display        "flex"
                          :flex-direction "row"
                          :height         "100%"
                          :background     ($/color-picker :lighter-gray)}}
            [nav-bar/main tabs]
            (case selected-page
              "Account Settings"
              [as/main {:password-set-date "1/2/2020"
                        :role-type         user-role
                        :user-name         @user-name
                        :on-change-update-user-name (fn [e]
                                                      (reset! user-name (input-value e)))
                        :on-click-save-user-name (fn []
                                                   (update-org-user! user-email @user-name))}]

              "Organization Settings"
              [os/main
               (let [;;TODO this selection should probably be resolved earlier on or happen a different way aka not create org-id->org if only one org
                     selected  (if (= user-role "super_admin")
                                 selected
                                 (-> @org-id->org keys first))
                     {:keys [unsaved-org-name
                             org-id auto-add?
                             auto-accept?
                             org-name
                             og-email->email
                             unsaved-org-name-support-message]} (@org-id->org selected)
                     selected-orgs-users
                     (if-not (= user-role "super_admin")
                       ;;TODO check if this shouldn't happen in the db instead.
                       (map #(set/rename-keys % {:full-name :name}) @users)
                       (filter (fn [{:keys [user-role organization-name]}]
                                 (and
                                  ;;TODO this conditional should be based on the og-id or org-unique name
                                  (= organization-name org-name)
                                  (#{"organization_admin" "organization_member"} user-role))) @users))]
                 {:og-email->email             og-email->email
                  :users                       selected-orgs-users
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

                  :on-click-save-changes
                  (fn []
                  ;; TODO consider adding a toast on success and something if there is a long delay.
                    (let [checked-og-email->email
                          (->> og-email->email
                               (reduce-kv
                                (fn [m id {:keys [email]}]

                                  (assoc m id (merge {:email email}
                                                     (when-not (email/valid-email-domain? email)
                                                       {:invalid? true}))))
                                {}))
                          ;;TODO unifiy the ways were collecting support/error messages here.
                          invalid-email-domains? (->> checked-og-email->email vals (some :invalid?))
                          organization-name-support-message (when (str/blank? unsaved-org-name) "Name cannot be blank.")]

                      (cond
                        invalid-email-domains?
                        (swap! org-id->org assoc-in [selected :og-email->email] checked-og-email->email)

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
                                                             auto-add?
                                                             auto-accept?))]
                          ;; TODO if not success case.
                            (if success
                              (do
                                (let [{:keys [org-name email-domains]} (@org-id->org org-id)]
                                  (swap! org-id->org
                                         (fn [o]
                                           (-> o
                                               (assoc-in [org-id :org-name] unsaved-org-name)
                                               (assoc-in [org-id :email-domains] unsaved-email-domains))))
                                  (let [new-name?  (not= org-name unsaved-org-name)
                                        new-email? (not= email-domains unsaved-email-domains)]
                                    (when new-name? (toast-message! (str "Updated Organization Name : " unsaved-org-name)))
                                    (when new-email? (toast-message! (str "Updated Email Domains: " unsaved-email-domains))))))))))))
                  :on-change-organization-name
                  (fn [e]
                    (swap! org-id->org
                           assoc-in
                           [selected :unsaved-org-name]
                           (.-value (.-target e))))})]
              [um/main {:users                       (filter (fn [{:keys [user-role]}]
                                                               ;; TODO consider using the roles var in roles.cljs
                                                               (#{"member" "none" "super_admin" "account_manager"} user-role)) @users)
                        :on-click-apply-update-users on-click-apply-update-users}])])])})))
