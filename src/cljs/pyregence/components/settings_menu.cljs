(ns pyregence.components.settings-menu
  (:require [reagent.core                    :as r]
            [clojure.core.async              :refer [<! go]]
            [pyregence.components.svg-icons  :as svg]
            [pyregence.styles                :as $]
            [pyregence.utils.async-utils     :as u-async]))


(def settings-dropdowns
  {:usr-settings {:label "Profile Settings"
                  :icon [svg/individual]
                  :on-click (fn [e]
                              #(set! (.-location js/window) "/account-settings")
                              )}
   :org-settings {:label "Organization Settings"
                  :icon [svg/wheel]}
   :log-out      {:label "Logout"
                  :icon [svg/logout]
                  :on-click (fn []
                              (go (<! (u-async/call-clj-async! "log-out"))
                                  (-> js/window .-location .reload)))}})

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
                        :width "12vw"
                        :right "1rem"
                        :top "40px"
                        :z-index 9999
                        :display :flex
                        :flex-direction :column
                        :align-items :flex-start
                        :border-radius "4px"
                        :border "1px solid var(--Md_gray, #6C6C6C)"
                        :background "#FFF"}}          
          (map
           (fn [[key option]]
             (let [{:keys [label icon on-click icon-height]} option]
               ^{:key key}
               [:div                
                {:style {
                         :display :flex
                         :height "50px"
                         :padding "16px 12px"
                         :align-items :center
                         :gap "10px"
                         :align-self :stretch
                         :cursor :pointer
                         }
                 :on-click (or on-click
                               (fn [e] (js/console.log (clj->js e))))}
                (into icon [:height "25px"])
                label]))
           settings-dropdowns)])])))
