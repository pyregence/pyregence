(ns pyregence.components.two-fa
  "Shared components for two-factor authentication UI"
  (:require [clojure.string                  :as str]
            [herb.core                       :refer [<class]]
            [pyregence.components.buttons    :as buttons]
            [pyregence.components.login-menu :refer [login-menu]]
            [pyregence.components.messaging  :refer [toast-message!]]
            [pyregence.components.utils      :as utils]
            [pyregence.styles                :as $]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def code-length             6)
(def code-validation-pattern #"^[A-Za-z0-9]{0,8}$")
(def totp-code-pattern       #"^\d{6}$")
(def backup-code-pattern     #"^[A-Za-z0-9]{8}$")

(defn valid-code?
  "Validates TOTP (6-digit) or backup (8-char) codes"
  [code]
  (or (re-matches totp-code-pattern code)
      (re-matches backup-code-pattern code)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn code-box [code & [used?]]
  [:div {:style (merge {:background-color ($/color-picker :neutral-light-gray)
                        :border           (str "1px solid " ($/color-picker :light-gray))
                        :border-radius    "4px"
                        :color            ($/color-picker :neutral-dark-gray)
                        :font-family      "monospace"
                        :font-size        "0.9rem"
                        :padding          "8px 4px"
                        :text-align       "center"}
                       (when used? {:opacity "0.5"}))}
   code])

(defn backup-codes-grid [codes]
  [:div {:style {:border        (str "1px solid " ($/color-picker :light-gray))
                 :border-radius "8px"
                 :margin-bottom "1.5rem"
                 :padding       "1.5rem"}}
   [:div {:style {:display               "grid"
                  :gap                   "10px"
                  :grid-template-columns "repeat(5, 1fr)"}}
    (for [backup-code codes]
      ^{:key (:code backup-code)}
      [code-box (:code backup-code) (:used? backup-code)])]])

(defn code-input
  "Code input field. Options: :on-submit :auto-focus? :placeholder"
  [code-atom & [{:keys [on-submit auto-focus? placeholder]
                 :or   {placeholder "6-Digit Code"}}]]
  [:input {:auto-focus  auto-focus?
           :class       (<class $/p-bordered-input)
           :maxLength   "8"
           :on-change   #(when-let [new-val (.-value (.-target %))]
                           (when (re-matches code-validation-pattern new-val)
                             (reset! code-atom new-val)))
           :on-key-down (when on-submit
                          (fn [e]
                            (when (= (.-key e) "Enter")
                              (.preventDefault e)
                              (on-submit))))
           :placeholder placeholder
           :style       {:border-color  ($/color-picker :neutral-soft-gray)
                         :border-radius "4px"
                         :font-size     "1rem"
                         :height        "3rem"
                         :padding-left  "0.75rem"
                         :text-align    "left"
                         :width         "280px"}
           :type        "text"
           :value       @code-atom}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Layout
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tfa-layout
  "Page layout wrapper for TFA flows. Takes :title and children."
  [{:keys [title]} & children]
  [:div {:style {:height         "100%"
                 :display        "flex"
                 :flex-direction "column"
                 :font-family    "Roboto"}}
   [:nav {:style {:display         "flex"
                  :justify-content "center"
                  :align-items     "center"
                  :width           "100%"
                  :height          "33px"
                  :background      ($/color-picker :yellow)
                  :flex-shrink     "0"
                  :font-size       "14px"}}
    [login-menu {:logged-in? true}]]
   [:div {:style {:display         "flex"
                  :justify-content "center"
                  :align-items     "flex-start"
                  :padding         "40px 160px"
                  :background      ($/color-picker :lighter-gray)
                  :flex            "1 0 0"}}
    [:div {:style {:display        "flex"
                   :flex-direction "column"
                   :gap            "16px"
                   :max-width      "750px"
                   :min-width      "500px"
                   :padding        "16px"
                   :border-radius  "4px"
                   :border         (str "1px solid " ($/color-picker :neutral-soft-gray))
                   :background     ($/color-picker :white)}}
     [:p {:style {:color          ($/color-picker :neutral-black)
                  :font-size      "14px"
                  :font-weight    "700"
                  :text-transform "uppercase"
                  :line-height    "14px"
                  :margin         "0"}}
      title]
     (into [:<>] children)]]])

(defn code-rejection
  "What to tell someone whose code was refused with `status`."
  [status]
  (if (= status 429)
    ;; Waiting is the only remedy: a password reset clears the login count, not this one.
    "Too many incorrect codes. Please wait 5 minutes before trying again."
    "Invalid verification code. Please try again."))

(defn- code-prompt [{:keys [auth-method user-email on-resend]}]
  (if (= :totp auth-method)
    [:<>
     [:p {:style {:margin-bottom "0.5rem"}}
      "Enter the 6-digit code from your authenticator app."]
     [:p {:style {:color       "#666"
                  :font-size   "0.9rem"
                  :margin-top  "0.5rem"}}
      "You can also use one of your 8-character backup codes."]]
    [:<>
     [:p {:style {:margin-bottom "0.5rem"}}
      [:span
       "Enter the verification code sent to "
       [:span {:style {:font-weight "bold"}} user-email]
       "."]]
     [:a {:href     "#"
          :on-click on-resend
          :style    {:color      ($/color-picker :link-color)
                     :font-size  "0.9rem"
                     :margin-top "0.5rem"}}
      "Resend verification code"]]))

(defn code-entry
  "The code input, its Verify button and what to type there, for `auth-method` :totp or :email."
  [{:keys [code on-verify] :as opts}]
  [:<>
   [utils/input-labeled {:label     "Verification Code"
                         :value     @code
                         :on-change #(reset! code (-> % .-target .-value))}]
   [buttons/primary {:text     "Verify"
                     :on-click #(if (str/blank? @code)
                                  (toast-message! "Please enter the verification code.")
                                  (on-verify))}]
   [code-prompt opts]])
