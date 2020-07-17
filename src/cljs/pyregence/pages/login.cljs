(ns pyregence.pages.login
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

(defonce forgot?  (r/atom false))
(defonce email    (r/atom ""))
(defonce password (r/atom ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn log-in! []
  (go
    (if (:success (<! (u/call-clj-async! "log-in" @email @password)))
      (u/jump-to-url! (:redirect-from (u/get-session-storage) "/near-term-forecast"))
      (toast-message! "Invalid login credentials. Please try again."))))

(defn request-password! []
  (go
    (reset! pending? true)
    (toast-message! "Submitting request. This may take a moment...")
    (if (:success (<! (u/call-clj-async! "send-email" @email :reset)))
      (do (toast-message! ["Your account has been created successfully."
                           "Please check your email for a registration confirmation."])
          (<! (timeout 4000))
          (u/jump-to-url! "/near-term-forecast"))
      (do (toast-message! ["An error occurred in registering"
                           "Please contact support@pyregence.org for help."])
          (reset! pending? false)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reset-link []
  [:a {:style ($/align :block :left)
       :href "#"
       :onClick #(reset! forgot? true)} "Forgot Password?"])

(defn root-component []
  [:div {:style ($/combine ($/disabled-group @pending?)
                           {:display "flex" :justify-content "center" :margin "5rem"})}
   (if @forgot?
     [simple-form
      "Request New Password"
      "Submit"
      [["Email" email "text"]]
      request-password!]
     [simple-form
      "Log in"
      "Log in"
      [["Email"    email "text"]
       ["Password" password "password"]]
      log-in!
      reset-link])])
