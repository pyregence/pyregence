(ns pyregence.components.nav-bar
  (:require [clojure.string                     :as str]
            [pyregence.components.forecast-tabs :refer [forecast-tabs]]
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

(defn- $nav-bar-header []
  {
   :align-items "center"
   :display     "flex"
   })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- nav-header [is-pyrecast?]
  [:div#header {:style ($nav-bar-header)}
   [:a {:rel   "home"
        :href  (if is-pyrecast? "/" "https://pyregence.org")
        :title "Pyregence"
        :style {:margin-bottom "0.3125rem"
                :margin-left   "10%"
                :margin-top    "0.3125rem"}}
    [:img {:src   (str "/images/" (if is-pyrecast? "pyrecast" "pyregence") "-logo.svg")
           :alt   "Pyregence Logo"
           :style {:height "40px"
                   :width  "auto"}}]]])

(defn- nav-branding [is-pyrecast?]
  (when is-pyrecast?
    [:a {:href   "https://pyregence.org"
         :target "pyregence"
         :style  {:margin-right "5%"}}
     [:img {:src   "/images/powered-by-pyregence.svg"
            :alt   "Powered by Pyregence Logo"
            :style {:height "1.25rem"
                    :width  "auto"}}]]))

(defn nav-bar
  "Defines the horizontal navigation component for the application"
  [props]
  [:nav {:style ($/combine $nav-bar {:background ($/color-picker :yellow)})}
   [:div {:style ($nav-bar-container)}
    [:pre props]
    [forecast-tabs]
    [login-menu props]
    ]])

(defn nav-bar-include-pyrecast-branding
  "Defines the horizontal navigation component for the application"
  [props]
  (let [is-pyrecast? (str/ends-with? (-> js/window .-location .-hostname) "pyrecast.org")]
    [:nav {:style ($/combine $nav-bar {:background ($/color-picker :yellow)})}
     [:div {:style ($nav-bar-container)}
      [nav-header is-pyrecast?]
      [forecast-tabs]
      [login-menu props]
      [nav-branding is-pyrecast?]
      ]]))
