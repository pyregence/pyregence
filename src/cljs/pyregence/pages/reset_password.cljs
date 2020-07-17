(ns pyregence.pages.reset-password
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
(defonce reset-key   (r/atom ""))
(defonce password    (r/atom ""))
(defonce re-password (r/atom ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reset-password! []
  (go
    (reset! pending? true)
    (cond
      (u/missing-data? @email @password @re-password)
      (toast-message! "Please fill in all the information.")

      (not= @password @re-password)
      (toast-message! "Password fields do not match.")

      (not (:success (<! (u/call-clj-async! "user-email-exists" @email -1))))
      (toast-message! (str "The email '" @email "' does not exist."))

      :else
      (if (:success (<! (u/call-clj-async! "set-user-password" @email @password @reset-key)))
        (do (toast-message! "Your password has been reset successfully.")
            (<! (timeout 2000))
            (u/jump-to-url! "/near-term-forecast"))
        (do (toast-message! (str "That is not the correct reset key for user " @email "."))
            (reset! pending? false))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component [params]
  (reset! email     (:email     params ""))
  (reset! reset-key (:reset-key params ""))
  (fn [_]
    [:div {:style ($/combine ($/disabled-group @pending?)
                             {:display "flex" :justify-content "center" :margin "5rem"})}
     [simple-form
      "Reset Password"
      "Reset Password"
      [["Email"                 email       "text"]
       ["New Password"          password    "password"]
       ["Re-enter New Password" re-password "password"]]
      reset-password!]]))
