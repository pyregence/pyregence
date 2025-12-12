(ns pyregence.components.login-menu
  (:require [clojure.core.async                 :refer [<! go]]
            [herb.core                          :refer [<class]]
            [reagent.core                       :as r]
            [pyregence.components.common        :refer [tool-tip-wrapper]]
            [pyregence.components.svg-icons     :as svg]
            [pyregence.styles                   :as $]
            [pyregence.utils.async-utils        :as u-async]
            [pyregence.utils.browser-utils      :as u-browser]))


(def login-dropdowns
  {:login    {:label      "Log In"
              :on-click  #(u-browser/jump-to-url! "/login")}
   :register {:label      "Create an Account"
              :on-click  #(u-browser/jump-to-url! "/register")}})

(defn login-menu
  "A login and logout navigation menu item"
  [{:keys [is-admin? logged-in? mobile?]}]
  (let [expanded? (r/atom false)]
    (fn [_]
      (when-not mobile?
        [:div {:style {:position "absolute" :right "3rem"}
               :class (<class $/p-add-hover)}
         (if logged-in?
           [:label {:style {:cursor "pointer" :margin ".16rem .2rem 0 0"}
                    :on-click #(-> js/window .-location (.assign "/account-settings"))}
            [svg/wheel :height "25px"]
            [:span {:style {:margin-left "8px" :vertical-align :middle}}
             "Settings"]]           
           [:label {:style {:cursor "pointer" :margin ".16rem .2rem 0 0"}
                    :on-click #(swap! expanded? not)}
            "Log In"
            [:span {:style {:min-width  "25px"
                            :display    :inline-block
                            :text-align :center}}
             (if @expanded?
               [svg/arrow-up   :height "25px"]
               [svg/arrow-down :height "25px"])]])
         (when @expanded?
           [:div
            {:style {:position :absolute
                     :width "12vw"
                     :right "1rem"
                     :top "40px"
                     :z-index 9999
                     :display :flex
                     :flex-direction :column
                     :align-items :flex-start
                     :border-radius "4px"
                     :border "1px solid var(--Md_gray, #6C6C6C)"
                     :background ($/color-picker :light-gray)
                     }}
            (map
             (fn [[key option]]
               (let [{:keys [label on-click icon-height]} option]
                 ^{:key key}
                 [:div
                  {:on-click on-click
                   :class (<class $/p-add-hover)
                   :style {:display :flex
                           :height "50px"
                           :padding "16px 12px"
                           :align-items :center
                           :gap "10px"
                           :align-self :stretch
                           :cursor :pointer}}
                  label]))
             login-dropdowns)])]))))
