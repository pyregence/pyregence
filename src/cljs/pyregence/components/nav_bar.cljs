(ns pyregence.components.nav-bar
  (:require [pyregence.components.forecast-tabs :refer [forecast-tabs]]
            [pyregence.components.login-menu    :refer [login-menu]]
            [pyregence.styles                   :as $]))

(defn- $nav-bar
  []
  {:display         "flex"
   :justify-content "center"
   :align-items     "center"
   :width           "100%"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn nav-bar
  "Defines the horizontal navigation component for the application."
  [props]
  [:nav {:style ($/combine $nav-bar {:background ($/color-picker :yellow)})}
   [forecast-tabs props]
   [login-menu props]])
