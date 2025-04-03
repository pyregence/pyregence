(ns pyregence.pages.register
  (:require
   [clojure.core.async             :refer [<! go timeout]]
   [pyregence.analytics            :refer [gtag]]
   [pyregence.components.common    :refer [simple-form]]
   [pyregence.components.messaging :refer [toast-message!]]
   [pyregence.styles               :as $]
   [pyregence.utils.async-utils    :as u-async]
   [pyregence.utils.browser-utils  :as u-browser]
   [pyregence.utils.data-utils     :as u-data]
   [reagent.core                   :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce pending?    (r/atom false))
(defonce email       (r/atom ""))
(defonce full-name   (r/atom ""))
(defonce password    (r/atom ""))
(defonce re-password (r/atom ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- add-user! []
  (go
    (toast-message! "Creating new account. This may take a moment...")
    (if (and (:success (<! (u-async/call-clj-async! "add-new-user" @email @full-name @password)))
             (:success (<! (u-async/call-clj-async! "send-email" @email :new-user))))
      (do (toast-message! ["Your account has been created successfully."
                           "Please check your email for a link to complete registration."])
          (gtag "registered-user" {})
          (<! (timeout 4000))
          (u-browser/jump-to-url! "/forecast"))
      (toast-message! ["An error occurred while registering."
                       "Please contact support@pyregence.org for help."]))))

(defn- register! []
  (go
    (reset! pending? true)
    (let [email-chan (u-async/call-clj-async! "user-email-taken" @email)
          errors     (remove nil?
                             [(when (u-data/missing-data? @email @password @re-password)
                                "You must fill in all required information to continue.")

                              (when (< (count @password) 8)
                                "Your password must be at least 8 charactors long.")

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

(defn root-component
  "The root component for the /register page.
   Displays the register form and a link to the login page."
  [_]
  [:<>
   [:div {:style ($/combine ($/disabled-group @pending?)
                            {:display "flex" :justify-content "center" :margin "5rem"})}
    [simple-form
     "Register"
     "Register"
     [["Email"            email       "email"    "email"]
      ["Full Name"        full-name   "text"     "name"]
      ["Password"         password    "password" "new-password"]
      ["Confirm Password" re-password "password" "confirm-password"]]
     register!]]
   [:div {:style ($/align "flex" "center")}
    "Already have an account?  "
    [:a {:href "/login" :style {:margin-left "0.2rem"}} "Log in here"]
    "."]])
