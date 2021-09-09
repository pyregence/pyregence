(ns pyregence.pages.reset-password
  (:require [reagent.core       :as r]
            [clojure.core.async :refer [go <! timeout]]
            [pyregence.utils    :as u]
            [pyregence.styles   :as $]
            [pyregence.components.common    :refer [simple-form]]
            [pyregence.components.messaging :refer [toast-message!
                                                    toast-message
                                                    process-toast-messages!]]))

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

(defn- reset-password! []
  (go
    (reset! pending? true)
    (let [errors (remove nil?
                         [(when (u/missing-data? @email @password @re-password)
                            "Please fill in all the information.")

                          (when (< (count @password) 8)
                            "Your password must be at least 8 charactors long.")

                          (when (not= @password @re-password)
                            "Password fields do not match.")

                          (when-not (:success (<! (u/call-clj-async! "user-email-taken" @email -1)))
                            (str "The email '" @email "' does not exist."))])]

      (if (pos? (count errors))
        (do (toast-message! errors)
            (reset! pending? false))
        (if (:success (<! (u/call-clj-async! "set-user-password" @email @password @reset-key)))
          (do (toast-message! "Your password has been reset successfully.")
              (<! (timeout 2000))
              (u/jump-to-url! "/forecast"))
          (do (toast-message! (str "Error reseting password for " @email "."))
              (reset! pending? false)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component [params]
  (reset! email     (:email     params ""))
  (reset! reset-key (:reset-key params ""))
  (process-toast-messages!)
  (fn [_]
    [:<>
     [toast-message]
     [:div {:style ($/combine ($/disabled-group @pending?)
                              {:display "flex" :justify-content "center" :margin "5rem"})}
      [simple-form
       "Reset Password"
       "Reset Password"
       [["Email"                 email       "email"    "email"]
        ["New Password"          password    "password" "new-password"]
        ["Re-enter New Password" re-password "password" "confirm-password"]]
       reset-password!]]]))
