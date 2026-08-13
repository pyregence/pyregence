(ns pyregence.pages.register
  (:require
   [clojure.core.async                        :refer [<! go timeout]]
   [pyregence.analytics                       :refer [gtag]]
   [pyregence.components.buttons              :as buttons]
   [pyregence.components.messaging            :refer [toast-message!]]
   [pyregence.components.nav-bar              :refer [nav-bar]]
   [pyregence.components.password.validations :as password->validations]
   [pyregence.components.utils                :as utils]
   [pyregence.state                           :as !]
   [pyregence.styles                          :as $]
   [pyregence.utils.async-utils               :as u-async]
   [pyregence.utils.browser-utils             :as u-browser]
   [pyregence.utils.data-utils                :as u-data]
   [pyregence.validation                      :as v]
   [reagent.core                              :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce pending?       (r/atom false))
(defonce email          (r/atom ""))
(defonce re-email       (r/atom ""))
(defonce password       (r/atom ""))
(defonce re-password    (r/atom ""))
(defonce marketplace?   (r/atom false))
(defonce org-name       (r/atom ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn email->username
  [email]
  (subs email 0 (str/index-of email "@")))


(defn- add-user! []
  (go
    (toast-message! "Creating new account. This may take a moment...")
    ;;TODO it's awkward that add-new-user requires a user's name when it's not a unique identifier. Consider alternatives.
    (let [{:keys [success body]} (<! (if @marketplace?
                                       (u-async/call-clj-async! "add-new-user" @email "" @password @org-name)
                                       (u-async/call-clj-async! "add-new-user" @email "" @password)))]
      (if (and success
               (:success (<! (u-async/call-clj-async! "send-email" @email :new-user))))
        (do (toast-message! ["Your account has been created successfully."
                             "Please check your email for a link to complete registration."])
            (gtag "registered-user" {})
            (<! (timeout 4000))
            (u-browser/jump-to-url! "/forecast"))
        ;; The backend's validation exceptions arrive as data
        ;; ({:message ... :errors [...]}); show the user exactly which
        ;; conditions failed rather than a generic apology.
        (toast-message!
         (if-let [errors (u-data/server-errors body)]
           (into ["We couldn't create your account:"] errors)
           ["An error occurred while registering."
            "Please contact support@pyrecast.com for help."]))))))

(defn- register! []
  (reset! pending? true)
  ;; Instant pre-validation from the same cljc rules the backend enforces
  ;; (pyregence.validation); the backend remains authoritative and reports
  ;; through the same toast if anything slips past.
  (let [errors (concat
                (remove nil?
                        [(when (u-data/missing-data? @email @re-email @password @re-password)
                           "You must fill in all required information to continue.")

                         (when-not (= @email @re-email)
                           "The emails you have entered do not match.")

                         (when-not (= @password @re-password)
                           "The passwords you have entered do not match.")])
                (when (u-data/has-data? @email)
                  (v/errors-for "Email" v/email-steps @email))
                (password->validations/->toast @password))]
    (if (seq errors)
      (do (toast-message! (vec errors))
          (reset! pending? false))
      (add-user!))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;TODO this should definatly share logic with the login and forgot-password page.
(defn root-component
  "The root component for the /register page.
   Displays the register form and a link to the login page."
  [_]
  (let [update-fn (atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (let [url-params (js/URLSearchParams. (.-search js/location))]
          (when (= "1" (.get url-params "marketplace"))
            (reset! marketplace? true)
            (reset! org-name (or (.get url-params "org") ""))))
        (reset! update-fn (fn [& _]
                            (-> js/window (.scrollTo 0 0))
                            (reset! !/mobile? (> 800.0 (.-innerWidth js/window)))))
        (-> js/window (.addEventListener "touchend" update-fn))
        (-> js/window (.addEventListener "resize"   update-fn))
        (@update-fn))
      :component-will-unmount
      (fn [_]
        (.removeEventListener js/window "touchend" @update-fn)
        (.removeEventListener js/window "resize" @update-fn))
      :reagent-render
      (fn [_]
        [utils/card-page
         (fn []
           [utils/card {:title "Register"
                        :children
                        [:<>
                         (when @marketplace?
                           [utils/input-labeled {:label       "Organization Name"
                                                 :placeholder "e.g., Acme Corp"
                                                 :on-change   #(reset! org-name (-> % .-target .-value))
                                                 :value       @org-name}])
                         [utils/input-labeled {:label       "Email"
                                               :placeholder "Enter Email Address"
                                               :on-change   #(reset! email (-> % .-target .-value))
                                               :value       @email}]
                         [utils/input-labeled {:label       "Confirm Email"
                                               :placeholder "Enter Email Address"
                                               :on-change   #(reset! re-email (-> % .-target .-value))
                                               :value       @re-email}]
                         [utils/input-labeled {:label       "Password"
                                               :type        "password"
                                               :placeholder "Enter Password"
                                               :on-change   #(reset! password (-> % .-target .-value))
                                               :value       @password}]
                         [password->validations/->cmpt @password]
                         [utils/input-labeled {:label       "Confirm Password"
                                               :type        "password"
                                               :placeholder "Confirm Password"
                                               :on-change   #(reset! re-password (-> % .-target .-value))
                                               :value       @re-password}]
                         [buttons/primary {:text      "Register"
                                           :on-click register!}]
                         [:p "Already have an account? "
                          [:a {:href  "/login"
                               :style {:color ($/color-picker :primary-main-orange)}}
                           [:u "Login Here."]]]]}])])})))
