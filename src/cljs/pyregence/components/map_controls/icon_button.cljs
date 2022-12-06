(ns pyregence.components.map-controls.icon-button
  (:require [herb.core                      :refer [<class]]
            [pyregence.styles               :as $]
            [pyregence.components.svg-icons :as svg]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $icon-button []
  {:cursor  "pointer"
   :fill    ($/color-picker :font-color)
   :height  "100%"
   :padding ".25rem"
   :width   "100%"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn icon-button
  "A component for a button with an SVG icon before the label text.
   --- Required Args ---
   btn-type:    A keyword corresponding to an SVG from svg-icons.cljs
   callback-fn: A callback function to be called on button click.
   label:       The label of the button.
   --- Optional Args ---
   disabled?: A boolean describing whether or not the button should be disabled.
   btn-size:  The size of the button. Either `:small`, `:large`, or `:circle`."
  [btn-type callback-fn label & {:keys [disabled? btn-size]}]
  [:button {:class    (<class $/p-form-button btn-size)
            :disabled disabled?
            :on-click callback-fn}
   [:span {:style {:align-items     "center"
                   :display         "flex"
                   :justify-content "center"}}
    (case btn-type
      :admin-user      [svg/admin-user :height "20px" :width "20px"]
      :binoculars      [svg/binoculars :height "20px" :width "20px"]
      :camera          [svg/camera :height "20px" :width "20px"]
      :center-on-point [svg/center-on-point :height "20px" :width "20px"]
      :close           [svg/close :height "20px" :width "20px"]
      :clock           [svg/clock :height "20px" :width "20px"]
      :dropdown-arrow  [svg/dropdown-arrow :height "20px" :width "20px"]
      :extent          [svg/extent :height "20px" :width "20px"]
      :flag            [svg/flag :height "20px" :width "20px"]
      :flame           [svg/flame :height "20px" :width "20px"]
      :help            [svg/help :height "20px" :width "20px"]
      :info            [svg/info :height "20px" :width "20px"]
      :layers          [svg/layers :height "20px" :width "20px"]
      :legend          [svg/legend :height "20px" :width "20px"]
      :magnify-zoom-in [svg/magnify-zoom-in :height "20px" :width "20px"]
      :my-location     [svg/my-location :height "20px" :width "20px"]
      :next-button     [svg/next-button :height "20px" :width "20px"]
      :pause-button    [svg/pause-button :height "20px" :width "20px"]
      :pin             [svg/pin :height "20px" :width "20px"]
      :play-button     [svg/play-button :height "20px" :width "20px"]
      :previous-button [svg/previous-button :height "20px" :width "20px"]
      :refresh         [svg/refresh :height "20px" :width "20px"]
      :return          [svg/return :height "20px" :width "20px"]
      :right-arrow     [svg/right-arrow :height "20px" :width "20px"]
      :share           [svg/share :height "20px" :width "20px"]
      :terrain         [svg/terrain :height "20px" :width "20px"]
      :trash           [svg/trash :height "20px" :width "20px"]
      :zoom-in         [svg/zoom-in :height "20px" :width "20px"]
      :zoom-out        [svg/zoom-out :height "20px" :width "20px"]
      [:<>])
    [:span {:style (when label {:padding-left ".2rem"})}
     label]]])
