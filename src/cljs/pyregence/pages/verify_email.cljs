(ns pyregence.pages.verify-email
  (:require [reagent.core :as r]
            [clojure.core.async :refer [go <! timeout]]
            [pyregence.utils :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce pending? (r/atom true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn verify-account! [email reset-key]
  (go
    (if (:success (<! (u/call-clj-async! "verify-user-email" email reset-key)))
      (do
        (<! (timeout 2000))
        (u/jump-to-url! "/near-term-forecast"))
      (reset! pending? false))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component [params]
  (verify-account! (or (:email params) "") (or (:reset-key params) ""))
  [:div {:style {:display "flex" :justify-content "center" :margin "5rem"}}
   [:h4 (if @pending?
          "Thank you for verifying your email. You will be automatically redirected to the near term forecast tool."
          "Email verification has failed. Please contact support@pyregence.org for help.")]])
