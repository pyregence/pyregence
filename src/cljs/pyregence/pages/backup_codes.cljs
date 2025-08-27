(ns pyregence.pages.backup-codes
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
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce state (r/atom {:backup-codes  []       ;; Current codes
                        :code          ""       ;; Verification code
                        :loading       true     ;; Loading state
                        :regenerating? false    ;; Regeneration dialog
                        :verifying?    false})) ;; Verification mode

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- fetch-backup-codes! []
  (go
    (let [response (<! (u-async/call-clj-async! "get-backup-codes"))]
      (if (and (:success response) (:body response))
        (let [parsed-body (reader/read-string (:body response))
              codes (:backup-codes parsed-body)]
          (swap! state assoc
                 :backup-codes codes
                 :loading false))
        (do
          (toast-message! "Failed to load backup codes")
          (swap! state assoc :loading false))))))

(defn- submit-regeneration! []
  (go
    (let [code (:code @state)]
      (when (two-fa/valid-code? code)
        (let [response (<! (u-async/call-clj-async! "regenerate-backup-codes" code))
              body (when (:success response)
                     (reader/read-string (:body response)))
              new-codes (:backup-codes body)]
          (swap! state assoc :code "")
          (cond
            (seq new-codes)
            (do (swap! state assoc
                       :backup-codes new-codes
                       :regenerating? false
                       :verifying? false)
                (toast-message! "New backup codes generated!"))

            (:success response)
            (do (toast-message! "Failed to regenerate backup codes")
                (fetch-backup-codes!))

            :else
            (toast-message! "Invalid code")))))))

(defn- regenerate-backup-codes! []
  (cond
    (:verifying? @state)
    nil

    (:regenerating? @state)
    (swap! state assoc :verifying? true :code "")

    :else
    (swap! state assoc :regenerating? true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- code-field
  "A verification code input field bound to a state map."
  [state-map & [opts]]
  (let [code (r/cursor state-map [:code])]
    [two-fa/code-input code opts]))

(defn- codes [backup-codes on-regenerate]
  [:div
   [:p {:style {:margin-bottom "1.5rem"}}
    "Save these backup codes in a safe place. Each code can only be used once."]

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
             :value    "COPY"}]
    [:input {:class    (<class $/p-form-button)
             :on-click on-regenerate
             :type     "button"
             :value    "REGENERATE"}]]])

(defn- confirmation [on-confirm on-cancel]
  [:div
   [:p {:style {:margin-bottom "1rem"}}
    "This will invalidate your current backup codes. Continue?"]
   [:div {:style {:display "flex"
                  :gap     "10px"}}
    [:input {:class    (<class $/p-form-button)
             :on-click on-confirm
             :type     "button"
             :value    "YES, REGENERATE"}]
    [:input {:class    (<class $/p-form-button)
             :on-click on-cancel
             :type     "button"
             :value    "CANCEL"}]]])

(defn- verification [{:keys [code]} on-submit on-cancel]
  [:div
   [:p {:style {:margin-bottom "1rem"}}
    "Enter your TOTP code or a backup code:"]
   [:div {:style {:margin-bottom "1rem"}}
    [code-field state {:on-submit   on-submit
                       :auto-focus? true
                       :placeholder "Enter code"}]]
   [:div {:style {:display "flex"
                  :gap     "10px"}}
    [:input {:class    (<class $/p-form-button)
             :disabled (not (two-fa/valid-code? code))
             :on-click on-submit
             :style    (when-not (two-fa/valid-code? code)
                         {:cursor  "not-allowed"
                          :opacity "0.6"})
             :type     "button"
             :value    "CONFIRM"}]
    [:input {:class    (<class $/p-form-button)
             :on-click on-cancel
             :type     "button"
             :value    "CANCEL"}]]])

(defn root-component
  "Root component for backup codes management."
  []
  (r/create-class
   {:component-did-mount fetch-backup-codes!
    :reagent-render
    (fn []
      (let [{:keys [loading backup-codes regenerating? verifying?]} @state]
        [:div {:style {:display         "flex"
                       :justify-content "center"
                       :margin          "5rem"}}
         [:div {:style ($/combine ($/action-box)
                                  {:max-width "600px"
                                   :min-width "500px"})}
          [:div {:style ($/action-header)}
           [:label "Backup Codes"]]
          [:div {:style {:overflow "auto"
                         :padding  "1.5rem"}}
           (cond
             loading
             [:p "Loading..."]

             (empty? backup-codes)
             [:div
              [:p {:style {:margin-bottom "1rem"}}
               "No backup codes available. Generate new codes to secure your account."]
              [:input {:class    (<class $/p-form-button)
                       :on-click regenerate-backup-codes!
                       :type     "button"
                       :value    "GENERATE BACKUP CODES"}]]

             :else
             [:div
              (cond
                verifying?
                [verification @state submit-regeneration! #(swap! state assoc :verifying? false :code "")]

                regenerating?
                [confirmation regenerate-backup-codes! #(swap! state assoc :regenerating? false)]

                :else
                [codes backup-codes regenerate-backup-codes!])])]]]))}))
