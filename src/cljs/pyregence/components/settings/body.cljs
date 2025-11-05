(ns pyregence.components.settings.body
  (:require
   [clojure.core.async                    :refer [<! go]]
   [herb.core                             :refer [<class]]
   [pyregence.components.messaging        :refer [toast-message!]]
   [pyregence.components.settings.buttons :as buttons]
   [pyregence.styles                      :as $]
   [pyregence.utils.async-utils           :as u-async]
   [pyregence.utils.dom-utils             :refer [input-value]]
   [clojure.string                        :as str]
   [reagent.core                          :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $standard-input-field
  []
  (with-meta
    {:background ($/color-picker :neutral-white)
     :border     (str "1px solid " ($/color-picker :neutral-soft-gray))}
    ;;TODO On some browsers, aka chrome, there is a black border that is being
    ;;imposed on top of the focused orange border. Try to fix this!
    {:pseudo {:focus {:background ($/color-picker :neutral-white)
                      :border     (str "1px solid " ($/color-picker :primary-standard-orange))}
              :hover {:background ($/color-picker :neutral-light-gray)}}}))

(def label-styles
  {:color       ($/color-picker :neutral-md-gray)
   :font-family "Roboto"
   :font-size   "14px"
   :font-weight "500"
   :font-style  "normal"
   :margin      "0"})

(def font-styles
  {:color       ($/color-picker :neutral-black)
   :font-family "Roboto"
   :font-size   "14px"
   :font-style  "normal"
   :font-weight "600"
   ;;TODO look into why all :p need margin-bottom 0 to look normal
   :margin      "0"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;TODO copied from admin.cljs, so either share that logic or deprecate admin.cljs eventually.
(defn- update-org-user!
  "Updates user identified by their `email` to have the `new-name`. Then makes a toast."
  [email new-name]
  (go
    (let [res (<! (u-async/call-clj-async! "update-user-name" email new-name))]
      (if (:success res)
        (toast-message! (str "The user " new-name " with the email " email  " has been updated."))
        (toast-message! (:body res))))))

;; TODO consider having a Namespace that handles all things roles.
(defn- role-type->label
  [role-type]
  (->> (str/split (str role-type) #"_")
       (map str/capitalize)
       (str/join " ")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- input-field
  [{:keys [value on-change]}]
  [:input {:type      "text"
           :class     (<class $standard-input-field)
           :style     {:weight        "500"
                       :width         "100%"
                       :max-width     "350px"
                       :height        "50px"
                       :font-size     "14px"
                       :font-style    "normal"
                       :line-weight   "22px"
                       :padding       "14px"
                       :border-radius "4px"}
           :value     value
           :on-change on-change}])

(defn- input-show
  [{:keys [label text icon]}]
  [:div {:style {:display        "flex"
                 :gap            "5px"
                 :flex-direction "row"
                 :align-items    "center"}}
   [:p {:style label-styles} (str label ":")]
   [:p {:style font-styles} text]
   (when icon [icon :height "16px" :width "16px"])])

(defn- user-name-card
  [user-name]
  [:div {:style {:display        "flex"
                 :flex-direction "column"
                 :width          "100%"}}
   [:div {:style label-styles}
    (let [styles {:font-family "Roboto"
                  :font-size   "14px"
                  :font-style  "normal"
                  :font-weight "500"
                  :color       ($/color-picker :neutral-black)}]
      [:div {:style {:display        "flex"
                     :flex-direction "row"
                     :width          "100%"
                     :height         "24px"}}
       [:p {:style styles}  "Full Name"]
       [:p {:style (assoc styles :color ($/color-picker :error-red))}  "*"]])
    [input-field user-name]]])

(defn- card
  [{:keys [title children]}]
  [:settings-body-card
   {:style {:display        "flex"
            :max-width      "750px"
            :min-width      "300px"
            :width          "100%"
            :padding        "16px"
            :flex-direction "column"
            :align-items    "flex-start"
            :gap            "16px"
            :align-self     "stretch"
            :border-radius  "4px"
            :border         (str "1px solid " ($/color-picker :neutral-soft-gray))
            :background     ($/color-picker :white)}}
   ;; neutral-black
   [:p {:style {:color          ($/color-picker :black)
                :font-size      "14px"
                :font-style     "normal"
                :font-weight    "700"
                :line-height    "14px"
                :text-transform "uppercase"
                :margin-bottom  "0px"}}
    title]
   children])

(defn- labeled-toggle
  [{:keys [label]}]
  [:<>
   [:p {:style (assoc font-styles :font-weight "400")} label]
   ;;TODO add toggle
   [:p "TOGGLE"]])

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
      [user-name-card {:value     @user-name
                       :on-change #(reset! user-name (input-value %))}]]
     [buttons/ghost {:text     "Save Changes"
                     :on-click #(update-org-user! email-address @user-name)}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn main
  [{:keys [password-set-date
           email-address
           role-type] :as user-info}]
  [:div {:style {:display        "flex"
                 :font-family    "Roboto"
                 :flex-direction "column"
                 :align-items    "flex-start"
                 :padding        "40px 160px"
                 :flex           "1 0 0"
                 :gap            "24px"}}
   [card {:title "MY ACCOUNT DETAILS"
          :children
          [:<>
           [input-show {:label "Email Address"
                        :text  email-address}]
           [input-show {:label "Role Type"
                        :text (role-type->label role-type)
                        ;; TODO add back in info tab when we get text that's associated with it.
                        #_#_:icon  svg/info-with-circle}]
           [user-full-name (select-keys user-info [:email-address :user-name])]]}]
   [card {:title "RESET MY PASSWORD"
          :children
          [:<>
           [:p {:style (assoc font-styles :font-weight "400")} "Once you send a request to reset your password, you will receive a link in your email to set up your new password."]
           [:div {:style {:display         "flex"
                          :flex-direction  "row"
                          :justify-content "space-between"
                          :align-items     "flex-end"
                          :width           "100%"
                          :gap             "10px"}}
            [:p {:style {:margin "0px"}}
             ;;TODO pass on-click to generate reset link, this will be handled in a future ticket.
             [buttons/ghost {:text "Send Reset Link"}]]
            [input-show {:label "Last Updated"
                         :text  password-set-date}]]]}]
   ;;TODO commented out because component isn't ready
   #_[card {:title "NOTIFICATION PREFERENCES"
            :children
            [:<>
             [labeled-toggle {:label "Receive emails about new fires (need proper text here)"}]
             [labeled-toggle {:label "Receive emails about new fires (need proper text here)"}]]}]])
