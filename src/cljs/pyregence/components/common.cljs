(ns pyregence.components.common
  (:require-macros [pyregence.herb-patch :refer [style->class]])
  (:require herb.core
            [pyregence.styles :as $]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; These are needed here to prevent circular dependencies

(defn input-value
  "Return the value property of the target property of an event."
  [event]
  (-> event .-target .-value))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $labeled-input []
  {:display        "flex"
   :flex           1
   :flex-direction "column"
   :margin         "0 .25rem .5rem .25rem"
   :width          "100%"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn labeled-input
  ([label-text state]
   [labeled-input label-text state "text" false #(reset! state (input-value %))])
  ([label-text state type]
   [labeled-input label-text state type false #(reset! state (input-value %))])
  ([label-text state type disabled?]
   [labeled-input label-text state type disabled? #(reset! state (input-value %))])
  ([label-text state type disabled? call-back]
   [:div {:style ($labeled-input)}
    [:label label-text]
    [:input {:class (style->class $/p-bordered-input)
             :disabled disabled?
             :type type
             :value @state
             :on-change call-back}]]))

(defn simple-form
  ([title button-text fields on-click]
   (simple-form title button-text fields on-click nil))
  ([title button-text fields on-click footer]
   [:div {:style {:height "fit-content" :width "25rem"}}
    [:div {:style ($/action-box)}
     [:div {:style ($/action-header)}
      [:label {:style ($/padding "1px" :l)} title]]
     [:div {:style ($/combine {:overflow "auto"})}
      [:div
       [:div {:style ($/combine $/flex-col [$/margin "1.5rem"])}
        (doall (map-indexed (fn [i [label state type]]
                              ^{:key i} [labeled-input label state type])
                            fields))
        [:input {:class "btn border-yellow text-brown"
                 :style ($/combine ($/align :block :right) {:margin-top ".5rem"})
                 :type "button"
                 :value button-text
                 :on-click on-click}]
        (when footer (footer))]]]]]))
