(ns pyregence.components.utils
  (:require
   [clojure.string                 :as str]
   [herb.core                      :refer [<class]]
   [pyregence.components.svg-icons :as svg]
   [pyregence.styles               :as $]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CSS Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $standard-input-field
  []
  (with-meta
    {:background ($/color-picker :neutral-white)
     :border     (str "1px solid " ($/color-picker :neutral-soft-gray))}
    ;;TODO On some browsers, aka chrome, there is a black border that is being
    ;;imposed on top of the focused orange border. Try to fix this!
    {:pseudo {:focus {:background ($/color-picker :neutral-white)
                      :border     (str "1px solid " ($/color-picker :primary-standard-orange))}
              :hover {:background ($/color-picker :neutral-light-gray)}}}))

(def main-styles
  {:display        "flex"
   :flex-direction "column"
   :align-items    "flex-start"
   :padding        "40px 160px"
   :flex           "1 0 0"
   :gap            "24px"
   :overflow       "auto"})

(def label-styles
  {:color       ($/color-picker :neutral-md-gray)
   :font-size   "14px"
   :font-weight "500"
   :margin      "0"})

(def font-styles
  {:color       ($/color-picker :neutral-black)
   :font-size   "14px"
   :font-weight "600"
   ;;TODO look into why all :p need margin-bottom 0 to look normal
   :margin      "0"})

(defn- $on-hover-darker-gray-border []
  (with-meta
    {:border     (str "1px solid " ($/color-picker :neutral-soft-gray))
     :background "rgba(246, 246, 246, 1)"}
    {:pseudo {:hover        {:border (str "1px solid " ($/color-picker :neutral-md-gray))}
              :focus-within {:border (str "1px solid " ($/color-picker :standard-orange))}}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn text-labeled
  [{:keys [label text icon]}]
  [:div {:style {:display        "flex"
                 :gap            "5px"
                 :flex-direction "row"
                 :align-items    "center"}}
   [:p {:style label-styles} (str label ":")]
   [:p {:style font-styles} text]
   (when icon [icon :height "16px" :width "16px"])])

(defn input-field
  [{:keys [value on-change style support-message placeholder type] :or {type "text"}}]
  [:div {:style {:display        "flex"
                 :flex-direction "column"}}
   [:input {:type      type
            :class     (<class $standard-input-field)
            :style     (merge {:font-weight   "500"
                               :width         "100%"
                               :max-width     "350px"
                               :height        "50px"
                               :font-size     "14px"
                               :font-style    "normal"
                               :line-height   "22px"
                               :padding       "14px"
                               :border-radius "4px"} style)
            :value     value
            :placeholder placeholder
            :pattern   ".+"
            :on-change on-change}]
   (when support-message
     [:p {:style {:color "red"}} support-message])])

(defn input-labeled
  [{:keys [label] :as m}]
  [:div {:style {:display        "flex"
                 :flex-direction "column"
                 :width          "100%"}}
   [:div {:style label-styles}
    (let [styles {:font-size   "14px"
                  :font-weight "500"
                  :color       ($/color-picker :neutral-black)}]
      [:div {:style {:display        "flex"
                     :flex-direction "row"
                     :width          "100%"
                     :height         "24px"}}
       [:p {:style styles} label]
       [:p {:style (assoc styles :color ($/color-picker :error-red))}  "*"]])
    [input-field m]]])

;; TODO taking children cmpts here is strange,
;; i think to share this `card` it should just share the styles instead.
(defn card
  [{:keys [title children]}]
  [:settings-body-card
   {:style {:display        "flex"
            :max-width      "1000px"
            :min-width      "300px"
            :width          "100%"
            :padding        "32px"
            :flex-direction "column"
            :align-items    "flex-start"
            :gap            "16px"
            :align-self     "stretch"
            :border-radius  "4px"
            :border         (str "1px solid " ($/color-picker :neutral-soft-gray))
            :background     ($/color-picker :white)}}
   [:p {:style {:color          ($/color-picker :neutral-black)
                :font-size      "14px"
                :font-style     "normal"
                :font-weight    "700"
                :line-height    "14px"
                :text-transform "uppercase"
                :margin-bottom  "0px"}}
    title]
   children])

(defn search-cmpt
  [{:keys [on-change value]}]
  [:div {:class (<class $on-hover-darker-gray-border)
         :style {:display        "flex"
                 :min-height     "42px"
                 :flex-direction "row"
                 :align-items    "center"
                 :border-radius  "4px"
                 :cursor         "pointer"}}
   [:div {:style {:padding-right "8px"
                  :padding-left  "16px"}}
    [svg/search :height "16px" :width "16px"]]
   [:input {:type        "text"
            :value       value
            :placeholder "Search"
            :style       {:border       "none"
                          :background   "transparent"
                          :width        "100%"
                          :outline      "none"}
            :on-change on-change}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; display functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn db->display
  [db-cell-name]
  (->>
   (str/split db-cell-name #"_")
   (map str/capitalize)
   (str/join " ")))
