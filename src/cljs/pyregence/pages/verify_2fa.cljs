(ns pyregence.pages.verify-2fa
  (:require [clojure.core.async             :refer [go <! timeout]]
            [pyregence.components.common    :refer [simple-form]]
            [pyregence.components.messaging :refer [toast-message!]]
            [pyregence.styles               :as $]
            [pyregence.utils.async-utils    :as u-async]
            [pyregence.utils.browser-utils  :as u-browser]
            [reagent.core                   :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce pending?          (r/atom false))   ;; Loading state
(defonce email             (r/atom ""))      ;; User email
(defonce verification-code (r/atom ""))      ;; Code input
(defonce method            (r/atom "email")) ;; 'email' or 'totp'

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- verify-2fa! []
  (go
    (reset! pending? true)
    (let [errors (remove nil?
                         [(when (empty? @verification-code)
                            "Please enter the verification code.")])]

      (if (seq errors)
        (do (toast-message! errors)
            (reset! pending? false))
        (if (:success (<! (u-async/call-clj-async! "verify-2fa" @email @verification-code)))
          (do (toast-message! "Your verification code has been verified successfully.")
              (<! (timeout 2000))
              (u-browser/jump-to-url! "/forecast"))
          (do (toast-message! "Invalid verification code. Please try again.")
              (reset! pending? false)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- prompt [{:keys [auth-method user-email]} on-resend-email]
  (if (= auth-method "totp")
    [:<>
     [:p {:style {:margin-bottom "0.5rem"}}
      "Enter the 6-digit code from your authenticator app."]
     [:p {:style {:color       "#666"
                  :font-size   "0.9rem"
                  :margin-top  "0.5rem"}}
      "You can also use one of your 8-character backup codes."]]
    [:<>
     [:p {:style {:margin-bottom "0.5rem"}}
      [:span
       "Enter the verification code sent to "
       [:span {:style {:font-weight "bold"}} user-email]
       "."]]
     [:a {:href     "#"
          :on-click on-resend-email
          :style    {:color      ($/color-picker :link-color)
                     :font-size  "0.9rem"
                     :margin-top "0.5rem"}}
      "Resend verification code"]]))

(defn root-component
  "The root component for the /verify-2fa page.
   Displays the 2FA verification form."
  [params]
  (reset! email (or (:email params) ""))
  (reset! method (or (:method params) "email"))
  ;; Redirect if no email
  (when (empty? @email)
    (u-browser/jump-to-url! "/login"))
  (fn [_]
    [:<>
     [:div {:style ($/combine ($/disabled-group @pending?)
                              {:display "flex" :justify-content "center" :margin "5rem"})}
      [simple-form
       "Two-Factor Authentication"
       "Verify"
       [["Verification Code" verification-code "text" "verification-code"]]
       verify-2fa!]]
     [:div {:style {:align-items    "center"
                    :display        "flex"
                    :flex-direction "column"
                    :margin-top     "1rem"}}
      [prompt {:auth-method @method :user-email @email}
       #(go
          (if (:success (<! (u-async/call-clj-async! "send-email" @email :2fa)))
            (toast-message! "A new verification code has been sent to your email.")
            (toast-message! "Failed to send verification code. Please try again.")))]]]))
