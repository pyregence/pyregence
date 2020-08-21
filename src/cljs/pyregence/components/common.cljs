(ns pyregence.components.common
  (:require-macros [pyregence.herb-patch :refer [style->class]])
  (:require herb.core
            [reagent.core :as r]
            [reagent.dom :as rd]
            [clojure.string :as str]
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
   :padding-bottom ".5rem"
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

(defn check-box
  [label-text state]
  [:span {:style {:margin-bottom ".5rem"}}
   [:input {:style {:margin-right ".25rem"}
            :type "checkbox"
            :checked @state
            :on-change #(swap! state not)}]
   [:label label-text]])

;; FIXME take in a map instead of having so many overloads
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

(defn $arrow [arrow-x arrow-y arrow-position]
  {:background-color ($/color-picker :font-color)
   :border-top       (when (#{:top :right} arrow-position)    (str "1.5px solid " ($/color-picker :bg-color)))
   :border-right     (when (#{:bottom :right} arrow-position) (str "1.5px solid " ($/color-picker :bg-color)))
   :border-bottom    (when (#{:left :bottom} arrow-position)  (str "1.5px solid " ($/color-picker :bg-color)))
   :border-left      (when (#{:top :left} arrow-position)     (str "1.5px solid " ($/color-picker :bg-color)))
   :content          "close-quote"
   :height           "16px"
   :left             arrow-x
   :position         "fixed"
   :top              arrow-y
   :transform        "rotate(45deg)"
   :width            "16px"
   :z-index          2001})

(defn $tool-tip [letter-count tip-x tip-y arrow-position]
  {:background-color ($/color-picker :font-color)
   :border           (str "1.5px solid " ($/color-picker :bg-color))
   :border-radius    "6px"
   :color            ($/color-picker :bg-color)
   :left             tip-x
   :top              tip-y
   :position         "fixed"
   :padding          ".5rem"
   :width            (str (min (if (#{:top :bottom} arrow-position) 20 30)
                               (- (/ letter-count 1.5) 1)) "rem")
   :z-index          2000})

(defn sibling-wrapper [sibling sibling-ref]
  (r/create-class
   {:component-did-mount
    (fn [this] (reset! sibling-ref (rd/dom-node this)))

    :render
    (fn [_] sibling)}))

(defn interpose-react [tag items]
  [:<> (for [i (butlast items)]
         ^{:key i} [:<> i tag])
   (last items)])

(defn show-line-break [text]
  (let [items (if (coll? text)
                (vec text)
                (str/split text #"\n"))]
    [:<> (interpose-react [:br] items)]))

(defn tool-tip [tool-box tool-tip-text tip-x tip-y arrow-x arrow-y arrow-position]
  (r/create-class
   {:component-did-mount
    (fn [this] (reset! tool-box (.getBoundingClientRect (rd/dom-node this))))

    :reagent-render
    (fn [_ tool-tip-text tip-x tip-y arrow-x arrow-y arrow-position]
      [:div {:style ($tool-tip (count tool-tip-text) tip-x tip-y arrow-position)}
       [:div {:style ($arrow arrow-x arrow-y arrow-position)}]
       [:div {:style {:position "relative" :z-index 2002}}
        [show-line-break tool-tip-text]]])}))

;; TODO abstract this to take content for things like a dropdown log in.
;; TODO calculate max widths and heights.
(defn tool-tip-wrapper [tool-tip-text arrow-position sibling]
  (let [show?       (r/atom false)
        tool-box    (r/atom #js {})
        sibling-ref (r/atom nil)]
    (r/create-class
     {:render
      (fn [_]
        [:div {:on-mouse-over  #(reset! show? true)
               :on-mouse-leave #(reset! show? false)}
         [sibling-wrapper sibling sibling-ref]
         (when @sibling-ref
           (let [sibling-box (.getBoundingClientRect @sibling-ref)
                 tool-width  (aget @tool-box "width")
                 tool-height (aget @tool-box "height")
                 max-x       (- (.-innerWidth js/window) tool-width 6)
                 max-y       (- (.-innerHeight js/window) tool-height 6)
                 [arrow-x tip-x] (condp #(%1 %2) arrow-position
                                   #{:top :bottom}
                                   (let [sibling-x (+ (aget sibling-box "x") (/ (aget sibling-box "width") 2))]
                                     [(- sibling-x 8) (- sibling-x (/ tool-width 2))])

                                   #{:left}
                                   (let [sibling-x (+ (aget sibling-box "x") (aget sibling-box "width"))]
                                     [(+ sibling-x 4.7) (+ sibling-x 13)])

                                   (let [sibling-x (aget sibling-box "x")]
                                     [(- sibling-x 22.6) (- sibling-x tool-width 13)]))
                 [arrow-y tip-y] (condp #(%1 %2) arrow-position
                                   #{:left :right}
                                   (let [sibling-y (+ (aget sibling-box "y") (/ (aget sibling-box "height") 2))]
                                     [(- sibling-y 4.7) (+ (- sibling-y (/ tool-height 2)) 4.7)])

                                   #{:top}
                                   (let [sibling-y (+ (aget sibling-box "y") (aget sibling-box "height"))]
                                     [(+ sibling-y 4.7) (+ sibling-y 13)])

                                   (let [sibling-y (aget sibling-box "y")]
                                     [(- sibling-y 22.6) (- sibling-y tool-height 13)]))]
             [tool-tip
              tool-box
              tool-tip-text
              (if @show? (max 6 (min tip-x max-x)) -1000)
              (max 6 (min tip-y max-y))
              (if @show? arrow-x -1000)
              arrow-y
              arrow-position]))])})))
