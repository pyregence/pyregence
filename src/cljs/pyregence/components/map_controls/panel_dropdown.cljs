(ns pyregence.components.map-controls.panel-dropdown
  (:require [pyregence.components.common    :refer [tool-tip-wrapper]]
            [pyregence.components.svg-icons :as svg]
            [pyregence.styles               :as $]
            [pyregence.utils.dom-utils      :as u-dom]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def dd (atom []))

(def args (atom []))
;;
;; val is the 3rd thing
(defn panel-dropdown [title tool-tip-text val options disabled? call-back & [selected-param-set]]
  (swap! dd conj [title tool-tip-text val options disabled? call-back & [selected-param-set]])
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
             :on-change (fn [v]
                          (swap! args conj {:v v :val val :call-back call-back})
                          (def call-back call-back)
                          (def v v)
                          (call-back (u-dom/input-keyword v)))}
    (->> options
         (remove (fn [[_ {:keys [hidden? opt-label]}]] (or hidden? (empty? opt-label))))
         (map (fn [[key {:keys [opt-label enabled? disabled-for]}]]
                [:option {:key      key
                          :value    key
                          :disabled (or (and (set? disabled-for) (some selected-param-set disabled-for))
                                        (and (fn? enabled?) (not (enabled?))))}
                 opt-label])))]])

(comment
  @args
  ;; => [{:v #object[SyntheticEvent [object Object]],
  ;;      :val :scperc,
  ;;      :call-back #object[Function]}]


  )
