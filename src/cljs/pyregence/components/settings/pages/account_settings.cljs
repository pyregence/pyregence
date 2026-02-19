(ns pyregence.components.settings.pages.account-settings
  (:require
   [cljs.reader                           :as reader]
   [clojure.core.async                    :refer [<! go]]
   [clojure.edn                           :as edn]
   [pyregence.components.buttons          :as buttons]
   [pyregence.components.messaging        :refer [toast-message!]]
   [pyregence.components.settings.roles   :as roles]
   [pyregence.components.utils            :refer [card font-styles
                                                  input-labeled main-styles
                                                  text-labeled]]
   [pyregence.styles                      :as $]
   [pyregence.utils.async-utils           :as u-async]
   [pyregence.utils.dom-utils :refer [input-value]]
   [reagent.core                          :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce security-state (r/atom {:error    false  ;; Error state
                                 :loading  true   ;; Loading state
                                 :settings nil    ;; User settings
                                 :user     nil}))

(defonce password-set-date (r/atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
        [card {:title "MANAGE TWO-FACTOR AUTHENTICATION (2FA)"
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- user-full-name
  []
  (let [user-name         (r/atom nil)
        unsaved-user-name (r/atom nil)]
    (go
      (let [n (:user-name
               (edn/read-string
                (:body (<! (u-async/call-clj-async! "get-user-name-by-email")))))]
        (reset! user-name n)
        (reset! unsaved-user-name n)))
    (fn []
      [:div {:style {:display        "flex"
                     :width          "100%"
                     :gap            "16px"
                     :flex-direction "column"}}
       [:div {:style {:display        "flex"
                      :flex-direction "row"
                      :width          "100%"
                      :gap            "16px"}}
        [input-labeled {:value     @unsaved-user-name
                        :label     "Full Name"
                        :on-change #(reset! unsaved-user-name (input-value %))}]]
       [buttons/ghost {:text       "Save Changes"
                       :disabled?  (= @user-name @unsaved-user-name)
                       :on-click   #(go
                                      (let [new-name @unsaved-user-name
                                            res (<! (u-async/call-clj-async! "update-own-user-name" new-name))]
                                        (if (:success res)
                                          (do
                                            (toast-message! (str "Your name has been updated to " new-name "."))
                                            (reset! user-name new-name))
                                          (toast-message! (:body res)))))}]])))

(defn show-password-set-date
  []
  (r/create-class
   {:component-did-mount
    #(go
       (reset! password-set-date
               (if-let [date
                        (edn/read-string
                         (:body (<! (u-async/call-clj-async! "get-password-set-date"))))]
                 date
                 "Never")))
    :reagent-render
    (fn []
      [text-labeled {:label "Last Updated"
                     :text  @password-set-date}])}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn main
  [{:keys [user-email user-role]}]
  [:div {:style main-styles}
   [card {:title "MY ACCOUNT DETAILS"
          :children
          [:<>
           [text-labeled {:label "Email Address"
                          :text  user-email}]
           [text-labeled {:label    "Role Type"
                          :text     (roles/type->label user-role)}]
           [user-full-name]]}]
   [security-card]
   [card {:title "RESET MY PASSWORD"
          :children
          [:<>
           [:p {:style (assoc font-styles :font-weight "400")}
            "Once you send a request to reset your password, you will receive a link in your email to set up your new password."]
           [:div {:style {:display         "flex"
                          :flex-direction  "row"
                          :justify-content "space-between"
                          :align-items     "flex-end"
                          :width           "100%"
                          :gap             "10px"}}
            [:p {:style {:margin "0px"}}
             [buttons/ghost
              {:text     "Send Reset Link"
               :on-click
               #(go
                  (if (:success (<! (u-async/call-clj-async! "send-email" user-email :reset)))
                    (toast-message! (str "Reset Link sent to " user-email "."))
                     ;;TODO consider pulling support email from config. See PYR1-1319.
                    (toast-message! "Something went wrong when sending the Reset Link. Please contact support@pyrecast.com or try again later.")))}]]
            [show-password-set-date]]]}]])
