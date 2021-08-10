(ns pyregence.components.map-controls
  (:require [reagent.core       :as r]
            [reagent.dom        :as rd]
            [reagent.dom.server :as rs]
            [herb.core :refer [<class]]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [clojure.core.async :refer [<! go go-loop timeout]]
            [clojure.pprint     :refer [cl-format]]
            [pyregence.styles    :as $]
            [pyregence.utils     :as u]
            [pyregence.config    :as c]
            [pyregence.geo-utils :as g]
            [pyregence.components.mapbox    :as mb]
            [pyregence.components.svg-icons :as svg]
            [pyregence.components.help      :as h]
            [pyregence.components.common           :refer [radio tool-tip-wrapper input-datetime]]
            [pyregence.components.messaging        :refer [set-message-box-content!]]
            [pyregence.components.resizable-window :refer [resizable-window]]
            [pyregence.components.vega             :refer [vega-box]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Common
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce show-panel?  (r/atom true))
(defonce show-legend? (r/atom true))

(defn $dropdown []
  (let [arrow (-> ($/color-picker :font-color)
                  (svg/dropdown-arrow)
                  (rs/render-to-string)
                  (string/replace "\"" "'"))]
    {:-moz-appearance     "none"
     :-webkit-appearance  "none"
     :appearance          "none"
     :background-color    ($/color-picker :bg-color)
     :background-image    (str "url(\"data:image/svg+xml;utf8," arrow "\")")
     :background-position "right .75rem center"
     :background-repeat   "no-repeat"
     :background-size     "1rem 0.75rem"
     :border              "1px solid"
     :border-color        ($/color-picker :border-color)
     :border-radius       "2px"
     :color               ($/color-picker :font-color)
     :font-family         "inherit"
     :height              "1.9rem"
     :padding             ".2rem .3rem"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tool Buttons
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $tool-button []
  {:cursor  "pointer"
   :height  "100%"
   :padding ".25rem"
   :width   "100%"
   :fill    ($/color-picker :font-color)})

(defn tool-button [type callback & [active?]]
  (if (= type :none)
    [:span {:style ($/fixed-size "32px")}]
    [:span {:class (<class $/p-button-hover active?)
            :style ($/combine $tool-button ($/fixed-size "32px"))
            :on-click callback}
     (case type
       :binoculars      [svg/binoculars]
       :camera          [svg/camera]
       :center-on-point [svg/center-on-point]
       :close           [svg/close]
       :extent          [svg/extent]
       :flag            [svg/flag]
       :flame           [svg/flame]
       :info            [svg/info]
       :layers          [svg/layers]
       :legend          [svg/legend]
       :magnify-zoom-in [svg/magnify-zoom-in]
       :my-location     [svg/my-location]
       :next-button     [svg/next-button]
       :pause-button    [svg/pause-button]
       :play-button     [svg/play-button]
       :previous-button [svg/previous-button]
       :share           [svg/share]
       :terrain         [svg/terrain]
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
               :type "range"
               :min "0"
               :max (dec (count @layers))
               :value (min (dec (count @layers)) (or @*layer-idx 0))
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
     [:select {:style ($/combine $dropdown {:width "5rem" :padding "0 0.5rem"})
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

(defn panel-dropdown [title tool-tip-text val options disabled? call-back & [selected-param-set]]
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
    (map (fn [[key {:keys [opt-label disabled]}]]
           [:option {:key key
                     :value key
                     :disabled (and (some? disabled)
                                    (u/intersects? disabled selected-param-set))}
            opt-label])
         options)]])

(defn get-layer-name [filter-set update-layer!]
  (go
    (let [name (edn/read-string (:body (<! (u/call-clj-async! "get-layer-name"
                                                              (pr-str filter-set)))))]
      (update-layer! :name name)
      name)))

(defn optional-layer [opt-label filter-set layer update-layer! id]
  (let [show? (:show? layer)]
    [:div {:style {:margin-top ".5rem" :padding "0 .5rem"}}
     [:div {:style {:display "flex"}}
      [:input {:style {:margin ".25rem .5rem 0 0"}
               :type "checkbox"
               :id id
               :checked show?
               :on-change (fn []
                            (go
                              (let [layer-name (or (:name layer)
                                                   (<! (get-layer-name filter-set update-layer!)))] ; Note, this redundancy is due to the way figwheel reloads.
                                (update-layer! :show? (not show?))
                                (mb/set-visible-by-title! layer-name (not show?)))))}]
      [:label {:for id} opt-label]]]))

(defn optional-layers [underlays *params select-param!]
  (r/create-class
   {:component-did-mount
    (fn []
      (let [f (fn [{:keys [filter-set]}] (get-layer-name filter-set identity))]
        (go-loop [sorted-underlays (reverse (sort-by :z-index (vals underlays)))]
          (let [tail       (rest sorted-underlays)
                layer-name (<! (f (first sorted-underlays)))]
            (mb/create-wms-layer! layer-name layer-name false)
            (when-not (empty? tail)
              (recur tail))))))

    :display-name "optional-layers"

    :reagent-render
    (fn [underlays *params]
      [:<>
       (doall
        (map (fn [[key {:keys [opt-label filter-set z-index]}]]
               (let [underlays (:underlays *params)]
                 ^{:key key}
                 [optional-layer
                  opt-label
                  filter-set
                  (get underlays key)
                  (fn [k v] (select-param! v :underlays key k))
                  key]))
             underlays))])}))

(defn collapsible-panel [*params select-param! active-opacity param-options mobile?]
  (let [*base-map        (r/atom c/base-map-default)
        select-base-map! (fn [id]
                           (reset! *base-map id)
                           (mb/set-base-map-source! (get-in (c/base-map-options) [@*base-map :source])))]
    (reset! show-panel? (not mobile?))
    (fn [*params select-param! active-opacity param-options mobile?]
      (let [selected-param-set (->> *params (vals) (filter keyword?) (set))]
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
                      (get *params key)
                      sorted-options
                      (= 1 (count sorted-options))
                      #(select-param! % key)
                      selected-param-set]
                     (when underlays
                       [optional-layers underlays *params select-param!])]))
                param-options)
           [:div {:style {:margin-top ".5rem"}}
            [:label (str "Opacity: " @active-opacity)]
            [:input {:style {:width "100%"}
                     :type "range" :min "0" :max "100" :value @active-opacity
                     :on-change #(do (reset! active-opacity (u/input-int-value %))
                                     (mb/set-opacity-by-title! "active" (/ @active-opacity 100.0)))}]]
           [panel-dropdown
            "Base Map"
            "Provided courtesy of Mapbox, we offer three map views. Select from the dropdown menu according to your preference."
            @*base-map
            (c/base-map-options)
            false
            select-base-map!]]]]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Share Tool
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn share-inner-modal [create-share-link]
  (r/with-let [copied     (r/atom false)
               share-link (create-share-link)
               on-click   #(do
                             (u/copy-input-clipboard! "share-link")
                             (reset! copied true))]
    [:div {:style ($/combine $/flex-row {:width "100%"})}
     [:input {:auto-focus true
              :on-click   on-click
              :id         "share-link"
              :style      {:width "100%"}
              :read-only  true
              :type       "text"
              :value      share-link}]
     [:input {:on-click on-click
              :style    ($/combine ($/bg-color :yellow)
                                   {:border-radius "3px"
                                    :border        "none"
                                    :color         "white"
                                    :font-size     "0.9rem"
                                    :margin-left   "1rem"
                                    :padding       "0.3rem"})
              :type     "button"
              :value    (if @copied "Copied!" "Copy URL")}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Red Flag Warning
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- add-red-flag-layer! []
  (go
    (let [data (-> (<! (u/call-clj-async! "get-red-flag-layer"))
                   (:body)
                   (js/JSON.parse))]
      (mb/create-red-flag-layer! "red-flag" data))))

(defn toggle-red-flag-layer!
  "Toggle the red-flag warning layer"
  [show-red-flag?]
  (swap! show-red-flag? not)
  (when (and @show-red-flag? (not (mb/layer-exists? "red-flag")))
    (add-red-flag-layer!))
  (mb/set-visible-by-title! "red-flag" @show-red-flag?))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Toolbars
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $tool-bar []
  {:display        "flex"
   :flex-direction "column"
   :right          "16px"})

(defn hs-str [hide?]
  (if hide? "Hide" "Show"))

(defn ed-str [enabled?]
  (if enabled? "Disable" "Enable"))

(defn tool-bar [show-info? show-match-drop? show-camera? show-red-flag? set-show-info! mobile? user-id]
  [:div#tool-bar {:style ($/combine $/tool $tool-bar {:top "16px"})}
   (->> [[:layers
          (str (hs-str @show-panel?) " layer selection")
          #(swap! show-panel? not)
          false]
         (when-not mobile?
           [:info
            (str (hs-str @show-info?) " point information")
            #(do (set-show-info! (not @show-info?))
                 (reset! show-match-drop? false)
                 (reset! show-camera? false))
            @show-info?])
         (when (and (c/feature-enabled? :match-drop) (number? user-id) (not mobile?))
           [:flame
            (str (hs-str @show-match-drop?) " match drop tool")
            #(do (swap! show-match-drop? not)
                 (set-show-info! false)
                 (reset! show-camera? false))
            @show-match-drop?])
         (when-not mobile?
           [:camera
            (str (hs-str @show-camera?) " cameras")
            #(do (swap! show-camera? not)
                 (set-show-info! false)
                 (reset! show-match-drop? false))
            @show-camera?])
         (when-not mobile?
           [:flag
            (str (hs-str @show-red-flag?) " red flag warnings")
            #(toggle-red-flag-layer! show-red-flag?)])
         [:legend
          (str (hs-str @show-legend?) " legend")
          #(swap! show-legend? not)
          false]]
        (remove nil?)
        (map-indexed (fn [i [icon hover-text on-click active?]]
                       ^{:key i} [tool-tip-wrapper
                                  hover-text
                                  :right
                                  [tool-button icon on-click active?]])))])

(defn zoom-bar [get-current-layer-extent mobile? create-share-link terrain?]
  (r/with-let [minZoom      (r/atom 0)
               maxZoom      (r/atom 28)
               *zoom        (r/atom 10)
               select-zoom! (fn [zoom]
                              (reset! *zoom (max @minZoom
                                                 (min @maxZoom
                                                      zoom)))
                              (mb/set-zoom! @*zoom))]
    (let [[cur min max] (mb/get-zoom-info)]
      (reset! *zoom cur)
      (reset! minZoom min)
      (reset! maxZoom max))
    (mb/add-map-zoom-end! #(reset! *zoom %))
    [:div#zoom-bar {:style ($/combine $/tool $tool-bar {:bottom (if mobile? "90px" "36px")})}
     (map-indexed (fn [i [icon hover-text on-click]]
                    ^{:key i} [tool-tip-wrapper
                               hover-text
                               :right
                               [tool-button icon on-click]])
                  [[:share
                    "Share current map"
                    #(set-message-box-content! {:title "Share Current Map"
                                                :body  [share-inner-modal create-share-link]
                                                :mode  :close})]
                   [:terrain
                    (str (ed-str @terrain?) " 3D terrain")
                    #(do
                       (swap! terrain? not)
                       (when @terrain? (h/show-help! :terrain mobile?))
                       (mb/toggle-dimensions! @terrain?)
                       (mb/ease-to! {:pitch (if @terrain? 45 0) :bearing 0}))]
                   [:my-location
                    "Center on my location"
                    #(some-> js/navigator .-geolocation (.getCurrentPosition mb/set-center-my-location!))]
                   ;; TODO move this action to the information panel
                   ;;  [:center-on-point
                   ;;   "Center on selected point"
                   ;;   #(mb/center-on-overlay!)]
                   [:extent
                    "Zoom to fit layer"
                    #(mb/zoom-to-extent! (get-current-layer-extent))]
                   [:zoom-in
                    "Zoom in"
                    #(select-zoom! (inc @*zoom))]
                   [:zoom-out
                    "Zoom out"
                    #(select-zoom! (dec @*zoom))]])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Match Drop Tool
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def poll? (atom false))

(defn- poll-status
  "Continually polls for updated information about the match drop run.
   Stops polling on finish or error signal."
  [job-id refresh-fire-names!]
  (go
    (while @poll?
      (let [{:keys [message md-status log]} (-> (u/call-clj-async! "get-md-status" job-id)
                                                (<!)
                                                (:body)
                                                (edn/read-string))]
        (case md-status
          0 (do
              (refresh-fire-names!)
              (set-message-box-content! {:body (str "Finished running match-drop-" job-id ".")})
              (reset! poll? false))

          2 (set-message-box-content! {:body message})

          (do
            (println message)
            (js/console.error log)
            (set-message-box-content! {:body (str "Error running match-drop-" job-id ".\n\n" message)})
            (reset! poll? false))))
      (<! (timeout 5000)))))

(defn- initiate-match-drop
  "Initiates the match drop run and initiates polling for updates."
  [[lon lat] datetime refresh-fire-names! user-id]
  (go
    (let [match-chan (u/call-clj-async! "initiate-md"
                                        {:ignition-time (u/time-zone-iso-date datetime true)
                                         :lon           lon
                                         :lat           lat
                                         :user-id       user-id})]
      (set-message-box-content! {:title  "Processing Match Drop"
                                 :body   "Initiating match drop run."
                                 :mode   :close
                                 :action #(reset! poll? false)}) ; TODO the close button is for dev, disable on final product
      (let [{:keys [error job-id]} (edn/read-string (:body (<! match-chan)))]
        (if error
          (set-message-box-content! {:body (str "Error: " error)})
          (do (reset! poll? true)
              (poll-status job-id refresh-fire-names!)))))))

;; Styles
(defn- $match-drop-location []
  ^{:combinators {[:> :div#md-lonlat] {:display "flex" :flex-direction "row"}
                  [:> :div#md-lonlat :div#md-lon] {:width "45%"}}}
  {:font-weight "bold"
   :margin      "0.5rem 0"})

(defn- $match-drop-cursor-position []
  {:display        "flex"
   :flex-direction "column"
   :font-size      "0.8rem"
   :font-weight    "bold"
   :margin         "0 1rem"})

;; Components
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

;; Root component
(defn match-drop-tool
  "Match Drop Tool view. Enables a user to start a simulated fire at a particular
   location and date/time."
  [parent-box close-fn! refresh-fire-names! user-id]
  (r/with-let [lon-lat        (r/atom [0 0])
               datetime       (r/atom "")
               click-event    (mb/add-single-click-popup! #(reset! lon-lat %))]
    [:div#match-drop-tool
     [resizable-window
      parent-box
      285
      300
      "Match Drop Tool"
      close-fn!
      (fn [_ _]
        [:div {:style {:display "flex" :flex-direction "column" :height "inherit"}}
         [:div {:style {:flex-grow 1 :margin "0.5rem 1rem" :font-size "0.9rem"}}
          [:div {:style {:font-size "0.8rem" :margin "0.5rem 0"}}
           c/match-drop-instructions]
          [lon-lat-position $match-drop-location "Location" @lon-lat]
          [input-datetime "Date/Time" "md-datetime" @datetime #(reset! datetime (u/input-value %))]
          [:div {:style {:display        "flex"
                         :flex-shrink     0
                         :justify-content "space-between"
                         :margin          "0.75rem 0 2.5rem"}}
           [:a {:class "btn btn-sm text-white"
                :style {:padding ".5rem .75rem"}
                :href  "/dashboard"
                :target "dashboard"}
            "Dashboard"]
           [:button {:class    "btn btn-sm border-yellow"
                     :style    ($/combine ($/disabled-group (or (= [0 0] @lon-lat) (= "" @datetime))) {:color "white"})
                     :on-click #(initiate-match-drop @lon-lat @datetime refresh-fire-names! user-id)}
            "Submit"]]]])]]
    (finally
      (mb/remove-event! click-event))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Wildfire Camera Tool
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $awf-logo-style []
  {:height    "auto"
   :left      "2rem"
   :min-width "100px"
   :position  "absolute"
   :top       "2rem"
   :width     "10%"})

(defn- get-current-image [camera]
  (u/call-remote! :post-blob
                  "clj/get-current-image"
                  {:clj-args (list camera)}))

(defn- get-current-image-src [camera]
  (go
    (->> (get-current-image camera)
         (<!)
         (:body)
         (js/URL.createObjectURL))))

(defn camera-tool [cameras parent-box terrain? close-fn!]
  (r/with-let [*camera     (r/atom nil)
               *image      (r/atom nil)
               zoom-camera (fn []
                             (let [{:keys [longitude latitude tilt pan]} @*camera]
                               (reset! terrain? true)
                               (mb/toggle-dimensions! true)
                               (mb/fly-to! {:center [longitude latitude]
                                            :zoom 15
                                            :bearing pan
                                            :pitch (min (+ 90 tilt) 85)}) 400))
               on-click    (fn [features]
                             (reset! *camera (js->clj (aget features "properties") :keywordize-keys true))
                             (reset! *image nil)
                             (when (some? (:name @*camera))
                               (go (reset! *image (<! (get-current-image-src (:name @*camera)))))))]
    (mb/create-camera-layer! "fire-cameras" (clj->js cameras))
    (mb/add-feature-highlight! "fire-cameras" "fire-cameras" on-click)
    [:div#wildfire-camera-tool
     [resizable-window
      parent-box
      290
      460
      "Wildfire Camera Tool"
      close-fn!
      (fn [_ _]
        (cond
          (nil? @*camera)
          [:div {:style {:padding "1.2em"}}
           "Click on a camera to view the most recent image. Powered by "
           [:a {:href "http://www.alertwildfire.org/"
                :ref "noreferrer noopener"
                :target "_blank"}
            "Alert Wildfire"] "."]

          (some? @*image)
          [:div
           [:div {:style {:position "absolute" :top "2rem" :width "100%" :display "flex" :justify-content "center"}}
            [:label (str "Camera: " (:name @*camera))]]
           [:img {:src "images/awf_logo.png" :style ($/combine $awf-logo-style)}]
           [tool-tip-wrapper
            "Zoom Map to Camera"
            :right
            [:button {:class    "btn btn-sm btn-secondary"
                      :on-click zoom-camera
                      :style    {:position "absolute"
                                 :bottom   "1.25rem"
                                 :right    "1rem"
                                 :padding  "2px"}}
             [:div {:style {:width  "32px"
                            :height "32px"
                            :fill   "white"}}
              [svg/binoculars]]]]
           [:img {:style {:width "100%" :height "auto"} :src @*image}]]

          :else
          [:div {:style {:padding "1.2em"}}
           (str "Loading camera " (:name @*camera) "...")]))]]
    (finally
      (mb/remove-layer! "fire-cameras"))))

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

(defn information-tool [get-point-info!
                        parent-box
                        *layer-idx
                        select-layer!
                        units
                        cur-hour
                        legend-list
                        last-clicked-info
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
        (let [has-point? (mb/get-overlay-center)]
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
             "This point does not have any information."])))]]
    (finally
      (mb/remove-event! click-event))))

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

(defn legend-box [legend-list reverse? mobile?]
  (reset! show-legend? (not mobile?))
  (fn [legend-list reverse? mobile?]
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
                       legend-list))]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Scale Control
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $scale-line [mobile?]
  {:background-color ($/color-picker :bg-color)
   :border           (str "1px solid " ($/color-picker :border-color))
   :bottom           (if mobile? "90px" "36px")
   :box-shadow       (str "0 0 0 2px " ($/color-picker :bg-color))
   :left             "auto"
   :right            "64px"
   :user-select      "none"})

(defn- $scale-line-inner []
  {:border       "2px solid"
   :border-color ($/color-picker :border-color)
   :border-top   "none"
   :color        ($/color-picker :border-color)
   :font-size    ".75rem"
   :font-weight  "bold"
   :margin       ".5rem"
   :text-align   "center"
   :transition   "all .25s"})

(defn scale-bar
  "Scale bar control which resizes based on map zoom/location."
  [mobile?]
  (r/with-let [max-width    100.0
               scale-params (r/atom {:distance 0 :ratio 1 :units "ft"})
               move-event   (mb/add-map-move! #(reset! scale-params (g/imperial-scale (mb/get-distance-meters))))]
    [:div {:style ($/combine $/tool ($scale-line mobile?) {:width (* (:ratio @scale-params) max-width)})}
     [:div {:style ($scale-line-inner)}
      (str (:distance @scale-params) " " (:units @scale-params))]]
    (finally
      (mb/remove-event! move-event))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Longitude/Latitude Control
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $mouse-lng-lat []
  {:background-color ($/color-picker :bg-color)
   :border           (str "1px solid " ($/color-picker :border-color))
   :bottom           "80px"
   :box-shadow       (str "0 0 0 2px " ($/color-picker :bg-color))
   :left             "auto"
   :right            "64px"})

(defn- $mouse-lng-lat-inner []
  {:font-size   "0.85rem"
   :font-weight "bold"
   :margin      "0.15rem 0.25rem 0 0.25rem"})

(defn mouse-lng-lat
  "Shows the current Longitude/latitude based on current mouse position."
  []
  (r/with-let [moving-lng-lat (r/atom [0 0])
               move-event     (mb/add-mouse-move-xy! #(reset! moving-lng-lat %))]
    [:div {:style ($/combine $/tool $mouse-lng-lat)}
     [:div#mouse-lng-lat {:style ($mouse-lng-lat-inner)}
      [:div {:style {:display         "flex"
                     :justify-content "space-between"}}
       [:span {:style {:padding-right "0.25rem"}} "Lat: "]
       [:span (cl-format nil "~,4f" (get @moving-lng-lat 1))]]
      [:div {:style {:display         "flex"
                     :justify-content "space-between"}}
       [:span {:style {:padding-right "0.25rem"}} "Lon: "]
       [:span (cl-format nil "~,4f" (get @moving-lng-lat 0))]]]]
    (finally
      (mb/remove-event! move-event))))
