(ns pyregence.components.map-controls.information-tool
  (:require  [clojure.set                                   :as set]
             [pyregence.components.common                   :refer [tool-tip-wrapper]]
             [pyregence.components.map-controls.tool-button :refer [tool-button]]
             [pyregence.components.mapbox                   :as mb]
             [pyregence.components.resizable-window         :refer [resizable-window]]
             [pyregence.components.vega                     :refer [vega-box]]
             [pyregence.config                              :as c]
             [pyregence.state                               :as !]
             [pyregence.utils                               :as u]
             [reagent.dom                                   :as rd]
             [reagent.core                                  :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- loading-cover [box-height box-width message]
  [:div#loading-cover {:style {:height   box-height
                               :padding  "2rem"
                               :position "absolute"
                               :width    box-width
                               :z-index  "1"}}
   [:label message]])

(defn- information-div [units info-height convert]
  (r/create-class
   {:component-did-mount
    (fn [this]
      (reset! info-height
              (-> this
                  (rd/dom-node)
                  (.getBoundingClientRect)
                  (aget "height"))))
    :render
    (fn [_]
      (let [cleaned-last-clicked-info (u/replace-no-data-nil @!/last-clicked-info
                                                             @!/no-data-quantities)
            current-point             (get cleaned-last-clicked-info @!/*layer-idx)]
        [:div {:style {:position "relative" :display "flex" :justify-content "center" :margin-top "-8px"}}
         [:label {:style {:text-align "center" :width "100%"}}
          (if-let [value (:band current-point)]
            (str (if (fn? convert)
                   (u/round-last-clicked-info (convert value))
                   (u/round-last-clicked-info value))
                 (u/clean-units units))
            "No info available for this timestep.")]
         [:div {:style {:position "absolute" :top "-5px" :right 0}}
          [tool-tip-wrapper
           "Center on selected point"
           :bottom
           [tool-button :center-on-point #(mb/center-on-overlay!)]]]]))}))

(defn- vega-information [box-height box-width select-layer! units cur-hour convert]
  (r/with-let [info-height (r/atom 0)]
    [:<>
     [vega-box
      (- box-height @info-height)
      box-width
      select-layer!
      units
      cur-hour
      convert]
     [information-div units info-height convert]]))

(defn- fbfm40-info []
  [:div {:style {:margin "0.125rem 0.75rem"}}
   [:p {:style {:margin-bottom "0.125rem"
                :text-align    "center"}}
    [:strong "Fuel Type: "]
    (get-in c/fbfm40-lookup [@!/last-clicked-info :fuel-type])]
   [:p {:style {:margin-bottom "0"}}
    [:strong "Description: "]
    (get-in c/fbfm40-lookup [@!/last-clicked-info :description])]])

(defn- single-point-info [box-height _ units convert no-convert]
  (let [legend-map  (u/mapm (fn [li] [(js/parseFloat (get li "quantity")) li]) @!/legend-list)
        legend-keys (sort (keys legend-map))
        color       (or (get-in legend-map [(-> @!/last-clicked-info
                                                (max (first legend-keys))
                                                (min (last legend-keys)))
                                            "color"])
                        (let [[low high] (u/find-boundary-values @!/last-clicked-info legend-keys)]
                          (when (and high low)
                            (u/interp-color (get-in legend-map [low "color"])
                                            (get-in legend-map [high "color"])
                                            (/ (- @!/last-clicked-info low) (- high low))))))
        *inputs     (->> @!/*params
                         (@!/*forecast)
                         (vals)
                         (into #{}))
        add-units   #(u/end-with % (u/clean-units units))
        fbfm40?     (contains? *inputs :fbfm40)
        display-val (cond
                      fbfm40? ; for all fbfm40 layers we just need a simple lookup
                      (get-in legend-map [@!/last-clicked-info "label"])

                      (and (fn? convert) (empty? (set/intersection no-convert *inputs))) ; convert the value
                      (add-units (convert @!/last-clicked-info))

                      :else ; otherwise, do not convert
                      (add-units @!/last-clicked-info))]
    [:div {:style {:align-items     "center"
                   :display         "flex"
                   :flex-direction  "column"
                   :height          box-height
                   :justify-content "space-around"
                   :position        "relative"
                   :width           "100%"}}
     [:div {:style {:display        "flex"
                    :flex-direction "row"
                    :margin-top     "0.75rem"}}
      [:div {:style {:background-color color
                     :height           "1.5rem"
                     :margin-right     "0.5rem"
                     :width            "1.5rem"}}]
      [:h4 display-val]]
     (when fbfm40?
       [fbfm40-info])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn information-tool
  "The point information tool component. Supports both single point info and
   multiple point info. The units, convert, and no-convert arguments
   are set in the :options key of a forecasts options map in config.cljs.
   For example, the :ch options key on the near term forecast fuels tab specifes
   that the units are meters, the value returned from get-point-info! should be
   converted using the provided function, and the value from get-point-info!
   should not be converted when using the :cfo Source."
  [get-point-info!
   parent-box
   select-layer!
   units
   convert
   no-convert
   cur-hour
   close-fn!]
  (r/with-let [click-event (mb/add-single-click-popup! #(get-point-info! (mb/get-overlay-bbox)))]
    [:div#info-tool
     [resizable-window
      parent-box
      290
      460
      "Point Information"
      close-fn!
      (fn [box-height box-width]
        (let [has-point?    (mb/get-overlay-center)
              single-point? (number? @!/last-clicked-info)
              no-info?      (if single-point?
                              (contains? @!/no-data-quantities (str @!/last-clicked-info))
                              (or (empty? @!/last-clicked-info)
                                  (->> @!/last-clicked-info
                                    (map (fn [entry]
                                           (contains? @!/no-data-quantities (str (:band entry)))))
                                    (every? true?))))]
          (cond
            (not has-point?)
            [loading-cover
             box-height
             box-width
             "Click on the map to view the value(s) of particular point."]

            @!/point-info-loading?
            [loading-cover
             box-height
             box-width
             "Loading..."]

            (and (nil? @!/last-clicked-info) (empty? @!/legend-list))
            [loading-cover
             box-height
             box-width
             "There was an issue getting point information for this layer."]

            (and (some? @!/last-clicked-info) (empty? @!/legend-list))
            [loading-cover
             box-height
             box-width
             "There was an issue getting the legend for this layer."]

            no-info?
            [loading-cover
             box-height
             box-width
             "This point does not have any information."]

            single-point?
            [single-point-info
             box-height
             box-width
             units
             convert
             no-convert]

            :else
            [vega-information
             box-height
             box-width
             select-layer!
             units
             cur-hour
             convert])))]]
    (finally
      (mb/remove-event! click-event))))
