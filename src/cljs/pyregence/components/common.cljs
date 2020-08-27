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

(defn $arrow [arrow-x arrow-y arrow-position show?]
  {:background-color ($/color-picker :font-color)
   :border-top       (when (#{:top :right} arrow-position)    (str "1.5px solid " ($/color-picker :bg-color)))
   :border-right     (when (#{:bottom :right} arrow-position) (str "1.5px solid " ($/color-picker :bg-color)))
   :border-bottom    (when (#{:left :bottom} arrow-position)  (str "1.5px solid " ($/color-picker :bg-color)))
   :border-left      (when (#{:top :left} arrow-position)     (str "1.5px solid " ($/color-picker :bg-color)))
   :content          "close-quote"
   :height           "16px"
   :left             arrow-x
   :opacity          (if show? 1.0 0.0)
   :position         "fixed"
   :top              arrow-y
   :transform        "rotate(45deg)"
   :transition       "opacity 300ms ease-in"
   :width            "16px"
   :z-index          201})

(defn $tool-tip [tip-x tip-y arrow-position show?]
  {:background-color ($/color-picker :font-color)
   :border           (str "1.5px solid " ($/color-picker :bg-color))
   :border-radius    "6px"
   :color            ($/color-picker :bg-color)
   :left             tip-x
   :opacity          (if show? 1.0 0.0)
   :top              tip-y
   :transition       "opacity 300ms ease-in"
   :position         "fixed"
   :pointer-events   (if show? "auto" "none")
   :padding          ".5rem"
   :max-width        (str (if (#{:top :bottom} arrow-position) 20 30) "rem")
   :z-index          200})

(defn interpose-react [tag items]
  [:<> (doall
        (map-indexed
         (fn [idx item] ^{:key idx} [:<> item tag])
         (butlast items)))
   (last items)])

(defn show-line-break [text]
  (let [items (if (coll? text)
                (vec text)
                (str/split text #"\n"))]
    (interpose-react [:br] items)))

(defn calc-tool-position [sibling-ref tool-ref arrow-position]
  (let [sibling-box (.getBoundingClientRect sibling-ref)
        tool-box    (.getBoundingClientRect tool-ref)
        tool-width  (aget tool-box "width")
        tool-height (aget tool-box "height")
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
                            [(- sibling-x 22.6) (- sibling-x tool-width 14)]))
        [arrow-y tip-y] (condp #(%1 %2) arrow-position
                          #{:left :right}
                          (let [sibling-y (+ (aget sibling-box "y") (/ (aget sibling-box "height") 2))]
                            [(- sibling-y 4.7) (+ (- sibling-y (/ tool-height 2)) 4.7)])

                          #{:top}
                          (let [sibling-y (+ (aget sibling-box "y") (aget sibling-box "height"))]
                            [(+ sibling-y 3) (+ sibling-y 11)])

                          (let [sibling-y (aget sibling-box "y")]
                            [(- sibling-y 22.6) (- sibling-y tool-height 14)]))]
    [(max 6 (min tip-x max-x)) (max 62 (min tip-y max-y)) arrow-x arrow-y])) ; There is a 56px y offset for the header

(defn tool-tip [tool-tip-text sibling-ref arrow-position show?]
  (let [tool-ref (atom nil)
        position (r/atom [-1000 -1000 -1000 -1000])]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (reset! tool-ref (rd/dom-node this))
        (reset! position (calc-tool-position sibling-ref @tool-ref arrow-position)))

      :component-did-update
      (fn [_ [_ prev-tool-tip-text & _]]
        (when-not (= tool-tip-text prev-tool-tip-text)
          (reset! position (calc-tool-position sibling-ref @tool-ref arrow-position))))

      :reagent-render
      (fn [tool-tip-text _ arrow-position show?]
        (let [[tip-x tip-y arrow-x arrow-y] @position]
          [:div {:style ($tool-tip tip-x tip-y arrow-position show?)}
           [:div {:style ($arrow arrow-x arrow-y arrow-position show?)}]
           [:div {:style {:position "relative" :width "fit-content" :z-index 203}}
            [show-line-break tool-tip-text]]]))})))

(defn sibling-wrapper [sibling sibling-ref]
  (r/create-class
   {:component-did-mount
    (fn [this] (reset! sibling-ref (rd/dom-node this)))

    :reagent-render
    (fn [sibling _] sibling)}))

;; TODO abstract this to take content for things like a dropdown log in.
(defn tool-tip-wrapper [tool-tip-text arrow-position sibling]
  (r/with-let [show?       (r/atom false)
               sibling-ref (r/atom nil)]
    [:div {:on-mouse-over  #(reset! show? true)
           :on-mouse-leave #(reset! show? false)}
     [sibling-wrapper sibling sibling-ref]
     (when @sibling-ref
       [tool-tip tool-tip-text @sibling-ref arrow-position @show?])]))
