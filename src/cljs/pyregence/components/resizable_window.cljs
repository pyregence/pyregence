(ns pyregence.components.resizable-window
  (:require [reagent.core     :as r]
            [pyregence.styles :as $]))

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
    :position "absolute"
    :left     "0"
    :z-index  "3"}))

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
     [:div
      {:style {:position "absolute" :bottom "-.5rem" :left "0" :font-size "1.25rem" :z-index "2"}}
      "O"]]))

(defn resizable-window [parent-rec init-height init-width render-content]
  (r/with-let [box-height (r/atom init-height)
               box-width  (r/atom init-width)]
    (let [p-height (aget parent-rec "height")
          p-width  (aget parent-rec "width")
          p-top    (aget parent-rec "top")]
      (when (> @box-height (/ p-height 1.5)) (reset! box-height (/ p-height 1.5)))
      (when (> @box-width  (/ p-width  1.5)) (reset! box-width  (/ p-width 1.5)))
      [:div#resizable {:style ($/combine $/tool ($resizable-window @box-height @box-width))}
       (render-content @box-height @box-width)
       [drag-sw-icon p-height p-width p-top box-height box-width]])))
