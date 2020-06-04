(ns pyregence.components.map-controls
  (:require [reagent.core :as r]
            [reagent.dom  :as rd]
            [herb.core :refer [<class]]
            [pyregence.styles :as $]
            [pyregence.utils  :as u]
            [pyregence.config :as c]
            [pyregence.components.common           :refer [radio]]
            [pyregence.components.openlayers       :as ol]
            [pyregence.components.resizable-window :refer [resizable-window]]
            [pyregence.components.svg-icons        :as svg]
            [pyregence.components.vega             :refer [vega-box]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Common
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce show-panel?  (r/atom true))
(defonce show-legend? (r/atom true))

(defn $dropdown []
  {:background-color ($/color-picker :bg-color)
   :border           "1px solid"
   :border-color     ($/color-picker :border-color)
   :border-radius    "2px"
   :color            ($/color-picker :font-color)
   :font-family      "inherit"
   :height           "1.9rem"
   :padding          ".2rem .3rem"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tool Buttons
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $tool-button []
  {:cursor  "pointer"
   :height  "100%"
   :padding ".25rem"
   :width   "100%"
   :fill    ($/color-picker :font-color)})

(defn tool-button [type tooltip callback]
  (if (= type :none)
    [:span {:style ($/fixed-size "32px")}]
    [:span {:class (<class $/p-button-hover)
            :style ($/combine $tool-button ($/fixed-size "32px"))
            :title tooltip
            :on-click callback}
     (case type
       :layers          [svg/layers]
       :center-on-point [svg/center-on-point]
       :extent          [svg/extent]
       :info            [svg/info]
       :legend          [svg/legend]
       :measure         [svg/measure]
       :my-location     [svg/my-location]
       :next-button     [svg/next-button]
       :pause-button    [svg/pause-button]
       :play-button     [svg/play-button]
       :previous-button [svg/previous-button]
       :zoom-in         [svg/zoom-in]
       :zoom-out        [svg/zoom-out]
       [:<>])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Time Slider
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $time-slider []
  {:align-items      "center"
   :display          "flex"
   :margin-right     "auto"
   :margin-left      "auto"
   :left             "0"
   :right            "0"
   :padding          ".5rem"
   :bottom           "1rem"
   :width            "fit-content"})

(defn time-slider [filtered-layers
                   *layer-idx
                   get-current-layer-full-time
                   select-layer!
                   cycle-layer!
                   show-utc?
                   select-time-zone!
                   animate?
                   loop-animation!
                   *speed]
  [:div#time-slider {:style ($/combine $/tool ($time-slider))}
   [:div {:style ($/combine $/flex-col {:align-items "flex-start"})}
    [radio "UTC"   show-utc? true  select-time-zone! true]
    [radio "Local" show-utc? false select-time-zone! true]]
   [:div {:style ($/flex-col)}
    [:input {:style {:width "12rem"}
             :type "range" :min "0" :max (dec (count (filtered-layers))) :value *layer-idx
             :on-change #(select-layer! (u/input-int-value %))}]
    [:label {:style {:font-size ".75rem"}}
     (get-current-layer-full-time)]]
   [:span {:style {:margin "0 1rem"}}
    [tool-button :previous-button "Previous layer" #(cycle-layer! -1)]
    [tool-button
     (if @animate? :pause-button :play-button)
     (str (if @animate? "Pause" "Play") " animation")
     #(do (swap! animate? not)
          (loop-animation!))]
    [tool-button :next-button "Next layer" #(cycle-layer! 1)]]
   [:select {:style ($/combine $dropdown)
             :value (or @*speed 1)
             :on-change #(reset! *speed (u/input-int-value %))}
    (map-indexed (fn [id {:keys [opt-label]}]
                   [:option {:key id :value id} opt-label])
                 c/speeds)]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Collapsible Panel
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $collapsible-panel [show?]
  {:background-color ($/color-picker :bg-color)
   :border-right     (str "1px solid " ($/color-picker :border-color))
   :box-shadow       (str "2px 0 " ($/color-picker :bg-color))
   :color            ($/color-picker :font-color)
   :height           "100%"
   :left             (if show? "0" "-18rem")
   :overflow         "auto"
   :position         "absolute"
   :transition       "all 200ms ease-in"
   :width            "18rem"
   :z-index          "101"})

(defn $layer-section []
  {:border        (str "1px solid " ($/color-picker :border-color))
   :border-radius "3px"
   :margin        ".75rem"
   :padding       ".75rem"})

(defn panel-dropdown [title val options call-back]
  [:div {:style {:display "flex" :flex-direction "column" :margin-top ".25rem"}}
   [:label title]
   [:select {:style ($dropdown)
             :value (or val -1)
             :on-change #(call-back (u/input-int-value %))}
    (map-indexed (fn [i {:keys [opt-label]}]
                   [:option {:key i :value i} opt-label])
                 options)]])

(defn collapsible-panel [*base-map
                         select-base-map!
                         *params
                         select-param!
                         param-options]
  (r/with-let [active-opacity    (r/atom 70.0)
               hillshade-opacity (r/atom 50.0)
               show-hillshade?   (r/atom false)]
    [:div#collapsible-panel {:style ($collapsible-panel @show-panel?)}
     [:div {:style {:overflow "auto"}}
      [:div#baselayer {:style ($layer-section)}
       [:label {:style {:font-size "1.25rem"}} "Base Layer"]
       [panel-dropdown "Map" *base-map c/base-map-options select-base-map!]
       [:div {:style {:margin-top ".5rem" :padding "0 .5rem"}}
        [:div {:style {:display "flex"}}
         [:input {:style {:margin ".25rem .5rem 0 0"}
                  :type "checkbox"
                  :on-click #(do (swap! show-hillshade? not)
                                 (ol/set-visible-by-title! "hillshade" @show-hillshade?))}]
         [:label "Hill shade overlay"]]
        (when @show-hillshade?
          [:<> [:label (str "Opacity: " @hillshade-opacity)]
           [:input {:style {:width "100%"}
                    :type "range" :min "0" :max "100" :value @hillshade-opacity
                    :on-change #(do (reset! hillshade-opacity (u/input-int-value %))
                                    (ol/set-opacity-by-title! "hillshade" (/ @hillshade-opacity 100.0)))}]])]]
      [:div#activelayer {:style ($/combine ($layer-section) {:margin-top "1rem"})}
       [:label {:style {:font-size "1.25rem"}} "Fire Layer"]
       (map-indexed (fn [i {:keys [opt-label options filter-on filter-key]}]
                      (let [filter-set       (get-in param-options [filter-on :options (get *params filter-on) filter-key])
                            filtered-options (if filter-set
                                               (filterv #(filter-set (:filter %)) options)
                                               options)]
                        ^{:key i} [panel-dropdown opt-label (get *params i) filtered-options #(select-param! i %)]))
                    param-options)
       [:div {:style {:margin-top ".5rem"}}
        [:label (str "Opacity: " @active-opacity)]
        [:input {:style {:width "100%"}
                 :type "range" :min "0" :max "100" :value @active-opacity
                 :on-change #(do (reset! active-opacity (u/input-int-value %))
                                 (ol/set-opacity-by-title! "active" (/ @active-opacity 100.0)))}]]]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Toolbars
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $tool-bar []
  {:display        "flex"
   :flex-direction "column"
   :right          "16px"
   :transition     "all 200ms ease-in"})

(defn hs-str [hide?]
  (if hide? "Hide" "Show"))

(defn tool-bar [show-info? show-measure? set-show-info!]
  [:div#tool-bar {:style ($/combine $/tool $tool-bar {:top "16px"})}
   (map-indexed (fn [i [icon title on-click]]
                  ^{:key i} [tool-button icon title on-click])
                [[:layers
                  (str (hs-str @show-panel?) " layer selection")
                  #(swap! show-panel? not)]
                 [:info
                  (str (hs-str @show-info?) " point information")
                  #(do (set-show-info! (not @show-info?))
                       (reset! show-measure? false))]
                 [:measure
                  (str (hs-str @show-measure?) " measure tool")
                  #(do (swap! show-measure? not)
                       (set-show-info! false))]
                 [:legend
                  (str (hs-str @show-legend?) " legend")
                  #(swap! show-legend? not)]])])

(defn zoom-bar [*zoom select-zoom! get-current-layer-extent]
  [:div#zoom-bar {:style ($/combine $/tool $tool-bar {:bottom "36px"})}
   (map-indexed (fn [i [icon title on-click]]
                  ^{:key i} [tool-button icon title on-click])
                [[:my-location
                  "Center on my location"
                  #(some-> js/navigator .-geolocation (.getCurrentPosition ol/set-center-my-location!))]
                 [:center-on-point
                  "Center on selected point"
                  #(ol/center-on-overlay!)] ; TODO move this action the the information panel
                 [:extent
                  "Zoom to fit layer"
                  #(ol/zoom-to-extent! (get-current-layer-extent))]
                 [:zoom-in
                  "Zoom in"
                  #(select-zoom! (inc *zoom))]
                 [:zoom-out
                  "Zoom out"
                  #(select-zoom! (dec *zoom))]])])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Measure Tool
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn measure-tool [my-box lon-lat close-fn!]
  [:div#measure-tool
   [resizable-window
    my-box
    75
    250
    "Measure Tool"
    close-fn!
    (fn [_ _]
      [:div
       [:label {:style {:width "50%" :text-align "left" :padding "1rem"}}
        "Lat:" (u/to-precision 4 (get lon-lat 1))]
       [:label {:style {:width "50%" :text-align "left"}}
        "Lon:" (u/to-precision 4 (get lon-lat 0))]])]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Information Tool
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn loading-cover [box-height box-width message]
  [:div#loading-cover {:style {:height   box-height
                               :padding  "2rem"
                               :position "absolute"
                               :width    box-width
                               :z-index  "1"}}
   [:label message]])

(defn information-div [last-clicked-info *layer-idx units info-height]
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
        (str (:band (get last-clicked-info @*layer-idx))
             " "
             units)]])}))

(defn vega-information [box-height box-width *layer-idx select-layer! units cur-hour legend-list last-clicked-info]
  (r/with-let [info-height (r/atom 0)]
    [:<>
     [vega-box
      (- box-height @info-height)
      box-width
      select-layer!
      units
      cur-hour
      legend-list
      last-clicked-info]
     [information-div last-clicked-info *layer-idx units info-height]]))

(defn information-tool [my-box *layer-idx select-layer! units cur-hour legend-list last-clicked-info close-fn!]
  [:div#info-tool
   [resizable-window
    my-box
    200
    400
    "Point Information"
    close-fn!
    (fn [box-height box-width]
      (let [has-point? (ol/get-overlay-center)]
        (cond
          (not has-point?)
          [loading-cover
           box-height
           box-width
           "Click on the map to see a change over time graph."]

          (nil? last-clicked-info)
          [loading-cover box-height box-width "Loading..."]

          (seq last-clicked-info)
          [vega-information
           box-height
           box-width
           *layer-idx
           select-layer!
           units
           cur-hour
           legend-list
           last-clicked-info]

          :else
          [loading-cover
           box-height
           box-width
           "This point does not have any information."])))]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Legend Box
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $legend-color [color]
  {:background-color color
   :height           "1rem"
   :margin-right     ".5rem"
   :width            "1rem"})

(defn $legend-location [show?]
  {:top        "16px"
   :left       (if show? "19rem" "1rem")
   :transition "all 200ms ease-in"
   :padding    ".25rem"})

(defn legend-box [legend-list reverse?]
  (when (and @show-legend? (seq legend-list))
    [:div#legend-box {:style ($/combine $/tool ($legend-location @show-panel?))}
     [:div {:style {:display "flex" :flex-direction "column"}}
      (map-indexed (fn [i leg]
                     ^{:key i}
                     [:div {:style ($/combine {:display "flex" :justify-content "flex-start"})}
                      [:div {:style ($legend-color (get leg "color"))}]
                      [:label (get leg "label")]])
                   (if reverse?
                     (reverse legend-list)
                     legend-list))]]))
