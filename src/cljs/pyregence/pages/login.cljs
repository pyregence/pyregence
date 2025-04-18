(ns pyregence.pages.login
  (:require
   [clojure.core.async             :refer [<! go timeout]]
   [pyregence.analytics            :refer [gtag]]
   [pyregence.components.common    :refer [simple-form]]
   [pyregence.components.messaging :refer [toast-message!]]
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
    (if (:success (<! (u-async/call-clj-async! "log-in" @email @password)))
      (let [url (:redirect-from (u-browser/get-session-storage) "/forecast")]
        (u-browser/clear-session-storage!)
        (u-browser/jump-to-url! url)
        (gtag "log-in" {}))
      ;; TODO, it would be helpful to show the user which of the two errors it actually is.
      (toast-message! ["Invalid login credentials. Please try again."
                       "If you feel this is an error, check your email for the verification email."]))))

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
                       "Please try again shortly or contact support@pyregence.org for help."]))
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
    [:a {:href "/register" :style {:margin-left "0.2rem"}} "Register here."]]])
