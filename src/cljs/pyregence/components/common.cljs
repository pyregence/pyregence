(ns pyregence.components.common
  (:require [pyregence.styles :as $]))

(defn $radio [checked?]
  (merge
   (when checked? {:background-color ($/color-picker :black 0.6)})
   {:border        "2px solid"
    :border-color  ($/color-picker :black)
    :border-radius "100%"
    :height        "1rem"
    :margin-right  ".4rem"
    :width         "1rem"}))

(defn radio [label state condition on-click]
  [:div {:style ($/flex-row)
         :on-click #(on-click condition)}
   [:div {:style ($/combine [$radio (= @state condition)])}]
   [:label {:style {:font-size ".8rem" :margin "4px .5rem 0 0"}} label]])
