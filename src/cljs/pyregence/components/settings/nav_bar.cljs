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

;;TODO move to svg ns, its not there bc it was throwing errors there...
(defn logout
  [& {:keys [height width]}]
  [:svg
   {:xmlns "http://www.w3.org/2000/svg",
    :width width,
    :height height,
    :viewBox "0 0 24 24",
    :fill "none"}
   [:mask
    {:id "mask0_228_3867",
     :style {:mask-type "alpha"},
     :maskunits "userSpaceOnUse",
     :x "0",
     :y "0",
     :width "24",
     :height "24"}
    [:rect {:width "24", :height "24", :fill "#D9D9D9"}]]
   [:g
    {:mask "url(#mask0_228_3867)"}
    [:path
     {:d
      "M5 21C4.45 21 3.97917 20.8042 3.5875 20.4125C3.19583 20.0208 3 19.55 3 19V5C3 4.45 3.19583 3.97917 3.5875 3.5875C3.97917 3.19583 4.45 3 5 3H12V5H5V19H12V21H5ZM16 17L14.625 15.55L17.175 13H9V11H17.175L14.625 8.45L16 7L21 12L16 17Z",
      :fill "black"}]]])

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
   [button {:text "Logout" :icon logout :tag :logout}]])
