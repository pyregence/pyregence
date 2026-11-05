(ns pyregence.pages.reset-password
  (:require
   [clojure.core.async                        :refer [<! go timeout]]
   [clojure.string                            :as str]
   [pyregence.components.buttons              :as buttons]
   [pyregence.components.messaging            :refer [toast-message!]]
   [pyregence.components.nav-bar              :as nav-bar]
   [pyregence.components.utils                :as utils]
   [pyregence.components.password.validations :as password->validations]
   [pyregence.components.two-fa               :as two-fa]
   [pyregence.state                           :as !]
   [pyregence.styles                          :as $]
   [pyregence.utils.async-utils               :as u-async]
   [pyregence.utils.browser-utils             :as u-browser]
   [pyregence.utils.data-utils                :as u-data]
   [reagent.core                              :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce pending?           (r/atom false))
(defonce email              (r/atom ""))
(defonce verification-token (r/atom ""))
(defonce password           (r/atom ""))
(defonce re-password        (r/atom ""))
(defonce link-state         (r/atom {:step :checking})) ; what the link needs next, as the server says
(defonce code               (r/atom ""))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- check-reset-link!
  "Asks the server what the link needs next."
  []
  (go
    (let [{:keys [success status body]} (<! (u-async/call-clj-async! "check-reset-link" @email @verification-token))]
      (reset! link-state (cond success        (u-data/response-data body)
                               (= 410 status) {:step :invalid}
                               :else          {:step :error})))))

(defn- call-once!
  "The server's answer to `args` on a channel, or nil while an earlier call is still out, so a double
   click sends once."
  [& args]
  (when-not @pending?
    (reset! pending? true)
    (go (let [response (<! (apply u-async/call-clj-async! args))]
          (reset! pending? false)
          response))))

(defn- send-reset-code! []
  (when-let [answer (call-once! "send-reset-code" @email @verification-token)]
    (go
      (let [{:keys [success status]} (<! answer)]
        (cond success        (do (swap! link-state assoc :sent? true)
                                 (toast-message! "A verification code has been sent to your email."))
              (= 410 status) (reset! link-state {:step :invalid})
              :else          (toast-message! "Failed to send verification code. Please try again."))))))

(defn- verify-reset-code! []
  (when-let [answer (call-once! "verify-reset-code" @email @verification-token @code)]
    (go
      (let [{:keys [success status]} (<! answer)]
        (cond success        (reset! link-state {:step :form})
              (= 410 status) (reset! link-state {:step :invalid})
              :else          (toast-message! (two-fa/code-rejection status)))))))

(defn- reset-password! []
  (when-not @pending?
    (reset! pending? true)
    (go
      (let [errors (concat
                    (remove nil?
                            [(when (u-data/missing-data? @email @password @re-password)
                               "Please fill in all the information.")

                             (when (not= @password @re-password)
                               "Password fields do not match.")])
                    ;; token is validated server-side
                    (password->validations/->toast @password))]

        (if (seq errors)
          (do (toast-message! (vec errors))
              (reset! pending? false))
          (let [{:keys [success body]} (<! (u-async/call-clj-async! "set-user-password" @email @password @verification-token))]
            (if success
              (do (toast-message! "Your password has been reset successfully.")
                  (<! (timeout 2000))
                  (u-browser/jump-to-url! "/forecast"))
              (do (toast-message! (or (u-data/server-errors body)
                                      "Error resetting password."))
                  (reset! pending? false)
                  ;; The code may have timed out meanwhile, so ask the link what it needs now.
                  (check-reset-link!)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- code-step
  "The code entry, or for an emailed-code account the button that mails the code first."
  [{:keys [method sent?]}]
  (if (or (= :totp method) sent?)
    [two-fa/code-entry {:auth-method method
                        :user-email  @email
                        :code        code
                        :on-verify   verify-reset-code!
                        :on-resend   send-reset-code!}]
    [:<>
     [:p "To keep your account safe, we'll email a verification code to "
      [:span {:style {:font-weight "bold"}} @email] " first."]
     [buttons/primary {:text     "Email me the code"
                       :on-click send-reset-code!}]]))

(defn- password-form []
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
   [password->validations/->cmpt @password]
   [utils/input-labeled {:label       "Re-enter New Password"
                         :type        "password"
                         :placeholder "New Password"
                         :on-change   #(reset! re-password (-> % .-target .-value))
                         :value       @re-password}]
   [buttons/primary {:text     "Reset Password"
                     :on-click reset-password!}]])

(defn root-component
  "The root component for the /reset-password page.
   Displays the reset password form once the link checks out."
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
        (reset! verification-token (:verification-token params ""))
        (reset! link-state {:step :checking})
        (reset! code "")
        (reset! pending? false)
        (check-reset-link!))
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
                        (case (:step @link-state)
                          :checking [:p "Checking your link..."]
                          :error    [:p "We could not check your link. Please reload the page."]
                          :invalid  [:p "This link is invalid or has expired. "
                                     [:a {:href "/login" :style {:color ($/color-picker :link-color)}}
                                      "Request a new one."]]
                          :code     [code-step @link-state]
                          [password-form])}])])})))
