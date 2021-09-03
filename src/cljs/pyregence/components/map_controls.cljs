(ns pyregence.components.map-controls
  (:require [reagent.core        :as r]
            [reagent.dom         :as rd]
            [reagent.dom.server  :as rs]
            [herb.core           :refer [<class]]
            [clojure.edn         :as edn]
            [clojure.string      :as string]
            [clojure.core.async  :refer [<! go go-loop put! timeout chan]]
            [clojure.pprint      :refer [cl-format]]
            [pyregence.styles    :as $]
            [pyregence.utils     :as u]
            [pyregence.config    :as c]
            [pyregence.geo-utils :as g]
            [pyregence.components.mapbox    :as mb]
            [pyregence.components.svg-icons :as svg]
            [pyregence.components.help      :as h]
            [pyregence.components.common           :refer [labeled-input radio tool-tip-wrapper input-hour limited-date-picker]]
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
     :border-color        ($/color-picker :border-color)
     :border-radius       "2px"
     :border-size         "1px"
     :border-width        "solid"
     :color               ($/color-picker :font-color)
     :font-family         "inherit"
     :height              "1.9rem"
     :padding             ".2rem .3rem"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tool Buttons
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $tool-button []
  {:cursor  "pointer"
   :fill    ($/color-picker :font-color)
   :height  "100%"
   :padding ".25rem"
   :width   "100%"})

(defn tool-button [type callback & [active?]]
  (if (= type :none)
    [:span {:style ($/fixed-size "32px")}]
    [:span {:class    (<class $/p-add-hover active?)
            :style    ($/combine $tool-button ($/fixed-size "32px"))
            :on-click callback}
     (case type
       :binoculars      [svg/binoculars]
       :camera          [svg/camera]
       :center-on-point [svg/center-on-point]
       :close           [svg/close]
       :clock           [svg/clock]
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
   :bottom       "1rem"
   :display      "flex"
   :left         "0"
   :margin-left  "auto"
   :margin-right "auto"
   :padding      ".5rem"
   :right        "0"
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
               :type      "range"
               :min       "0"
               :max       (dec (count @layers))
               :value     (min (dec (count @layers)) (or @*layer-idx 0))
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
     [:select {:style     ($/combine $dropdown {:padding "0 0.5rem" :width "5rem"})
               :value     (or @*speed 1)
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
   [:select {:style     ($dropdown)
             :value     (or val :none)
             :disabled  disabled?
             :on-change #(call-back (u/input-keyword %))}
    (map (fn [[key {:keys [opt-label enabled?]}]]
           [:option {:key      key
                     :value    key
                     :disabled (and (fn? enabled?) (not (enabled?)))}
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
      [:input {:style     {:margin ".25rem .5rem 0 0"}
               :type      "checkbox"
               :id        id
               :checked   show?
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
      (go-loop [sorted-underlays (->> underlays
                                      (map (fn [[id v]] (assoc v :id id)))
                                      (sort-by :z-index)
                                      (reverse))]
        (let [{:keys [id filter-set]} (first sorted-underlays)
              layer-name (<! (get-layer-name filter-set identity))]
          (mb/create-wms-layer! layer-name
                                layer-name
                                (get-in *params [:underlays id :show?]))
          (when-let [tail (seq (rest sorted-underlays))]
            (recur tail)))))

    :display-name "optional-layers"

    :reagent-render
    (fn [underlays *params]
      [:<>
       (doall
        (map (fn [[key {:keys [opt-label filter-set z-index enabled?]}]]
               (let [underlays (:underlays *params)]
                 (when (or (nil? enabled?) (and (fn? enabled?) (enabled?)))
                   ^{:key key}
                   [optional-layer
                    opt-label
                    filter-set
                    (get underlays key)
                    (fn [k v] (select-param! v :underlays key k))
                    key])))
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
            [:input {:style     {:width "100%"}
                     :type      "range"
                     :min       "0"
                     :max       "100"
                     :value     @active-opacity
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

(defn $share-link [mobile?]
  (if mobile?
    {:position "absolute"
     :width    "1rem"
     :z-index  "-1"}
    {:width "100%"}))

(defn share-inner-modal [create-share-link mobile?]
  (r/with-let [copied     (r/atom false)
               share-link (create-share-link)
               on-click   #(do
                             (u/copy-input-clipboard! "share-link")
                             (reset! copied true))]
    [:div {:style (if mobile?
                    {:display         "flex"
                     :justify-content "center"}
                    ($/combine $/flex-row {:width "100%"}))}
     [:input {:id         "share-link"
              :style      ($share-link mobile?)
              :auto-focus true
              :read-only  true
              :type       "text"
              :value      share-link
              :on-click   on-click}]
     [:input {:class    (<class $/p-form-button)
              :style    (when-not mobile? {:margin-left "0.9rem"})
              :type     "button"
              :value    (if @copied "Copied!" "Copy URL")
              :on-click on-click}]]))

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
;; Fire History
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn toggle-fire-history-layer!
  "Toggles the fire history layer."
  [show-fire-history?]
  (swap! show-fire-history? not)
  (when (and @show-fire-history? (not (mb/layer-exists? "fire-history")))
    (mb/create-fire-history-layer! "fire-history" "fire-detections%3Afire-history"))
  (mb/set-visible-by-title! "fire-history" @show-fire-history?))

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

(defn tool-bar [show-info? show-match-drop? show-camera? show-red-flag? show-fire-history? set-show-info! mobile? user-id]
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
         [:flag
          (str (hs-str @show-red-flag?) " red flag warnings")
          #(toggle-red-flag-layer! show-red-flag?)]
         (when (and (c/feature-enabled? :fire-history) (not mobile?))
           [:clock
            (str (hs-str @show-fire-history?) " fire history")
            #(toggle-fire-history-layer! show-fire-history?)])
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
                                                :body  [share-inner-modal create-share-link mobile?]
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
  [job-id refresh-fire-names! user-id]
  (go
    (while @poll?
      (let [{:keys [message md-status log]} (-> (u/call-clj-async! "get-md-status" job-id)
                                                (<!)
                                                (:body)
                                                (edn/read-string))]
        (case md-status
          0 (do
              (refresh-fire-names! user-id)
              (set-message-box-content! {:body (str "Finished running match-drop-" job-id ".")})
              (reset! poll? false))

          2 (set-message-box-content! {:body message})

          (do
            (println message)
            (js/console.error log)
            (set-message-box-content! {:body (str "Error running match-drop-" job-id ".\n\n" message)})
            (reset! poll? false))))
      (<! (timeout 5000)))))

(defn- initiate-match-drop!
  "Initiates the match drop run and initiates polling for updates."
  [display-name [lon lat] md-date md-hour refresh-fire-names! user-id]
  (go
    (let [datetime   (.toString (js/Date. (+ md-date (* md-hour 3600000))))
          match-chan (u/call-clj-async! "initiate-md"
                                        {:display-name  (when-not (empty? display-name) display-name)
                                         :ignition-time (u/time-zone-iso-date datetime true)
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
              (poll-status job-id refresh-fire-names! user-id)))))))

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
  (r/with-let [display-name (r/atom "")
               lon-lat      (r/atom [0 0])
               md-date      (r/atom (u/current-date-ms)) ; Stored in milliseconds
               md-hour      (r/atom (.getHours (js/Date.))) ; hour (0-23) in the local timezone
               click-event  (mb/add-single-click-popup! #(reset! lon-lat %))]
    [:div#match-drop-tool
     [resizable-window
      parent-box
      350
      300
      "Match Drop Tool"
      close-fn!
      (fn [_ _ _]
        [:div {:style {:display "flex" :flex-direction "column" :height "inherit"}}
         [:div {:style {:flex-grow 1 :font-size "0.9rem" :margin "0.5rem 1rem"}}
          [:div {:style {:font-size "0.8rem" :margin "0.5rem 0"}}
           c/match-drop-instructions]
          [labeled-input "Name:" display-name {:placeholder "New Fire"}]
          [lon-lat-position $match-drop-location "Location" @lon-lat]
          [:div {:style {:display "flex"}}
           [:div {:style {:flex "auto" :padding "0 0.5rem 0 0"}}
            [limited-date-picker "Forecast Date:" "md-date" md-date 7 0]]
           [:div {:style {:flex "auto" :padding "0 0 0 0.5rem"}}
            [input-hour "Start Time:" "md-time" md-hour]]]
          [:div {:style {:display         "flex"
                         :flex-shrink     0
                         :justify-content "space-between"
                         :margin          "0.75rem 0 2.5rem"}}
           [:button {:class    (<class $/p-themed-button)
                     :on-click #(js/window.open "/dashboard" "/dashboard")}
            "Dashboard"]
           [:button {:class    (<class $/p-button :bg-color :yellow :font-color :orange :white)
                     :disabled (or (= [0 0] @lon-lat) (nil? @md-date) (nil? @md-hour))
                     :on-click #(initiate-match-drop! @display-name @lon-lat @md-date @md-hour refresh-fire-names! user-id)}
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

(defn- get-current-image [camera-name]
  (u/call-remote! :post-blob
                  "clj/get-current-image"
                  {:clj-args (list camera-name)}))

(defn- get-current-image-src [camera-name]
  (go
    (->> (get-current-image camera-name)
         (<!)
         (:body)
         (js/URL.createObjectURL))))

(defn- refresh-camera-image! [image-url *camera]
  (go (reset! image-url (<! (get-current-image-src (:name *camera))))))

(defn camera-tool [cameras parent-box mobile? terrain? close-fn!]
  (r/with-let [*camera     (r/atom nil)
               image-url   (r/atom nil)
               exit-ch     (chan)
               zoom-camera (fn []
                             (let [{:keys [longitude latitude tilt pan]} @*camera]
                               (reset! terrain? true)
                               (h/show-help! :terrain mobile?)
                               (mb/toggle-dimensions! true)
                               (mb/fly-to! {:center [longitude latitude]
                                            :zoom 15
                                            :bearing pan
                                            :pitch (min (+ 90 tilt) 85)}) 400))
               on-click    (fn [features]
                             (when-let [new-camera (js->clj (aget features "properties") :keywordize-keys true)]
                               (when (some? @*camera) (put! exit-ch :exit))
                               (reset! *camera new-camera)
                               (reset! image-url nil)
                               (refresh-camera-image! image-url @*camera)
                               (u/refresh-on-interval! #(refresh-camera-image! image-url @*camera) 60000 exit-ch)))
               ;; TODO, this form is sloppy.  Maybe return some value to store or convert to form 3 component.
               _           (mb/create-camera-layer! "fire-cameras" (clj->js cameras))
               _           (mb/add-feature-highlight! "fire-cameras" "fire-cameras" on-click)]
    [:div#wildfire-camera-tool
     [resizable-window
      parent-box
      290
      460
      "Wildfire Camera Tool"
      close-fn!
      (fn [_ _ _]
        (cond
          (nil? @*camera)
          [:div {:style {:padding "1.2em"}}
           "Click on a camera to view the most recent image. Powered by "
           [:a {:href   "http://www.alertwildfire.org/"
                :ref    "noreferrer noopener"
                :target "_blank"}
            "Alert Wildfire"] "."]

          (some? @image-url)
          [:div
           [:div {:style {:display         "flex"
                          :justify-content "center"
                          :position        "absolute"
                          :top             "2rem"
                          :width           "100%"}}
            [:label (str "Camera: " (:name @*camera))]]
           [:img {:src   "images/awf_logo.png"
                  :style ($/combine $awf-logo-style)}]
           [tool-tip-wrapper
            "Zoom Map to Camera"
            :right
            [:button {:class    (<class $/p-themed-button)
                      :on-click zoom-camera
                      :style    {:bottom   "1.25rem"
                                 :padding  "2px"
                                 :position "absolute"
                                 :right    "1rem"}}
             [:div {:style {:height "32px"
                            :width  "32px"}}
              [svg/binoculars]]]]
           [:img {:src   @image-url
                  :style {:height "auto" :width "100%"}}]]

          :else
          [:div {:style {:padding "1.2em"}}
           (str "Loading camera " (:name @*camera) "...")]))]]
    (finally
      (put! exit-ch :exit)
      (mb/remove-layer! "fire-cameras")
      (mb/clear-highlight! "fire-cameras" :selected))))

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

(defn- vega-information [box-height box-width *layer-idx select-layer! units cur-hour legend-list last-clicked-info]
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

(defn- single-point-info [box-height _ band legend-list units convert]
  (let [legend-map  (u/mapm (fn [li] [(js/parseFloat (get li "quantity")) li]) legend-list)
        legend-keys (sort (keys legend-map))
        color       (or (get-in legend-map [(-> band
                                                (max (first legend-keys))
                                                (min (last legend-keys)))
                                            "color"])
                        (let [[low high] (u/find-boundary-values band (sort (keys legend-map)))]
                          (when (and high low)
                            (u/interp-color (get-in legend-map [low "color"])
                                            (get-in legend-map [high "color"])
                                            (/ (- band low) (- high low))))))]
    [:div {:style {:align-items     "center"
                   :display         "flex"
                   :height          box-height
                   :justify-content "center"
                   :position        "relative"
                   :width           "100%"}}
     [:div {:style {:display "flex" :flex-direction "row"}}
      [:div {:style {:background-color color
                     :height           "1.5rem"
                     :margin-right     "0.5rem"
                     :width            "1.5rem"}}]
      [:h4 (u/end-with (or (get-in legend-map [band "label"])
                           (if (fn? convert) (convert band) band))
                       units)]]]))

(defn information-tool [get-point-info!
                        parent-box
                        *layer-idx
                        select-layer!
                        units
                        convert
                        cur-hour
                        legend-list
                        last-clicked-info
                        close-fn!]
  (r/with-let [click-event      (mb/add-single-click-popup! #(get-point-info! (mb/get-overlay-bbox)))
               init-box-height  200
               init-box-width   400]
    [:div#info-tool
     [resizable-window
      parent-box
      init-box-height
      init-box-width
      "Point Information"
      close-fn!
      (fn [box-height box-width title-height]
        (let [has-point? (mb/get-overlay-center)
              height     (max (- init-box-height title-height) box-height)
              width      (max init-box-width box-width)]
          (cond
            (not has-point?)
            [loading-cover
             height
             width
             "Click on the map to view the value(s) of particular point."]

            (nil? last-clicked-info)
            [loading-cover box-height box-width "Loading..."]

            (number? last-clicked-info)
            [single-point-info
             height
             width
             last-clicked-info
             legend-list
             units
             convert]

            (not-empty last-clicked-info)
            [vega-information
             height
             width
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
  {:left       (if show? "19rem" "1rem")
   :padding    ".25rem"
   :top        "16px"
   :transition "all 200ms ease-in"})

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
  {:border-color ($/color-picker :border-color)
   :border-style "solid"
   :border-width "0 2px 2px 2px"
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
