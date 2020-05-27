(ns pyregence.components.common
  (:require [pyregence.styles :as $]))

(defn $radio [checked? themed?]
  (merge
   (when checked? {:background-color ($/color-picker (if themed? :border-color :black) 0.6)})
   {:border        "2px solid"
    :border-color  ($/color-picker (if themed? :border-color :black))
    :border-radius "100%"
    :height        "1rem"
    :margin-right  ".4rem"
    :width         "1rem"}))

(defn radio
  ([label state condition on-click]
   (radio label state condition on-click false))
  ([label state condition on-click themed?]
   [:div {:style ($/flex-row)
          :on-click #(on-click condition)}
    [:div {:style ($/combine [$radio (= @state condition) themed?])}]
    [:label {:style {:font-size ".8rem" :margin "4px .5rem 0 0"}} label]]))
