(ns pyregence.components.map-controls.measure-tool
  (:require [clojure.pprint                        :refer [cl-format]]
            [herb.core                             :refer [<class]]
            [pyregence.components.mapbox           :as mb]
            [pyregence.components.resizable-window :refer [resizable-window]]
            [pyregence.components.common           :refer [radio]]
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
               miles                   (r/atom true)
               click-event             (mb/enqueue-marker-on-click!
                                        #(do (reset! point-one (first %))
                                             (reset! point-two (second %)))
                                        {:queue-type :lifo :queue-size 2})]
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
          [:div {:style {:display         "flex"
                         :flex-direction  "column"
                         :margin-top      ".2rem"}}
           (when (> @distance-between-points 0)
             [:label {:style ($/padding "1px" :1)}

               ;; -- show both option
              (let [format #(str (cl-format nil "~,1f" %1) " " %2)
                    m->mi #(* 0.00062137 %)
                    m->km #(/ % 1000)
                    meters @distance-between-points]
                [:p
                 [:span (str (format (m->mi meters) "miles"))]
                 [:span (str " / " (format  (m->km meters) "kilometers"))]])
               ;; -- Radio button option
               ;; this is what was orginal discussed.
               ;;TODO css classes md-lonlat and md-lon are from the long-lat functionality and
               ;; though they stylistically in lining things up, the names are confusing here. so
               ;; consider changing something.
              #_[:div#md-lonlat {:style {:display "flex"
                                         :column-gap "10px"}}
                 [:div#md-lon {:style {:display "flex"
                                       :align-items "center"}}
                  (let [format-distance (fn [label distance]
                                          (str label ": "
                                               (cl-format nil
                                                          "~,1f"
                                                          distance)))]
                    (if @miles
                      (format-distance "Miles" (* 0.00062137 @distance-between-points))
                      (format-distance "Kilometers" (/ @distance-between-points 1000))))]
                 [:div {:style {:display "flex"
                                :flex-direction "column"}}
                  [:<>
                   [radio "mi" @miles true #(reset! miles true)]
                   [radio "km" @miles false #(reset! miles false)]]]]])

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
