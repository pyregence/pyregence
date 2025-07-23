(ns pyregence.pages.totp-setup
  (:require [clojure.core.async             :refer [go <!]]
            [cljs.reader                    :as reader]
            [clojure.string                 :as str]
            [herb.core                      :refer [<class]]
            [pyregence.components.messaging :refer [toast-message!]]
            [pyregence.components.two-fa    :as two-fa]
            [pyregence.styles               :as $]
            [pyregence.utils.async-utils    :as u-async]
            [pyregence.utils.browser-utils  :as u-browser]
            [reagent.core                   :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private redirect-delay-ms 1500)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce state
  ^{:doc "Component state for TOTP setup process"}
  (r/atom {:backup-codes []       ;; Generated codes
           :code         ""       ;; Verification code
           :loading      true     ;; Loading state
           :qr-uri       nil      ;; QR code URI
           :setup-key    nil      ;; Manual entry key
           :show-key?    false})) ;; Show manual option

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- generate-qr-url [uri]
  (str "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data="
       (js/encodeURIComponent uri)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- start-totp-setup! []
  (go
    (let [response (<! (u-async/call-clj-async! "begin-totp-setup"))]
      (if (and (:success response) (:body response))
        (let [data (reader/read-string (:body response))
              {:keys [qr-uri secret backup-codes]} data]
          (swap! state merge
                 {:backup-codes (vec backup-codes)
                  :loading      false
                  :qr-uri       qr-uri
                  :setup-key    secret}))
        (do
          (toast-message! "Failed to start 2FA setup")
          (swap! state assoc :loading false))))))

(defn- complete-totp-setup! []
  (go
    (let [code (:code @state)]
      (if (re-matches two-fa/totp-code-pattern code)
        (let [response (<! (u-async/call-clj-async! "complete-totp-setup" code))]
          (if (:success response)
            (do
              (toast-message! "2FA setup complete!")
              (js/setTimeout #(set! (.-location js/window) "/settings") redirect-delay-ms))
            (toast-message! "Invalid code. Please try again.")))
        (toast-message! "Please enter a 6-digit code")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- code-field
  "A verification code input field bound to a state map."
  [state-map & [opts]]
  (let [code (r/cursor state-map [:code])]
    [two-fa/code-input code opts]))

(defn- step
  "UI component for displaying a numbered step with title and content."
  [step-number title content]
  [:div {:style {:margin-bottom "1.5rem"}}
   [:p {:style {:font-weight   "500"
                :margin-bottom "1rem"}}
    (str step-number ". " title)]
   content])

(defn- enrollment [{:keys [qr-uri setup-key show-key?]} on-toggle-key]
  [:div
   (when qr-uri
     [:div {:style {:border        (str "1px solid " ($/color-picker :light-gray))
                    :border-radius "4px"
                    :margin-bottom "1rem"
                    :padding       "2rem 1rem"
                    :text-align    "center"}}
      [:img {:alt   "QR Code"
             :src   (generate-qr-url qr-uri)
             :style {:background-color "white"
                     :border           (str "1px solid " ($/color-picker :light-gray))
                     :padding          "10px"}}]])

   [:div {:style {:margin-top "1rem"}}
    [:a {:href     "#"
         :on-click (fn [e]
                     (.preventDefault e)
                     (on-toggle-key))
         :style    {:color           "inherit"
                    :cursor          "pointer"
                    :font-size       "0.9rem"
                    :text-decoration "none"}}
     (str (if show-key? "▲" "▼") " Unable to scan? Use setup key instead")]
    (when show-key?
      [:div {:style {:margin-top "1rem"}}
       [:p {:style {:font-size     "0.9rem"
                    :margin-bottom "0.75rem"}}
        "Enter this key in your authenticator app:"]
       [:div {:style {:align-items "center"
                      :display     "flex"
                      :gap         "10px"}}
        [two-fa/code-box setup-key]
        [:input {:class    (<class $/p-form-button)
                 :on-click #(do
                              (if (u-browser/copy-to-clipboard! setup-key)
                                (toast-message! "Copied to clipboard")
                                (toast-message! "Failed to copy")))
                 :type     "button"
                 :value    "COPY"}]]])]])

(defn- codes [backup-codes]
  (when (seq backup-codes)
    [:div
     [two-fa/backup-codes-grid backup-codes]
     [:div {:style {:display "flex"
                    :gap     "10px"}}
      [:input {:class    (<class $/p-form-button)
               :on-click #(u-browser/download-backup-codes! (map :code backup-codes))
               :type     "button"
               :value    "DOWNLOAD"}]
      [:input {:class    (<class $/p-form-button)
               :on-click #(do
                            (if (u-browser/copy-to-clipboard! (str/join "\n" (map :code backup-codes)))
                              (toast-message! "Copied to clipboard")
                              (toast-message! "Failed to copy")))
               :type     "button"
               :value    "COPY"}]]]))

(defn- verification [state-atom on-submit]
  (let [{:keys [code]} @state-atom]
    [:div
     [:div {:style {:margin-bottom "1rem"}}
      [code-field state-atom {:on-submit on-submit
                              :placeholder "Enter 6-digit code"}]]

     [:div {:style {:margin-top "1rem"
                    :text-align "right"}}
      [:input {:class    (<class $/p-form-button)
               :disabled (not= two-fa/code-length (count code))
               :on-click on-submit
               :style    (when (not= two-fa/code-length (count code))
                           {:cursor  "not-allowed"
                            :opacity "0.6"})
               :type     "button"
               :value    "CONTINUE"}]]]))

(defn root-component
  "Root component for TOTP setup process."
  []
  (r/create-class
   {:component-did-mount start-totp-setup!
    :reagent-render
    (fn []
      (let [{:keys [loading backup-codes]} @state]
        [:div {:style {:display         "flex"
                       :justify-content "center"
                       :margin          "5rem"}}
         [:div {:style ($/combine ($/action-box)
                                  {:max-width "600px"
                                   :min-width "500px"})}
          [:div {:style ($/action-header)}
           [:label "2FA"]]
          [:div {:style {:overflow "auto"
                         :padding  "1.5rem"}}
           (if loading
             [:div {:style {:padding    "2rem"
                            :text-align "center"}}
              [:p "Loading..."]]
             [:div
              [step 1 "Scan QR code with your authenticator app"
               [enrollment @state #(swap! state update :show-key? not)]]

              [step 2 "Save your backup codes"
               [codes backup-codes]]

              [step 3 "Verify your device"
               [verification state complete-totp-setup!]]])]]]))}))
