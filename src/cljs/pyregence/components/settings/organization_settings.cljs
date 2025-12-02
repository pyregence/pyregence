(ns pyregence.components.settings.organization-settings
  (:require
   [pyregence.components.settings.buttons     :as buttons]
   [pyregence.components.settings.users-table :refer [table-with-buttons]]
   [pyregence.components.settings.utils       :refer [card input-field
                                                      input-labeled label-styles
                                                      main-styles]]
   [pyregence.styles                          :as $]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;TODO should be "domain" not "email"
(defn- email-domain-cmpt
  [{:keys [email on-change] :as m}]
  ;;TODO This position stuff feels hacky!
  [:div {:style {:position "relative"}}
   [:span {:style {:position "absolute"
                   :color    "grey"
                   :left     "3px"
                   :top      "12px"}} "@"]
   [input-field (assoc m
                       :style {:padding "14px 14px 14px 20px"}
                       :value email
                       :on-change on-change)]])

;;TODO consider sharing styles with labeled-input cmpt
(defn- email-domains-cmpt
  [{:keys [og-email->email on-delete on-change]}]
  [:div {:style {:display        "flex"
                 :flex-direction "column"
                 :width          "100%"}}
   [:div {:style label-styles}
    (let [styles {:font-size   "14px"
                  :font-weight "500"
                  :color       ($/color-picker :neutral-black)}]
      [:div {:style {:display        "flex"
                     :flex-direction "row"
                     :width          "100%"
                     :height         "24px"}}
       [:p {:style styles} "Email Domains"]])]
   [:div {:style {:display        "flex"
                  :flex-direction "column"
                  :gap            "8px"}}
    (for [[og-email {:keys [email invalid?]}] og-email->email]
      [:div {:key   og-email
             :style {:display        "flex"
                     :flex-direction "row"
                     :gap            "8px"}}

       [:div {:style {:display "flex"
                      :flex-direction "column"
                      :gap "3px"}}
        [email-domain-cmpt {:email     email
                            :on-change (on-change og-email)}]
        (when invalid?
          [:p {:style {:color "red"}} "Invalid domain"])]
       [buttons/delete {:on-click (on-delete og-email)}]])]])

(defn- organization-settings
  [{:keys [on-change-organization-name
           on-change-email-name
           on-click-add-email
           on-delete-email
           on-click-save-changes
           unsaved-org-name
           unsaved-org-name-support-message
           og-email->email
           auto-accept?
           on-change-auto-accept-user-as-org-member]}]
  [:<>
   [input-labeled
    (cond->
     {:label     "Organization Name"
      :on-change on-change-organization-name
      :value     unsaved-org-name}
      unsaved-org-name-support-message
      (assoc :support-message unsaved-org-name-support-message))]
   [:div {:style {:display "flex"
                  :flex-direction "row"
                  :gap "10px"}}
    [:input {:id "auto-accept"
             :checked auto-accept?
             :on-change on-change-auto-accept-user-as-org-member
             :type "checkbox"}]
     ;; TODO add some version of this description (below), maybe as a pop-up info box?
     ;; "When enabled, users who join your organization through automatic email
     ;; domain registration will be automatically \"Accepted\". This allows them to
     ;; view all of your organization's private layers. Only users with email
     ;; addresses with the domain(s) specified above for your organization will be
     ;; eligible for auto-acceptance (e.g. user@company.com). If this box is
     ;; unchecked, your Organization Admin(s) will need to manually approve members
     ;; before they can log in and view your organization's private layers."
    [:label {:for "auto-accept"
             :style {:color "black"}}
     "Auto Accept User as Organization Member"]]
   [email-domains-cmpt {:on-change on-change-email-name
                        :on-delete on-delete-email
                        :og-email->email og-email->email}]
   [:div {:style {:display        "flex"
                  :flex-direction "row"
                  :gap            "16px"}}
    [buttons/add {:text     "Add Another Domain"
                  :on-click on-click-add-email}]
    [buttons/ghost {:text     "Save Changes"
                    :on-click on-click-save-changes}]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn main
  [m]
  [:div {:style main-styles}
   [card {:title "ORGANIZATION SETTINGS"
          :children
          [organization-settings m]}]
   [card {:title    "MEMBER USER-LIST"
          ;; TODO consider removing :show-remove-user and instead compose the table
          ;; per use: org, unaffilated, etc..
          :children [table-with-buttons (assoc m :show-remove-user? true)]}]])
