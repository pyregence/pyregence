(ns pyregence.components.settings.account-settings
  (:require
   [clojure.core.async                    :refer [<! go]]
   [clojure.string                        :as str]
   [pyregence.components.messaging        :refer [toast-message!]]
   [pyregence.components.settings.buttons :as buttons]
   [pyregence.components.settings.utils   :refer [card font-styles
                                                  input-labeled main-styles
                                                  text-labeled]]
   [pyregence.utils.async-utils           :as u-async]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- role-type->label
  [role-type]
  (->> (str/split (str role-type) #"_")
       (map str/capitalize)
       (str/join " ")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- user-full-name
  [m]
  [:div {:style {:display        "flex"
                 :width          "100%"
                 :gap            "16px"
                 :flex-direction "column"}}
   [:div {:style {:display        "flex"
                  :flex-direction "row"
                  :width          "100%"
                  :gap            "16px"}}
    [input-labeled {:value     (:user-name m)
                    :label     "Full Name"
                    :on-change (:on-change-update-user-name m)}]]
   [buttons/ghost {:text     "Save Changes"
                   :on-click (:on-click-save-user-name m)}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn main
  [{:keys [password-set-date
           email-address
           role-type] :as user-info}]
  [:div {:style main-styles}
   [card {:title "MY ACCOUNT DETAILS"
          :children
          [:<>
           [text-labeled {:label "Email Address"
                          :text  email-address}]
           [text-labeled {:label    "Role Type"
                          :text     (role-type->label role-type)}]
           [user-full-name user-info]]}]
   [card {:title "RESET MY PASSWORD"
          :children
          [:<>
           [:p {:style (assoc font-styles :font-weight "400")}
            "Once you send a request to reset your password, you will receive a link in your email to set up your new password."]
           [:div {:style {:display         "flex"
                          :flex-direction  "row"
                          :justify-content "space-between"
                          :align-items     "flex-end"
                          :width           "100%"
                          :gap             "10px"}}
            [:p {:style {:margin "0px"}}
             [buttons/ghost {:text "Send Reset Link"
                             :on-click (fn []
                                         ;;TODO what should we do if this fails? Log it? ask them to contact us?
                                         (go (:success (<! (u-async/call-clj-async! "send-email" email-address :reset))))
                                         (toast-message! (str "Reset Link sent to " email-address ".")))}]]
            [text-labeled {:label "Last Updated"
                           :text  password-set-date}]]]}]])
