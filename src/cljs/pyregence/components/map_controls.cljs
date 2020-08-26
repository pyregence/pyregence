(ns pyregence.components.map-controls
  (:require [reagent.core :as r]
            [reagent.dom  :as rd]
            [herb.core :refer [<class]]
            [clojure.edn :as edn]
            [clojure.core.async :refer [go <!]]
            [pyregence.styles :as $]
            [pyregence.utils  :as u]
            [pyregence.config :as c]
            [pyregence.components.common           :refer [radio tool-tip-wrapper]]
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

(defn tool-button [type callback]
  (if (= type :none)
    [:span {:style ($/fixed-size "32px")}]
    [:span {:class (<class $/p-button-hover)
            :style ($/combine $tool-button ($/fixed-size "32px"))
            :on-click callback}
     (case type
       :center-on-point [svg/center-on-point]
       :close           [svg/close]
       :extent          [svg/extent]
       :info            [svg/info]
       :layers          [svg/layers]
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
  {:align-items  "center"
   :display      "flex"
   :margin-right "auto"
   :margin-left  "auto"
   :left         "0"
   :right        "0"
   :padding      ".5rem"
   :bottom       "1rem"
   :width        "min-content"})

(defn time-slider [layers *layer-idx layer-full-time select-layer! show-utc? select-time-zone! mobile?]
  (r/with-let [animate?        (r/atom false)
               *speed          (r/atom 1)
               cycle-layer!    (fn [change]
                                 (select-layer! (mod (+ change @*layer-idx) (count @layers))))
               loop-animation! (fn la []
                                 (when @animate?
                                   (cycle-layer! 1)
                                   (js/setTimeout la (get-in c/speeds [@*speed :delay]))))]
    [:div#time-slider {:style ($/combine $/tool ($time-slider))}
     (when-not mobile?
       [:div {:style ($/combine $/flex-col {:align-items "flex-start"})}
        [radio "UTC"   show-utc? true  select-time-zone! true]
        [radio "Local" show-utc? false select-time-zone! true]])
     [:div {:style ($/flex-col)}
      [:input {:style {:width "12rem"}
               :type "range" :min "0" :max (dec (count @layers)) :value (or @*layer-idx 0)
               :on-change #(select-layer! (u/input-int-value %))}]
      [:label layer-full-time]]
     [:span {:style {:display "flex" :margin "0 1rem"}}
      [tool-tip-wrapper
       "Previous layer"
       :bottom
       [tool-button :previous-button #(cycle-layer! -1)]]
      [tool-tip-wrapper
       (str (if @animate? "Pause" "Play") " animation")
       :bottom
       [tool-button
        (if @animate? :pause-button :play-button)
        #(do (swap! animate? not)
             (loop-animation!))]]
      [tool-tip-wrapper
       "Next layer"
       :bottom
       [tool-button :next-button #(cycle-layer! 1)]]]
     [:select {:style ($/combine $dropdown)
               :value (or @*speed 1)
               :on-change #(reset! *speed (u/input-int-value %))}
      (map-indexed (fn [id {:keys [opt-label]}]
                     [:option {:key id :value id} opt-label])
                   c/speeds)]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Collapsible Panel
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $collapsible-panel [show? mobile?]
  {:background-color ($/color-picker :bg-color)
   :border-right     (str "1px solid " ($/color-picker :border-color))
   :box-shadow       (str "2px 0 " ($/color-picker :bg-color))
   :color            ($/color-picker :font-color)
   :height           "100%"
   :left             (if show?
                       "0"
                       (if mobile?
                         "calc(-100% + 2px)"
                         "calc(-18rem + 2px)"))
   :overflow         "auto"
   :position         "absolute"
   :transition       "all 200ms ease-in"
   :width            (if mobile? "100%" "18rem")
   :z-index          "101"})

(defn $layer-selection []
  {:border-bottom (str "2px solid " ($/color-picker :border-color))
   :font-size     "1.5rem"
   :margin-bottom ".5rem"
   :width         "100%"})

(defn panel-dropdown [title tool-tip-text val options disabled? call-back]
  [:div {:style {:display "flex" :flex-direction "column" :margin-top ".25rem"}}
   [:div {:style {:display "flex" :justify-content "space-between"}}
    [:label title]
    [tool-tip-wrapper
     tool-tip-text
     :left
     [:div {:style ($/combine ($/fixed-size "1rem")
                              {:margin "0 .25rem 4px 0"
                               :fill   ($/color-picker :font-color)})}
      [svg/help]]]]
   [:select {:style ($dropdown)
             :value (or val :none)
             :disabled disabled?
             :on-change #(call-back (u/input-keyword %))}
    (map (fn [[key {:keys [opt-label]}]]
           [:option {:key key :value key} opt-label])
         options)]])

(defn optional-layer [opt-label filter-set]
  (let [show-layer? (r/atom false)
        layer-name  (r/atom "")]
    (go
      (reset! layer-name (edn/read-string (:message (<! (u/call-clj-async! "get-layer-name"
                                                                           (pr-str filter-set)))))))
    (fn [opt-label _]
      [:div {:style {:margin-top ".5rem" :padding "0 .5rem"}}
       [:div {:style {:display "flex"}}
        [:input {:style {:margin ".25rem .5rem 0 0"}
                 :type "checkbox"
                 :on-click #(do (swap! show-layer? not)
                                (if @show-layer?
                                  (ol/create-wms-layer! @layer-name @layer-name)
                                  (ol/set-visible-by-title! @layer-name false)))}]
        [:label opt-label]]])))

(defn collapsible-panel [*params select-param! param-options mobile?]
  (let [active-opacity   (r/atom 100.0)
        show-hillshade?  (r/atom false)
        *base-map        (r/atom :mapbox-topo)
        select-base-map! (fn [id]
                           (reset! *base-map id)
                           (ol/set-base-map-source! (get-in c/base-map-options [@*base-map :source])))]
    (reset! show-panel? (not mobile?))
    (fn [*params select-param! param-options mobile?]
      ;; TODO this should not need to be called each render.
      ;;      Figwheel has components mount in a different order than normal.
      (select-base-map! @*base-map)
      [:div#collapsible-panel {:style ($collapsible-panel @show-panel? mobile?)}
       [:div {:style {:overflow "auto"}}
        [:div#layer-selection {:style {:padding "1rem"}}
         [:div {:style {:display "flex" :justify-content "space-between"}}
          [:label {:style ($layer-selection)} "Layer Selection"]
          [:span {:style {:margin-right "-.5rem"}}
           [tool-button :close #(reset! show-panel? false)]]]
         (map (fn [[key {:keys [opt-label hover-text options underlays sort?]}]]
                (let [sorted-options (if sort? (sort-by (comp :opt-label second) options) options)]
                  ^{:key hover-text}
                  [:<>
                   [panel-dropdown
                    opt-label
                    hover-text
                    (*params key)
                    sorted-options
                    (= 1 (count sorted-options))
                    #(select-param! key %)]
                   (when underlays
                     (map (fn [[key {:keys [opt-label filter-set]}]]
                            ^{:key key} [optional-layer opt-label filter-set])
                          underlays))]))
              param-options)
         [:div {:style {:margin-top ".5rem"}}
          [:label (str "Opacity: " @active-opacity)]
          [:input {:style {:width "100%"}
                   :type "range" :min "0" :max "100" :value @active-opacity
                   :on-change #(do (reset! active-opacity (u/input-int-value %))
                                   (ol/set-opacity-by-title! "active" (/ @active-opacity 100.0)))}]]
         [panel-dropdown "Base Map" "Underlying map source." @*base-map c/base-map-options false select-base-map!]
         [:div {:style {:margin-top ".5rem" :padding "0 .5rem"}}
          [:div {:style {:display "flex"}}
           [:input {:style {:margin ".25rem .5rem 0 0"}
                    :type "checkbox"
                    :on-click #(do (swap! show-hillshade? not)
                                   (ol/set-visible-by-title! "hillshade" @show-hillshade?))}]
           [:label "Hill shade overlay"]]]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Toolbars
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $tool-bar []
  {:display        "flex"
   :flex-direction "column"
   :right          "16px"})

(defn hs-str [hide?]
  (if hide? "Hide" "Show"))

(defn tool-bar [show-info? show-measure? set-show-info! mobile?]
  [:div#tool-bar {:style ($/combine $/tool $tool-bar {:top "16px"})}
   (->> [[:layers
          (str (hs-str @show-panel?) " layer selection")
          #(swap! show-panel? not)]
         (when-not mobile?
           [:info
            (str (hs-str @show-info?) " point information")
            #(do (set-show-info! (not @show-info?))
                 (reset! show-measure? false))])
         (when-not mobile?
           [:measure
            (str (hs-str @show-measure?) " measure tool")
            #(do (swap! show-measure? not)
                 (set-show-info! false))])
         [:legend
          (str (hs-str @show-legend?) " legend")
          #(swap! show-legend? not)]]
        (remove nil?)
        (map-indexed (fn [i [icon hover-text on-click]]
                       ^{:key i} [tool-tip-wrapper
                                  hover-text
                                  :right
                                  [tool-button icon on-click]])))])

(defn zoom-bar [get-current-layer-extent mobile?]
  (r/with-let [minZoom      (r/atom 0)
               maxZoom      (r/atom 28)
               *zoom        (r/atom 10)
               select-zoom! (fn [zoom]
                              (reset! *zoom (max @minZoom
                                                 (min @maxZoom
                                                      zoom)))
                              (ol/set-zoom! @*zoom))]
    (let [[cur min max] (ol/get-zoom-info)]
      (reset! *zoom cur)
      (reset! minZoom min)
      (reset! maxZoom max))
    (ol/add-map-zoom-end! #(reset! *zoom %))
    [:div#zoom-bar {:style ($/combine $/tool $tool-bar {:bottom (if mobile? "90px" "36px")})}
     (map-indexed (fn [i [icon hover-text on-click]]
                    ^{:key i} [tool-tip-wrapper
                               hover-text
                               :right
                               [tool-button icon on-click]])
                  [[:my-location
                    "Center on my location"
                    #(some-> js/navigator .-geolocation (.getCurrentPosition ol/set-center-my-location!))]
                   ;; TODO move this action to the information panel
                   ;;  [:center-on-point
                   ;;   "Center on selected point"
                   ;;   #(ol/center-on-overlay!)]
                   [:extent
                    "Zoom to fit layer"
                    #(ol/zoom-to-extent! (get-current-layer-extent))]
                   [:zoom-in
                    "Zoom in"
                    #(select-zoom! (inc @*zoom))]
                   [:zoom-out
                    "Zoom out"
                    #(select-zoom! (dec @*zoom))]])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Measure Tool
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn measure-tool [parent-box close-fn!]
  (r/with-let [lon-lat (r/atom [0 0])]
    (ol/add-map-mouse-move! #(reset! lon-lat %))
    [:div#measure-tool
     [resizable-window
      parent-box
      75
      250
      "Measure Tool"
      close-fn!
      (fn [_ _]
        [:div
         [:label {:style {:width "50%" :text-align "left" :padding "1rem"}}
          "Lat:" (u/to-precision 4 (get @lon-lat 1))]
         [:label {:style {:width "50%" :text-align "left"}}
          "Lon:" (u/to-precision 4 (get @lon-lat 0))]])]]))

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

(defn information-tool [parent-box *layer-idx select-layer! units cur-hour legend-list last-clicked-info close-fn!]
  [:div#info-tool
   [resizable-window
    parent-box
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
