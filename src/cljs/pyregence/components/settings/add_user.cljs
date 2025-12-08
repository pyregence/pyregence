(ns pyregence.components.settings.add-user
  (:require
   [clojure.core.async                    :refer [<! go]]
   [herb.core                             :refer [<class]]
   [pyregence.components.messaging        :refer [toast-message!]]
   [pyregence.components.settings.buttons :as buttons :refer [$drop-down-styles
                                                              $on-hover-gray]]
   [pyregence.components.settings.roles   :as roles]
   [pyregence.components.settings.utils   :as utils]
   [pyregence.components.svg-icons        :as svg]
   [pyregence.styles                      :as $]
   [pyregence.utils.async-utils           :as u-async]
   [reagent.core                          :as r]))

(def email-regex
  #"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}")

(defn valid-email? [email]
  (boolean (re-matches email-regex email)))

(defn add-new-users!
  [org-id users]
  (go (<! (u-async/call-clj-async! "add-org-users" org-id users))))

(defn enter-email-address
  [{:keys [email on-change invalid-email?]}]
  [:div {:style {:display        "flex"
                 :flex-direction "column"
                 :align-items    "flex-start"
                 :gap            "4px"
                 :flex           "1 0 0"}}
   ;;TODO fix label here and add the red dot.
   ;;TODO why do i have to add margin-bottom 0? why does it have a margin?
   [:input {:style       {:width         "100%"
                    ;; TODO maybe we should use
                          :type          "email"
                          :cursor        "pointer"
                          :padding       "10px"
                          :border-radius "4px"
                          :height        "48px"
                          :border        (str "1px solid "
                                              (if invalid-email?
                                                ($/color-picker :error-red)
                                                ($/color-picker :neutral-soft-gray)))}
            :placeholder "Enter Email Address"
            :on-change   on-change
            :value       email}]
   (when invalid-email?
     [:p {:style
          {:color "red"}} "Email not valid"])])

(defn drop-down-options
  [{:keys [options on-click-role]}]
  (let [border-styles (str "1px solid " ($/color-picker :neutral-soft-gray))]
    [:div {:style {:display "flex"
                   :width "100%"
                   :border border-styles
                   :border-radius "4px"
                   :flex-direction "column"}}
     [:div {:style {:display "flex"
                    :padding "10px"
                    :gap "4px"
                    :border border-styles
                    :flex-direction "column"}}
      (doall
       (for [opt options]
         [:div {:key opt
                :style {:display "flex"
                        :flex-direction "row"}}
          ;; TODO [Important!] This needs to update the table with the changes and emit a toast.
          [:button {:on-click (on-click-role opt)
                    :class (<class #(buttons/$on-hover-gray {:background "white"}))
                    :style {:border "none"
                            :display "flex"
                            :justify-content "start"
                            :width "100%"}}
           (utils/db->display opt)]]))]]))

;;TODO try to merge this back into buttons ns, it was changed to gird and we assoced in a width.
(defn ghost-drop-down
  [{:keys [text class on-click selected?]
    :or   {class (<class #($on-hover-gray (assoc $drop-down-styles :width "100%")))}}]
  [:button
   {:class class :on-click on-click}
   [:div {:style {:display               "grid"
                  :grid-template-columns "4fr 1fr"
                  :gap                   "8px"
                  :height                "100%"
                  :width                 "100%"}}
    [:span {:style {:padding "12px 14px"}} text]
    [:div {:style
           {:display         "flex"
            :align-items     "center"
            :justify-content "center"
            :border-left     (str "2px solid " ($/color-picker :primary-standard-orange))}}
     (if-not selected?
       ;;TODO these are supposed to be black triangles
       [svg/arrow-down]
       [svg/arrow-up])]]])

(defn select-user-role
  [{:keys [role on-click on-click-role selected?]}]
  [:div
   [:div
    [ghost-drop-down {:text role
                      :on-click on-click}]]
   [:div {:style {:display (if selected? "block" "none")
                  :background "white"
                  :position "absolute"}}
    [drop-down-options {:options (roles/role->roles-below role)
                        ;; TODO actually save to db on click
                        :on-click-role on-click-role}]]])

(defn invite-modal
  [{:keys [on-click-close-dialog user-role org-id]}]
  (let [default-user {:email "" :role user-role}]
    (r/with-let [id->user (r/atom {1 default-user})
                 selected-id (r/atom nil)]
      (let [border (str "1px solid " ($/color-picker :neutral-soft-gray))]
        [:div {:style {:display "flex"
                       :gap "16px"
                       :flex-direction "column"
                       :width "600px"}}
         [:p {:style {:display "flex"
                      :padding "16px 24px"
                      :justify-content "space-between"
                      :align-self "stretch"
                      :border-bottom (str "1px solid " ($/color-picker :neutral-soft-gray))
                      :font-size "18px"
                      :font-weight "500"}}
          "Add New User"]
         [:div {:style {:display "grid"
                        :padding-left "16px"
                        :gap "16px"
                        :align-items "start"
                      ;; TODO this `grid-template-columns` choice might need to double checked.
                      ;; TODO the user role box ideally would be the same size no matter the text, which means
                      ;; it has to be the text size of the largest option...
                        :grid-template-columns "1fr 250px auto"}}
          [:p {:style {:margin-bottom "0px"}} "Email Address"]
          [:p {:style {:margin-bottom "0px"}} "User Role"]
        ;;NOTE Left blank on purpose
          [:div]
          [:<>
           (doall
           ;;TODO hashmap might be unsorted... think about that.
            (for [[id {:keys [email role invalid-email?]}] @id->user]
              [:<> {:key id}
               [enter-email-address {:email email
                                     :invalid-email? invalid-email?
                                     :on-change (fn [e]
                                                  (let [email (.-value (.-target e))]
                                                    (swap! id->user assoc-in [id :email] email)))}]
               [select-user-role {:role (utils/db->display role)
                                  :selected? (= @selected-id id)
                                  :on-click-role (fn [new-role]
                                                   (fn []
                                                     (swap! id->user assoc-in [id :role] new-role)
                                                     (reset! selected-id nil)))
                                  :on-click #(reset! selected-id id)}]
             ;;TODO this needs to remove the item
               [:div {:on-click #(swap! id->user dissoc id)
                      :style {:cursor "pointer"
                              :padding-right "20px"
                              ;; TODO adding marging is a hack because we can't align center, because
                              ;; when the invalid state hits it throws things off.
                              :margin-top "10px"}}
                [svg/x-mark]]]))]]
         [:div {:style {:padding-left "16px"}}
          [buttons/ghost {:text "Add Another User"
                        ;;TODO what email and role should be assoced?
                          :on-click #(swap! id->user assoc (random-uuid) default-user)
                          :icon [svg/add]}]]
         [:div {:style {:display "flex"
                        :flex-direction "row"
                        :gap "8px"
                        :justify-content "flex-end"
                        :padding-top "24px"
                        :padding-right "24px"
                        ;;TODO this is large because of the drop down options, think of a better way.
                        :padding-bottom "58px"
                        :padding-left "24px"
                        :border-top border}}
          [buttons/ghost {:text "Cancel"
                          :on-click on-click-close-dialog}
           "Cancle"]
          [buttons/primary {:text "Confirm"
                            :on-click (fn []
                                        (let [id->user
                                              (swap! id->user
                                                     (fn [id->user]
                                                       (reduce-kv
                                                         (fn [id->user id {:keys [email] :as user}]
                                                           (assoc id->user id (assoc user :invalid-email? (not (valid-email? email)))))
                                                         {}
                                                         id->user)))
                                              invalid-emails? (->> id->user
                                                                   vals
                                                                   (some :invalid-email?))]
                                          (when-not invalid-emails?
                                            (add-new-users! org-id (->> id->user vals (map #(dissoc % :invalid-email?))))
                                            ;;TODO better toast
                                            (toast-message! "Invite Emails sent!")
                                            ;; TODO this needs to update the client
                                            ;; TODO this needs to do some light validations on the emails and report back
                                            )))}]]]))))

(defn add-user-dialog [{:keys [user-role org-id]}]
  (r/with-let [dialog-elem (atom nil)]
    ;;TODO find why does this need no wrap when the other buttons don't?
    [:div {:style {:white-space "nowrap"}}
     ;;TODO consider making the background darker with a pseudo background.
     [:dialog {:ref #(reset! dialog-elem %)
               :style {:border "none"
                       :padding "0px"
                       :border-radius "10px"}}
      [:div {:style {:overflow "hidden"}}
       [invite-modal {:on-click-close-dialog #(.close @dialog-elem)
                      :org-id                org-id
                      :user-role             user-role}]]]
     [buttons/add {:text "Add A New User"
                   :on-click #(.showModal @dialog-elem)}]]))
