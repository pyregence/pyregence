(ns pyregence.components.login-menu
  (:require [herb.core                      :refer [<class]]
            [reagent.core                   :as r]
            [pyregence.components.svg-icons :as svg]
            [pyregence.styles               :as $]
            [pyregence.utils.browser-utils  :as u-browser]))

(defn- $login-label [selected? logged-in?]
  {:background-color (when selected? ($/color-picker :primary-main-orange))
   :color            ($/color-picker :black)
   :cursor           "pointer"
   :font-family      "Roboto"
   :font-weight      400
   :font-size        "16px"
   :padding          (if logged-in?
                       "16px 24px 16px 18px"
                       "16px 18px 16px 24px")})

(def login-dropdowns
  {:login    {:label      "Log In"
              :on-click  #(u-browser/jump-to-url! "/login")}
   :register {:label      "Create an Account"
              :on-click  #(u-browser/jump-to-url! "/register")}})

(defn login-dropdown-item [first-item?]
  (with-meta
    {:background-color ($/color-picker :white)
     :border-top       (when-not first-item?
                         (str "1px solid " ($/color-picker :neutral-soft-gray)))
     :color            ($/color-picker :black)
     :cursor           :pointer
     :font-family      "Roboto"
     :font-size        "14px"
     :font-weight      400
     :height           "50px"
     :padding          "14px"
     :width            "175px"}
    {:pseudo {:hover {:background-color ($/color-picker :neutral-soft-gray 0.4)}}}))

(defn- login-popup []
  [:div {:style {:background     ($/color-picker :white)
                 :border         (str "1px solid " ($/color-picker :neutral-soft-gray))
                 :border-radius  "4px"
                 :display        :flex
                 :flex-direction :column
                 :right          0
                 :top            "50px"
                 :position       :absolute
                 :z-index        9999}}
   (map-indexed
    (fn [idx [login-key option]]
      (let [{:keys [label on-click]} option
            first-item? (zero? idx)]
        ^{:key login-key}
        [:div {:on-click on-click
               :class    (<class login-dropdown-item first-item?)}
         label]))
    login-dropdowns)])

(defn login-menu
  "A login and logout navigation menu item"
  [{:keys [logged-in?]}]
  (let [expanded? (r/atom false)]
    (fn [_]
      [:div {:style {:position "absolute" :right "3rem"}
             :class (<class $/p-add-hover)}
       (if logged-in?
         [:label {:style    ($login-label @expanded? logged-in?)
                  :on-click #(u-browser/jump-to-url! "/account-settings")}
          [svg/wheel :height "25px"]
          [:span {:style {:margin-left "8px" :vertical-align :middle}}
           "Settings"]]
         [:label {:style    ($login-label @expanded? logged-in?)
                  :on-click #(swap! expanded? not)}
          "Sign In"
          [:span {:style {:min-width  "25px"
                          :display    :inline-block
                          :text-align :center}}
           (if @expanded? [svg/arrow-up] [svg/arrow-down])]])
       (when @expanded?
         [login-popup])])))
