(ns pyregence.components.map-controls.measure-tool
  (:require [clojure.pprint                        :refer [cl-format]]
            [herb.core                             :refer [<class]]
            [pyregence.components.mapbox           :as mb]
            [pyregence.components.resizable-window :refer [resizable-window]]
            [pyregence.geo-utils                   :as geo]
            [pyregence.styles                      :as $]
            [reagent.core                          :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- lon-lat-position [label lon-lat]
  [:div {:style {:font-weight "bold" :margin "0.5rem 0"}}
   [:div label]
   [:div#md-lonlat {:style {:display "flex"}}
    [:div#md-lon {:style {:width "45%"}}
     "Lat: " (cl-format nil "~,4f" (get lon-lat 1))]
    [:div#md-lat
     "Lon: " (cl-format nil "~,4f" (get lon-lat 0))]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn measure-tool
  "A map control tool that measures the distance between two points."
  [parent-box close-fn!]
  ; TODO: Move some of the below atoms into `state.cljs` so we can clear the values
  ; inside of near_term_forecast.cljs/clear-info! instead of reseting show-measure-tool? to false
  (r/with-let [distance-between-points (r/atom 0)
               point-one               (r/atom nil)
               point-two               (r/atom nil)
               click-event             (mb/enqueue-marker-on-click!
                                        #(do (reset! point-one (first %))
                                             (reset! point-two (second %)))
                                        {:queue-type :lifo :queue-size 2})
               format                  (fn [number label] (str (cl-format nil "~,1f" number) " " label))
               meters->miles           #(-> % (* 0.00062137) (format "miles"))
               meters->kilometers      #(-> % (* 0.001) (format "kilometers"))]
    [:div#measure-tool
     [resizable-window
      parent-box
      410
      320
      "Measure Distance Tool"
      close-fn!
      (fn [_ _]
        [:div {:style {:display "flex" :flex-direction "column" :height "inherit"}}
         [:div {:style {:flex-grow 1 :font-size "0.9rem" :margin "0.5rem 1rem"}}
          [:div {:style {:font-size "0.8rem" :margin "0.5rem 0"}}
           [:p "Measures the distance between two points using an implementation of the"
            [:a {:href "https://en.wikipedia.org/wiki/Haversine_formula" :target "_blank"} " Haversine formula."]]
           [:p "Note: There is a +/- 0.5% Great-Circle calculation error."]]
          [lon-lat-position "Point One Location" (if @point-one @point-one [0 0])]
          [lon-lat-position "Point Two Location" (if @point-two @point-two [0 0])]
          (when (> @distance-between-points 0)
            [:strong {:style {:display         "flex"
                              :font-size       "1.2rem"
                              :justify-content "center"
                              :margin          ".4rem"}}
             (str (meters->miles @distance-between-points)
                  " (" (meters->kilometers @distance-between-points) ")")])
          [:div {:style {:display        "flex"
                         :flex-direction "column"
                         :margin-top     ".2rem"}}
           [:button {:class    (<class $/p-themed-button)
                     :style    {:margin-bottom "1rem"}
                     :disabled (or (not @point-one)
                                   (not @point-two))
                     :on-click #(reset! distance-between-points (geo/distance @point-one @point-two))}
            "Distance Between Points"]
           [:button {:class    (<class $/p-themed-button)
                     :style    {:margin-bottom "1rem"}
                     :on-click (fn []
                                 (reset! point-one nil)
                                 (reset! point-two nil)
                                 (reset! distance-between-points nil)
                                 (mb/remove-markers!))}
            "Clear Markers"]]]])]]
    (finally
      (mb/remove-markers!)
      (mb/remove-event! click-event))))
