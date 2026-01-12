(ns pyregence.pages.register
  (:require
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
   [pyregence.utils.data-utils     :as u-data]
   [reagent.core                   :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce pending?       (r/atom false))
(defonce email          (r/atom ""))
(defonce re-email       (r/atom ""))
(defonce password       (r/atom ""))
(defonce re-password    (r/atom ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- add-user! []
  (go
    (toast-message! "Creating new account. This may take a moment...")
    ;;TODO it's awkward that add-new-user requires a user's name when it's not a unique identifier. Consider alternatives.
    (if (and (:success (<! (u-async/call-clj-async! "add-new-user" @email "" @password)))
             (:success (<! (u-async/call-clj-async! "send-email" @email :new-user))))
      (do (toast-message! ["Your account has been created successfully."
                           "Please check your email for a link to complete registration."])
          (gtag "registered-user" {})
          (<! (timeout 4000))
          (u-browser/jump-to-url! "/forecast"))
      (toast-message! ["An error occurred while registering."
                       "Please contact support@pyrecast.com for help."]))))

(defn- register! []
  (go
    (reset! pending? true)
    (let [email-chan (u-async/call-clj-async! "user-email-taken" @email)
          errors     (remove nil?
                             ;;TODO consider adding an email validation.
                             [(when (u-data/missing-data? @email @re-email @password @re-password)
                                "You must fill in all required information to continue.")

                              (when-not (= @email @re-email)
                                "The emails you have entered do not match.")

                              (when (< (count @password) 8)
                                "Your password must be at least 8 characters long.")

                              (when-not (= @password @re-password)
                                "The passwords you have entered do not match.")

                              (when (:success (<! email-chan))
                                (str "A user with the email '" @email "' has already been created."))])]
      (if (pos? (count errors))
        (do (toast-message! errors)
            (reset! pending? false))
        (add-user!)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;TODO this should definatly share logic with the login and forgot-password page.
(defn root-component
  "The root component for the /register page.
   Displays the register form and a link to the login page."
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
        [:div
         {:style {:height         "100vh"
                  :margin-bottom  "40px"
                  :display        "flex"
                  :flex-direction "column"
                  :font-family    "Roboto"
                  :padding-bottom "60px"
                  :background     ($/color-picker :lighter-gray)
                  :place-items    "center"}}
         [nav-bar {:mobile?            @!/mobile?
                   :on-forecast-select (fn [forecast]
                                         (u-browser/jump-to-url!
                                          (str "/?forecast=" (name forecast))))}]
         [:div {:style {:display         "flex"
                        :justify-content "center"
                        :align-content   "center"
                        :height          "100%"
                        :margin          "100px"}}
          [utils/card {:title "Register"
                       :children
                       [:<>
                        [utils/input-labeled {:label       "Email"
                                              :placeholder "Enter Email Address"
                                              :on-change   #(reset! email (-> % .-target .-value))
                                              :value       @email}]
                        [utils/input-labeled {:label       "Confirm Email"
                                              :placeholder "Enter Email Address"
                                              :on-change   #(reset! re-email (-> % .-target .-value))
                                              :value       @re-email}]
                        [utils/input-labeled {:label       "Password"
                                              :type        "password"
                                              :placeholder "Enter Password"
                                              :on-change   #(reset! password (-> % .-target .-value))
                                              :value       @password}]
                        [utils/input-labeled {:label       "Confirm Password"
                                              :type        "password"
                                              :placeholder "Confirm Password"
                                              :on-change   #(reset! re-password (-> % .-target .-value))
                                              :value       @re-password}]
                        [buttons/primary {:text     "Register"
                                          :on-click register!}]
                        [:p "Already have an account? "
                         [:a {:href  "/login"
                              :style {:color ($/color-picker :primary-main-orange)}}
                          [:u "Login Here."]]]]}]]])})))
