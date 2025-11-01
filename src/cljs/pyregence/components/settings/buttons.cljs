(ns pyregence.components.settings.buttons
  (:require
   [pyregence.components.svg-icons :as svg]
   [herb.core                      :refer [<class]]
   [pyregence.styles               :as $]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def $primary-styles
  {:display          "flex"
   :height           "44px"
   :width            "fit-content"
   :align-items      "center"
   :background-color ($/color-picker :primary-standard-orange)
   :padding          "12px 14px"
   :border-radius    "4px"
   :border           (str "2px solid " ($/color-picker :primary-standard-orange))
   ;;TODO move topography properties to top of page.
   :line-height      "normal"
   :font-family      "Roboto"
   :font-size        "14px"
   :font-style       "normal"
   :font-weight      "400"})

(def $ghost-styles
  (assoc $primary-styles :background ($/color-picker :white)))

(defn- $on-hover-darken-orange
  [styles]
  (with-meta
    styles
    (let [main-orange ($/color-picker :primary-main-orange)]
      {:pseudo {:hover {:background main-orange
                        :border     (str "2px solid " main-orange)}}})))

(defn- $white-to-red-on-hover
  []
  (with-meta {:background ($/color-picker :white)}
    {:pseudo {:hover {:background ($/color-picker :light-red)}}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Icons
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def add-icon
  [:div {:style {:height "11px" :width "11px"}}
   [svg/add :height "11px" :width "11px"]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn primary
  [{:keys [text icon class on-click]
    :or   {class (<class #($on-hover-darken-orange $primary-styles))}}]
  [:button
   {:class class :on-click on-click}
   [:div {:style {:display "flex"
                  :gap     "8px"
                  :height  "100%"
                  :width   "100%"}}
    (when icon icon)
    [:span text]]])

(defn ghost
  [m]
  [primary (assoc m :class
                  (<class #($on-hover-darken-orange $ghost-styles)))])

(defn add [m]
  [primary (assoc m :icon add-icon)])

(defn delete
  [{:keys [on-click]}]
  [:button {:class (<class $white-to-red-on-hover)
            :style {:display         "flex"
                    :width           "50px"
                    :height          "50px"
                    :justify-content "center"
                    :align-items     "center"
                    :border-radius   "4px"
                    :border          (str "1px solid " ($/color-picker :error-red))
                    :border-width    "1.5px"}
            :on-click on-click}
   [:div {:flex "0 0 auto"}
    [svg/trash-can :height "50px" :width "50px"]]])
