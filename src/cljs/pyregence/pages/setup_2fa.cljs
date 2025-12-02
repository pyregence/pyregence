(ns pyregence.pages.setup-2fa
  (:require [clojure.core.async                    :refer [go <!]]
            [cljs.reader                           :as reader]
            [clojure.string                        :as str]
            [pyregence.components.messaging        :refer [toast-message!]]
            [pyregence.components.settings.buttons :as buttons]
            [pyregence.components.svg-icons        :as svg]
            [pyregence.components.two-fa           :as two-fa]
            [pyregence.styles                      :as $]
            [pyregence.utils.async-utils           :as u-async]
            [pyregence.utils.browser-utils         :as u-browser]
            [reagent.core                          :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private redirect-delay-ms 1500)
(def ^:private qr-code-size "200x200")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private initial-state
  {:backup-codes []              ;; Generated codes
   :code         ""              ;; Verification code
   :loading      false           ;; Loading state
   :qr-uri       nil             ;; QR code URI
   :setup-key    nil             ;; Manual entry key
   :show-key?    false           ;; Show manual option
   :step         :method-choice  ;; Setup flow step
   :submitting?  false           ;; Form submitting
   :user-email   nil})           ;; User email

(defonce state
  ^{:doc "Component state for TOTP setup process"}
  (r/atom initial-state))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- generate-qr-url [uri]
  (str "https://api.qrserver.com/v1/create-qr-code/?size=" qr-code-size "&data="
       (js/encodeURIComponent uri)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- start-totp-setup! []
  (swap! state assoc :loading true)
  (go
    (let [response (<! (u-async/call-clj-async! "begin-totp-setup"))]
      (if (and (:success response) (:body response))
        (let [data (reader/read-string (:body response))
              {:keys [qr-uri secret backup-codes]} data]
          (swap! state merge
                 {:backup-codes (vec backup-codes)
                  :loading      false
                  :qr-uri       qr-uri
                  :setup-key    secret
                  :step         :totp-setup}))
        (do
          (toast-message! "Failed to start 2FA setup")
          (swap! state assoc :loading false :step :method-choice))))))

(defn- complete-totp-setup! []
  (go
    (let [code (:code @state)]
      (when (and (two-fa/valid-code? code) (not (:submitting? @state)))
        (swap! state assoc :submitting? true)
        (let [response (<! (u-async/call-clj-async! "complete-totp-setup" code))]
          (swap! state assoc :submitting? false)
          (if (:success response)
            (do
              (toast-message! "2FA setup complete!")
              (js/setTimeout #(set! (.-location js/window) "/account-settings") redirect-delay-ms))
            (toast-message! "Invalid code. Please try again.")))))))

(defn- fetch-user-email! []
  (go
    (let [response (<! (u-async/call-clj-async! "get-current-user-settings"))]
      (when (and (:success response) (:body response))
        (let [data (reader/read-string (:body response))]
          (swap! state assoc :user-email (:email data)))))))

(defn- enable-email-2fa! []
  (go
    (let [user-email (:user-email @state)]
      (if-not user-email
        (toast-message! "Loading user information...")
        (if (:success (<! (u-async/call-clj-async! "send-email" user-email :2fa)))
          (do
            (toast-message! "Verification code sent to your email")
            (swap! state assoc :code "" :step :email-verify))
          (toast-message! "Failed to send verification code"))))))

(defn- complete-email-2fa! []
  (go
    (let [code (:code @state)]
      (when (and (two-fa/valid-code? code) (not (:submitting? @state)))
        (swap! state assoc :submitting? true)
        (let [response (<! (u-async/call-clj-async! "enable-email-2fa" code))]
          (swap! state assoc :submitting? false)
          (if (:success response)
            (do
              (toast-message! "Email 2FA enabled successfully!")
              (js/setTimeout #(set! (.-location js/window) "/account-settings") redirect-delay-ms))
            (toast-message! "Invalid verification code")))))))

(defn- send-email-verification-for-switch! []
  (go
    (let [user-email (:user-email @state)]
      (if-not user-email
        (toast-message! "Loading user information...")
        (if (:success (<! (u-async/call-clj-async! "send-email" user-email :2fa)))
          (toast-message! "Verification code sent to your email")
          (toast-message! "Failed to send verification code"))))))

(defn- verify-email-and-begin-switch! []
  (go
    (let [code (:code @state)]
      (when (and (two-fa/valid-code? code) (not (:submitting? @state)))
        (swap! state assoc :submitting? true)
        (let [response (<! (u-async/call-clj-async! "disable-2fa" code))]
          (swap! state assoc :code "" :submitting? false)
          (if (:success response)
            (do
              (toast-message! "Email verification successful")
              (start-totp-setup!))
            (toast-message! "Invalid code. Please try again.")))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- code-field
  "A verification code input field bound to a state map."
  [state-map & [opts]]
  (let [code (r/cursor state-map [:code])]
    [two-fa/code-input code opts]))

(defn- step [step-number title content]
  [:div {:style {:margin-bottom "0.5rem"}}
   [:p {:style {:color       ($/color-picker :neutral-black)
                :font-size   "16px"
                :font-weight "600"
                :margin      "0 0 1rem 0"
                :line-height "1.4"}}
    (str step-number ". " title)]
   content])

(defn- enrollment [{:keys [qr-uri setup-key show-key?]} on-toggle-key]
  [:div
   (when qr-uri
     [:div {:style {:border        (str "1px solid " ($/color-picker :light-gray))
                    :border-radius "4px"
                    :margin-bottom "1rem"
                    :padding       "2rem 1rem"
                    :text-align    "center"}}
      [:img {:alt   "QR Code"
             :src   (generate-qr-url qr-uri)
             :style {:background-color ($/color-picker :white)
                     :border           (str "1px solid " ($/color-picker :light-gray))
                     :padding          "10px"}}]])

   [:div {:style {:margin-bottom "1rem"
                  :margin-top    "0.75rem"}}
    [:a {:href     "#"
         :on-click (fn [e]
                     (.preventDefault e)
                     (on-toggle-key))
         :style    {:color           ($/color-picker :neutral-black)
                    :cursor          "pointer"
                    :display         "block"
                    :font-size       "14px"
                    :font-weight     "300"
                    :text-decoration "none"}}
     [:span "Unable to scan? Use setup key instead"]
     [:span {:style {:display     "inline-block"
                     :font-size   "12px"
                     :margin-left "4px"
                     :transform   (if show-key? "rotate(180deg)" "rotate(0deg)")
                     :transition  "transform 0.2s ease"}}
      "▾"]]
    (when show-key?
      [:div {:style {:margin-top "1rem"}}
       [:p {:style {:color         ($/color-picker :neutral-black)
                    :font-size     "14px"
                    :font-weight   "400"
                    :line-height   "1.5"
                    :margin        "0 0 0.75rem 0"}}
        "Enter this key in your authenticator app:"]
       [:div {:style {:align-items "center"
                      :display     "flex"
                      :gap         "10px"}}
        [two-fa/code-box setup-key]
        [buttons/ghost {:text     "COPY"
                        :on-click #(do
                                     (if (u-browser/copy-to-clipboard! setup-key)
                                       (toast-message! "Copied to clipboard")
                                       (toast-message! "Failed to copy")))}]]])]])

(defn- codes [backup-codes]
  (when (seq backup-codes)
    [:div
     [:div {:style {:margin-bottom "1rem"}}
      [:p {:style {:color       ($/color-picker :neutral-black)
                   :font-size   "14px"
                   :font-weight "400"
                   :line-height "1.5"
                   :margin      "0 0 1rem 0"}}
       "You can use backup codes to sign in to your account when you don't have your phone. Each code can be used only once. Keep them somewhere safe so you never get locked out of your account."]]
     [two-fa/backup-codes-grid backup-codes]
     [:div {:style {:display     "flex"
                    :gap         "12px"
                    :margin-top  "1rem"}}
      [buttons/ghost {:text     "Download"
                      :icon     [svg/download :height "16px" :width "16px"]
                      :on-click #(u-browser/download-backup-codes! (map :code backup-codes))}]
      [buttons/ghost {:text     "Copy"
                      :icon     [svg/copy-icon :height "16px" :width "16px"]
                      :on-click #(do
                                   (if (u-browser/copy-to-clipboard! (str/join "\n" (map :code backup-codes)))
                                     (toast-message! "Copied to clipboard")
                                     (toast-message! "Failed to copy")))}]
      [buttons/ghost {:text     "Print"
                      :icon     [svg/print-icon :height "16px" :width "16px"]
                      :on-click #(js/window.print)}]]]))

(defn- verification [state-atom on-submit]
  (let [{:keys [code submitting?]} @state-atom
        code-valid? (two-fa/valid-code? code)]
    [:div
     [:p {:style {:color       ($/color-picker :neutral-black)
                  :font-size   "14px"
                  :font-weight "400"
                  :margin      "0 0 1rem 0"}}
      "Enter or Paste the 6-digit code you see on the app."]
     [:div {:style {:margin-bottom "1rem"}}
      [code-field state-atom {:on-submit   (when (and code-valid? (not submitting?)) on-submit)
                              :auto-focus? true
                              :placeholder "6-Digit Code"}]]

     [:div {:style {:display    "flex"
                    :flex-wrap  "wrap"
                    :gap        "12px"
                    :margin-top "1rem"}}
      [buttons/ghost {:text      "Cancel"
                      :disabled? submitting?
                      :on-click  #(set! (.-location js/window) "/account-settings")}]
      [buttons/primary {:text      "Continue"
                        :disabled? (or (not code-valid?) submitting?)
                        :on-click  on-submit}]]]))

(defn- method-selection []
  [:div {:style {:display        "flex"
                 :flex-direction "column"
                 :gap            "12px"}}
   [:p {:style {:color       ($/color-picker :neutral-black)
                :font-size   "16px"
                :font-weight "600"
                :margin      "0"
                :line-height "1.4"}}
    "Which 2FA method do you want to use? Choose one method:"]

   [:div {:style {:display        "flex"
                  :flex-direction "column"
                  :gap            "12px"}}
    [:p {:style {:color       ($/color-picker :neutral-black)
                 :font-size   "14px"
                 :font-weight "600"
                 :margin      "0"}}
     "Verify with an App (Recommended)"]
    [:p {:style {:color       ($/color-picker :neutral-md-gray)
                 :font-size   "14px"
                 :font-weight "400"
                 :line-height "1.5"
                 :margin      "0"}}
     "Use an authentication app like Google Authenticator, Microsoft Authenticator, or Authy."]
    [buttons/ghost {:text     "Setup Authentication App"
                    :on-click start-totp-setup!}]]

   [:div {:style {:display        "flex"
                  :flex-direction "column"
                  :gap            "12px"}}
    [:p {:style {:color       ($/color-picker :neutral-black)
                 :font-size   "14px"
                 :font-weight "600"
                 :margin      "0"}}
     "Get a verification code via Email"]
    [:p {:style {:color       ($/color-picker :neutral-md-gray)
                 :font-size   "14px"
                 :font-weight "400"
                 :line-height "1.5"
                 :margin      "0"}}
     "Receive Codes via Email"]
    [buttons/ghost {:text     "Email Verification"
                    :on-click enable-email-2fa!}]]])

(defn- email-verification []
  (let [{:keys [code submitting?]} @state
        code-valid? (two-fa/valid-code? code)]
    [:div {:style {:display        "flex"
                   :flex-direction "column"
                   :gap            "12px"}}
     [:p {:style {:color       ($/color-picker :neutral-black)
                  :font-size   "14px"
                  :font-weight "400"
                  :margin      "0"}}
      "Enter the verification code sent to your email:"]
     [code-field state {:on-submit   (when (and code-valid? (not submitting?))
                                       complete-email-2fa!)
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
                        :on-click  complete-email-2fa!}]]]))

(defn- verify-email-before-switch []
  (let [{:keys [code submitting?]} @state
        code-valid? (two-fa/valid-code? code)]
    [:div {:style {:display        "flex"
                   :flex-direction "column"
                   :gap            "12px"}}
     [:p {:style {:color       ($/color-picker :neutral-black)
                  :font-size   "14px"
                  :font-weight "400"
                  :margin      "0"}}
      "Enter the verification code sent to your email:"]
     [code-field state {:on-submit   (when (and code-valid? (not submitting?))
                                       verify-email-and-begin-switch!)
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
                        :on-click  verify-email-and-begin-switch!}]]]))

(defn root-component []
  (r/create-class
   {:component-did-mount
    (fn []
      (reset! state initial-state)
      (let [url-params (js/URLSearchParams. (.-search js/location))
            mode (if (= "switch" (.get url-params "mode")) :switch :initial)
            initial-step (if (= mode :switch) :verify-email-before-switch :method-choice)]
        (swap! state assoc :mode mode :step initial-step)
        (when (= mode :switch)
          (go
            (<! (fetch-user-email!))
            (when (:user-email @state)
              (send-email-verification-for-switch!))))
        (when (= mode :initial)
          (fetch-user-email!))))
    :reagent-render
    (fn []
      (let [{:keys [loading backup-codes mode]
             current-step :step} @state]
        [two-fa/tfa-layout {:title (if (= mode :switch) "SWITCH TO AUTHENTICATOR" "SETUP 2FA")}
         [:div
          (cond
            loading
            [:div {:style {:padding    "2rem"
                           :text-align "center"}}
             [:p "Loading..."]]

            (= current-step :method-choice)
            [method-selection]

            (= current-step :email-verify)
            [email-verification]

            (= current-step :verify-email-before-switch)
            [verify-email-before-switch]

            (= current-step :totp-setup)
            [:div
             [step 1 "Scan QR code with your authenticator app"
              [enrollment @state #(swap! state update :show-key? not)]]

             [step 2 "Save your backup codes"
              [codes backup-codes]]

             [:div {:style {:margin-top "2rem"}}
              [step 3 "Verify your device"
               [verification state complete-totp-setup!]]]])]]))}))
