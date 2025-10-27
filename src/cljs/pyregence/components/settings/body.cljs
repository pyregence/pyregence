(ns pyregence.components.settings.body
  (:require
   [pyregence.components.svg-icons :as svg]
   [pyregence.utils.dom-utils :refer [input-value]]))

;;TODO turn into herb styles
(def label-styles
  {:color       "var(--Neutral-Md-Gray, #767575)"
   :font-family "Roboto"
   :font-size   "14px"
   :font-weight "500"
   :font-style  "normal"
   :margin      "0"})

(def font-styles
  {:color       "var(--Neutral-Black, #000)"
   :font-family "Roboto"
   :font-size   "14px"
   :font-style  "normal"
   :font-weight "600"
   ;;TODO look into why all :p need margin-bottom 0 to look normal
   :margin      "0"})

(defn input-show
  [{:keys [label text icon]}]
  [:div {:style {:display        "flex"
                 :gap            "5px"
                 :flex-direction "row"
                 :align-items    "center"}}
   [:p {:style label-styles} (str label ":")]
   [:p {:style font-styles} text]
   (when icon [icon :height "16px" :width "16px"])])

(defn user-name
  [{:keys [name-part name]}]
  [:div {:style {:display        "flex"
                 :flex-direction "column"}}
   [:div {:style (assoc label-styles :color "var(--Neutral-Dark-gray, #4A4A4A)")}
    (let [styles {:font-family "Roboto"
                  :font-size   "14px"
                  :font-style  "normal"
                  :font-weight "500"}]
      [:div {:style {:display        "flex"
                     :flex-direction "row"
                     :height         "24px"}}
       [:p {:style styles}  (str name-part " Name")]
       [:p {:style (assoc styles :color "red")}  "*"]])
    [:input {:type  "text"
             :style {:display       "flex"
                     :align-items   "center"
                     :height        "50px"
                     :padding       "14px"
                     :border-radius "4px"
                     :border        "1px solid var(--Neutral-soft-gray, #E1E1E1)"
                     :background    "var(--Neutral-White, #FFF)"}
             :value name}]]])

(defn card
  [{:keys [title children]}]
  [:card
   {:style {:display        "flex"
            :width          "100%"
            :padding        "16px"
            :flex-direction "column"
            :align-items    "flex-start"
            :gap            "16px"
            :align-self     "stretch"
            :border-radius  "4px"
            :border         "1px solid var(--Neutral-Soft-gray, #E1E1E1)"
            :background     "var(--Neutral-White, #FFF)"}}
   ;; neutral-black
   [:p {:style {:color          "#000"
                :font-family    "Public Sans"
                :font-size      "14px"
                :font-style     "normal"
                :font-weight    "700"
                :line-height    "14px"
                :text-transform "uppercase"
                :margin-bottom  "0px"}}
    title]
   children])

(defn my-account-details
  []
  [:my-account-details
   {:style {:display        "flex"
            :width          "100%"
            :padding        "16px"
            :flex-direction "column"
            :align-items    "flex-start"
            :gap            "16px"
            :align-self     "stretch"
            :border-radius  "4px"
            :border         "1px solid var(--Neutral-Soft-gray, #E1E1E1)"
            :background     "var(--Neutral-White, #FFF)"}}
   ;; neutral-black
   ])

(defn labled-toggle
  [{:keys [label]}]
  [:<>
   [:p {:style (assoc font-styles :font-weight "400")} label]
   [:p "TODO TOGGLE"]])

(defn main
  []
  [:div {:style {:display        "flex"
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
                        :text  "agorule@sig-gis.com"}]
           [input-show {:label "Role Type"
                        :text  "Account Manager"
                        :icon  svg/info-with-circle}]
           [:div {:style {:display        "flex"
                          :flex-direction "row"
                          :gap            "16px"}}
            [user-name {:name-part "First" :name "Adi"}]
            [user-name {:name-part "Last" :name "Gorule"}]]]}
   ;;TODO add the button here, those are in another PR though
    [:TODO-BUTTON]]
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
            [:p {:style {:margin "0px"}} "TODO BUTTON"]
            [input-show {:label "Last Updated"
                         :text  "11/16/2024"}]]]}]
   [card {:title "NOTIFICATION PREFERENCES"
          :children
          [:<>
           [labled-toggle {:label "Receive emails about new fires (need proper text here)"}]
           [labled-toggle {:label "Receive emails about new fires (need proper text here)"}]]}]])
