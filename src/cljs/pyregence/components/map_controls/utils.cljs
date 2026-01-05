(ns pyregence.components.map-controls.utils
  (:require [reagent.core     :as r]
            [pyregence.styles :as $]))

;;TODO reuse-this on collapsible panel
(defn option [{:keys [id on-change label]}]
  (r/with-let [show?               (r/atom false)]
    [:div {:style {:margin-top ".5rem" :padding "0 .5rem"}}
     [:div#optional-layer-checkbox {:style {:display "flex"}}
      [:input {:id        id
               :style     {:margin ".25rem .5rem 0 0"}
               :type      "checkbox"
               :checked   @show?
               :on-change #(on-change show?)}]
      [:label {:for id} label]]]))


(defn collapsible-panel-section
  "A section component to differentiate content in the collapsible panel."
  [id body]
  [:section {:id    (str "section-" id)
             :style {:padding "0.75rem 0.6rem 0 0.6rem"}}
   [:div {:style {:background-color ($/color-picker :header-color 0.6)
                  :border-radius "8px"
                  :box-shadow    "0px 0px 3px #bbbbbb"
                  :padding       "0.5rem"}}
    body]])
