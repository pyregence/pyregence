(ns pyregence.pages.disable-2fa
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
  {:code        ""     ;; Verification code
   :error       false  ;; Error state
   :loading     true   ;; Loading state
   :method      nil    ;; 2FA method
   :submitting? false  ;; Form submitting
   :user-email  nil})  ;; User email

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

(defn- send-verification-email! []
  (go
    (let [user-email (:user-email @state)]
      (if (:success (<! (u-async/call-clj-async! "send-email" user-email :2fa)))
        (toast-message! "Verification code sent to your email")
        (toast-message! "Failed to send verification code")))))

(defn- handle-disable-2fa! []
  (go
    (let [code (:code @state)]
      (when (and (two-fa/valid-code? code) (not (:submitting? @state)))
        (swap! state assoc :submitting? true)
        (let [response (<! (u-async/call-clj-async! "disable-2fa" code))]
          (swap! state assoc :submitting? false)
          (if (:success response)
            (do
              (toast-message! "Two-Factor Authentication disabled")
              (js/setTimeout #(set! (.-location js/window) "/account-settings") redirect-delay-ms))
            (toast-message! "Invalid code. Please try again.")))))))

(defn- code-field [state-map & [opts]]
  (let [code (r/cursor state-map [:code])]
    [two-fa/code-input code opts]))

(defn- disable-verification [method]
  (let [{:keys [code submitting?]} @state
        is-totp? (= method :totp)
        code-valid? (two-fa/valid-code? code)]
    [:div {:style {:display        "flex"
                   :flex-direction "column"
                   :gap            "20px"}}
     [:p {:style {:color       ($/color-picker :neutral-black)
                  :font-size   "14px"
                  :font-weight "400"
                  :line-height "1.5"
                  :margin      "0"}}
      (if is-totp?
        "Enter your authenticator code or a backup code to disable 2FA:"
        "Enter the verification code sent to your email to disable 2FA:")]
     [code-field state {:on-submit   (when (and code-valid? (not submitting?)) handle-disable-2fa!)
                        :auto-focus? true
                        :placeholder "6-Digit Code"}]
     [:div {:style {:display   "flex"
                    :flex-wrap "wrap"
                    :gap       "12px"}}
      [buttons/ghost {:text      "Cancel"
                      :disabled? submitting?
                      :on-click  #(set! (.-location js/window) "/account-settings")}]
      [buttons/primary {:text      "Disable 2FA"
                        :disabled? (or (not code-valid?) submitting?)
                        :on-click  handle-disable-2fa!}]]]))

(defn root-component []
  (r/create-class
   {:component-did-mount
    (fn []
      (reset! state initial-state)
      (let [url-params (js/URLSearchParams. (.-search js/location))
            method-str (.get url-params "method")
            method (cond
                     (= method-str "totp") :totp
                     (= method-str "email") :email
                     :else :email)]
        (swap! state assoc :method method)
        (go
          (<! (fetch-user-settings!))
          (when (and (= method :email) (:user-email @state))
            (send-verification-email!)))))

    :reagent-render
    (fn []
      (let [{:keys [loading method error]} @state]
        [two-fa/tfa-layout {:title "TURN OFF 2FA"}
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

           :else
           [disable-verification method])]))}))
