(ns pyregence.pages.account-settings
  (:require
   [pyregence.styles :as $]))

;; TODO $nav-bar was copied from the nav_bar.cljs to mock this page out, this page will
;; need, according to the designs, the forecast tabs and the new settings tabs.
(defn- $nav-bar
  []
  {:display         "flex"
   :justify-content "center"
   :align-items     "center"
   :width           "100%"
   ;;TODO added height
   :height "33px"})

(defn root-component
  "The root component of the /account-settings page."
  []
  ;;TODO add settings components
  [:nav  {:style ($/combine $nav-bar {:background ($/color-picker :yellow)})}])
