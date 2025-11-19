(ns pyregence.pages.account-settings
  (:require
   [clojure.core.async                                  :refer [<! go]]
   [clojure.edn                                         :as edn]
   [clojure.string                                      :as str]
   [pyregence.components.messaging                      :refer [toast-message!]]
   [pyregence.components.settings.fetch                 :refer [get-orgs! get-users!]]
   [pyregence.components.settings.account-settings      :as as]
   [pyregence.components.settings.nav-bar               :as nav-bar]
   [pyregence.components.settings.organization-settings :as os]
   [pyregence.components.settings.unaffilated-members   :as um]
   [pyregence.styles                                    :as $]
   [pyregence.utils.async-utils                         :as u-async]
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
                                       (assoc m e e))
                                     {}
                                     (str/split email-domains #",")))))
   {}
   orgs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component
  "The root component of the /account-settings page."
  [{:keys [user-role]}]
  (let [org-id->org (r/atom nil)
        users       (r/atom nil)
        selected-log (r/atom ["Account Settings"])]
    (r/create-class
     {:display-name "account-settings"
      :component-did-mount
      #(go
         (reset! users (<! (get-users! user-role)))
         (reset! org-id->org (orgs->org->id (<! (get-orgs! user-role)))))
      :reagent-render
      (fn [{:keys [user-role user-email user-name]}]
        [:div
         {:style {:height         "100%"
                  :display        "flex"
                  :flex-direction "column"
                  :font-family    "Roboto"}}
        ;; rODO this mock `:nav` with actual upper nav bar, this will happen in another PR.
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
               (fn [get-selected-emails-fn]
                 (fn [update-users-role-by-email]
                   (fn [new-role]
                     (fn []
                       (let [emails (get-selected-emails-fn)]
                         ;; TODO this needs error handling.
                         (update-users-role-by-email new-role emails)
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
                        :email-address     user-email
                        :role-type         user-role
                        :user-name         user-name}]
              "Organization Settings"
              [os/main
               (let [{:keys [unsaved-org-name org-id auto-add? auto-accept? org-name og-email->email]} (@org-id->org selected)]
                 {:og-email->email og-email->email
                  :users (filter (fn [{:keys [user-role organization-name]}]
                                   (and
                                     ;;TODO this conditional should be based on the og-id or org-unique name
                                    (= organization-name org-name)
                                    (#{"organization_admin" "organization_member"} user-role))) @users)
                  :unsaved-org-name unsaved-org-name
                  :on-click-apply-update-users on-click-apply-update-users
                  :on-click-add-email  (fn [] (swap! org-id->org assoc-in [selected :og-email->email (random-uuid)] ""))
                  :on-delete-email (fn [og-email]
                                     (fn [_]
                                       ;;TODO this means i might have to filter out empty ones.
                                       (swap! org-id->org update-in [selected :og-email->email] dissoc og-email)))
                  :on-change-email-name
                  (fn [og-email]
                    (fn [e]
                      (let [new-email (.-value (.-target e))]
                        (swap! org-id->org assoc-in [selected :og-email->email og-email] new-email))))

                  :on-click-save-changes
                  (fn []
                  ;; TODO consider adding a toast on success and something if there is a long delay.
                    (go
                      (let [unsaved-email-domains (->> og-email->email vals (str/join ","))
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
                              (let [new-name? (not= org-name unsaved-org-name)
                                    new-email? (not= email-domains unsaved-email-domains)]
                                (when new-name? (toast-message! (str "Updated Organization Name : " unsaved-org-name)))
                                (when new-email? (toast-message! (str "Updated Domain emails: " unsaved-email-domains))))))))))
                  :on-change-organization-name
                  (fn [e]
                    (swap! org-id->org
                           assoc-in
                           [selected :unsaved-org-name]
                           (.-value (.-target e))))})]
              [um/main {:users (filter (fn [{:keys [user-role]}] (#{"member" "none" "super_admin" "account_manager"} user-role)) @users)
                        :on-click-apply-update-users on-click-apply-update-users
                        }])])])})))
