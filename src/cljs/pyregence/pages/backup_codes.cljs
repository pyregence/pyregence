(ns pyregence.pages.backup-codes
  (:require [clojure.core.async                    :refer [go <!]]
            [cljs.reader                           :as reader]
            [clojure.string                        :as str]
            [pyregence.components.messaging        :refer [toast-message!]]
            [pyregence.components.settings.buttons :as buttons]
            [pyregence.components.svg-icons        :as svg]
            [pyregence.components.two-fa           :as two-fa]
            [pyregence.styles                      :as $]
            [pyregence.utils.async-utils           :as u-async]
            [pyregence.utils.browser-utils         :as u-browser]
            [reagent.core                          :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private initial-state
  {:backup-codes  []      ;; Current codes
   :code          ""      ;; Verification code
   :loading       true    ;; Loading state
   :regenerating? false   ;; Regeneration dialog
   :submitting?   false   ;; Form submitting
   :verifying?    false}) ;; Verification mode

(defonce state (r/atom initial-state))

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
      (when (and (two-fa/valid-code? code) (not (:submitting? @state)))
        (swap! state assoc :submitting? true)
        (let [response (<! (u-async/call-clj-async! "regenerate-backup-codes" code))]
          (swap! state assoc :code "" :submitting? false)
          (if-not (:success response)
            (toast-message! "Invalid code. Please try again.")
            (let [body (reader/read-string (:body response))
                  new-codes (:backup-codes body)]
              (if (seq new-codes)
                (do (swap! state assoc
                           :backup-codes new-codes
                           :regenerating? false
                           :verifying? false)
                    (toast-message! "New backup codes generated!"))
                (do (toast-message! "Error generating codes. Please try again.")
                    (fetch-backup-codes!))))))))))

(defn- regenerate-backup-codes! []
  (cond
    (:verifying? @state)
    nil

    (:regenerating? @state)
    (swap! state assoc :code "" :verifying? true)

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

(defn- codes [backup-codes]
  [:div {:style {:display        "flex"
                 :flex-direction "column"
                 :gap            "24px"}}
   [:p {:style {:color       ($/color-picker :neutral-black)
                :font-size   "14px"
                :font-weight "400"
                :line-height "1.5"
                :margin      "0"}}
    "You can use backup codes to sign in to your account when you don't have your phone. Each code can be used only once. Keep them somewhere safe so you never get locked out of your account."]

   [two-fa/backup-codes-grid backup-codes]

   [:div {:style {:display "flex"
                  :gap     "12px"}}
    [buttons/ghost {:text     "Download"
                    :icon     [svg/download :height "16px" :width "16px"]
                    :on-click #(u-browser/download-backup-codes! (map :code backup-codes))}]
    [buttons/ghost {:text     "Copy"
                    :icon     [svg/copy-icon :height "16px" :width "16px"]
                    :on-click #(do
                                 (if (u-browser/copy-to-clipboard! (str/join "\n" (map :code backup-codes)))
                                   (toast-message! "Copied to clipboard")
                                   (toast-message! "Failed to copy")))}]
    [buttons/ghost {:text     "Print"
                    :icon     [svg/print-icon :height "16px" :width "16px"]
                    :on-click #(js/window.print)}]]])

(defn- confirmation [on-confirm on-cancel]
  [:div
   [:p {:style {:margin-bottom "1rem"}}
    "This will invalidate your current backup codes. Continue?"]
   [:div {:style {:display "flex"
                  :gap     "12px"}}
    [buttons/ghost {:text     "Cancel"
                    :on-click on-cancel}]
    [buttons/primary {:text     "Yes, Regenerate"
                      :on-click on-confirm}]]])

(defn- verification [{:keys [code submitting?]} on-submit on-cancel]
  (let [code-valid? (two-fa/valid-code? code)]
    [:div
     [:p {:style {:margin-bottom "1rem"}}
      "Enter your TOTP code or a backup code:"]
     [:div {:style {:margin-bottom "1rem"}}
      [code-field state {:on-submit   (when (and code-valid? (not submitting?)) on-submit)
                         :auto-focus? true
                         :placeholder "6-Digit Code or Backup Code"}]]
     [:div {:style {:display "flex"
                    :gap     "12px"}}
      [buttons/ghost {:text      "Cancel"
                      :disabled? submitting?
                      :on-click  on-cancel}]
      [buttons/primary {:text      "Confirm"
                        :disabled? (or (not code-valid?) submitting?)
                        :on-click  on-submit}]]]))

(defn root-component []
  (r/create-class
   {:component-did-mount
    (fn []
      (reset! state initial-state)
      (fetch-backup-codes!))
    :reagent-render
    (fn []
      (let [{:keys [loading backup-codes regenerating? verifying? submitting?]} @state]
        [two-fa/tfa-layout {:title "BACKUP CODES"}
         (cond
           loading
           [:p {:style {:margin "0"}} "Loading..."]

           (empty? backup-codes)
           [:div {:style {:display        "flex"
                          :flex-direction "column"
                          :gap            "16px"}}
            [:p {:style {:margin "0"}}
             "No backup codes available. Generate new codes to secure your account."]
            [buttons/primary {:text     "GENERATE BACKUP CODES"
                              :on-click regenerate-backup-codes!}]]

           :else
           [:div {:style {:display        "flex"
                          :flex-direction "column"
                          :gap            "24px"}}
            (cond
              verifying?
              [verification @state submit-regeneration! #(swap! state assoc :code "" :verifying? false)]

              regenerating?
              [confirmation regenerate-backup-codes! #(swap! state assoc :regenerating? false)]

              :else
              [:div {:style {:display        "flex"
                             :flex-direction "column"
                             :gap            "24px"}}
               [codes backup-codes]
               [:div {:style {:display   "flex"
                              :flex-wrap "wrap"
                              :gap       "12px"}}
                [buttons/ghost {:text      "Regenerate Backup Codes"
                                :disabled? (or verifying? submitting?)
                                :on-click  regenerate-backup-codes!}]
                [buttons/primary {:text     "Back to Account Settings"
                                  :on-click #(set! (.-location js/window) "/account-settings")}]]])])]))}))
