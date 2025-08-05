(ns pyregence.pages.settings
  (:require [clojure.core.async             :refer [go <!]]
            [clojure.string                 :as str]
            [cljs.reader                    :as reader]
            [herb.core                      :refer [<class]]
            [pyregence.components.messaging :refer [toast-message!]]
            [pyregence.components.two-fa    :as two-fa]
            [pyregence.styles               :as $]
            [pyregence.utils.async-utils    :as u-async]
            [reagent.core                   :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce state (r/atom {:code       ""       ;; Verification code
                        :disabling? false    ;; Disabling 2FA
                        :loading    true     ;; Loading state
                        :switching? false    ;; Switching methods
                        :user       nil      ;; User data
                        :verifying? false})) ;; Viewing codes

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Functions
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
          (swap! state assoc 
                 :loading false 
                 :user (assoc parsed-body :settings settings)))
        (swap! state assoc :loading false)))))

(defn- handle-enable-email-2fa! []
  (go
    (let [code (:code @state)
          settings (:settings (:user @state))
          two-factor (:two-factor settings)
          email (:email (:user @state))]
      (cond
        (= two-factor :totp)
        (cond
          (not (:switching? @state))
          (swap! state assoc :switching? true :code "")

          (and (:switching? @state) (seq code))
          (let [disable-response (<! (u-async/call-clj-async! "disable-2fa" code))]
            (if (:success disable-response)
              (do
                (<! (fetch-user-settings!))
                (if (:success (<! (u-async/call-clj-async! "send-email" email :2fa)))
                  (do
                    (toast-message! "TOTP disabled. Check your email for verification code")
                    (swap! state assoc :code ""))
                  (do
                    (toast-message! "Failed to send verification email")
                    (swap! state assoc :switching? false :code ""))))
              (do
                (toast-message! "Invalid verification code")
                (swap! state assoc :code "")))))

        :else
        (cond
          (not (:switching? @state))
          (if (:success (<! (u-async/call-clj-async! "send-email" email :2fa)))
            (do
              (toast-message! "Verification code sent to your email")
              (swap! state assoc :switching? true :code ""))
            (toast-message! "Failed to send verification code"))

          (and (:switching? @state)
               (two-fa/valid-code? code))
          (let [enable-response (<! (u-async/call-clj-async! "enable-email-2fa" code))]
            (if (:success enable-response)
              (do
                (toast-message! "Email 2FA enabled successfully")
                (swap! state assoc :switching? false :code "")
                (fetch-user-settings!))
              (do
                (toast-message! "Invalid verification code")
                (swap! state assoc :code "")))))))))

(defn- handle-switch-to-totp! []
  (go
    (let [code (:code @state)
          email (:email (:user @state))]
      (cond
        ;; Send verification email
        (not (:switching? @state))
        (if (:success (<! (u-async/call-clj-async! "send-email" email :2fa)))
          (do
            (toast-message! "Verification code sent to your email")
            (swap! state assoc :switching? true :code ""))
          (toast-message! "Failed to send verification code"))

        ;; Verify code
        (and (:switching? @state)
             (re-matches two-fa/totp-code-pattern code))
        (let [verify-response (<! (u-async/call-clj-async! "verify-2fa" email code))]
          (if (:success verify-response)
            ;; Redirect to TOTP setup
            (do
              (swap! state assoc :switching? false :code "")
              (set! (.-location js/window) "/totp-setup"))
            (do
              (toast-message! "Invalid verification code")
              (swap! state assoc :code ""))))))))

(defn- handle-disable-2fa! []
  (go
    (let [code (:code @state)
          settings (:settings (:user @state))
          two-factor (:two-factor settings)
          email (:email (:user @state))]
      (cond
        ;; Send email code
        (and (= two-factor :email) (not (:disabling? @state)))
        (if (:success (<! (u-async/call-clj-async! "send-email" email :2fa)))
          (do
            (toast-message! "Verification code sent to your email")
            (swap! state assoc :disabling? true :code ""))
          (toast-message! "Failed to send verification code"))

        (and (= two-factor :totp) (not (:disabling? @state)))
        (swap! state assoc :disabling? true :code "")

        ;; Disable 2FA
        (and (:disabling? @state) (seq code))
        (let [response (<! (u-async/call-clj-async! "disable-2fa" code))]
          (if (:success response)
            (do
              (toast-message! "2FA disabled successfully")
              (swap! state assoc :disabling? false :code "")
              (fetch-user-settings!))
            (do
              (toast-message! "Invalid verification code")
              (swap! state assoc :code ""))))))))

(defn- verify-and-view-codes! []
  (go
    (let [code (:code @state)
          user-email (:email (:user @state))]
      (if (two-fa/valid-code? code)
        ;; Verify code
        (let [response (<! (u-async/call-clj-async! "verify-2fa" user-email code))]
          (if (:success response)
            ;; Redirect to backup codes
            (do
              (swap! state assoc :verifying? false :code "")
              (set! (.-location js/window) "/backup-codes"))
            (do
              (toast-message! "Invalid code. Please try again.")
              (swap! state assoc :code ""))))
        (toast-message! "Please enter a valid code")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- code-field [state-map & [opts]]
  (let [code (r/cursor state-map [:code])]
    [two-fa/code-input code opts]))

(defn- code-verification-dialog [title field-options on-confirm on-cancel code-valid?]
  [:div
   [:p {:style {:margin-bottom "1rem"}} title]
   (when (str/includes? title "authenticator code")
     [:p {:style {:color         "#666"
                  :font-size     "0.9rem"
                  :margin-bottom "1rem"}}
      "You can also use one of your backup codes."])
   [:div {:style {:margin-bottom "1rem"}}
    [code-field state field-options]]
   [:div {:style {:display "flex"
                  :gap     "10px"}}
    [:input (cond-> {:class    (<class $/p-form-button)
                     :on-click on-confirm
                     :type     "button"
                     :value    (if (str/includes? title "view backup codes")
                                 "VERIFY"
                                 "CONFIRM")}
              (not code-valid?)
              (assoc :disabled true
                     :style    {:cursor  "not-allowed"
                                :opacity "0.6"}))]
    [:input {:class    (<class $/p-form-button)
             :on-click on-cancel
             :type     "button"
             :value    "CANCEL"}]]])

(defn- totp-method-controls [on-view-codes on-switch on-disable]
  [:div {:style {:display         "flex"
                 :gap             "10px"
                 :justify-content "space-between"}}
   [:div {:style {:display "flex"
                  :gap     "10px"}}
    [:input {:class    (<class $/p-form-button)
             :on-click on-view-codes
             :type     "button"
             :value    "VIEW BACKUP CODES"}]
    [:input {:class    (<class $/p-form-button)
             :on-click on-switch
             :type     "button"
             :value    "SWITCH TO EMAIL 2FA"}]]
   [:input {:class    (<class $/p-form-button)
            :on-click on-disable
            :style    {:border-color ($/color-picker :red)}
            :type     "button"
            :value    "DISABLE 2FA"}]])

(defn- authenticator [{:keys [verifying? disabling? switching? code]}
                      on-view-codes on-switch-to-email on-disable]
  [:div
   [:p {:style {:margin-bottom "1rem"}}
    "Two-factor authentication is enabled using an authenticator app."]
   (cond
     verifying?
     [code-verification-dialog
      "Enter your authenticator code or a backup code to view backup codes:"
      {:on-submit on-view-codes :auto-focus? true}
      on-view-codes
      #(swap! state assoc :verifying? false :code "")
      (two-fa/valid-code? code)]

     disabling?
     [code-verification-dialog
      "Enter your authenticator code to disable 2FA:"
      {:on-submit on-disable :auto-focus? true :placeholder "Enter code"}
      on-disable
      #(swap! state assoc :disabling? false :code "")
      (two-fa/valid-code? code)]

     switching?
     [code-verification-dialog
      "Enter your authenticator code to switch to email 2FA:"
      {:on-submit on-switch-to-email :auto-focus? true}
      on-switch-to-email
      #(swap! state assoc :switching? false :code "")
      (two-fa/valid-code? code)]

     :else
     [totp-method-controls
      #(swap! state assoc :verifying? true)
      on-switch-to-email
      on-disable])])

(defn- email [{:keys [disabling? switching? code]}
              on-switch-to-totp on-disable]
  [:div
   [:p {:style {:margin-bottom "1rem"}}
    "Two-factor authentication is enabled using email verification."]
   (cond
     disabling?
     [:div
      [:p {:style {:margin-bottom "1rem"}}
       "Enter the verification code sent to your email:"]
      [:div {:style {:margin-bottom "1rem"}}
       [code-field state {:on-submit   on-disable
                          :auto-focus? true}]]
      [:div {:style {:display "flex"
                     :gap     "10px"}}
       [:input {:class    (<class $/p-form-button)
                :disabled (not (two-fa/valid-code? code))
                :on-click on-disable
                :style    (when-not (two-fa/valid-code? code)
                            {:cursor  "not-allowed"
                             :opacity "0.6"})
                :type     "button"
                :value    "CONFIRM"}]
       [:input {:class    (<class $/p-form-button)
                :on-click #(swap! state assoc :disabling? false :code "")
                :type     "button"
                :value    "CANCEL"}]]]

     switching?
     [:div
      [:p {:style {:margin-bottom "1rem"}}
       "Enter the verification code to switch to authenticator:"]
      [:div {:style {:margin-bottom "1rem"}}
       [code-field state {:on-submit on-switch-to-totp
                          :auto-focus? true}]]
      [:div {:style {:display "flex"
                     :gap     "10px"}}
       [:input {:class    (<class $/p-form-button)
                :disabled (not (two-fa/valid-code? code))
                :on-click on-switch-to-totp
                :style    (when-not (two-fa/valid-code? code)
                            {:cursor  "not-allowed"
                             :opacity "0.6"})
                :type     "button"
                :value    "CONFIRM"}]
       [:input {:class    (<class $/p-form-button)
                :on-click #(swap! state assoc :switching? false :code "")
                :type     "button"
                :value    "CANCEL"}]]]

     :else
     [:div {:style {:display         "flex"
                    :gap             "10px"
                    :justify-content "space-between"}}
      [:input {:class    (<class $/p-form-button)
               :on-click on-switch-to-totp
               :type     "button"
               :value    "SWITCH TO AUTHENTICATOR"}]
      [:input {:class    (<class $/p-form-button)
               :on-click on-disable
               :style    {:border-color ($/color-picker :red)}
               :type     "button"
               :value    "DISABLE 2FA"}]])])

(defn- method-choice [{:keys [switching? code]}
                      on-enable-email]
  [:div
   (if switching?
     [:div
      [:p {:style {:margin-bottom "1rem"}}
       "Enter the verification code sent to your email:"]
      [:div {:style {:margin-bottom "1rem"}}
       [code-field state {:on-submit   on-enable-email
                          :auto-focus? true}]]
      [:div {:style {:display "flex"
                     :gap     "10px"}}
       [:input {:class    (<class $/p-form-button)
                :disabled (not (two-fa/valid-code? code))
                :on-click on-enable-email
                :style    (when-not (two-fa/valid-code? code)
                            {:cursor  "not-allowed"
                             :opacity "0.6"})
                :type     "button"
                :value    "VERIFY"}]
       [:input {:class    (<class $/p-form-button)
                :on-click #(swap! state assoc :switching? false :code "")
                :type     "button"
                :value    "CANCEL"}]]]
     [:div
      [:p {:style {:margin-bottom "1rem"}}
       "Enable two-factor authentication to secure your account."]
      [:p {:style {:font-weight   "500"
                   :margin-bottom "1rem"}}
       "Choose one method:"]
      [:div {:style {:display        "flex"
                     :flex-direction "column"
                     :gap            "20px"}}
       [:div
        [:input {:class    (<class $/p-form-button)
                 :on-click #(set! (.-location js/window) "/totp-setup")
                 :style    {:margin-bottom "0.5rem"}
                 :type     "button"
                 :value    "AUTHENTICATOR APP"}]
        [:p {:style {:color         "#666"
                     :font-size     "0.9rem"
                     :margin        "0"
                     :margin-bottom "0.3rem"}}
         "Use an authenticator app (Recommended)"]
        [:p {:style {:color     "#666"
                     :font-size "0.85rem"
                     :margin    "0"}}
         "Install Google Authenticator, Microsoft Authenticator, or Authy from your app store"]]
       [:div
        [:input {:class    (<class $/p-form-button)
                 :on-click on-enable-email
                 :style    {:margin-bottom "0.5rem"}
                 :type     "button"
                 :value    "EMAIL VERIFICATION"}]
        [:p {:style {:color     "#666"
                     :font-size "0.9rem"
                     :margin    "0"}}
         "Receive codes via email"]]]])])

(defn root-component
  "Root component for 2FA settings page."
  []
  (r/create-class
   {:component-did-mount fetch-user-settings!
    :reagent-render
    (fn []
      (let [{:keys [loading user]} @state
            settings (:settings user)
            two-factor (:two-factor settings)]
        [:div {:style {:display         "flex"
                       :justify-content "center"
                       :margin          "5rem"}}
         [:div {:style ($/combine ($/action-box)
                                  {:max-width "600px"
                                   :min-width "500px"})}
          [:div {:style ($/action-header)}
           [:label "2FA"]]
          [:div {:style {:overflow "auto"
                         :padding  "1.5rem"}}
           (cond
             loading
             [:div {:style {:text-align "center"}}
              [:p "Loading..."]]

             (= two-factor :totp)
             [authenticator @state verify-and-view-codes! handle-enable-email-2fa! handle-disable-2fa!]

             (= two-factor :email)
             [email @state handle-switch-to-totp! handle-disable-2fa!]

             :else
             [method-choice @state handle-enable-email-2fa!])]]]))}))
