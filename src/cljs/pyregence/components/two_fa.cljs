(ns pyregence.components.two-fa
  "Shared components for two-factor authentication UI"
  (:require [herb.core        :refer [<class]]
            [pyregence.styles :as $]))

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
  [:div {:style (merge {:background-color "white"
                        :border           (str "1px solid " ($/color-picker :light-gray))
                        :border-radius    "4px"
                        :color            "#333"
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
                 :or   {placeholder "Enter code"}}]]
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
           :style       {:font-size      "0.9rem"
                         :height         "2.2rem"
                         :letter-spacing "0.1em"
                         :text-align     "center"
                         :width          "160px"}
           :type        "text"
           :value       @code-atom}])
