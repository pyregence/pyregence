(ns pyregence.pages.login
  (:require
   [cljs.reader                    :as edn]
   [clojure.core.async             :refer [<! go timeout]]
   [pyregence.analytics            :refer [gtag]]
   [pyregence.components.buttons   :as buttons]
   [pyregence.components.messaging :refer [toast-message!]]
   [pyregence.components.nav-bar   :refer [nav-bar]]
   [pyregence.components.utils     :as utils]
   [pyregence.state                :as !]
   [pyregence.styles               :as $]
   [pyregence.utils.async-utils    :as u-async]
   [pyregence.utils.browser-utils  :as u-browser]
   [reagent.core                   :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce pending? (r/atom false))
(defonce forgot?  (r/atom false))
(defonce email    (r/atom ""))
(defonce password (r/atom ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- log-in! []
  (go
    ;;TODO consider validating email before sending it.
    (let [response (<! (u-async/call-clj-async! "log-in" @email @password))]
      (if (:success response)
        (let [resp-data (edn/read-string (:body response))]
          (if (:require-2fa resp-data)
            ;; 2FA is required, redirect to 2FA verification page
            (u-browser/jump-to-url! (str "/verify-2fa?email=" (:email resp-data) "&method=" (:method resp-data)))
            ;; Normal login success
            (let [url (:redirect-from (u-browser/get-session-storage) "/forecast")]
              (u-browser/clear-session-storage!)
              (u-browser/jump-to-url! url)
              (gtag "log-in" {}))))
        ;; Login failed
        ;; TODO, it would be helpful to show the user which of the two errors it actually is.
        (toast-message! ["Invalid login credentials. Please try again."
                         "If you feel this is an error, check your email for the verification email."])))))

(defn- request-password! []
  (go
    (reset! pending? true)
    (toast-message! "Submitting request. This may take a moment...")
    (cond
      (not (:success (<! (u-async/call-clj-async! "user-email-taken" @email))))
      (toast-message! (str "There is no user with the email '" @email "'"))

      (:success (<! (u-async/call-clj-async! "send-email" @email :reset)))
      (do (toast-message! "Please check your email for a password reset link.")
          (<! (timeout 4000))
          (u-browser/jump-to-url! "/forecast"))

      :else
      (toast-message! ["An error occurred."
                       "Please try again shortly or contact support@pyrecast.com for help."]))
    (reset! pending? false)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component
  "The root component for the /login page.
   Displays either the login form or request new password form and a link to the register page."
  [_]
  (let [update-fn (atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (reset! update-fn (fn [& _]
                            (-> js/window (.scrollTo 0 0))
                            (reset! !/mobile? (> 800.0 (.-innerWidth js/window)))))
        (-> js/window (.addEventListener "touchend" update-fn))
        (-> js/window (.addEventListener "resize"   update-fn))
        (@update-fn))
      :component-will-unmount
      (fn [_]
        (.removeEventListener js/window "touchend" @update-fn)
        (.removeEventListener js/window "resize" @update-fn))
      :reagent-render
      (fn [_]
        ;; TODO consider making a page component to share styles with account settings.
        ;; At the very least the styles on the page div should probably be shared to avoid drift.
        [utils/card-page
         (fn []
           (let [color         ($/color-picker :primary-main-orange)
                 email-cmpt    (fn [] [utils/input-labeled {:label        "Email"
                                                           :placeholder "Enter Email Address"
                                                           :on-change   #(reset! email (-> % .-target .-value))
                                                           :value       @email}])
                 register-cmpt (fn [] [:p "Don't have an account? "
                                      [:a {:href  "/register"
                                           :style {:color color}}
                                       [:u "Register Here."]]])]
             (if-not @forgot?
               [utils/card {:title "LOGIN"
                            :children
                            [:<>
                             [email-cmpt]
                             [utils/input-labeled {:label       "Password"
                                                   :type        "password"
                                                   :placeholder "Enter Password"
                                                   :on-change   #(reset! password (-> % .-target .-value))
                                                   :value       @password}]
                             [:a {:href     "#"
                                  :on-click #(reset! forgot? true)
                                  :style    {:color     color
                                             :underline true}}
                              [:u "Forgot Password?"]]
                             [buttons/primary {:text     "Login"
                                               :on-click log-in!}]
                             [register-cmpt]]}]
               [utils/card {:title "Request New Password"
                            :children
                            [:<>
                             [email-cmpt]
                             [buttons/primary {:text     "Submit"
                                               :on-click request-password!}]
                             [register-cmpt]]}])))])})))
