(ns pyregence.components.vega
  (:require
   ["vega-embed"                 :as vega-embed :refer [embed]]
   [cljs.core.async.interop      :refer-macros [<p!]]
   [clojure.core.async           :refer [go]]
   [pyregence.state              :as !]
   [pyregence.utils.data-utils   :as u-data]
   [pyregence.utils.misc-utils   :as u-misc]
   [pyregence.utils.number-utils :as u-num]
   [react                        :as react]
   [reagent.core                 :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- create-stops [processed-legend processed-point-info]
  (let [max-band (reduce (fn [acc cur] (max acc (:band cur))) 1.0 processed-point-info)]
    (reductions
     (fn [last cur] (let [last-q (get last :quantity  0.0)
                          cur-q  (get cur  "quantity" 0.0)]
                      {:quantity cur-q
                       :offset   (min (/ cur-q max-band) 1.0)
                       :color    (if (< last-q max-band cur-q)
                                   (u-misc/interp-color (get last :color)
                                                        (get cur  "color")
                                                        (/ (- max-band last-q)
                                                           (- cur-q last-q)))
                                   (get cur "color"))}))
     {:offset 0.0
      :color  (get (first processed-legend) "color")}
     (rest processed-legend))))

(defn- create-data-scale []
  (let [all-hours (mapv :hour @!/last-clicked-info)]
    {:type   "linear"
     :domain [(reduce min all-hours) (reduce max all-hours)]
     :nice   false}))

(defn- create-color-scale [processed-legend]
  {:type   "linear"
   :domain (mapv #(get % "quantity") processed-legend)
   :range  (mapv #(get % "color")    processed-legend)})

(defn- layer-line-plot [units current-hour convert]
  (let [processed-legend     (cond->> (u-data/filter-no-data @!/legend-list)
                               (fn? convert) (mapv #(update % "quantity" (comp str convert))))
        processed-point-info (cond->> (u-data/replace-no-data-nil @!/last-clicked-info @!/no-data-quantities)
                               (fn? convert) (mapv (fn [entry]
                                                     (update entry :band #(u-num/round-last-clicked-info (convert %)))))
                               :else         (mapv #(update % :band u-num/round-last-clicked-info)))
        x-axis-units         ({:near-term "Hour" :long-term "Year"} @!/*forecast-type)]
    {:width    "container"
     :height   "container"
     :autosize {:type "fit" :resize true :contains "content"}
     :data     {:values processed-point-info}
     :layer    [{:encoding {:x       {:field "hour"
                                      :type  "quantitative"
                                      :title x-axis-units
                                      :scale (create-data-scale)}
                            :y       {:field "band"
                                      :type  "quantitative"
                                      :title units}
                            :tooltip [{:field "band" :title units :type "nominal"}
                                      {:field "date" :title "Date" :type "nominal"}
                                      {:field "time" :title "Time" :type "nominal"}
                                      {:field "hour" :title x-axis-units :type "nominal"}]}
                 :layer    [{:mark {:type        "line"
                                    :interpolate "monotone"
                                    :stroke      {:x2       0
                                                  :y1       1
                                                  :gradient "linear"
                                                  :stops    (create-stops processed-legend
                                                                          processed-point-info)}}}
                            ;; This defines all of the points for selection
                            {:mark      {:type    "point"
                                         :opacity 0}
                             :selection {:point-hover {:type  "single"
                                                       :on    "mouseover"
                                                       :empty "none"}}}
                            {:transform [{:filter {:or [{:field "hour" :lt current-hour}
                                                        {:field "hour" :gt current-hour}]}}]
                             :mark      {:type   "point"
                                         :filled true}
                             :encoding  {:size  {:condition {:selection :point-hover :value 150}
                                                 :value     75}
                                         :color {:field  "band"
                                                 :type   "quantitative"
                                                 :scale  (create-color-scale processed-legend)
                                                 :legend false}}}
                            ; The black point to show the currently selected hour (point)
                            {:transform [{:filter {:field "hour" :equal current-hour}}]
                             :mark      {:type   "point"
                                         :filled false
                                         :fill   "black"
                                         :stroke "black"}
                             :encoding  {:size {:condition {:selection :point-hover :value 150}
                                                :value     75}}}]}]}))

(defn- render-vega [spec layer-click! elem]
  (when (and spec (seq (get-in spec [:data :values])))
    (go
      (try
        (let [result (<p! (embed elem
                                 (clj->js spec)
                                 (clj->js {:renderer "canvas"
                                           :mode     "vega-lite"})))]
          (-> result .-view (.addEventListener
                             "click"
                             (fn [_ data]
                               (when-let [hour (or (u-misc/try-js-aget data "datum" "datum" "hour")
                                                   (u-misc/try-js-aget data "datum" "hour"))]
                                 (layer-click! hour))))))
        (catch ExceptionInfo e (js/console.log (ex-cause e)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- vega-canvas []
  (let [ref (react/createRef)]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (let [{:keys [spec layer-click!]} (r/props this)]
          (render-vega spec layer-click! (.-current ref))))
      :component-did-update
      (fn [this _]
        (let [{:keys [spec layer-click!]} (r/props this)]
          (render-vega spec layer-click! (.-current ref))))
      :render
      (fn [this]
        [:div#vega-canvas
         {:ref   ref
          :style {:background-color "white"
                  :height           (:box-height (r/props this))
                  :padding-left     "10px"
                  :padding-bottom   "16px"
                  :padding-top      "16px"
                  :padding-right    "32px"
                  :width            (:box-width (r/props this))}}])})))

(defn vega-box
  "A function to create a Vega line plot."
  [box-height box-width layer-click! units current-hour convert]
  [vega-canvas {:spec         (layer-line-plot units current-hour convert)
                :box-height   box-height
                :box-width    box-width
                :layer-click! layer-click!}])
