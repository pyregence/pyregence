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
   :min-height       "44px"
   :width            "fit-content"
   :align-items      "center"
   :background-color ($/color-picker :primary-standard-orange)
   :padding          "12px 14px"
   :border-radius    "4px"
   :border           (str "2px solid " ($/color-picker :primary-standard-orange))
   :font-size        "14px"
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
  [{:keys [text icon class on-click disabled?]
    :or   {class (<class #($on-hover-darken-orange $primary-styles))}}]
  [:button
   {:class class
    :on-click on-click
    :disabled disabled?
    :style (when disabled? {:opacity "0.5" :cursor "not-allowed"})}
   [:div {:style {:display     "flex"
                  :align-items "center"
                  :gap         "8px"
                  :height      "100%"
                  :width       "100%"}}
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
   [:div {:style {:flex "0 0 auto"}}
    [svg/trash-can :height "50px" :width "50px"]]])

(defn toggle
  "Toggle switch with label."
  [{:keys [on? label on-click]}]
  [:button {:on-click on-click
            :style    {:display     "flex"
                       :align-items "center"
                       :gap         "10px"
                       :background  "transparent"
                       :border      "none"
                       :cursor      "pointer"
                       :padding     "0"
                       :font-family "inherit"
                       :font-size   "14px"
                       :color       ($/color-picker :neutral-black)}}
   [:div {:style {:position      "relative"
                  :width         "48px"
                  :height        "24px"
                  :background    ($/color-picker (if on? :primary-teal :neutral-soft-gray))
                  :border-radius "12px"}}
    [:div {:style {:position      "absolute"
                   :top           "2px"
                   :width         "20px"
                   :height        "20px"
                   :background    "white"
                   :border-radius "50%"
                   :right         (when on? "2px")
                   :left          (when-not on? "2px")}}]]
   [:span {:style {:font-weight "600"}} label]])
