(ns pyregence.components.resizable-window
  (:require
   [herb.core                            :refer [<class]]
   [pyregence.components.svg-icons       :refer [close]]
   [pyregence.styles                     :as $]
   [react                                :as react]
   [reagent.core                         :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $resizable-window [box-height box-width]
  {:height      box-height
   :overflow    "hidden"
   :right       "72px"
   :top         "1rem"
   :width       box-width})

(defn- $sw-drag []
  (merge
   ($/fixed-size "1.5rem")
   {:bottom   "0"
    :cursor   "sw-resize"
    :left     "0"
    :position "absolute"
    :z-index  "3"}))

(defn- $sw-drag-icon []
  {:border-right (str "1px solid " ($/color-picker :border-color))
   :bottom       "-.5rem"
   :height       "1rem"
   :left         "-.5rem"
   :position     "absolute"
   :transform    "rotate(-45deg)"
   :width        "1rem"
   :z-index      "2"})

(defn- $close-button [height]
  {:cursor   "pointer"
   :fill     ($/color-picker :font-color)
   :height   height
   :position "absolute"
   :right    ".25rem"
   :width    height})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- drag-sw-icon [p-height p-top box-height box-width init-height init-width]
  (let [drag-state (atom {:start-x 0 :start-y 0 :start-w 0 :start-h 0})
        handle-move (fn [e]
                      (let [{:keys [start-x start-y start-w start-h]} @drag-state
                            delta-x (- (.-clientX e) start-x)
                            delta-y (- (.-clientY e) start-y)
                            new-w   (+ start-w (- delta-x))
                            new-h   (+ start-h delta-y)
                            min-w   (/ init-width 2)
                            min-h   (/ init-height 2)]
                        (when (>= new-w min-w)
                          (reset! box-width new-w))
                        (when (and (>= new-h min-h) (<= new-h (- p-height p-top)))
                          (reset! box-height new-h))))

        handle-up (fn handle-up []
                    (js/window.removeEventListener "mousemove" handle-move)
                    (js/window.removeEventListener "mouseup" handle-up))

        handle-down (fn [e]
                      (.preventDefault e)
                      (reset! drag-state {:start-x (.-clientX e)
                                          :start-y (.-clientY e)
                                          :start-w @box-width
                                          :start-h @box-height})
                      (js/window.addEventListener "mousemove" handle-move)
                      (js/window.addEventListener "mouseup" handle-up))]
    [:div#drag-icon
     {:style ($sw-drag)
      :on-mouse-down handle-down}
     [:div {:style ($sw-drag-icon)}
      [:span {:style {:border-right (str "1px solid " ($/color-picker :border-color))
                      :display      "flex"
                      :height       "100%"
                      :margin-right "2px"}}]]]))

(defn- title-div [title title-height on-click]
  (let [ref (react/createRef)]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (reset! title-height
                (-> (.-current ref)
                    (.getBoundingClientRect)
                    (aget "height"))))
      :render
      (fn [_]
        [:div {:ref   ref
               :style {:border-bottom (str "1px solid " ($/color-picker :border-color)) :width "100%"}}
         [:label {:style {:margin-left ".5rem" :margin-top ".25rem"}} title]
         [:span {:class    (<class $/p-add-hover)
                 :style    ($close-button @title-height)
                 :on-click on-click}
          [close]]])})))

(defn resizable-window
  "A component for a resizable window. Takes in the window's parent, initial dimensions,
   close function, and content to render inside."
  [parent-rec init-height init-width title close-fn! render-content]
  (r/with-let [box-height   (r/atom init-height)
               box-width    (r/atom init-width)
               title-height (r/atom 0)]
    (let [p-height (aget parent-rec "height")
          p-top    (aget parent-rec "top")]
      [:div#resizable {:style ($/combine $/tool ($resizable-window @box-height @box-width))}
       [title-div title title-height close-fn!]
       [:div {:style {:height     (- @box-height @title-height)
                      :overflow-y "auto"
                      :position   "relative"}}
        (render-content (- @box-height @title-height) @box-width)]
       [drag-sw-icon p-height p-top box-height box-width init-height init-width]])))
