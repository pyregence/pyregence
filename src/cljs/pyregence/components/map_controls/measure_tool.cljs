(ns pyregence.components.map-controls.measure-tool
  (:require [clojure.pprint                        :refer [cl-format]]
            [herb.core                             :refer [<class]]
            [pyregence.components.mapbox           :as mb :refer [remove-events!]]
            [pyregence.components.resizable-window :refer [resizable-window]]
            [pyregence.geo-utils                   :as geo :refer [California Paris]]
            [pyregence.styles                      :as $]
            [reagent.core                          :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $measure-tool-location []
  ^{:combinators {[:> :div#md-lonlat] {:display "flex" :flex-direction "row"}
                  [:> :div#md-lonlat :div#md-lon] {:width "45%"}}}
  {:font-weight "bold"
   :margin      "0.5rem 0"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- lon-lat-position [$class label lon-lat]
  [:div {:class (<class $class)}
   [:div label]
   [:div#md-lonlat
    [:div#md-lon {:style {:display         "flex"
                          :flex            1
                          :justify-content "start"}}
     "Lat: " (cl-format nil "~,4f" (get lon-lat 1))]
    [:div#md-lat {:style {:display         "flex"
                          :flex            1
                          :justify-content "start"}}
     "Lon: " (cl-format nil "~,4f" (get lon-lat 0))]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn measure-tool
  "A map control tool that measures the distance between two points."
  [parent-box close-fn!]
  (r/with-let [distance-between-points (r/atom 0)
               point-one               (r/atom nil)
               point-two               (r/atom nil)
               click-event             (mb/add-marker-on-click
                                        #(do (reset! point-one (first %))
                                             (reset! point-two (second %)))
                                        {:limit 2})]
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
            [:a {:href "https://en.wikipedia.org/wiki/Haversine_formula" :target "_blank"} " Haverine formula."]]
           [:p "Note: There is a +/- 0.5% Great-Circle calculation error."]]
          [lon-lat-position $measure-tool-location "Point One Location" (if @point-one @point-one [0 0])]
          [lon-lat-position $measure-tool-location "Point Two Location" (if @point-two @point-two [0 0])]
          [:div {:style {:display         "flex"
                         :flex-direction  "column"
                         :margin-top      ".2rem"}}
           [:div {:style {:height 30 :margin ".2rem"}}
            (when (> @distance-between-points 0) [:label {:style ($/padding "1px" :1)} (str (cl-format nil "~,4f" @distance-between-points) " meters")])]
           [:button {:class (<class $/p-themed-button)
                     :style {:margin-bottom "1rem"}
                     :on-click #(reset! distance-between-points (geo/distance @point-one @point-two))}
            "Distance Between Points"]
           [:button {:class    (<class $/p-themed-button)
                     :style {:margin-bottom "1rem"}
                     :on-click mb/remove-markers!}
            "Clear Markers"]]]])]]
    (finally
      (mb/remove-event! click-event))))
