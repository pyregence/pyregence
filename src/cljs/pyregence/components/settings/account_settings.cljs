(ns pyregence.components.settings.account-settings
  (:require
   [clojure.core.async                    :refer [<! go]]
   [clojure.string                        :as str]
   [pyregence.components.messaging        :refer [toast-message!]]
   [pyregence.components.settings.buttons :as buttons]
   [pyregence.components.settings.utils   :refer [card font-styles
                                                  text-labeled
                                                  main-styles
                                                  input-labeled]]
   [pyregence.utils.async-utils           :as u-async]
   [pyregence.utils.dom-utils             :refer [input-value]]
   [reagent.core                          :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO copied from admin.cljs, so either share that logic or deprecate admin.cljs eventually.
(defn- update-org-user!
  "Updates user identified by their `email` to have the `new-name`. Then makes a toast."
  [email new-name]
  (go
    (let [res (<! (u-async/call-clj-async! "update-user-name" email new-name))]
      (if (:success res)
        (toast-message! (str "The user " new-name " with the email " email  " has been updated."))
        (toast-message! (:body res))))))

(defn- role-type->label
  [role-type]
  (->> (str/split (str role-type) #"_")
       (map str/capitalize)
       (str/join " ")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- user-full-name
  [{:keys [user-name email-address]}]
  (r/with-let [user-name (r/atom user-name)]
    [:div {:style {:display        "flex"
                   :width          "100%"
                   :gap            "16px"
                   :flex-direction "column"}}
     [:div {:style {:display        "flex"
                    :flex-direction "row"
                    :width          "100%"
                    :gap            "16px"}}
      [input-labeled {:value     @user-name
                      :label     "Full Name"
                      :on-change #(reset! user-name (input-value %))}]]
     [buttons/ghost {:text     "Save Changes"
                     :on-click #(update-org-user! email-address @user-name)}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn main
  [{:keys             [password-set-date
                       email-address
                       role-type] :as user-info}]
  [:div {:style main-styles}
   [card {:title "MY ACCOUNT DETAILS"
          :children
          [:<>
           [text-labeled {:label "Email Address"
                          :text  email-address}]
           [text-labeled {:label "Role Type"
                          :text  (role-type->label role-type)}]
           [user-full-name (select-keys user-info [:email-address :user-name])]]}]
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
             [buttons/ghost {:text "Send Reset Link"}]]
            [text-labeled {:label "Last Updated"
                           :text  password-set-date}]]]}]])
