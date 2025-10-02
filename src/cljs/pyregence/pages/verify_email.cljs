(ns pyregence.pages.verify-email
  (:require [clojure.core.async            :refer [go <! timeout]]
            [pyregence.utils.async-utils   :as u-async]
            [pyregence.utils.browser-utils :as u-browser]
            [reagent.core                  :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce pending? (r/atom true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- verify-account! [email verification-token]
  (go
    (if (:success (<! (u-async/call-clj-async! "verify-user-email" email verification-token)))
      (do
        (<! (timeout 2000))
        (u-browser/jump-to-url! "/forecast"))
      (reset! pending? false))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component
  "The root component for the /verify-email page.
   Displays the appropriate message depending on the email verification status."
  [params]
  (verify-account! (:email params "") (:verification-token params ""))
  (fn [_]
    [:div {:style {:display "flex" :justify-content "center" :margin "5rem"}}
     [:h4 (if @pending?
            "Thank you for verifying your email. You will be automatically redirected to the near term forecast tool."
            "Email verification has failed. Please contact support@pyrecast.com for help.")]]))
