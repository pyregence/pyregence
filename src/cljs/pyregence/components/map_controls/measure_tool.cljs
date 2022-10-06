(ns pyregence.components.map-controls.measure-tool
  (:require [clojure.pprint                        :refer [cl-format]]
            [herb.core                             :refer [<class]]
            [pyregence.components.mapbox           :as mb]
            [pyregence.components.common           :refer [labeled-input]]
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
  "A map control tool that measure the distance between to points."
  [parent-box close-fn!]
  (r/with-let [point-one (r/atom [0 0])
               point-two (r/atom [0 0])
               click-event (mb/add-single-click-popup! #(reset! point-one %))
               distance-to-paris (r/atom 0.0)]
    [:div#measure-tool
     [resizable-window
      parent-box
      350
      320
      "Measure Distance Tool"
      close-fn!
      (fn [_ _]
        [:div {:style {:display "flex" :flex-direction "column" :height "inherit"}}
         [:div {:style {:flex-grow 1 :font-size "0.9rem" :margin "0.5rem 1rem"}}
          [:div {:style {:font-size "0.8rem" :margin "0.5rem 0"}}
           "Measures the distance between two points using an implementation of the haverine formula. Thanks Val."]
          [lon-lat-position $measure-tool-location "Point One Location" @point-one]
          [lon-lat-position $measure-tool-location "Point Two Location" @point-two]
          [:div {:style {:display "flex"}}
           [:div {:style {:flex "auto" :padding "0 0.5rem 0 0"}}
            "That's it for this tool"]]
          [:div {:style {:display         "flex"
                         :flex-direction  "column"
                         :margin-top      "2rem"}}
           [:div {:style {:height 30 :margin ".2rem"}}
            (when (> @distance-to-paris 0) [:label {:style ($/padding "1px" :1)} (str (cl-format nil "~,4f" @distance-to-paris) " meters")])]
           [:button {:class (<class $/p-themed-button)
                     :on-click #(reset! distance-to-paris (geo/distance California Paris))}
            "Calc Distance: LA to Paris"]]]])]]
    (finally
      (mb/remove-event! click-event))))
