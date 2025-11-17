(ns pyregence.components.settings.organization-settings
  (:require
   [pyregence.components.settings.buttons     :as buttons]
   [pyregence.components.settings.users-table :refer [table-with-buttons]]
   [pyregence.components.settings.utils       :refer [card input-field
                                                  input-labeled label-styles
                                                  main-styles]]
   [pyregence.styles                          :as $]
   [reagent.core                              :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- email-domain-cmpt
  [{:keys [email on-change] :as m}]
  (r/with-let [email (r/atom email)]
    [input-field (assoc m
                        :value @email
                        :on-change (on-change email))]))

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
    (for [[og-email email] og-email->email]
      [:div {:key   og-email
             :style {:display        "flex"
                     :flex-direction "row"
                     :gap            "8px"}}
       [email-domain-cmpt {:email     email
                           :on-change (on-change og-email)}]
       [buttons/delete {:on-click (on-delete og-email)}]])]])

(defn- organization-settings
  [{:keys [on-change-organization-name
           on-click-save-changes
           unsaved-org-name
           email-domains-list]}]
  (r/with-let [og-email->email (r/atom (reduce #(assoc %1 %2 %2) {} email-domains-list))]
    [:<>
     [input-labeled {:label     "Organization Name"
                     :on-change on-change-organization-name
                     :value     unsaved-org-name}]
     [email-domains-cmpt {:og-email->email @og-email->email
                          :on-change       (fn [og-email]
                                             (fn [*current-email]
                                               (fn [e]
                                                 (let [new-email (.-value (.-target e))]
                                                   (swap! og-email->email assoc og-email new-email)
                                                   (reset! *current-email new-email)))))
                          :on-delete       (fn [og-email]
                                             (fn [_]
                                               (swap! og-email->email dissoc og-email)))}]
     [:div {:style {:display        "flex"
                    :flex-direction "row"
                    :gap            "16px"}}
      [buttons/add {:text     "Add Another Domain"
                    :on-click #(swap! og-email->email assoc (random-uuid) "")}]
      [buttons/ghost {:text     "Save Changes"
                      :on-click (on-click-save-changes (remove empty? (vals @og-email->email)))}]]]))

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
