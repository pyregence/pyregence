(ns pyregence.components.map-controls.panel-dropdown
  (:require [pyregence.components.common    :refer [tool-tip-wrapper]]
            [pyregence.components.svg-icons :as svg]
            [pyregence.styles               :as $]
            [pyregence.utils                :as u]
            [pyregence.utils.dom-utils      :as u-dom]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn panel-dropdown [title tool-tip-text val options disabled? call-back & [selected-param-set]]
  [:div {:style {:display "flex" :flex-direction "column" :margin-top ".25rem"}}
   [:div {:style {:display "flex" :justify-content "space-between"}}
    [:label title]
    [tool-tip-wrapper
     tool-tip-text
     :left
     [:div {:style ($/combine ($/fixed-size "1rem")
                              {:margin "0 .25rem 4px 0"
                               :fill   ($/color-picker :font-color)})}
      [svg/help]]]]
   [:select {:style     ($/dropdown)
             :value     (or val :none)
             :disabled  disabled?
             :on-change #(call-back (u-dom/input-keyword %))}
    (map (fn [[key {:keys [opt-label enabled? disabled-for]}]]
           [:option {:key      key
                     :value    key
                     :disabled (or (and (set? disabled-for) (some selected-param-set disabled-for))
                                   (and (fn? enabled?) (not (enabled?))))}
            opt-label])
         options)]])
