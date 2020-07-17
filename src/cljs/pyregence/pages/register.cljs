(ns pyregence.pages.register
  (:require herb.core
            [reagent.core :as r]
            [clojure.core.async :refer [go <! timeout]]
            [pyregence.utils  :as u]
            [pyregence.styles :as $]
            [pyregence.components.common :refer [simple-form]]
            [pyregence.components.messaging :refer [toast-message!]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def pending? (r/atom false))

(defonce email       (r/atom ""))
(defonce full-name   (r/atom ""))
(defonce password    (r/atom ""))
(defonce re-password (r/atom ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-user! []
  (go
    (toast-message! "Creating new account. This may take a moment...")
    (if (and (:success (<! (u/call-clj-async! "insert-contact" @email @full-name @password)))
             (:success (<! (u/call-clj-async! "send-email" @email :new-user))))
      (do (toast-message! ["Your account has been created successfully."
                           "Please check your email for a registration confirmation."])
          (<! (timeout 4000))
          (u/jump-to-url! "/near-term-forecast"))
      (toast-message! ["An error occurred in registering"
                       "Please contact support@pyregence.org for help."]))))

(defn register! []
  (go
    (reset! pending? true)
    (let [email-chan (u/call-clj-async! "user-email-exists" -1 @email)
          errors     (remove nil?
                             [(when (u/missing-data? @email @password @re-password)
                                "You must fill in all required information to continue.")

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

(defn root-component []
  [:div {:style ($/combine ($/disabled-group @pending?)
                           {:display "flex" :justify-content "center" :margin "5rem"})}
   [simple-form
    "Register"
    "Register"
    [["Email"             email       "text"]
     ["Full Name"         full-name   "text"]
     ["Password"          password    "password"]
     ["Re-enter Password" re-password "password"]]
    register!]])
