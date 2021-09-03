(ns pyregence.components.resizable-window
  (:require [reagent.core     :as r]
            [reagent.dom      :as rd]
            [herb.core :refer [<class]]
            [pyregence.styles :as $]
            [pyregence.components.svg-icons :refer [close]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $resizable-window [box-height box-width]
  {:height      box-height
   :overflow    "hidden"
   :right       "72px"
   :top         "1rem"
   :width       box-width})

(defn $sw-drag []
  (merge
   ($/fixed-size "1.5rem")
   {:bottom   "0"
    :cursor   "sw-resize"
    :left     "0"
    :position "absolute"
    :z-index  "3"}))

(defn $sw-drag-icon []
  {:border-right (str "1px solid " ($/color-picker :border-color))
   :bottom       "-.5rem"
   :height       "1rem"
   :left         "-.5rem"
   :position     "absolute"
   :transform    "rotate(-45deg)"
   :width        "1rem"
   :z-index      "2"})

(defn $close-button [height]
  {:fill     ($/color-picker :font-color)
   :height   height
   :position "absolute"
   :right    ".25rem"
   :width    height})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn drag-sw-icon [p-height p-width p-top box-height box-width]
  (r/with-let [drag-started? (r/atom false)]
    [:<>
     [:div#drag-icon
      {:style ($sw-drag)
       :draggable true
       :on-drag #(if @drag-started?
                   (reset! drag-started? false) ; ignore first value, fixes jumpy movement on start
                   (let [mouse-x (.-clientX %)
                         mouse-y (.-clientY %)]
                     (when (< (/ p-width 3) (+ mouse-x 68) (- p-width 50))
                       (reset! box-width (- p-width (+ mouse-x 68))))
                     (when (> (/ p-height 1.5) (- mouse-y p-top 12) 50)
                       (reset! box-height (- mouse-y p-top 12)))))
       :on-drag-start #(reset! drag-started? true)}]
     [:div {:style ($sw-drag-icon)}
      [:span {:style {:border-right (str "1px solid " ($/color-picker :border-color))
                      :display      "flex"
                      :height       "100%"
                      :margin-right "2px"}}]]]))

(defn title-div [title title-height on-click]
  (r/create-class
   {:component-did-mount
    (fn [this]
      (reset! title-height
              (-> this
                  (rd/dom-node)
                  (.getBoundingClientRect)
                  (aget "height"))))

    :render
    (fn [_]
      [:div {:style {:border-bottom (str "1px solid " ($/color-picker :border-color)) :width "100%"}}
       [:label {:style {:margin-left ".5rem" :margin-top ".25rem"}} title]
       [:span {:class    (<class $/p-add-hover)
               :style    ($close-button @title-height)
               :on-click on-click}
        [close]]])}))

(defn resizable-window [parent-rec init-height init-width title close-fn! render-content]
  (r/with-let [box-height   (r/atom init-height)
               box-width    (r/atom init-width)
               title-height (r/atom 0)]
    (let [p-height (aget parent-rec "height")
          p-width  (aget parent-rec "width")
          p-top    (aget parent-rec "top")]
      (when (> @box-height (/ p-height 1.5)) (reset! box-height (/ p-height 1.5)))
      (when (> @box-width  (/ p-width  1.5)) (reset! box-width  (/ p-width 1.5)))
      [:div#resizable {:style ($/combine $/tool ($resizable-window (max init-height @box-height) (max init-width @box-width)))}
       [title-div title title-height close-fn!]
       (render-content (- @box-height @title-height) @box-width @title-height)
       [drag-sw-icon p-height p-width p-top box-height box-width]])))
