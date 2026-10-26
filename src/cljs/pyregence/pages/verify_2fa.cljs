(ns pyregence.pages.verify-2fa
  (:require
   [clojure.core.async             :refer [<! go timeout]]
   [pyregence.components.messaging :refer [toast-message!]]
   [pyregence.components.nav-bar   :refer [nav-bar]]
   [pyregence.components.two-fa    :as two-fa]
   [pyregence.components.utils     :as utils]
   [pyregence.state                :as !]
   [pyregence.utils.async-utils    :as u-async]
   [pyregence.utils.browser-utils  :as u-browser]
   [reagent.core                   :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce pending?          (r/atom false))   ;; Loading state
(defonce email             (r/atom ""))      ;; User email
(defonce verification-code (r/atom ""))      ;; Code input
(defonce method            (r/atom :email)) ;; :email or :totp

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- verify-2fa! []
  (go
    (reset! pending? true)
    (let [{:keys [success status]} (<! (u-async/call-clj-async! "verify-2fa" @email @verification-code))]
      (cond
        success
        (do (toast-message! "Your verification code has been verified successfully.")
            (<! (timeout 2000))
            (u-browser/jump-to-url! "/forecast"))

        (= status 401)
        (do (toast-message! "Your login has expired. Please sign in again.")
            (<! (timeout 2000))
            (u-browser/jump-to-url! "/login"))

        :else
        (do (toast-message! (two-fa/code-rejection status))
            (reset! pending? false))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
        (reset! method (keyword (or (:method params) "email")))
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
             [two-fa/code-entry {:auth-method @method
                                 :user-email  @email
                                 :code        verification-code
                                 :on-verify   verify-2fa!
                                 :on-resend   #(go
                                                 (if (:success (<! (u-async/call-clj-async! "send-email" @email :2fa)))
                                                   (toast-message! "A new verification code has been sent to your email.")
                                                   (toast-message! "Failed to send verification code. Please try again.")))}]}])])})))
