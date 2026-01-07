(ns pyregence.pages.reset-password
  (:require [clojure.core.async             :refer [go <! timeout]]
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

(defonce pending?           (r/atom false))
(defonce email              (r/atom ""))
(defonce verification-token (r/atom ""))
(defonce password           (r/atom ""))
(defonce re-password        (r/atom ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- reset-password! []
  (go
    (reset! pending? true)
    (let [errors (remove nil?
                         [(when (u-data/missing-data? @email @password @re-password)
                            "Please fill in all the information.")

                          (when (< (count @password) 8)
                            "Your password must be at least 8 characters long.")

                          (when (not= @password @re-password)
                            "Password fields do not match.")

                          (when-not (:success (<! (u-async/call-clj-async! "user-email-taken" @email -1)))
                            (str "The email '" @email "' does not exist."))])]

      (if (pos? (count errors))
        (do (toast-message! errors)
            (reset! pending? false))
        (if (:success (<! (u-async/call-clj-async! "set-user-password" @email @password @verification-token)))
          (do (toast-message! "Your password has been reset successfully.")
              (<! (timeout 2000))
              (u-browser/jump-to-url! "/forecast"))
          (do (toast-message! (str "Error reseting password for " @email "."))
              (reset! pending? false)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component
  "The root component for the /reset-password page.
   Displays the reset password form."
  [params]
  (reset! email              (:email params ""))
  (reset! verification-token (:verification-token params ""))
  (fn [_]
    [:<>
     [:div {:style ($/combine ($/disabled-group @pending?)
                              {:display "flex" :justify-content "center" :margin "5rem"})}
      [simple-form
       "Reset Password"
       "Reset Password"
       [["Email"                 email       "email"    "email"]
        ["New Password"          password    "password" "new-password"]
        ["Re-enter New Password" re-password "password" "confirm-password"]]
       reset-password!]]]))
