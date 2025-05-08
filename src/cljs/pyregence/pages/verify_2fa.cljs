(ns pyregence.pages.verify-2fa
  (:require [cljs.reader                    :as edn]
            [clojure.core.async             :refer [go <! timeout]]
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
(defonce verification-code  (r/atom ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- verify-2fa! []
  (go
    (reset! pending? true)
    (let [errors (remove nil?
                         [(when (u-data/missing-data? @email @verification-code)
                            "Please enter the verification code.")])]

      (if (pos? (count errors))
        (do (toast-message! errors)
            (reset! pending? false))
        (if (:success (<! (u-async/call-clj-async! "verify-2fa" @email @verification-code)))
          (do (toast-message! "Your verification code has been verified successfully.")
              (<! (timeout 2000))
              (u-browser/jump-to-url! "/forecast"))
          (do (toast-message! "Invalid verification code. Please try again.")
              (reset! pending? false)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component
  "The root component for the /verify-2fa page.
   Displays the 2FA verification form."
  [params]
  (reset! email (or (:email params) ""))
  (fn [_]
    [:<>
     [:div {:style ($/combine ($/disabled-group @pending?)
                              {:display "flex" :justify-content "center" :margin "5rem"})}
      [simple-form
       "Two-Factor Authentication"
       "Verify"
       [["Verification Code" verification-code "text" "verification-code"]]
       verify-2fa!]]
     [:div {:style {:display "flex"
                    :flex-direction "column"
                    :align-items "center"
                    :margin-top "1rem"}}
      [:p {:style {:margin-bottom "0.5rem"}}
       (str "Enter the verification code sent to ")
       [:span {:style {:font-weight "bold"}} @email]
       "."]
      [:a {:href "#"
           :style {:color ($/color-picker :link-color)}
           :on-click #(go
                        (if (:success (<! (u-async/call-clj-async! "send-email" @email :2fa)))
                          (toast-message! "A new verification code has been sent to your email.")
                          (toast-message! "Failed to send verification code. Please try again.")))}
       "Resend verification code"]]]))
