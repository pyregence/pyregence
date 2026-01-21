(ns pyregence.pages.reset-password
  (:require
   [clojure.core.async             :refer [<! go timeout]]
   [clojure.string                 :as str]
   [pyregence.components.buttons   :as buttons]
   [pyregence.components.messaging :refer [toast-message!]]
   [pyregence.components.nav-bar   :as nav-bar]
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
  (let [update-fn (atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (reset! update-fn (fn [& _]
                            (-> js/window (.scrollTo 0 0))
                            (reset! !/mobile? (> 800.0 (.-innerWidth js/window)))))
        (-> js/window (.addEventListener "touchend" update-fn))
        (-> js/window (.addEventListener "resize"   update-fn))
        (@update-fn)
        (reset! email              (:email params ""))
        (reset! verification-token (:verification-token params "")))
      :component-will-unmount
      (fn [_]
        (.removeEventListener js/window "touchend" @update-fn)
        (.removeEventListener js/window "resize" @update-fn))
      :reagent-render
      (fn [_]
        [utils/card-page
         (fn []
           [utils/card {:title "Request New Password"
                        :children
                        [:<>
                         [utils/input-labeled {:label       "Email"
                                               :placeholder "Enter Email Address"
                                               :on-change   #(reset! email (-> % .-target .-value))
                                               :value       @email}]
                         [utils/input-labeled {:label       "New Password"
                                               :type        "password"
                                               :placeholder "New Password"
                                               :on-change   #(reset! password (-> % .-target .-value))
                                               :value       @password}]
                         [utils/input-labeled {:label       "Re-enter New Password"
                                               :type        "password"
                                               :placeholder "New Password"
                                               :on-change   #(reset! re-password (-> % .-target .-value))
                                               :value       @re-password}]
                         [buttons/primary {:text     "Reset Password"
                                           :on-click reset-password!}]]}])])})))
