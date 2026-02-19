(ns pyregence.components.settings.pages.organization-settings
  (:require
   [clojure.core.async                        :refer [<! go]]
   [clojure.string                            :as str]
   [pyregence.components.buttons              :as buttons]
   [pyregence.components.messaging            :refer [toast-message!]]
   [pyregence.components.settings.email       :as email]
   [pyregence.components.settings.roles       :as roles]
   [pyregence.components.settings.users-table :refer [table-with-buttons]]
   [pyregence.components.utils                :refer [card input-field
                                                      input-labeled
                                                      label-styles main-styles]]
   [pyregence.styles                          :as $]
   [pyregence.utils.async-utils               :as u-async]))

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
  [{:keys [user-role org-id->org selected-log] :as m}]
  (let [selected
        (if (#{"super_admin" "account_manager"} user-role)
          (last @selected-log)
          (-> @org-id->org keys first))
        {:keys [unsaved-org-name
                org-id auto-add?
                org-name
                og-email->email
                auto-accept?
                unsaved-auto-accept?
                unsaved-auto-add?
                unsaved-org-name-support-message]} (@org-id->org selected)
        roles                                      (->> user-role roles/role->roles-below (filter roles/organization-roles))]
    [:div {:style main-styles}
     [card {:title "ORGANIZATION SETTINGS"
            :children
            [organization-settings
             (merge
              m
              {:save-changes-disabled? (and (= unsaved-org-name org-name)
                                            (= unsaved-auto-accept? auto-accept?)
                                            (= unsaved-auto-add? auto-add?)
                                            (not (some (fn [[_ {:keys [email unsaved-email]}]] (not= email unsaved-email)) og-email->email)))
               :og-email->email        og-email->email
               :unsaved-org-name       unsaved-org-name
               :unsaved-org-name-support-message
               unsaved-org-name-support-message
               :on-click-add-email     (fn [] (swap! org-id->org assoc-in [selected :og-email->email (random-uuid)] {:email "" :unsaved-email ""}))
               :on-delete-email        (fn [og-email]
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
                               (when new-name? (toast-message! (str "Updated Organization Name ​ " unsaved-org-name)))
                               (when new-email? (toast-message! (str "Updated Email Domains​ " unsaved-email-domains)))
                               (when new-auto-accept? (toast-message! (str "Updated Auto Accept​ " (if unsaved-auto-accept? "On" "Off"))))
                               (when new-auto-add? (toast-message! (str "Updated Auto Add​ " (if unsaved-auto-add? "On" "Off"))))))))))))

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
               :auto-add?         auto-add?
               :unsaved-auto-add? unsaved-auto-add?
               :on-change-auto-add-user-as-org-member
               #(swap! org-id->org update-in [selected :unsaved-auto-add?] not)})]}]
     [card {:title    "MEMBER USER-LIST"
            :children [table-with-buttons
                       (assoc m
                              :users-filter        (fn [{:keys [user-role organization-name]}]
                                                     (and
                                                      (= organization-name org-name)
                                                      (#{"organization_admin" "organization_member"} user-role)))
                              :role-options        roles
                              :default-role-option (first roles)
                              :statuses            ["accepted" "pending"]
                              :org-id              org-id)]}]]))
