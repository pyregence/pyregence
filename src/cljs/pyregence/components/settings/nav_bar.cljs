(ns pyregence.components.settings.nav-bar
  (:require
   [pyregence.components.svg-icons       :as svg]
   [reagent.core                         :as r]))

(defn settings-page
  []
  [:div
   {:style
    {:display              "grid"
     :height "100%"
     :width  "100%"
     :grid-template-columns "1fr 4fr"
     :grid-template-rows "40px 20px 1fr"
     :grid-template-areas "\"banner banner\" \"header header\" \"nav body\""}}
   [:div {:style {:grid-area "banner"}}]])

(defn button
  [{:keys [icon text tag on-click selected?]}]
  [tag {:on-click on-click
        :style (cond-> {:display "flex"
                        :width "320px"
                        :height "52px"
                        :padding "16px"
                        :align-self "stretch"}
                 selected?
                 (assoc
                  :background "#F8E8CC"
                  :border-left "5px solid #E58154"))}
   [:f1739327813 {:style {:display "flex"
                          :align-items "center"
                          :gap "12px"}}
    [icon :height "24px" :width "24px"]
    [:label {:style {:color "#4A4A4A"
                     :text-align "justify"
                     :font-family "Roboto"
                     :font-size "16px"
                     :font-style "normal"
                     :font-weight "400"
                     :line-height "16px"
                     :text-transform "capitalize"}} text]]])

(defn drop-down
  "A button that when click shows children"
  [{:keys [selected? tag] :as m} children]
  [tag
   {:style {:display "flex"
            :align-items "center"
            :gap "12px"}}
   [button m]
   (if selected?
     [svg/up-arrow :height "24px" :width "24px"]
     [svg/down-arrow :height "24px" :width "24px"])
   #_(when selected? [children])])

(def settings-config
  (->>
   [[button "Account Settings"]
    [drop-down "Organization Settings" ["Org 1"
                                        "Org 2"
                                        "Org 3"]]
    [button "Unaffilated Members"]]))

(defn settings
  []
  (r/with-let [selected-setting (r/atom nil)]
    [:<>
     [button
      (let [id :account-settings]
        {:text "Account Settings"
         :icon svg/wheel
         :tag id
         :on-click #(reset! selected-setting id)
         :selected? (= @selected-setting id)})]
      ;;TODO handle on click correctly
     [drop-down
      {:icon svg/group
       :tag :organization-settings
       :text "Organization Settings"
       :on-click #(reset! selected-setting :organization-settings)}]
     [button
      (let [id :unaffiliated-members]
        {:text "Unaffiliated Members"
         :icon svg/individual
         :tag id
         :on-click #(reset! selected-setting id)
         :selected? (= @selected-setting id)})]]))

(defn main
  []
  [:nav-bar {:style {:display "flex"
                     :height "100%"
                     :width "360px"
                     :padding "40px 0"
                     :flex-direction "column"
                     :justify-content "space-between"
                     :border-right "1px solid #E1E1E1"
                     :backgrond "#FFF"}}
   [:settings {:style {:display "flex"
                       :flex-direction "column"
                       :border-top "1px solid #E1E1E1"
                       :border-bottom "1px solid #E1E1E1"}}
    [settings]]
   [button {:text "Logout" :icon svg/logout :tag :logout}]])
