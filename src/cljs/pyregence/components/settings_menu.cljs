(ns pyregence.components.settings-menu
  (:require ;;[pyregence.components.login-menu :refer [login-menu]]
   [reagent.core :as r]
   [pyregence.components.svg-icons  :as svg]))


(defn settings-menu
  "a dropdown menu with options: profile settings, organization settings, logout"
  [{:keys [is-admin? logged-in? mobile?]}]
  (let [expanded? (r/atom false)]
    (fn [_]
      [:<>
       [:div {:style {:position "absolute" :right "3rem"}}
        [:label {:syle {:cursor "pointer"}
                 :on-click #(swap! expanded? not)}
         "Settings"
         [:span {:style {:min-width  "25px"
                         :display    :inline-block
                         :text-align :center}}
          (if @expanded?
            [svg/arrow-up   :height "25px"]
            [svg/arrow-down :height "25px"])]]]
       (when @expanded?
         [:div {:style {:position :absolute
                        :width "10vw"
                        :background-color :black
                        :right "1rem"
                        :top "40px"
                        :z-index 9999
                        }}
          [:div "Profile Settings"]
          [:div "Organization Settings"]
          [:div "Logout"]])]))
  )
