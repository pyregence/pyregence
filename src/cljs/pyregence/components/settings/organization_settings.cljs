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
    (for [[og-email {:keys [unsaved-email invalid?]}] og-email->email
          :when unsaved-email]
      [:div {:key   og-email
             :style {:display        "flex"
                     :flex-direction "row"
                     :gap            "8px"}}

       [:div {:style {:display "flex"
                      :flex-direction "column"
                      :gap "3px"}}
        [email-domain-cmpt {:email     unsaved-email
                            :on-change (on-change og-email)}]
        (when invalid?
          [:p {:style {:color "red"}} "Invalid domain"])]
       [buttons/delete {:on-click (on-delete og-email)}]])]])

(defn checkbox
  [checked on-change text]
  [:div {:style {:display        "flex"
                 :flex-direction "row"
                 :gap            "10px"}}
   [:input {:id        text
            :checked   checked
            :on-change on-change
            :type      "checkbox"}]
   [:label {:for   text
            :style {:color "black"}}
    text]])

(defn- organization-settings
  [{:keys [on-change-organization-name
           on-change-email-name
           on-click-add-email
           on-delete-email
           on-click-save-changes
           unsaved-org-name
           unsaved-org-name-support-message
           og-email->email
           unsaved-auto-accept?
           on-change-auto-accept-user-as-org-member
           unsaved-auto-add?
           on-change-auto-add-user-as-org-member
           save-changes-disabled?]}]
  [:<>
   [input-labeled
    (cond->
     {:label     "Organization Name"
      :on-change on-change-organization-name
      :value     unsaved-org-name}
      unsaved-org-name-support-message
      (assoc :support-message unsaved-org-name-support-message))]
   ;; TODO consider having this share styles with Update User checkbox.
   ;; TODO consider adding pop up info, maybe what displays on the admin page
   [checkbox unsaved-auto-accept? on-change-auto-accept-user-as-org-member "Auto Accept User as Organization Member"]
   [checkbox unsaved-auto-add? on-change-auto-add-user-as-org-member       "Auto Add User as Organization Member"]
   [email-domains-cmpt {:on-change       on-change-email-name
                        :on-delete       on-delete-email
                        :og-email->email og-email->email}]
   [:div {:style {:display        "flex"
                  :flex-direction "row"
                  :gap            "16px"}}
    [buttons/add {:text     "Add Another Domain"
                  :on-click on-click-add-email}]
    [buttons/ghost {:text      "Save Changes"
                    :disabled? save-changes-disabled?
                    :on-click  on-click-save-changes}]]])

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
