(ns pyregence.components.map-controls.tool-button
  (:require [herb.core                      :refer [<class]]
            [pyregence.styles               :as $]
            [pyregence.components.svg-icons :as svg]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $tool-button []
  {:cursor  "pointer"
   :fill    ($/color-picker :font-color)
   :height  "100%"
   :padding ".25rem"
   :width   "100%"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tool-button [type callback & [active?]]
  (if (= type :none)
    [:span {:style ($/fixed-size "32px")}]
    [:span {:class    (<class $/p-add-hover active?)
            :style    ($/combine $tool-button ($/fixed-size "32px"))
            :on-click callback}
     (case type
       :admin-user      [svg/admin-user]
       :binoculars      [svg/binoculars]
       :camera          [svg/camera]
       :center-on-point [svg/center-on-point]
       :close           [svg/close]
       :clock           [svg/clock]
       :dropdown-arrow  [svg/dropdown-arrow]
       :exclamation     [svg/exclamation-point]
       :extent          [svg/extent]
       :flag            [svg/flag]
       :flame           [svg/flame]
       :help            [svg/help]
       :info            [svg/info]
       :layers          [svg/layers]
       :legend          [svg/legend]
       :magnify-zoom-in [svg/magnify-zoom-in]
       :measure-ruler   [svg/measure-ruler]
       :my-location     [svg/my-location]
       :next-button     [svg/next-button]
       :pause-button    [svg/pause-button]
       :pin             [svg/pin]
       :play-button     [svg/play-button]
       :previous-button [svg/previous-button]
       :refresh         [svg/refresh]
       :return          [svg/return]
       :right-arrow     [svg/right-arrow]
       :share           [svg/share]
       :terrain         [svg/terrain]
       :trash           [svg/trash]
       :zoom-in         [svg/zoom-in]
       :zoom-out        [svg/zoom-out]
       [:<>])]))
