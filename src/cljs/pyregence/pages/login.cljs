(ns pyregence.pages.login
  (:require
   [cljs.reader                    :as edn]
   [clojure.core.async             :refer [<! go timeout]]
   [pyregence.analytics            :refer [gtag]]
   [pyregence.components.common    :refer [simple-form]]
   [pyregence.components.messaging :refer [toast-message!]]
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

(defn- reset-link []
  [:a {:style    ($/align :block :left)
       :href     "#"
       :on-click #(reset! forgot? true)}
   "Forgot Password?"])

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
        [:<>
         [:div {:style ($/combine ($/disabled-group @pending?)
                                  {:display "flex" :justify-content "center" :margin "5rem"})}
          (if @forgot?
            [simple-form
             "Request New Password"
             "Submit"
             [["Email" email "email" "email"]]
             request-password!]
            [simple-form
             "Log in"
             "Log in"
             [["Email"    email    "email"    "email"]
              ["Password" password "password" "current-password"]]
             log-in!
             reset-link])]
         [:div {:style ($/align "flex" "center")}
          "Don't have an account?  "
          [:a {:href "/register" :style {:margin-left "0.2rem"}} "Register here."]]])})))
