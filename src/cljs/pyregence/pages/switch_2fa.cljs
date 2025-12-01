(ns pyregence.pages.switch-2fa
  (:require [clojure.core.async                    :refer [go <!]]
            [cljs.reader                           :as reader]
            [pyregence.components.messaging        :refer [toast-message!]]
            [pyregence.components.settings.buttons :as buttons]
            [pyregence.components.two-fa           :as two-fa]
            [pyregence.styles                      :as $]
            [pyregence.utils.async-utils           :as u-async]
            [reagent.core                          :as r]))

(def ^:private redirect-delay-ms 1500)

(def ^:private initial-state
  {:code        ""           ;; Verification code
   :error       false        ;; Error state
   :loading     true         ;; Loading state
   :step        :verify-totp ;; :verify-totp, :verify-email
   :submitting? false        ;; Form submitting
   :user-email  nil})        ;; User email

(defonce state (r/atom initial-state))

(defn- fetch-user-settings! []
  (go
    (let [response (<! (u-async/call-clj-async! "get-current-user-settings"))]
      (if (and (:success response) (:body response))
        (let [data (reader/read-string (:body response))]
          (swap! state assoc
                 :error false
                 :loading false
                 :user-email (:email data)))
        (do
          (toast-message! "Failed to load user settings")
          (swap! state assoc
                 :error true
                 :loading false))))))

(defn- handle-verify-totp! []
  (go
    (let [code (:code @state)]
      (when (and (two-fa/valid-code? code) (not (:submitting? @state)))
        (swap! state assoc :submitting? true)
        (let [disable-response (<! (u-async/call-clj-async! "disable-2fa" code))]
          (if (:success disable-response)
            (let [user-email (:user-email @state)
                  email-response (<! (u-async/call-clj-async! "send-email" user-email :2fa))]
              (swap! state assoc :submitting? false)
              (if (:success email-response)
                (do
                  (toast-message! "Verification code sent to your email")
                  (swap! state assoc :code "" :step :verify-email))
                (do
                  (toast-message! "TOTP disabled but failed to send email. Please re-enable 2FA from Account Settings.")
                  (js/setTimeout #(set! (.-location js/window) "/account-settings") redirect-delay-ms))))
            (do
              (swap! state assoc :submitting? false)
              (toast-message! "Invalid TOTP code. Please try again."))))))))

(defn- handle-verify-email! []
  (go
    (let [code (:code @state)]
      (when (and (two-fa/valid-code? code) (not (:submitting? @state)))
        (swap! state assoc :submitting? true)
        (let [response (<! (u-async/call-clj-async! "enable-email-2fa" code))]
          (swap! state assoc :submitting? false)
          (if (:success response)
            (do
              (toast-message! "Successfully switched to Email 2FA!")
              (js/setTimeout #(set! (.-location js/window) "/account-settings") redirect-delay-ms))
            (toast-message! "Invalid verification code. Please try again.")))))))

(defn- code-field [state-map & [opts]]
  (let [code (r/cursor state-map [:code])]
    [two-fa/code-input code opts]))

(defn- step-circle
  "Circle indicator for step n. Active when highlighted."
  [n active?]
  [:div {:style {:display         "flex"
                 :align-items     "center"
                 :justify-content "center"
                 :width           "24px"
                 :height          "24px"
                 :border-radius   "50%"
                 :background      ($/color-picker (if active? :primary-standard-orange :neutral-soft-gray))
                 :color           ($/color-picker :white)
                 :font-size       "12px"
                 :font-weight     "700"}}
   (str n)])

(defn- step-line
  "Connecting line between steps. Active when highlighted."
  [active?]
  [:div {:style {:flex       "1"
                 :height     "2px"
                 :background ($/color-picker (if active? :primary-standard-orange :neutral-soft-gray))}}])

(defn- step-indicator
  "Two-step progress indicator. step is 1 or 2."
  [step]
  [:div {:style {:display     "flex"
                 :align-items "center"
                 :gap         "8px"
                 :margin      "0 0 24px 0"}}
   [step-circle 1 true]
   [step-line (= step 2)]
   [step-circle 2 (= step 2)]])

(defn- verify-totp-step []
  (let [{:keys [code submitting?]} @state
        code-valid? (two-fa/valid-code? code)]
    [:div {:style {:display        "flex"
                   :flex-direction "column"
                   :gap            "20px"}}
     [step-indicator 1]

     [:p {:style {:color       ($/color-picker :neutral-black)
                  :font-size   "16px"
                  :font-weight "600"
                  :margin      "0"
                  :line-height "1.4"}}
      "Step 1: Verify Current Authenticator"]

     [:p {:style {:color       ($/color-picker :neutral-black)
                  :font-size   "14px"
                  :font-weight "400"
                  :line-height "1.5"
                  :margin      "0"}}
      "Enter your authenticator code or a backup code to continue:"]

     [:p {:style {:color       ($/color-picker :neutral-md-gray)
                  :font-size   "14px"
                  :font-weight "400"
                  :line-height "1.5"
                  :margin      "12px 0 0 0"}}
      "Your authenticator app will be disabled and you'll switch to email verification codes."]

     [code-field state {:on-submit (when (and code-valid? (not submitting?)) handle-verify-totp!)
                        :auto-focus? true
                        :placeholder "6-Digit Code"}]

     [:div {:style {:display   "flex"
                    :flex-wrap "wrap"
                    :gap       "12px"}}
      [buttons/ghost {:text      "Cancel"
                      :disabled? submitting?
                      :on-click  #(set! (.-location js/window) "/account-settings")}]
      [buttons/primary {:text      "Continue"
                        :disabled? (or (not code-valid?) submitting?)
                        :on-click  handle-verify-totp!}]]]))

(defn- verify-email-step []
  (let [{:keys [code submitting?]} @state
        code-valid? (two-fa/valid-code? code)]
    [:div {:style {:display        "flex"
                   :flex-direction "column"
                   :gap            "20px"}}
     [step-indicator 2]

     [:p {:style {:color       ($/color-picker :neutral-black)
                  :font-size   "16px"
                  :font-weight "600"
                  :margin      "0"
                  :line-height "1.4"}}
      "Step 2: Verify Email"]

     [:p {:style {:color       ($/color-picker :neutral-black)
                  :font-size   "14px"
                  :font-weight "400"
                  :line-height "1.5"
                  :margin      "0"}}
      "Enter the verification code sent to your email:"]

     [code-field state {:on-submit (when (and code-valid? (not submitting?)) handle-verify-email!)
                        :auto-focus? true
                        :placeholder "6-Digit Code"}]

     [:div {:style {:display   "flex"
                    :flex-wrap "wrap"
                    :gap       "12px"}}
      [buttons/ghost {:text      "Cancel"
                      :disabled? submitting?
                      :on-click  #(set! (.-location js/window) "/account-settings")}]
      [buttons/primary {:text      "Complete Switch"
                        :disabled? (or (not code-valid?) submitting?)
                        :on-click  handle-verify-email!}]]]))

(defn root-component []
  (r/create-class
   {:component-did-mount
    (fn []
      (reset! state initial-state)
      (fetch-user-settings!))

    :reagent-render
    (fn []
      (let [{:keys [loading step error]} @state]
        [two-fa/tfa-layout {:title "SWITCH TO EMAIL"}
         (cond
           loading
           [:p "Loading..."]

           error
           [:div {:style {:display        "flex"
                          :flex-direction "column"
                          :gap            "12px"}}
            [:p {:style {:color       ($/color-picker :neutral-black)
                         :font-size   "14px"
                         :font-weight "400"
                         :margin      "0"}}
             "Failed to load user settings. Please try again."]
            [buttons/ghost {:text     "Retry"
                            :on-click fetch-user-settings!}]]

           (= step :verify-totp)
           [verify-totp-step]

           (= step :verify-email)
           [verify-email-step])]))}))
