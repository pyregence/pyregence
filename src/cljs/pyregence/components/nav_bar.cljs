(ns pyregence.components.nav-bar
  (:require [pyregence.components.forecast-tabs :refer [forecast-tabs]]
            [pyregence.components.login-menu    :refer [login-menu]]
            [pyregence.components.svg-icons     :as svg]
            [pyregence.styles                   :as $]
            [pyregence.utils.browser-utils      :as u-browser]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn nav-bar
  "Defines the horizontal navigation component for the application."
  [props]
  [:nav {:style {:align-items     "center"
                 :background      ($/color-picker :primary-standard-orange)
                 :display         "flex"
                 :height          "45px"
                 :justify-content "center"
                 :width           "100%"}}
   [forecast-tabs props]
   (if (:mobile? props)
     (if (:logged-in? props)
       [:span {:style     {:cursor   "pointer"
                           :position "absolute"
                           :right    "10px"}
                :on-click #(u-browser/jump-to-url! "/account-settings")}
        [svg/wheel :height "25px"]]
       [:span {:style    {:cursor   "pointer"
                          :position "absolute"
                          :right    "10px"}
               :on-click #(u-browser/jump-to-url! "/login")}
        [svg/login :height "25px"]])
     [login-menu props])])
