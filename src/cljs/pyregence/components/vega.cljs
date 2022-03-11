(ns pyregence.components.vega
  (:require [cljsjs.vega-embed]
            [reagent.core    :as r]
            [reagent.dom     :as rd]
            [pyregence.state :as !]
            [pyregence.utils :as u]
            [clojure.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- create-stops [processed-legend]
  (let [max-band         (reduce (fn [acc cur] (max acc (:band cur))) 1.0 @!/last-clicked-info)]
    (reductions
     (fn [last cur] (let [last-q (get last :quantity  0.0)
                          cur-q  (get cur  "quantity" 0.0)]
                      {:quantity cur-q
                       :offset   (min (/ cur-q max-band) 1.0)
                       :color    (if (< last-q max-band cur-q)
                                   (u/interp-color (get last :color)
                                                   (get cur  "color")
                                                   (/ (- max-band last-q)
                                                      (- cur-q last-q)))
                                   (get cur "color"))}))
     {:offset 0.0
      :color  (get (first processed-legend) "color")}
     (rest processed-legend))))

(defn- create-scale [processed-legend]
  {:type   "linear"
   :domain (mapv #(get % "quantity") processed-legend)
   :range  (mapv #(get % "color")    processed-legend)})

(defn- layer-line-plot [units current-hour]
  (let [processed-legend (u/filter-legend-list)]
    {:width    "container"
     :height   "container"
     :autosize {:type "fit" :resize true}
     :padding  {:left "16" :top "16" :right "16" :bottom "16"}
     :data     {:values (or @!/last-clicked-info [])}
     :layer    [{:encoding {:x {:field "hour" :type "quantitative" :title "Hour"}
                            :y {:field "band" :type "quantitative" :title units}
                            :tooltip [{:field "band" :title units  :type "nominal"}
                                      {:field "date" :title "Date" :type "nominal"}
                                      {:field "time" :title "Time" :type "nominal"}]}
                 :layer [{:mark {:type        "line"
                                 :interpolate "monotone"
                                 :stroke      {:x2       0
                                               :y1       1
                                               :gradient "linear"
                                               :stops    (create-stops processed-legend)}}}
                           ;; Layer with all points for selection
                         {:mark      {:type   "point"
                                      :opacity 0}
                          :selection {:point-hover {:type  "single"
                                                    :on    "mouseover"
                                                    :empty "none"}}}
                         {:transform [{:filter {:or [{:field "hour" :lt current-hour}
                                                     {:field "hour" :gt current-hour}]}}]
                          :mark     {:type   "point"
                                     :filled true}
                          :encoding {:size {:condition {:selection :point-hover :value 150}
                                            :value     75}
                                     :color {:field  "band"
                                             :type   "quantitative"
                                             :scale  (create-scale processed-legend)
                                             :legend false}}}
                         {:transform [{:filter {:field "hour" :equal current-hour}}]
                          :mark {:type   "point"
                                 :filled false
                                 :fill   "black"
                                 :stroke "black"}
                          :encoding {:size {:condition {:selection :point-hover :value 150}
                                            :value     75}}}]}]}))

(defn- render-vega [spec layer-click! elem]
  (when (and spec (seq (get-in spec [:data :values])))
    (go
      (try
        (let [result (<p! (js/vegaEmbed elem
                                        (clj->js spec)
                                        (clj->js {:renderer "canvas"
                                                  :mode     "vega-lite"})))]
          (-> result .-view (.addEventListener
                             "click"
                             (fn [_ data]
                               (when-let [hour (or (u/try-js-aget data "datum" "datum" "hour")
                                                   (u/try-js-aget data "datum" "hour"))]
                                 (layer-click! hour))))))
        (catch ExceptionInfo e (js/console.log (ex-cause e)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- vega-canvas []
  (r/create-class
   {:component-did-mount
    (fn [this]
      (let [{:keys [spec layer-click!]} (r/props this)]
        (render-vega spec layer-click! (rd/dom-node this))))

    :component-did-update
    (fn [this _]
      (let [{:keys [spec layer-click!]} (r/props this)]
        (render-vega spec layer-click! (rd/dom-node this))))

    :render
    (fn [this]
      [:div#vega-canvas
       {:style {:height (:box-height (r/props this))
                :width  (:box-width  (r/props this))}}])}))

(defn vega-box
  "A function to create a Vega line plot."
  [box-height box-width layer-click! units current-hour]
  [vega-canvas {:spec         (layer-line-plot units current-hour)
                :box-height   box-height
                :box-width    box-width
                :layer-click! layer-click!}])
