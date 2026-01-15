(ns pyregence.components.settings.add-user
  (:require
   [clojure.core.async                    :refer [<! go]]
   [clojure.string                        :as str]
   [herb.core                             :refer [<class]]
   [pyregence.components.messaging        :refer [toast-message!]]
   [pyregence.components.settings.buttons :as buttons :refer [$drop-down-styles
                                                              $on-hover-gray]]
   [pyregence.components.settings.utils   :as utils]
   [pyregence.components.svg-icons        :as svg]
   [pyregence.styles                      :as $]
   [pyregence.utils.async-utils           :as u-async]
   [reagent.core                          :as r]))

(def email-regex
  #"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}")

(defn valid-email? [email]
  (boolean (re-matches email-regex email)))

(defn- add-new-users!
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
  [:div {:style {:display                    "flex"
                 :width                      "250px"
                 :border-color               ($/color-picker :neutral-soft-gray)
                 :flex-direction             "column"
                 :gap                        "4px"
                 :border-style               "solid"
                 :border-top-style           "none"
                 :border-bottom-right-radius "4px"
                 :border-bottom-left-radius  "4px"
                 :border-width               "2px"}}
   (doall
    (for [opt options]
      [:button {:key      opt
                :on-click (on-click-role opt)
                :class    (<class #(buttons/$on-hover-gray {:background "white"}))
                :style    {:border          "none"
                           :display         "flex"
                           :padding         "12px 14px"
                           :justify-content "start"
                           :width           "100%"}}
       (utils/db->display opt)]))])

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
    [:span {:style {:padding "12px 14px"
                    :text-align "start"}} text]
    [:div {:style
           {:display         "flex"
            :align-items     "center"
            :justify-content "center"
            :border-left     (str "2px solid " ($/color-picker :primary-standard-orange))}}
     (if-not selected?
       ;;TODO these are supposed to be black triangles
       [svg/arrow-down]
       [svg/arrow-up])]]])

(defn select-default-role-option
  [{:keys [role on-click on-click-role selected? role-options]}]
  [:div
   [:div
    [ghost-drop-down {:text      (utils/db->display role)
                      :selected? selected?
                      :on-click  on-click}]]
   [:div {:style {:display (if selected? "block" "none")
                  :background "white"
                  :position "absolute"}}
    [drop-down-options {:options       (remove #{role} role-options)
                        :on-click-role on-click-role}]]])

(defn invite-modal
  [{:keys [on-click-close-dialog default-role-option org-id role-options]}]
  (let [default-user  {:email "" :role default-role-option}
        default-state {1 default-user}]
    (r/with-let [id->user    (r/atom default-state)
                 selected-id (r/atom nil)]
      (let [border (str "1px solid " ($/color-picker :neutral-soft-gray))]
        [:div {:style {:display        "flex"
                       :gap            "16px"
                       :flex-direction "column"
                       :width          "600px"}}
         [:p {:style {:display         "flex"
                      :padding         "16px 24px"
                      :justify-content "space-between"
                      :align-self      "stretch"
                      :border-bottom   (str "1px solid " ($/color-picker :neutral-soft-gray))
                      :font-size       "18px"
                      :font-weight     "500"}}
          "Add New User"]
         [:div {:style {:display               "grid"
                        :padding-left          "16px"
                        :gap                   "16px"
                        :align-items           "start"
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
               [enter-email-address {:email          email
                                     :invalid-email? invalid-email?
                                     :on-change      (fn [e]
                                                       (let [email (.-value (.-target e))]
                                                         (swap! id->user assoc-in [id :email] email)))}]
               [select-default-role-option {:role          role
                                            :role-options  role-options
                                            :selected?     (= @selected-id id)
                                            :on-click-role (fn [new-role]
                                                             (fn []
                                                               (swap! id->user assoc-in [id :role] new-role)
                                                               (reset! selected-id nil)))
                                  ;; NOTE condition allows de-selecting by clicking the toggle
                                            :on-click      #(reset! selected-id (when-not (= @selected-id id) id))}]
               [:div {:on-click #(swap! id->user dissoc id)
                      :style    {:cursor        "pointer"
                                 :padding-right "20px"
                                 ;; TODO adding margin is a hack because we can't align center, because
                                 ;; when the invalid state hits it throws things off.
                                 :margin-top    "10px"}}
                (when-not (= 1 (count @id->user))
                  [svg/x-mark])]]))]]
         [:div {:style {:padding-left "16px"}}
          [buttons/ghost {:text     "Add Another User"
                          :on-click #(swap! id->user assoc (random-uuid) default-user)
                          :icon     [svg/add]}]]
         [:div {:style {:display         "flex"
                        :flex-direction  "row"
                        :gap             "8px"
                        :justify-content "flex-end"
                        :padding         "24px"
                        :border-top      border}}
          [buttons/ghost {:text     "Cancel"
                          :on-click on-click-close-dialog}
           "Cancel"]
          [buttons/primary {:text     "Confirm"
                            :disabled? (or (every? (fn [[_ {:keys [email]}]] (str/blank? email)) @id->user)
                                           (= @id->user default-state))
                            :on-click  (fn []
                                         ;;TODO it would be better to check email validity on every keystroke.
                                         (let [id->user*
                                               (swap! id->user
                                                      (fn [id->user]
                                                        (reduce-kv
                                                         (fn [id->user id {:keys [email] :as user}]
                                                           (assoc id->user id (assoc user :invalid-email? (not (valid-email? email)))))
                                                         {}
                                                         id->user)))
                                               invalid-emails? (->> id->user*
                                                                    vals
                                                                    (some :invalid-email?))]
                                           (if invalid-emails?
                                             (toast-message! "One or more email addresses are invalid.")
                                             (go
                                               (let [resp (<! (add-new-users!
                                                               org-id
                                                               (->> id->user* vals (map #(dissoc % :invalid-email?)))))]
                                                 (if (:success resp)
                                                   (do
                                                     (reset! id->user default-state)
                                                     (toast-message! "User(s) added and invite email(s) sent!")
                                                     (on-click-close-dialog))
                                                   (toast-message! "Something went wrong when adding the new user(s).")))))))}]]]))))

(defn add-user-dialog [{:keys [org-id role-options default-role-option]}]
  (r/with-let [dialog-elem (atom nil)]
    ;;TODO find why does this need no wrap when the other buttons don't?
    [:div {:style {:white-space "nowrap"}}
     ;;TODO consider making the background darker with a pseudo background.
     [:dialog {:ref   #(reset! dialog-elem %)
               :style {:border        "none"
                       :padding       "0px"
                       :border-radius "10px"
                       :overflow      "visible"}}
      [:div {:style {:overflow "hidden"}}
       [invite-modal {:on-click-close-dialog #(.close @dialog-elem)
                      :default-role-option     default-role-option
                      :org-id                org-id
                      :role-options          role-options}]]]
     [buttons/add {:text     "Add A New User"
                   :on-click #(.showModal @dialog-elem)}]]))
