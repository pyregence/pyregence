(ns pyregence.components.nav-bar
  (:require [pyregence.components.forecast-tabs :refer [forecast-tabs]]
            [pyregence.components.login-menu    :refer [login-menu]]
            [pyregence.styles                   :as $]))

(defn- $nav-bar []
  {
   :display         "flex"
   :justify-content "center"
   :padding         "4px"
   :width           "100%"
   ;; :border-bottom "1px solid lightgrey"
   })

(defn- $nav-bar-container []
  {
   :display "flex"
   :margin  "0px 10px"
   })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn nav-bar
  "Defines the horizontal navigation component for the application"
  [props]
  [:nav {:style ($/combine $nav-bar {:background ($/color-picker :yellow)})}
   [:div {:style ($nav-bar-container)}
    [forecast-tabs props]
    [login-menu props]
    ]])
