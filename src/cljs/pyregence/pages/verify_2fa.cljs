(ns pyregence.pages.verify-2fa
  (:require
   [clojure.core.async             :refer [<! go timeout]]
   [pyregence.components.messaging :refer [toast-message!]]
   [pyregence.components.nav-bar   :refer [nav-bar]]
   [pyregence.components.utils     :as utils]
   [pyregence.state                :as !]
   [pyregence.styles               :as $]
   [pyregence.utils.async-utils    :as u-async]
   [pyregence.utils.browser-utils  :as u-browser]
   [reagent.core                   :as r]
   [pyregence.components.buttons   :as buttons]))

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
  (let [update-fn (atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (reset! update-fn (fn [& _]
                            (-> js/window (.scrollTo 0 0))
                            (reset! !/mobile? (> 800.0 (.-innerWidth js/window)))))
        (-> js/window (.addEventListener "touchend" update-fn))
        (-> js/window (.addEventListener "resize"   update-fn))
        (@update-fn)
        (reset! email (or (:email params) ""))
        (reset! method (or (:method params) "email"))
        (when (empty? @email)
          (u-browser/jump-to-url! "/login")))

      :component-will-unmount
      (fn [_]
        (.removeEventListener js/window "touchend" @update-fn)
        (.removeEventListener js/window "resize" @update-fn))
      :reagent-render
      (fn [_]
        [utils/card-page
         (fn []
           [utils/card
            {:title "Two-Factor Authentication"
             :children
             [:<>
              [utils/input-labeled {:label     "Verification Code"
                                    :value     @verification-code
                                    :on-change #(reset! verification-code (-> % .-target .-value))}]
              [buttons/primary {:text     "Verify"
                                :on-click verify-2fa!}]
              [prompt {:auth-method @method :user-email @email}
               #(go
                  (if (:success (<! (u-async/call-clj-async! "send-email" @email :2fa)))
                    (toast-message! "A new verification code has been sent to your email.")
                    (toast-message! "Failed to send verification code. Please try again.")))]]}])])})))
