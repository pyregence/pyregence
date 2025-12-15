(ns pyregence.email.messages
  "Unified email message templates for both text and HTML formats."
  (:require [hiccup.page       :refer [html5]]
            [triangulum.config :refer [get-config]])
  (:import (java.net URLEncoder)))

;; ============================================================================
;; Color Palette for HTML
;; ============================================================================

(def ^:private colors
  {:background   "#F7F7F7"
   :content-box  "#FFFFFF"
   :brand-orange "#F9AF2F"
   :text-black   "#000000"
   :brand-brown  "#61411C"
   :link-blue    "#6B98B2"
   :border-gray  "#E0E0E0"})

;; ============================================================================
;; Shared Styles - Pre-built for Hiccup compatibility
;; ============================================================================

(def ^:private common-styles
  {:h2           {:color         (:text-black colors)
                  :font-size     "24px"
                  :margin-bottom "20px"
                  :margin-top    "0"}
   :p            {:color       (:text-black colors)
                  :font-size   "15px"
                  :line-height "1.6"
                  :margin      "0 0 15px 0"}
   :p-top        {:color       (:text-black colors)
                  :font-size   "15px"
                  :line-height "1.6"
                  :margin      "20px 0 15px 0"}
   :link         {:color           (:link-blue colors)
                  :text-decoration "underline"}
   :thanks       {:color       (:text-black colors)
                  :font-size   "15px"
                  :line-height "1.6"
                  :margin      "30px 0 0 0"}
   :info-box     {:background-color (:background colors)
                  :padding          "15px"
                  :border-radius    "4px"
                  :margin           "20px 0"}
   :info-p       {:color     (:text-black colors)
                  :font-size "15px"
                  :margin    "5px 0"}
   :code-display {:background-color (:background colors)
                  :padding          "20px"
                  :border-radius    "4px"
                  :margin           "30px 0"
                  :text-align       "center"}
   :code-text    {:color          (:text-black colors)
                  :font-size      "36px"
                  :font-weight    "bold"
                  :letter-spacing "5px"
                  :margin         "0"}
   :separator    {:border-top      (str "1px solid " (:border-gray colors))
                  :border-bottom   "none"
                  :border-left     "none"
                  :border-right    "none"
                  :margin          "30px 0"
                  :padding         "0"
                  :height          "0"}})

;; ============================================================================
;; Shared Helpers
;; ============================================================================

(defn- url-encode [s]
  (URLEncoder/encode s "UTF-8"))

(defn- logo-url []
  "URL to email header logo (uses production base URL)"
  (str (get-config :triangulum.email/base-url) "/images/email/logo.png"))

(defn- icon-url []
  "URL to email footer icon (uses production base URL)"
  (str (get-config :triangulum.email/base-url) "/images/email/icon.png"))

(defn- text-footer []
  (let [current-year (.getValue (java.time.Year/now))]
    (str "\n\n"
         "──────────────────────────\n"
         (format "© %d PyreCast LLC\n" current-year)
         "support@pyrecast.com")))

(defn- html-footer []
  (let [footer-style     {:text-align  "center"
                          :margin-top  "40px"
                          :padding-top "20px"
                          :border-top  (str "1px solid " (:border-gray colors))}
        company-style    {:color       (:brand-brown colors)
                          :font-weight "600"
                          :margin      "10px 0"}
        email-p-style    {:color  (:link-blue colors)
                          :margin "5px 0"}
        email-link-style {:color           (:link-blue colors)
                          :text-decoration "none"}
        icon-style       {:height     "30px"
                          :margin-top "10px"}]
    [:div {:style footer-style}
     [:p {:style company-style}
      "PyreCast, LLC"]
     [:p {:style email-p-style}
      [:a {:href "mailto:support@pyrecast.com"
           :style email-link-style}
       "support@pyrecast.com"]]
     [:img {:src (icon-url)
            :alt "PyreCast"
            :style icon-style}]]))

(defn- html-button [text url]
  (let [button-wrapper-style {:text-align "center"
                              :margin     "25px 0"}
        button-style         {:background-color (:brand-orange colors)
                              :color            (:text-black colors)
                              :padding          "12px 32px"
                              :text-decoration  "none"
                              :border           (str "2px solid " (:text-black colors))
                              :border-radius    "4px"
                              :font-weight      "bold"
                              :font-size        "16px"
                              :display          "inline-block"}]
    [:div {:style button-wrapper-style}
     [:a {:href url :style button-style}
      text]]))

(defn- html-header []
  (let [header-style {:text-align    "center"
                      :padding       "20px 0"
                      :border-bottom (str "3px solid " (:brand-orange colors))}
        logo-style   {:height "50px"}]
    [:div {:style header-style}
     [:img {:src (logo-url)
            :alt "PyreCast"
            :style logo-style}]]))

(defn- html-wrapper [content]
  (let [body-style          {:margin           "0"
                             :padding          "0"
                             :background-color (:background colors)
                             :font-family      "Arial, sans-serif"}
        outer-table-style   {:background-color (:background colors)}
        outer-cell-style    {:padding "20px"}
        content-table-style {:background-color (:content-box colors)
                             :border-radius    "8px"
                             :overflow         "hidden"}
        content-cell-style  {:padding "20px"}
        content-div-style   {:padding "30px 20px"}]
    (html5
      [:head
       [:meta {:charset "UTF-8"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]]
      [:body {:style body-style}
       [:table {:width "100%" :cellpadding "0" :cellspacing "0"
                :style outer-table-style}
        [:tr
         [:td {:align "center" :style outer-cell-style}
          [:table {:width "600" :cellpadding "0" :cellspacing "0"
                   :style content-table-style}
           [:tr
            [:td {:style content-cell-style}
             (html-header)
             [:div {:style content-div-style}
              content]
             (html-footer)]]]]]]])))

(defn- fallback-url-note [url]
  [:p {:style (:p common-styles)}
   "If you're having trouble with the button above, copy and paste the URL below into your web browser:"
   [:br]
   [:a {:href url :style (:link common-styles)}
    url]])

;; ============================================================================
;; Welcome/Registration Email
;; ============================================================================

(defmulti welcome-email
  "Generate welcome/registration verification email.
   Dispatches on format (:text or :html)."
  (fn [fmt & _] fmt))

(defmethod welcome-email :text
  [_ base-url email user-name verification-token]
  (let [greeting   (or user-name email)
        verify-url (str base-url "/verify-email?email=" (url-encode email) "&verification-token=" (url-encode verification-token))]
    (str "Hi " greeting ",\n\n"
         "  Thanks for trying PyreCast. We're thrilled to have you on board. "
         "To get the most out of PyreCast, please verify your email by clicking this link:\n\n"
         "  " verify-url "\n\n"
         "  For reference, here's your login information:\n"
         "  Login Page: " base-url "/login\n"
         "  Username: " email "\n\n"
         "  Thanks,\n"
         "  The PyreCast Team\n\n"
         "  P.S. Need immediate help getting started? Check out our help documentation: https://support.pyrecast.com/support/solutions/32000023528. Or, just reply to this email, the PyreCast support team is always ready to help!"
         (text-footer))))

(defmethod welcome-email :html
  [_ base-url email user-name verification-token]
  (let [greeting (or user-name email)
        verify-url (str base-url "/verify-email?email=" (url-encode email) "&verification-token=" (url-encode verification-token))
        login-url (str base-url "/login")]
    (html-wrapper
     [:div
      [:h2 {:style (:h2 common-styles)}
       (str "Welcome " greeting "!")]
      [:p {:style (:p common-styles)}
       "Thanks for trying PyreCast. We're thrilled to have you on board. "
       "To get the most out of PyreCast, please verify your email by clicking this link:"]
      (html-button "Verify Your Account" verify-url)
      [:p {:style (:p-top common-styles)}
       "For reference, here's your login information:"]
      [:div {:style (:info-box common-styles)}
       [:p {:style (:info-p common-styles)}
        [:strong "Login Page: "]
        [:a {:href login-url :style (:link common-styles)} login-url]]
       [:p {:style (:info-p common-styles)}
        [:strong "Username: "] email]]
      [:p {:style (:thanks common-styles)}
       "Thanks," [:br] "The PyreCast Team"]
      [:hr {:style (:separator common-styles)}]
      [:p {:style (:p-top common-styles)}
       [:strong "P.S."] " P.S. Need immediate help getting started? Check out our help "
       [:a {:href "https://support.pyrecast.com/support/solutions/32000023528" :style (:link common-styles)} "Documentation"]
       ". Or, just reply to this email, the PyreCast support team is always ready to help!"]
      (fallback-url-note verify-url)])))

;; ============================================================================
;; Password Reset Email
;; ============================================================================

(defmulti password-reset-email
  "Generate password reset email.
   Dispatches on format (:text or :html)."
  (fn [fmt & _] fmt))

(defmethod password-reset-email :text
  [_ base-url email user-name verification-token]
  (let [greeting  (or user-name email)
        reset-url (str base-url "/reset-password?email=" (url-encode email) "&verification-token=" (url-encode verification-token))]
    (str "Hi " greeting ",\n\n"
         "  You recently requested to reset your password for your PyreCast account. "
         "Use the link below to reset it.\n\n"
         "  " reset-url "\n\n"
         "  If you did not request a password reset, please ignore this email or "
         "contact us at support@pyrecast.com if you have questions.\n\n"
         "  If you're having trouble clicking the link, copy and paste the URL above into your web browser.\n\n"
         "  Thanks,\n"
         "  The PyreCast Team"
         (text-footer))))

(defmethod password-reset-email :html
  [_ base-url email user-name verification-token]
  (let [greeting (or user-name email)
        reset-url (str base-url "/reset-password?email=" (url-encode email) "&verification-token=" (url-encode verification-token))]
    (html-wrapper
     [:div
      [:h2 {:style (:h2 common-styles)}
       "Password Reset Request"]
      [:p {:style (:p common-styles)}
       (str "Hi " greeting ",")]
      [:p {:style (:p common-styles)}
       "You recently requested to reset your password for your PyreCast account. "
       "Use the button below to reset it."]
      (html-button "Reset Your Password" reset-url)
      [:p {:style (:p common-styles)}
       "If you did not request a password reset, please ignore this email or "
       [:a {:href "mailto:support@pyrecast.com" :style (:link common-styles)}
        "contact us"] " if you have questions."]
      [:p {:style (:thanks common-styles)}
       "Thanks," [:br] "The PyreCast Team"]
      [:hr {:style (:separator common-styles)}]
      (fallback-url-note reset-url)])))

;; ============================================================================
;; Invite Email (Admin-created users)
;; ============================================================================

(defmulti invite-email
  "Generate invite email for admin-created users.
   Dispatches on format (:text or :html)."
  (fn [fmt & _] fmt))

(defmethod invite-email :text
  [_ base-url email _user-name verification-token org]
  (let [action-url (str base-url "/reset-password?email=" (url-encode email)
                        "&verification-token=" (url-encode verification-token))
        login-url  (str base-url "/login")]
    (str "Welcome to PyreCast!\n\n"
         (if org
           (str "  " org " invited you to join their organization on PyreCast. ")
           "  You've been invited to join PyreCast. ")
         "To get started, please finish setting up your profile:\n\n"
         "  " action-url "\n\n"
         "  For reference, here's your login information:\n"
         "  Login Page: " login-url "\n"
         "  Username: " email "\n\n"
         "  Thanks,\n"
         "  The PyreCast Team"
         (text-footer))))

(defmethod invite-email :html
  [_ base-url email _user-name verification-token org]
  (let [action-url (str base-url "/reset-password?email=" (url-encode email)
                        "&verification-token=" (url-encode verification-token))
        login-url  (str base-url "/login")]
    (html-wrapper
     [:div
      [:h2 {:style (:h2 common-styles)} "Welcome to PyreCast!"]
      [:p {:style (:p common-styles)}
       (if org
         (str org " invited you to join their organization on PyreCast. ")
         "You've been invited to join PyreCast. ")
       "To get started, please finish setting up your profile by clicking this link:"]
      (html-button "Set Up Your Profile" action-url)
      [:p {:style (:p-top common-styles)} "For reference, here's your login information:"]
      [:div {:style (:info-box common-styles)}
       [:p {:style (:info-p common-styles)}
        [:strong "Login Page: "]
        [:a {:href login-url :style (:link common-styles)} login-url]]
       [:p {:style (:info-p common-styles)}
        [:strong "Username: "] email]]
      [:p {:style (:thanks common-styles)} "Thanks," [:br] "The PyreCast Team"]
      [:hr {:style (:separator common-styles)}]
      (fallback-url-note action-url)])))

;; ============================================================================
;; Two-Factor Authentication Email
;; ============================================================================

(defmulti two-fa-email
  "Generate 2FA verification code email.
   Dispatches on format (:text or :html)."
  (fn [fmt & _] fmt))

(defmethod two-fa-email :text
  [_ email user-name code expiry-mins]
  (let [greeting (or user-name email)]
    (str "Hi " greeting ",\n\n"
         "  Please use this login verification code to access your PyreCast account. "
         "This verification code is only valid for the next " expiry-mins " minutes.\n\n"
         "  " code "\n\n"
         "  If you did not request a verification code, please ignore this email or "
         "contact us at support@pyrecast.com if you have questions.\n\n"
         "  Thanks,\n"
         "  The PyreCast Team"
         (text-footer))))

(defmethod two-fa-email :html
  [_ email user-name code expiry-mins]
  (let [greeting (or user-name email)]
    (html-wrapper
     [:div
      [:h2 {:style (:h2 common-styles)}
       "Login Verification Code"]
      [:p {:style (:p common-styles)}
       (str "Hi " greeting ",")]
      [:p {:style (:p common-styles)}
       "Please use this login verification code to access your PyreCast account. "
       (str "This verification code is only valid for the next " expiry-mins " minutes.")]
      [:div {:style (:code-display common-styles)}
       [:h1 {:style (:code-text common-styles)}
        code]]
      [:p {:style (:p common-styles)}
       "If you did not request a verification code, please ignore this email or "
       [:a {:href "mailto:support@pyrecast.com" :style (:link common-styles)}
        "contact us"] " if you have questions."]
      [:p {:style (:thanks common-styles)}
       "Thanks," [:br] "The PyreCast Team"]])))

;; ============================================================================
;; Match Drop Email
;; ============================================================================

(defmulti match-drop-email
  "Generate Match Drop notification email.
   Dispatches on format (:text or :html)."
  (fn [fmt & _] fmt))

(defmethod match-drop-email :text
  [_ base-url email user-name job-id display-name status]
  (let [greeting    (or user-name email)
        results-url (str base-url "/match-drop/results/" job-id)]
    (case status
      :completed (str "Hi " greeting ",\n\n"
                      "  Your Match Drop fire progression run " display-name " completed successfully. "
                      "Use the link below to view your forecasted fire spread in PyreCast.\n\n"
                      "  " results-url "\n\n"
                      "  Fire Name: " display-name "\n"
                      "  Fire ID: " job-id "\n\n"
                      "  Thanks,\n"
                      "  The PyreCast Team"
                      (text-footer))

      :failed    (str "Hi " greeting ",\n\n"
                      "  Your Match Drop simulation encountered an error.\n\n"
                      "  Please try running your simulation again. If the problem persists, "
                      "contact support at support@pyrecast.com.\n\n"
                      "  Thanks,\n"
                      "  The PyreCast Team"
                      (text-footer))

      ;; Default
      (str "Hi " greeting ",\n\n"
           "  Your Match Drop simulation status has been updated.\n\n"
           "  Thanks,\n"
           "  The PyreCast Team"
           (text-footer)))))

(defmethod match-drop-email :html
  [_ base-url email user-name job-id display-name status]
  (let [greeting    (or user-name email)
        results-url (str base-url "/match-drop/results/" job-id)
        title       (case status
                      :completed "Match Drop Completed"
                      :failed    "Match Drop Failed"
                      "Match Drop Update")
        status-text (case status
                      :completed (str "Your Match Drop fire progression run " display-name " completed successfully.")
                      :failed    "Your Match Drop simulation encountered an error."
                      "Your Match Drop simulation status has been updated.")]
    (html-wrapper
     [:div
      [:h2 {:style (:h2 common-styles)}
       title]
      [:p {:style (:p common-styles)}
       (str "Hi " greeting ",")]
      [:p {:style (:p common-styles)}
       status-text]
      (when (= status :completed)
        [:div
         [:p {:style (:p common-styles)}
          "Use the button below to view your forecasted fire spread in PyreCast."]
         (html-button "View Match Drop in PyreCast" results-url)
         [:div {:style (:info-box common-styles)}
          [:p {:style (:info-p common-styles)}
           [:strong "Fire Name: "] display-name]
          [:p {:style (:info-p common-styles)}
           [:strong "Fire ID: "] job-id]]])
      (when (= status :failed)
        [:p {:style (:p common-styles)}
         "Please try running your simulation again. If the problem persists, "
         [:a {:href "mailto:support@pyrecast.com" :style (:link common-styles)}
          "contact support"] "."])
      [:p {:style (:thanks common-styles)}
       "Thanks," [:br] "The PyreCast Team"]
      (when (= status :completed)
        [:div
         [:hr {:style (:separator common-styles)}]
         (fallback-url-note results-url)])])))

;; ============================================================================
;; Testing Functions
;; ============================================================================

(defn preview-email
  "Generate preview of an email template for testing.
   Usage: (preview-email :welcome :html)
          (preview-email :password-reset :text)"
  [email-type fmt]
  (let [test-base-url     "https://pyrecast.com"
        test-email        "test@example.com"
        test-name         "John Doe"
        test-token        "test-token-123"
        test-code         "123456"
        test-job-id       "job-456"
        test-display-name "Test Fire"
        test-expiry       15]
    (case email-type
      :welcome              (welcome-email fmt test-base-url test-email test-name test-token)
      :password-reset       (password-reset-email fmt test-base-url test-email test-name test-token)
      :invite               (invite-email fmt test-base-url test-email test-name test-token "Acme Inc")
      :two-fa               (two-fa-email fmt test-email test-name test-code test-expiry)
      :match-drop-completed (match-drop-email fmt test-base-url test-email test-name test-job-id test-display-name :completed)
      :match-drop-failed    (match-drop-email fmt test-base-url test-email test-name test-job-id test-display-name :failed)
      "Unknown email type")))
