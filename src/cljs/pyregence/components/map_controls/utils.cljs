(ns pyregence.components.map-controls.utils
  (:require [reagent.core :as r]))

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
