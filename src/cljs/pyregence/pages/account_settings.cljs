(ns pyregence.pages.account-settings
  (:require
   [clojure.core.async                                  :refer [<! go]]
   [clojure.edn                                         :as edn]
   [clojure.string :as str]
   [pyregence.components.settings.account-settings      :as as]
   [pyregence.components.settings.nav-bar               :as nav-bar]
   [pyregence.components.settings.organization-settings :as os]
   [pyregence.styles                                    :as $]
   [pyregence.utils.async-utils                         :as u-async]
   [reagent.core                                        :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-orgs!
  [user-role]
  (go
    (let [api-route (if (= user-role "super_admin")
                      "get-all-organizations"
                      "get-current-user-organization")
          response  (<! (u-async/call-clj-async! api-route))]
      (if (:success response)
        (->> (:body response)
             (edn/read-string))
        {}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component
  "The root component of the /account-settings page."
  [{:keys [user-role user-email user-name]}]
  (r/with-let [org-id->org (r/atom nil)
               selected-log (r/atom ["Account Settings"])]
    (when-not @org-id->org
      (go (reset! org-id->org
                  (reduce
                   (fn [org-id->org {:keys [org-id org-name] :as org}]
                     (assoc org-id->org org-id
                            (assoc org :unsaved-org-name org-name)))
                   {} (<! (get-orgs! "super_admin"))))))
    [:div
     {:style {:height         "100%"
              :display        "flex"
              :flex-direction "column"
              :font-family    "Roboto"}}
   ;; TODO replace with actual upper nav bar
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
           selected-page    (selected->tab-id selected)]
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
           (let [{:keys [email-domains unsaved-org-name
                         org-id auto-add? auto-accept?]} (@org-id->org selected)]
             {:email-domains-list (str/split email-domains #",")
              :unsaved-org-name unsaved-org-name
              :on-click-save-changes (fn [email-domains]
                                       ;; TODO consider adding a toast on success and something if there is a long delay.
                                       (fn []
                                         (go
                                           (let [unsaved-email-domains (str/join "," email-domains)
                                                 {:keys [success]}
                                                 (<! (u-async/call-clj-async! "update-org-info"
                                                                              org-id
                                                                              unsaved-org-name
                                                                              unsaved-email-domains
                                                                              auto-add?
                                                                              auto-accept?))]
                                             (if success
                                               (swap! org-id->org
                                                      (fn [o]
                                                        (-> o
                                                            (assoc-in [org-id :org-name] unsaved-org-name)
                                                            (assoc-in [org-id :email-domains] unsaved-email-domains))))
                                               ;;TODO what if it fails?
                                               )))))
              :on-change-organization-name
              (fn [e]
                (swap! org-id->org
                       assoc-in
                       [selected :unsaved-org-name]
                       (.-value (.-target e))))})]
          [:p "Page Not Found"])])]))
