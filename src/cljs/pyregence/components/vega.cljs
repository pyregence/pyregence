(ns pyregence.components.vega
  (:require
   ["vega-embed"                            :as vega-embed :refer [embed]]
   [cljs.core.async.interop                 :refer-macros [<p!]]
   [clojure.core.async                      :refer [go]]
   [pyregence.state                         :as !]
   [pyregence.utils.data-utils              :as u-data]
   [pyregence.utils.misc-utils              :as u-misc]
   [pyregence.utils.number-utils            :as u-num]
   [react                                   :as react]
   [reagent.core                            :as r]))

(def dataaa
  [{:band 5, :vec-id "", :js-time #inst "2025-02-12T19:00:00.000-00:00", :date "2025-02-12", :time "16:00 GMT-3", :hour 7} {:band 12.2, :vec-id "", :js-time #inst "2025-02-12T20:00:00.000-00:00", :date "2025-02-12", :time "17:00 GMT-3", :hour 8} {:band 6.2, :vec-id "", :js-time #inst "2025-02-12T21:00:00.000-00:00", :date "2025-02-12", :time "18:00 GMT-3", :hour 9} {:band 9.5, :vec-id "", :js-time #inst "2025-02-12T22:00:00.000-00:00", :date "2025-02-12", :time "19:00 GMT-3", :hour 10} {:band 4.1, :vec-id "", :js-time #inst "2025-02-12T23:00:00.000-00:00", :date "2025-02-12", :time "20:00 GMT-3", :hour 11} {:band 4.4, :vec-id "", :js-time #inst "2025-02-13T00:00:00.000-00:00", :date "2025-02-12", :time "21:00 GMT-3", :hour 12} {:band 5.4, :vec-id "", :js-time #inst "2025-02-13T01:00:00.000-00:00", :date "2025-02-12", :time "22:00 GMT-3", :hour 13} {:band 3.8, :vec-id "", :js-time #inst "2025-02-13T02:00:00.000-00:00", :date "2025-02-12", :time "23:00 GMT-3", :hour 14} {:band 1.2, :vec-id "", :js-time #inst "2025-02-13T03:00:00.000-00:00", :date "2025-02-13", :time "00:00 GMT-3", :hour 15} {:band 1, :vec-id "", :js-time #inst "2025-02-13T04:00:00.000-00:00", :date "2025-02-13", :time "01:00 GMT-3", :hour 16} {:band 1, :vec-id "", :js-time #inst "2025-02-13T05:00:00.000-00:00", :date "2025-02-13", :time "02:00 GMT-3", :hour 17} {:band 1.5, :vec-id "", :js-time #inst "2025-02-13T06:00:00.000-00:00", :date "2025-02-13", :time "03:00 GMT-3", :hour 18} {:band 1, :vec-id "", :js-time #inst "2025-02-13T07:00:00.000-00:00", :date "2025-02-13", :time "04:00 GMT-3", :hour 19} {:band 0, :vec-id "", :js-time #inst "2025-02-13T08:00:00.000-00:00", :date "2025-02-13", :time "05:00 GMT-3", :hour 20} {:band 0, :vec-id "", :js-time #inst "2025-02-13T09:00:00.000-00:00", :date "2025-02-13", :time "06:00 GMT-3", :hour 21} {:band 0, :vec-id "", :js-time #inst "2025-02-13T10:00:00.000-00:00", :date "2025-02-13", :time "07:00 GMT-3", :hour 22} {:band 0, :vec-id "", :js-time #inst "2025-02-13T11:00:00.000-00:00", :date "2025-02-13", :time "08:00 GMT-3", :hour 23} {:band 0, :vec-id "", :js-time #inst "2025-02-13T12:00:00.000-00:00", :date "2025-02-13", :time "09:00 GMT-3", :hour 24} {:band 0, :vec-id "", :js-time #inst "2025-02-13T13:00:00.000-00:00", :date "2025-02-13", :time "10:00 GMT-3", :hour 25} {:band 0, :vec-id "", :js-time #inst "2025-02-13T14:00:00.000-00:00", :date "2025-02-13", :time "11:00 GMT-3", :hour 26} {:band 0, :vec-id "", :js-time #inst "2025-02-13T15:00:00.000-00:00", :date "2025-02-13", :time "12:00 GMT-3", :hour 27} {:band 0, :vec-id "", :js-time #inst "2025-02-13T16:00:00.000-00:00", :date "2025-02-13", :time "13:00 GMT-3", :hour 28} {:band 0, :vec-id "", :js-time #inst "2025-02-13T17:00:00.000-00:00", :date "2025-02-13", :time "14:00 GMT-3", :hour 29} {:band 0, :vec-id "", :js-time #inst "2025-02-13T18:00:00.000-00:00", :date "2025-02-13", :time "15:00 GMT-3", :hour 30} {:band 1, :vec-id "", :js-time #inst "2025-02-13T19:00:00.000-00:00", :date "2025-02-13", :time "16:00 GMT-3", :hour 31} {:band 0, :vec-id "", :js-time #inst "2025-02-13T20:00:00.000-00:00", :date "2025-02-13", :time "17:00 GMT-3", :hour 32} {:band 0, :vec-id "", :js-time #inst "2025-02-13T21:00:00.000-00:00", :date "2025-02-13", :time "18:00 GMT-3", :hour 33} {:band 1, :vec-id "", :js-time #inst "2025-02-13T22:00:00.000-00:00", :date "2025-02-13", :time "19:00 GMT-3", :hour 34} {:band 0, :vec-id "", :js-time #inst "2025-02-13T23:00:00.000-00:00", :date "2025-02-13", :time "20:00 GMT-3", :hour 35} {:band 0, :vec-id "", :js-time #inst "2025-02-14T00:00:00.000-00:00", :date "2025-02-13", :time "21:00 GMT-3", :hour 36} {:band 0, :vec-id "", :js-time #inst "2025-02-14T01:00:00.000-00:00", :date "2025-02-13", :time "22:00 GMT-3", :hour 37} {:band 0, :vec-id "", :js-time #inst "2025-02-14T02:00:00.000-00:00", :date "2025-02-13", :time "23:00 GMT-3", :hour 38} {:band 0, :vec-id "", :js-time #inst "2025-02-14T03:00:00.000-00:00", :date "2025-02-14", :time "00:00 GMT-3", :hour 39} {:band 1, :vec-id "", :js-time #inst "2025-02-14T04:00:00.000-00:00", :date "2025-02-14", :time "01:00 GMT-3", :hour 40} {:band 0, :vec-id "", :js-time #inst "2025-02-14T05:00:00.000-00:00", :date "2025-02-14", :time "02:00 GMT-3", :hour 41} {:band 0, :vec-id "", :js-time #inst "2025-02-14T06:00:00.000-00:00", :date "2025-02-14", :time "03:00 GMT-3", :hour 42} {:band 0, :vec-id "", :js-time #inst "2025-02-14T07:00:00.000-00:00", :date "2025-02-14", :time "04:00 GMT-3", :hour 43} {:band 0, :vec-id "", :js-time #inst "2025-02-14T08:00:00.000-00:00", :date "2025-02-14", :time "05:00 GMT-3", :hour 44} {:band 0, :vec-id "", :js-time #inst "2025-02-14T09:00:00.000-00:00", :date "2025-02-14", :time "06:00 GMT-3", :hour 45} {:band 0, :vec-id "", :js-time #inst "2025-02-14T10:00:00.000-00:00", :date "2025-02-14", :time "07:00 GMT-3", :hour 46} {:band 1, :vec-id "", :js-time #inst "2025-02-14T11:00:00.000-00:00", :date "2025-02-14", :time "08:00 GMT-3", :hour 47} {:band 0, :vec-id "", :js-time #inst "2025-02-14T12:00:00.000-00:00", :date "2025-02-14", :time "09:00 GMT-3", :hour 48} {:band 0, :vec-id "", :js-time #inst "2025-02-14T13:00:00.000-00:00", :date "2025-02-14", :time "10:00 GMT-3", :hour 49} {:band 0, :vec-id "", :js-time #inst "2025-02-14T14:00:00.000-00:00", :date "2025-02-14", :time "11:00 GMT-3", :hour 50} {:band 0, :vec-id "", :js-time #inst "2025-02-14T15:00:00.000-00:00", :date "2025-02-14", :time "12:00 GMT-3", :hour 51} {:band 0, :vec-id "", :js-time #inst "2025-02-14T16:00:00.000-00:00", :date "2025-02-14", :time "13:00 GMT-3", :hour 52} {:band 0, :vec-id "", :js-time #inst "2025-02-14T17:00:00.000-00:00", :date "2025-02-14", :time "14:00 GMT-3", :hour 53} {:band 0, :vec-id "", :js-time #inst "2025-02-14T18:00:00.000-00:00", :date "2025-02-14", :time "15:00 GMT-3", :hour 54} {:band 0, :vec-id "", :js-time #inst "2025-02-14T19:00:00.000-00:00", :date "2025-02-14", :time "16:00 GMT-3", :hour 55} {:band 1, :vec-id "", :js-time #inst "2025-02-14T20:00:00.000-00:00", :date "2025-02-14", :time "17:00 GMT-3", :hour 56} {:band 0, :vec-id "", :js-time #inst "2025-02-14T21:00:00.000-00:00", :date "2025-02-14", :time "18:00 GMT-3", :hour 57} {:band 0, :vec-id "", :js-time #inst "2025-02-14T22:00:00.000-00:00", :date "2025-02-14", :time "19:00 GMT-3", :hour 58} {:band 1, :vec-id "", :js-time #inst "2025-02-14T23:00:00.000-00:00", :date "2025-02-14", :time "20:00 GMT-3", :hour 59} {:band 0, :vec-id "", :js-time #inst "2025-02-15T00:00:00.000-00:00", :date "2025-02-14", :time "21:00 GMT-3", :hour 60} {:band 0, :vec-id "", :js-time #inst "2025-02-15T01:00:00.000-00:00", :date "2025-02-14", :time "22:00 GMT-3", :hour 61} {:band 1, :vec-id "", :js-time #inst "2025-02-15T02:00:00.000-00:00", :date "2025-02-14", :time "23:00 GMT-3", :hour 62} {:band 1, :vec-id "", :js-time #inst "2025-02-15T03:00:00.000-00:00", :date "2025-02-15", :time "00:00 GMT-3", :hour 63} {:band 0, :vec-id "", :js-time #inst "2025-02-15T04:00:00.000-00:00", :date "2025-02-15", :time "01:00 GMT-3", :hour 64} {:band 0, :vec-id "", :js-time #inst "2025-02-15T05:00:00.000-00:00", :date "2025-02-15", :time "02:00 GMT-3", :hour 65} {:band 0, :vec-id "", :js-time #inst "2025-02-15T06:00:00.000-00:00", :date "2025-02-15", :time "03:00 GMT-3", :hour 66} {:band 0, :vec-id "", :js-time #inst "2025-02-15T07:00:00.000-00:00", :date "2025-02-15", :time "04:00 GMT-3", :hour 67} {:band 0, :vec-id "", :js-time #inst "2025-02-15T08:00:00.000-00:00", :date "2025-02-15", :time "05:00 GMT-3", :hour 68} {:band 0, :vec-id "", :js-time #inst "2025-02-15T09:00:00.000-00:00", :date "2025-02-15", :time "06:00 GMT-3", :hour 69} {:band 0, :vec-id "", :js-time #inst "2025-02-15T10:00:00.000-00:00", :date "2025-02-15", :time "07:00 GMT-3", :hour 70} {:band 0, :vec-id "", :js-time #inst "2025-02-15T11:00:00.000-00:00", :date "2025-02-15", :time "08:00 GMT-3", :hour 71} {:band 0, :vec-id "", :js-time #inst "2025-02-15T12:00:00.000-00:00", :date "2025-02-15", :time "09:00 GMT-3", :hour 72} {:band 0, :vec-id "", :js-time #inst "2025-02-15T13:00:00.000-00:00", :date "2025-02-15", :time "10:00 GMT-3", :hour 73} {:band 1, :vec-id "", :js-time #inst "2025-02-15T14:00:00.000-00:00", :date "2025-02-15", :time "11:00 GMT-3", :hour 74} {:band 3.9, :vec-id "", :js-time #inst "2025-02-15T15:00:00.000-00:00", :date "2025-02-15", :time "12:00 GMT-3", :hour 75} {:band 1.3, :vec-id "", :js-time #inst "2025-02-15T16:00:00.000-00:00", :date "2025-02-15", :time "13:00 GMT-3", :hour 76} {:band 5.8, :vec-id "", :js-time #inst "2025-02-15T17:00:00.000-00:00", :date "2025-02-15", :time "14:00 GMT-3", :hour 77} {:band 5.3, :vec-id "", :js-time #inst "2025-02-15T18:00:00.000-00:00", :date "2025-02-15", :time "15:00 GMT-3", :hour 78} {:band 7.9, :vec-id "", :js-time #inst "2025-02-15T19:00:00.000-00:00", :date "2025-02-15", :time "16:00 GMT-3", :hour 79} {:band 5.6, :vec-id "", :js-time #inst "2025-02-15T20:00:00.000-00:00", :date "2025-02-15", :time "17:00 GMT-3", :hour 80} {:band 3.4, :vec-id "", :js-time #inst "2025-02-15T21:00:00.000-00:00", :date "2025-02-15", :time "18:00 GMT-3", :hour 81} {:band 2.3, :vec-id "", :js-time #inst "2025-02-15T22:00:00.000-00:00", :date "2025-02-15", :time "19:00 GMT-3", :hour 82} {:band 1.3, :vec-id "", :js-time #inst "2025-02-15T23:00:00.000-00:00", :date "2025-02-15", :time "20:00 GMT-3", :hour 83} {:band 1, :vec-id "", :js-time #inst "2025-02-16T00:00:00.000-00:00", :date "2025-02-15", :time "21:00 GMT-3", :hour 84} {:band 0, :vec-id "", :js-time #inst "2025-02-16T01:00:00.000-00:00", :date "2025-02-15", :time "22:00 GMT-3", :hour 85} {:band 0, :vec-id "", :js-time #inst "2025-02-16T02:00:00.000-00:00", :date "2025-02-15", :time "23:00 GMT-3", :hour 86} {:band 0, :vec-id "", :js-time #inst "2025-02-16T03:00:00.000-00:00", :date "2025-02-16", :time "00:00 GMT-3", :hour 87} {:band 0, :vec-id "", :js-time #inst "2025-02-16T04:00:00.000-00:00", :date "2025-02-16", :time "01:00 GMT-3", :hour 88} {:band 0, :vec-id "", :js-time #inst "2025-02-16T05:00:00.000-00:00", :date "2025-02-16", :time "02:00 GMT-3", :hour 89} {:band 1, :vec-id "", :js-time #inst "2025-02-16T06:00:00.000-00:00", :date "2025-02-16", :time "03:00 GMT-3", :hour 90} {:band 0, :vec-id "", :js-time #inst "2025-02-16T07:00:00.000-00:00", :date "2025-02-16", :time "04:00 GMT-3", :hour 91} {:band 0, :vec-id "", :js-time #inst "2025-02-16T08:00:00.000-00:00", :date "2025-02-16", :time "05:00 GMT-3", :hour 92} {:band 0, :vec-id "", :js-time #inst "2025-02-16T09:00:00.000-00:00", :date "2025-02-16", :time "06:00 GMT-3", :hour 93} {:band 1, :vec-id "", :js-time #inst "2025-02-16T10:00:00.000-00:00", :date "2025-02-16", :time "07:00 GMT-3", :hour 94} {:band 1, :vec-id "", :js-time #inst "2025-02-16T11:00:00.000-00:00", :date "2025-02-16", :time "08:00 GMT-3", :hour 95} {:band 3.6, :vec-id "", :js-time #inst "2025-02-16T12:00:00.000-00:00", :date "2025-02-16", :time "09:00 GMT-3", :hour 96} {:band 2.5, :vec-id "", :js-time #inst "2025-02-16T13:00:00.000-00:00", :date "2025-02-16", :time "10:00 GMT-3", :hour 97} {:band 1.3, :vec-id "", :js-time #inst "2025-02-16T14:00:00.000-00:00", :date "2025-02-16", :time "11:00 GMT-3", :hour 98} {:band 5.6, :vec-id "", :js-time #inst "2025-02-16T15:00:00.000-00:00", :date "2025-02-16", :time "12:00 GMT-3", :hour 99} {:band 3.7, :vec-id "", :js-time #inst "2025-02-16T16:00:00.000-00:00", :date "2025-02-16", :time "13:00 GMT-3", :hour 100} {:band 2.8, :vec-id "", :js-time #inst "2025-02-16T17:00:00.000-00:00", :date "2025-02-16", :time "14:00 GMT-3", :hour 101} {:band 8.1, :vec-id "", :js-time #inst "2025-02-16T18:00:00.000-00:00", :date "2025-02-16", :time "15:00 GMT-3", :hour 102} {:band 5.5, :vec-id "", :js-time #inst "2025-02-16T19:00:00.000-00:00", :date "2025-02-16", :time "16:00 GMT-3", :hour 103} {:band 11.6, :vec-id "", :js-time #inst "2025-02-16T20:00:00.000-00:00", :date "2025-02-16", :time "17:00 GMT-3", :hour 104} {:band 8, :vec-id "", :js-time #inst "2025-02-16T21:00:00.000-00:00", :date "2025-02-16", :time "18:00 GMT-3", :hour 105} {:band 3, :vec-id "", :js-time #inst "2025-02-16T22:00:00.000-00:00", :date "2025-02-16", :time "19:00 GMT-3", :hour 106} {:band 1.3, :vec-id "", :js-time #inst "2025-02-16T23:00:00.000-00:00", :date "2025-02-16", :time "20:00 GMT-3", :hour 107} {:band 1, :vec-id "", :js-time #inst "2025-02-17T00:00:00.000-00:00", :date "2025-02-16", :time "21:00 GMT-3", :hour 108} {:band 1, :vec-id "", :js-time #inst "2025-02-17T01:00:00.000-00:00", :date "2025-02-16", :time "22:00 GMT-3", :hour 109} {:band 0, :vec-id "", :js-time #inst "2025-02-17T02:00:00.000-00:00", :date "2025-02-16", :time "23:00 GMT-3", :hour 110} {:band 0, :vec-id "", :js-time #inst "2025-02-17T03:00:00.000-00:00", :date "2025-02-17", :time "00:00 GMT-3", :hour 111} {:band 0, :vec-id "", :js-time #inst "2025-02-17T04:00:00.000-00:00", :date "2025-02-17", :time "01:00 GMT-3", :hour 112} {:band 0, :vec-id "", :js-time #inst "2025-02-17T05:00:00.000-00:00", :date "2025-02-17", :time "02:00 GMT-3", :hour 113} {:band 0, :vec-id "", :js-time #inst "2025-02-17T06:00:00.000-00:00", :date "2025-02-17", :time "03:00 GMT-3", :hour 114}])

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
  (let [#_#_processed-legend     (cond->> (u-data/filter-no-data @!/legend-list)
                                   (fn? convert) (mapv #(update % "quantity" (comp str convert))))
        #_#_processed-point-info dataaa
        #_#_x-axis-units         ({:near-term "Hour" :long-term "Year"} @!/*forecast-type)]
    (def x-axis-units "Hour")

    (def processed-legend [{"label" "0", "quantity" "0", "color" "#FFFFFF", "opacity" "0.4"} {"label" "1", "quantity" "1", "color" "#449900", "opacity" "1.0"} {"label" "4", "quantity" "4", "color" "#449900", "opacity" "1.0"} {"label" "16", "quantity" "16", "color" "#22FF00", "opacity" "1.0"} {"label" "64", "quantity" "64", "color" "#FFFF00", "opacity" "1.0"} {"label" "256", "quantity" "256", "color" "#FF0000", "opacity" "1.0"} {"label" "1024", "quantity" "1024", "color" "#550000", "opacity" "1.0"}])

    (def processed-point-info dataaa)
    {:usermeta {:embedOptions {:defaultStyle true
                               :actions true}}
     :width    "container"
     :height   "container"
     :autosize {:type "fit" :resize false}
     :padding  {:left "16" :top "16" :right "16" :bottom "16"}
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

(defn- hack-it! []
  (let [summary (.querySelector js/document ".vega-embed summary")]
    (set! (.-transform (.-style summary)) "translate(-20%, 20%)")
    #_(.-style detail)
    #_(.setAttribute detail "style" "transform: translate(-20%, 20%)")))

(defn- unhack-it! []
  (prn "unhack-it!:" unhack-it!)
  (let [summary (.querySelector js/document ".vega-embed summary")]
    (set! (.-transform (.-style summary)) "")
    #_(.-style detail)
    #_(.setAttribute detail "style" "transform: translate(-20%, 20%)")))

(comment (def bla
           (hack-it!)))

;; => #object[CSS2Properties [object CSS2Properties]]

(defn- render-vega [spec layer-click! elem]
  (when (and spec (seq (get-in spec [:data :values])))
    (go
      (try
        (let [result (<p! (embed elem
                                 (clj->js spec)
                                 (clj->js {:renderer "canvas"
                                           :mode     "vega-lite"})))]
          (hack-it!)
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
     {:component-will-unmount
      (fn [this]
        (unhack-it!))
      :component-did-mount
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
          :style {:height           (:box-height (r/props this))
                  :background-color "white"
                  :width            (:box-width  (r/props this))}}])})))

;; var elements = document.getElementsByClassName('hidden-class');
;; for (var i in elements) {
;;   if (elements.hasOwnProperty(i)) {
;;     elements[i].className = 'show-class';
;;   }
;; }

;; document.querySelectorAll('input[type=text]')

;; .vega-embed detail {
;; transform: translate(-50%, 25%);
;; transform: translate(-25%);
;; transform: translate(-20%, 20%);
;; how to change vega embed actions css

(defn vega-box
  "A function to create a Vega line plot."
  [box-height box-width layer-click! units current-hour convert]
  [vega-canvas {:spec         (layer-line-plot units current-hour convert)
                :box-height   box-height
                :box-width    box-width
                :layer-click! layer-click!}])
