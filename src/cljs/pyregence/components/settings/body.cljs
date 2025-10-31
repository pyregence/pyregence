(ns pyregence.components.settings.body
  (:require
   [herb.core                      :refer [<class]]
   [pyregence.components.settings.buttons :as buttons]
   [pyregence.components.svg-icons :as svg]
   [pyregence.styles               :as $]
   [pyregence.utils.dom-utils      :refer [input-value]]
   [reagent.core                   :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $standard-input-field
  []
  (with-meta
    {:background ($/color-picker :neutral-white)
     :border     (str "1px solid " ($/color-picker :neutral-soft-gray))}
    ;;TODO On some browsers, aka chrome, there is a black border that is being
    ;;imposed ontop of the focused orange border. Try to fix this!
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
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- input-field
  [{:keys [value on-change]}]
  [:input {:type      "text"
           :class     (<class $standard-input-field)
           :style     {:display       "flex"
                       :weight        "500"
                       :width         "100%"
                       :align-items   "center"
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

(defn- user-name
  [{:keys [name-part] :as name-info}]
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
       [:p {:style styles}  (str name-part " Name")]
       [:p {:style (assoc styles :color ($/color-picker :error-red))}  "*"]])
    [input-field name-info]]])

(defn- card
  [{:keys [title children]}]
  [:card
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
                :font-family    "Public Sans"
                :font-size      "14px"
                :font-style     "normal"
                :font-weight    "700"
                :line-height    "14px"
                :text-transform "uppercase"
                :margin-bottom  "0px"}}
    title]
   children])

(defn- labled-toggle
  [{:keys [label]}]
  [:<>
   [:p {:style (assoc font-styles :font-weight "400")} label]
   ;;TODO add toggle
   [:p "TOGGLE"]])

(defn- user-full-name
  [full-name]
  (r/with-let [full-name (r/atom full-name)]
    [:div {:style {:display        "flex"
                   :width          "100%"
                   :gap            "16px"
                   :flex-direction "column"}}
     [:div {:style {:display        "flex"
                    :flex-direction "row"
                    :width          "100%"
                    :gap            "16px"}}
      [user-name {:name-part "First"
                  :value     (:first-name @full-name)
                  :on-change #(swap! full-name assoc :first-name (input-value %))}]
      [user-name {:name-part "Last"
                  :value     (:last-name @full-name)
                  :on-change #(swap! full-name assoc :last-name (input-value %))}]]
     [buttons/ghost {:text "Save Changes"}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn main
  [{:keys [password-set-date
           email-address
           role-type
           first-name
           last-name]}]
  [:div {:style {:display        "flex"
                 :font-family    "Roboto"
                 :height         "942px"
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
                        :text  role-type
                        :icon  svg/info-with-circle}]
           [user-full-name {:first-name first-name :last-name last-name}]]}]
   [card {:title "RESET MY PASSWORD"
          :children
          [:<>
           [:p {:style (assoc font-styles :font-weight "400")} "Once you send a request to reset your password, you will receive a link on your email to set up your new password."]
           ;;TODO add the button here, those are in another PR though
           [:div {:style {:display         "flex"
                          :flex-direction  "row"
                          :justify-content "space-between"
                          :align-items     "flex-end"
                          :width           "100%"}}
            [:p {:style {:margin "0px"}}
             [buttons/ghost {:text "Send Reset Link"}]]
            [input-show {:label "Last Updated"
                         :text  password-set-date}]]]}]
   [card {:title "NOTIFICATION PREFERENCES"
          :children
          [:<>
           [labled-toggle {:label "Receive emails about new fires (need proper text here)"}]
           [labled-toggle {:label "Receive emails about new fires (need proper text here)"}]]}]])
