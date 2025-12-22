(ns pyregence.components.nav-bar
  (:require [pyregence.components.forecast-tabs :refer [forecast-tabs]]
            [pyregence.components.login-menu    :refer [login-menu]]
            [pyregence.components.settings-menu :refer [settings-menu]]
            [pyregence.styles                   :as $]))

(defn- $nav-bar
  []
  {:display         "flex"
   :justify-content "center"
   :align-items     "center"
   :height          "45px"
   :width           "100%"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn nav-bar
  "Defines the horizontal navigation component for the application."
  [props]
  [:nav {:style ($/combine $nav-bar {:background ($/color-picker :primary-standard-orange)})}
   [forecast-tabs props]
   [login-menu props]])
