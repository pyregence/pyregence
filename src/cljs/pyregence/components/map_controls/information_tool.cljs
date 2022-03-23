(ns pyregence.components.information-tool
  (:require  [reagent.core     :as r]
             [reagent.dom      :as rd]
             [pyregence.state  :as !]
             [pyregence.utils  :as u]
             [pyregence.config :as c]
             [pyregence.components.mapbox           :as mb]
             [pyregence.components.vega             :refer [vega-box]]
             [pyregence.components.resizable-window :refer [resizable-window]]))

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

(defn- information-div [units info-height]
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
      [:div {:style {:bottom "0" :position "absolute" :width "100%"}}
       [:label {:style {:margin-top ".5rem" :text-align "center" :width "100%"}}
        (str (:band (get @!/last-clicked-info @!/*layer-idx)) (u/clean-units units))]])}))

(defn- vega-information [box-height box-width select-layer! units cur-hour]
  (r/with-let [info-height (r/atom 0)]
    [:<>
     [vega-box
      (- box-height @info-height)
      box-width
      select-layer!
      units
      cur-hour]
     [information-div units info-height]]))

(defn- single-point-info [box-height _ units convert]
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
                                            (/ (- @!/last-clicked-info low) (- high low))))))]
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
      [:h4 (u/end-with (or (get-in legend-map [@!/last-clicked-info "label"])
                           (if (fn? convert) (convert @!/last-clicked-info) @!/last-clicked-info))
                       (u/clean-units units))]]
     (when (some #(= "TU1" (get % "label")) @!/legend-list)
       [:div {:style {:margin "0.125rem 0.75rem"}}
        [:p {:style {:margin-bottom "0.125rem"
                     :text-align    "center"}}
         [:strong "Fuel Type: "]
         (get-in c/fbfm40-lookup [@!/last-clicked-info :fuel-type])]
        [:p {:style {:margin-bottom "0"}}
         [:strong "Description: "]
         (get-in c/fbfm40-lookup [@!/last-clicked-info :description])]])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn information-tool [get-point-info!
                        parent-box
                        select-layer!
                        units
                        convert
                        cur-hour
                        close-fn!]
  (r/with-let [click-event (mb/add-single-click-popup! #(get-point-info! (mb/get-overlay-bbox)))]
    [:div#info-tool
     [resizable-window
      parent-box
      200
      400
      "Point Information"
      close-fn!
      (fn [box-height box-width]
        (let [has-point?   (mb/get-overlay-center)
              loading-info [loading-cover
                            box-height
                            box-width
                            "Loading..."]]
          (cond
            (not has-point?)
            [loading-cover
             box-height
             box-width
             "Click on the map to view the value(s) of particular point."]

            @!/point-info-loading?
            loading-info

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

            (and (number? @!/last-clicked-info)
                 (>= @!/last-clicked-info -50))
            [single-point-info
             box-height
             box-width
             units
             convert]

            (or (< @!/last-clicked-info -50)
                (-> @!/last-clicked-info (first) (:band) (< -50)))
            [loading-cover
             box-height
             box-width
             "This point does not have any information."]

            (and (not-empty @!/last-clicked-info) (not-empty @!/legend-list))
            [vega-information
             box-height
             box-width
             select-layer!
             units
             cur-hour]

            :else loading-info)))]]
    (finally
      (mb/remove-event! click-event))))
