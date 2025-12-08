(ns pyregence.components.two-fa
  "Shared components for two-factor authentication UI"
  (:require [herb.core                       :refer [<class]]
            [pyregence.components.login-menu :refer [login-menu]]
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
