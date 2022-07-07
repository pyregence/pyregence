(ns pyregence.pages.verify-email
  (:require [clojure.core.async            :refer [go <! timeout]]
            [pyregence.utils.async-utils   :refer [call-clj-async!]]
            [pyregence.utils.browser-utils :refer [jump-to-url!]]
            [reagent.core                  :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce pending? (r/atom true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- verify-account! [email reset-key]
  (go
    (if (:success (<! (call-clj-async! "verify-user-email" email reset-key)))
      (do
        (<! (timeout 2000))
        (jump-to-url! "/forecast"))
      (reset! pending? false))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component
  "The root component for the /verify-email page.
   Displays the appropriate message depending on the email verification status."
  [params]
  (verify-account! (:email params "") (:reset-key params ""))
  (fn [_]
    [:div {:style {:display "flex" :justify-content "center" :margin "5rem"}}
     [:h4 (if @pending?
            "Thank you for verifying your email. You will be automatically redirected to the near term forecast tool."
            "Email verification has failed. Please contact support@pyregence.org for help.")]]))
