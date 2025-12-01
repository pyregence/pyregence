(ns pyregence.components.settings.body
  (:require
   [clojure.core.async                    :refer [<! go]]
   [cljs.reader                           :as reader]
   [herb.core                             :refer [<class]]
   [pyregence.components.messaging        :refer [toast-message!]]
   [pyregence.components.settings.buttons :as buttons]
   [pyregence.styles                      :as $]
   [pyregence.utils.async-utils           :as u-async]
   [pyregence.utils.dom-utils             :refer [input-value]]
   [clojure.string                        :as str]
   [reagent.core                          :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $standard-input-field
  []
  (with-meta
    {:background ($/color-picker :neutral-white)
     :border     (str "1px solid " ($/color-picker :neutral-soft-gray))}
    ;;TODO On some browsers, aka chrome, there is a black border that is being
    ;;imposed on top of the focused orange border. Try to fix this!
    {:pseudo {:focus {:background ($/color-picker :neutral-white)
                      :border     (str "1px solid " ($/color-picker :primary-standard-orange))}
              :hover {:background ($/color-picker :neutral-light-gray)}}}))

(def label-styles
  {:color       ($/color-picker :neutral-md-gray)
   :font-size   "14px"
   :font-weight "500"
   :margin      "0"})

(def font-styles
  {:color       ($/color-picker :neutral-black)
   :font-size   "14px"
   :font-weight "600"
   ;;TODO look into why all :p need margin-bottom 0 to look normal
   :margin      "0"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;TODO copied from admin.cljs, so either share that logic or deprecate admin.cljs eventually.
(defn- update-org-user!
  "Updates user identified by their `email` to have the `new-name`. Then makes a toast."
  [email new-name]
  (go
    (let [res (<! (u-async/call-clj-async! "update-user-name" email new-name))]
      (if (:success res)
        (toast-message! (str "The user " new-name " with the email " email  " has been updated."))
        (toast-message! (:body res))))))

;; TODO consider having a Namespace that handles all things roles.
(defn- role-type->label
  [role-type]
  (->> (str/split (str role-type) #"_")
       (map str/capitalize)
       (str/join " ")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- input-field
  [{:keys [value on-change]}]
  [:input {:type      "text"
           :class     (<class $standard-input-field)
           :style     {:font-weight   "500"
                       :width         "100%"
                       :max-width     "350px"
                       :height        "50px"
                       :font-size     "14px"
                       :font-style    "normal"
                       :line-height   "22px"
                       :padding       "14px"
                       :border-radius "4px"}
           :value     value
           :on-change on-change}])

(defn- input-show
  [{:keys [label text icon]}]
  [:div {:style {:display        "flex"
                 :gap            "5px"
                 :flex-direction "row"
                 :align-items    "center"}}
   [:p {:style label-styles} (str label ":")]
   [:p {:style font-styles} text]
   (when icon [icon :height "16px" :width "16px"])])

(defn- user-name-card
  [user-name]
  [:div {:style {:display        "flex"
                 :flex-direction "column"
                 :width          "100%"}}
   [:div {:style label-styles}
    (let [styles {:color       ($/color-picker :neutral-black)
                  :font-size   "14px"
                  :font-weight "500"}]
      [:div {:style {:display        "flex"
                     :flex-direction "row"
                     :width          "100%"
                     :height         "24px"}}
       [:p {:style styles}  "Full Name"]
       [:p {:style (assoc styles :color ($/color-picker :error-red))}  "*"]])
    [input-field user-name]]])

(defn- card
  [{:keys [title children]}]
  [:settings-body-card
   {:style {:display        "flex"
            :max-width      "750px"
            :min-width      "300px"
            :width          "100%"
            :padding        "16px"
            :flex-direction "column"
            :align-items    "flex-start"
            :gap            "16px"
            :align-self     "stretch"
            :border-radius  "4px"
            :border         (str "1px solid " ($/color-picker :neutral-soft-gray))
            :background     ($/color-picker :white)}}
   [:p {:style {:color          ($/color-picker :neutral-black)
                :font-size      "14px"
                :font-style     "normal"
                :font-weight    "700"
                :line-height    "14px"
                :text-transform "uppercase"
                :margin-bottom  "0px"}}
    title]
   children])

(defn- labeled-toggle
  [{:keys [label]}]
  [:<>
   [:p {:style (assoc font-styles :font-weight "400")} label]
   ;;TODO add toggle
   [:p "TOGGLE"]])

(defonce security-state (r/atom {:error    false  ;; Error state
                                 :loading  true   ;; Loading state
                                 :settings nil    ;; User settings
                                 :user     nil})) ;; User data

(defn- fetch-user-settings! []
  (go
    (let [response (<! (u-async/call-clj-async! "get-current-user-settings"))]
      (if (and (:success response) (:body response))
        (let [parsed-body (reader/read-string (:body response))
              settings (if (string? (:settings parsed-body))
                         (try
                           (reader/read-string (:settings parsed-body))
                           (catch :default _ {}))
                         (or (:settings parsed-body) {}))]
          (swap! security-state assoc
                 :error false
                 :loading false
                 :settings settings
                 :user parsed-body))
        (do
          (toast-message! "Failed to load security settings")
          (swap! security-state assoc
                 :error true
                 :loading false))))))

(defn- security-2fa-enabled []
  (let [{:keys [settings]} @security-state
        two-factor (:two-factor settings)
        is-totp? (= two-factor :totp)]
    [:div {:style {:display        "flex"
                   :flex-direction "column"
                   :gap            "24px"}}
     [:p {:style {:color       ($/color-picker :neutral-black)
                  :font-size   "14px"
                  :font-weight "400"
                  :line-height "1.5"
                  :margin      "0"}}
      (str "Two-Factor Authentication is on via "
           (if is-totp? "Authenticator App" "Email"))]
     [buttons/toggle {:on?      true
                      :label    "Turn Off"
                      :on-click #(set! (.-location js/window)
                                       (str "/disable-2fa?method=" (if is-totp? "totp" "email")))}]
     [:div {:style {:display   "flex"
                    :flex-wrap "wrap"
                    :gap       "12px"}}
      [buttons/ghost {:text     (if is-totp? "Switch to Email 2FA" "Switch to Authenticator")
                      :on-click #(set! (.-location js/window)
                                       (if is-totp? "/switch-2fa" "/setup-2fa?mode=switch"))}]
      (when is-totp?
        [buttons/ghost {:text     "View Backup Codes"
                        :on-click #(set! (.-location js/window) "/backup-codes")}])]]))

(defn- security-2fa-disabled []
  [:div {:style {:display        "flex"
                 :flex-direction "column"
                 :gap            "24px"}}
   [:p {:style {:color       ($/color-picker :neutral-black)
                :font-size   "14px"
                :font-weight "400"
                :line-height "1.5"
                :margin      "0"}}
    "Enable Two Factor Authentication to secure your account. Each time you login you will need your password, and a verification code."]
   [:div {:style {:display        "flex"
                  :flex-direction "column"
                  :gap            "12px"}}
    [:p {:style {:color       ($/color-picker :neutral-black)
                 :font-size   "14px"
                 :font-weight "600"
                 :margin      "0"}}
     "Two-step verification is off"]
    [buttons/ghost {:text     "Setup 2FA"
                    :on-click #(set! (.-location js/window) "/setup-2fa")}]]])

(defn- security-card []
  (r/create-class
   {:component-did-mount fetch-user-settings!
    :reagent-render
    (fn []
      (let [{:keys [loading settings error]} @security-state
            two-factor (:two-factor settings)]
        [card {:title "MANAGE TWO FACTOR AUTHENTICATION (2FA)"
               :children
               (cond
                 loading
                 [:p {:style (assoc font-styles :font-weight "400")} "Loading..."]

                 error
                 [:div {:style {:display        "flex"
                                :flex-direction "column"
                                :gap            "12px"}}
                  [:p {:style {:color       ($/color-picker :neutral-black)
                               :font-size   "14px"
                               :font-weight "400"
                               :margin      "0"}}
                   "Failed to load security settings. Please try again."]
                  [buttons/ghost {:text     "Retry"
                                  :on-click fetch-user-settings!}]]

                 (or (= two-factor :totp) (= two-factor :email))
                 [security-2fa-enabled]

                 :else
                 [security-2fa-disabled])}]))}))

(defn- user-full-name
  [{:keys [user-name email-address]}]
  (r/with-let [user-name (r/atom user-name)]
    [:div {:style {:display        "flex"
                   :width          "100%"
                   :gap            "16px"
                   :flex-direction "column"}}
     [:div {:style {:display        "flex"
                    :flex-direction "row"
                    :width          "100%"
                    :gap            "16px"}}
      [user-name-card {:value     @user-name
                       :on-change #(reset! user-name (input-value %))}]]
     [buttons/ghost {:text     "Save Changes"
                     :on-click #(update-org-user! email-address @user-name)}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn main
  [{:keys [password-set-date
           email-address
           role-type] :as user-info}]
  [:div {:style {:display        "flex"
                 :flex-direction "column"
                 :align-items    "flex-start"
                 :padding        "40px 160px"
                 :flex           "1 0 0"
                 :gap            "24px"}}
   [card {:title "MY ACCOUNT DETAILS"
          :children
          [:<>
           [input-show {:label "Email Address"
                        :text  email-address}]
           [input-show {:label "Role Type"
                        :text (role-type->label role-type)
                        ;; TODO add back in info tab when we get text that's associated with it.
                        #_#_:icon  svg/info-with-circle}]
           [user-full-name (select-keys user-info [:email-address :user-name])]]}]
   [security-card]
   [card {:title "RESET MY PASSWORD"
          :children
          [:<>
           [:p {:style (assoc font-styles :font-weight "400")} "Once you send a request to reset your password, you will receive a link in your email to set up your new password."]
           [:div {:style {:display         "flex"
                          :flex-direction  "row"
                          :justify-content "space-between"
                          :align-items     "flex-end"
                          :width           "100%"
                          :gap             "10px"}}
            [:p {:style {:margin "0px"}}
             ;;TODO pass on-click to generate reset link, this will be handled in a future ticket.
             [buttons/ghost {:text "Send Reset Link"}]]
            [input-show {:label "Last Updated"
                         :text  password-set-date}]]]}]
   ;;TODO commented out because component isn't ready
   #_[card {:title "NOTIFICATION PREFERENCES"
            :children
            [:<>
             [labeled-toggle {:label "Receive emails about new fires (need proper text here)"}]
             [labeled-toggle {:label "Receive emails about new fires (need proper text here)"}]]}]])
