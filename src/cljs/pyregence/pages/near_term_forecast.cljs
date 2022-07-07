(ns pyregence.pages.near-term-forecast
  (:require [clojure.core.async                                  :refer [go <!]]
            [clojure.edn                                         :as edn]
            [clojure.pprint                                      :refer [pprint]]
            [clojure.spec.alpha                                  :as s]
            [herb.core                                           :refer [<class]]
            [pyregence.components.nav-bar                      :refer [nav-bar]]
            [pyregence.components.map-controls.camera-tool       :refer [camera-tool]]
            [pyregence.components.map-controls.collapsible-panel :refer [collapsible-panel]]
            [pyregence.components.map-controls.information-tool  :refer [information-tool]]
            [pyregence.components.map-controls.legend-box        :refer [legend-box]]
            [pyregence.components.map-controls.match-drop-tool   :refer [match-drop-tool]]
            [pyregence.components.map-controls.mouse-lng-lat     :refer [mouse-lng-lat]]
            [pyregence.components.map-controls.scale-bar         :refer [scale-bar]]
            [pyregence.components.map-controls.time-slider       :refer [time-slider]]
            [pyregence.components.map-controls.tool-bar          :refer [tool-bar]]
            [pyregence.components.map-controls.zoom-bar          :refer [zoom-bar]]
            [pyregence.components.mapbox                         :as mb]
            [pyregence.components.messaging                      :refer [message-box-modal toast-message!]]
            [pyregence.components.svg-icons                      :as svg]
            [pyregence.config                                    :as c]
            [pyregence.state                                     :as !]
            [pyregence.styles                                    :as $]
            [pyregence.utils.async-utils                         :as u-async]
            [pyregence.utils.browser-utils                       :as u-browser]
            [pyregence.utils.layer-utils                         :as u-layer :refer [get-any-level-key]]
            [pyregence.utils.misc-utils                          :as u-misc]
            [pyregence.utils.time-utils                          :as u-time]
            [reagent.core                                        :as r]
            [reagent.dom                                         :as rd]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::clear-point?  boolean?)
(s/def ::filter        string?)
(s/def ::filter-set    set?)
(s/def ::geoserver-key keyword?)
(s/def ::opt-label     string?)
(s/def ::units         string?)
(s/def ::z-index       int?)
(s/def ::layer-config  (s/keys :req-un [::opt-label (or ::filter ::filter-set)]
                               :opt-un [::clear-point? ::geoserver-key ::units ::z-index]))
(s/def ::layer-path    (s/and (s/coll-of keyword? :kind vector? :min-count 2)
                              (s/cat :forecast #{:fuels :fire-weather :fire-risk :active-fire :psps-zonal}
                                     :second   #(or (= % :params)
                                                    (= % :underlays))
                                     :etc      (s/+ keyword?))))
(defn clear-info! []
  (mb/clear-point!)
  (reset! !/last-clicked-info [])
  (when (u-layer/get-forecast-opt :block-info?)
    (reset! !/show-info? false)))

(defn set-show-info! [show?]
  (if (and show? (u-layer/get-forecast-opt :block-info?))
    (toast-message! "There is currently no point information available for this layer.")
    (do (reset! !/show-info? show?)
        (clear-info!))))

(defn select-time-zone! [utc?]
  (reset! !/show-utc? utc?)
  (swap! !/last-clicked-info #(mapv (fn [{:keys [js-time] :as layer}]
                                      (assoc layer
                                             :date (u-time/get-date-from-js js-time @!/show-utc?)
                                             :time (u-time/get-time-from-js js-time @!/show-utc?)))
                                    @!/last-clicked-info))
  (swap! !/processed-params  #(update-in %
                                       [:model-init :options]
                                       (fn [options]
                                         (u-misc/mapm (fn [[k {:keys [utc-time] :as v}]]
                                                   [k (assoc v
                                                             :opt-label
                                                             (u-time/time-zone-iso-date utc-time @!/show-utc?))])
                                                 options)))))

(defn- params->selected-options
  "Parses url query parameters into the selected options"
  [options-config forecast params]
  {forecast (as-> options-config oc
              (get-in oc [forecast :params])
              (keys oc)
              (select-keys params oc)
              (u-misc/mapm (fn [[k v]] [k (keyword v)]) oc))})

;;; Capabilities
(defn process-capabilities! [fire-names user-layers options-config]
  (reset! !/capabilities
          (-> (reduce (fn [acc {:keys [layer_path layer_config]}]
                        (let [layer-path   (edn/read-string layer_path)
                              layer-config (edn/read-string layer_config)]
                          (if (and (s/valid? ::layer-path   layer-path)
                                   (s/valid? ::layer-config layer-config))
                            (assoc-in acc layer-path layer-config)
                            acc)))
                      options-config
                      user-layers)
              (update-in [:active-fire :params :fire-name :options]
                         merge
                         fire-names)))
  (reset! !/*params (u-misc/mapm
                     (fn [[forecast _]]
                       (let [params           (get-in @!/capabilities [forecast :params])
                             selected-options (params->selected-options options-config @!/*forecast params)]
                         [forecast (merge (u-misc/mapm (fn [[k v]]
                                                    [k (or (get-in selected-options [forecast k])
                                                           (:default-option v)
                                                           (ffirst (:options v)))])
                                                  params))]))
                     options-config)))

(defn refresh-fire-names! [user-id]
  (go
    (as-> (u-async/call-clj-async! "get-fire-names" user-id) fire-names
      (<! fire-names)
      (:body fire-names)
      (edn/read-string fire-names)
      (swap! !/capabilities update-in [:active-fire :params :fire-name :options] merge fire-names))))

(defn- initialize! [{:keys [user-id forecast-type forecast layer-idx lat lng zoom] :as params}]
  (go
    (let [{:keys [options-config layers]} (c/get-forecast forecast-type)
          user-layers-chan                (u-async/call-clj-async! "get-user-layers" user-id)
          fire-names-chan                 (u-async/call-clj-async! "get-fire-names" user-id)
          fire-cameras                    (u-async/call-clj-async! "get-cameras")]
      (reset! !/*forecast-type forecast-type)
      (reset! !/*forecast (or (keyword forecast)
                              (keyword (forecast-type @!/default-forecasts))))
      (reset! !/*layer-idx (if layer-idx (js/parseInt layer-idx) 0))
      (mb/init-map! "map" layers (if (every? nil? [lng lat zoom]) {} {:center [lng lat] :zoom zoom}))
      (process-capabilities! (edn/read-string (:body (<! fire-names-chan)))
                             (edn/read-string (:body (<! user-layers-chan)))
                             options-config)
      (<! (u-layer/select-forecast! @!/*forecast))
      (reset! !/user-org-list (edn/read-string (:body (<! (u-async/call-clj-async! "get-org-list" user-id)))))
      (reset! !/the-cameras (edn/read-string (:body (<! fire-cameras))))
      (reset! !/loading? false))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $control-layer []
  {:height   "100%"
   :position "absolute"
   :width    "100%"})

(defn $message-modal [loading-message?]
  {:background-color "white"
   :border-radius    "3px"
   :display          "flex"
   :flex-direction   "column"
   :margin           (cond
                       (and @!/mobile? loading-message?) "10rem 4rem .5rem 4rem"
                       @!/mobile?                ".25rem"
                       :else                  "8rem auto")
   :overflow         "hidden"
   :max-height       (if @!/mobile? "calc(100% - .5rem)" "50%")
   :width            (if @!/mobile? "unset" "25rem")})

(defn $p-mb-cursor []
  (with-meta
    {}
    {:combinators {[:descendant :.mapboxgl-canvas-container] {:cursor "inherit"}}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn control-layer [user-id]
  (let [my-box (r/atom #js {})]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (let [this-node      (rd/dom-node this)
              update-my-box! (fn [& _] (reset! my-box (.getBoundingClientRect this-node)))]
          (update-my-box!)
          (if (nil? (.-ResizeObserver js/window)) ;; Handle older mobile browsers
            (.addEventListener js/document "resize" update-my-box!)
            (-> (js/ResizeObserver. update-my-box!)
                (.observe this-node)))))

      :render
      (fn []
        [:div {:style ($control-layer)}
         [collapsible-panel
          (get @!/*params @!/*forecast)
          u-layer/select-param!
          (get-any-level-key :underlays)]
         (when (aget @my-box "height")
           [:<>
            (when @!/show-info?
              [information-tool
               u-layer/get-point-info!
               @my-box
               u-layer/select-layer-by-hour!
               (u-layer/get-current-layer-key :units)
               (u-layer/get-current-layer-key :convert)
               (or (u-layer/get-current-layer-key :no-convert) #{})
               (u-layer/get-current-layer-hour)
               #(set-show-info! false)])
            (when @!/show-match-drop?
              [match-drop-tool @my-box #(reset! !/show-match-drop? false) refresh-fire-names! user-id])
            (when @!/show-camera?
              [camera-tool @my-box #(reset! !/show-camera? false)])])
         [legend-box
          (get-any-level-key :reverse-legend?)
          (get-any-level-key :time-slider?)
          (u-layer/get-current-layer-key :units)]
         [tool-bar
          set-show-info!
          get-any-level-key
          user-id]
         [scale-bar (get-any-level-key :time-slider?)]
         (when-not @!/mobile? [mouse-lng-lat])
         [zoom-bar
          (u-layer/get-current-layer-extent)
          (u-layer/current-layer)
          u-layer/create-share-link
          (get-any-level-key :time-slider?)]
         (when (get-any-level-key :time-slider?)
           [time-slider
            (u-layer/get-current-layer-full-time)
            u-layer/select-layer!
            select-time-zone!])])})))

(defn pop-up []
  [:div#pin {:style ($/fixed-size "2rem")}
   [svg/pin]])

(defn map-layer []
  (r/with-let [mouse-down? (r/atom false)
               cursor-fn   #(cond
                              @mouse-down?                           "grabbing"
                              (or @!/show-info? @!/show-match-drop?) "crosshair" ; TODO get custom cursor image from Ryan
                              :else                                  "grab")]
    [:div#map {:class (<class $p-mb-cursor)
               :style {:height "100%" :position "absolute" :width "100%" :cursor (cursor-fn)}
               :on-mouse-down #(reset! mouse-down? true)
               :on-mouse-up   #(reset! mouse-down? false)}]))

(defn message-modal []
  (r/with-let [show-me? (r/atom (not @!/dev-mode?))]
    (when @show-me?
      [:div#message-modal {:style ($/modal)}
       [:div {:style ($message-modal false)}
        [:div {:style {:background ($/color-picker :yellow)
                       :width      "100%"}}
         [:label {:style {:padding ".5rem 0 0 .5rem" :font-size "1.5rem"}}
          "Disclaimer"]]
        [:div {:style {:padding ".5rem" :overflow "auto"}}
         [:label {:style {:margin-bottom ".5rem"}}
          "This site is currently a work in progress and is in a Beta testing phase.
           It provides access to an experimental fire spread forecast tool. Use at your own risk."]
         [:label
          "Your use of this web site is undertaken at your sole risk.
           This site is available on an “as is” and “as available” basis without warranty of any kind.
           We do not warrant that this site will (i) be uninterrupted or error-free; or (ii) result in any desired outcome.
           We are not responsible for the availability or content of other services, data or public information
           that may be used by or linked to this site. To the fullest extent permitted by law, the Pyregence Consortium,
           and each and every individual, entity and collaborator therein, hereby disclaims (for itself, its affiliates,
           subcontractors, and licensors) all representations and warranties, whether express or implied, oral or written,
           with respect to this site, including without limitation, all implied warranties of title, non-infringement,
           quiet enjoyment, accuracy, integration, merchantability or fitness for any particular purpose,
           and all warranties arising from any course of dealing, course of performance, or usage of trade."]
         [:label {:style {:margin "1rem .25rem 0 0"}}
          "Please see our "
          [:a {:style {:margin-right ".25rem"}
               :href "/terms-of-use"
               :target "_blank"} "Terms of Use"]
          "and"
          [:a {:style {:margin-left ".25rem"}
               :href "/privacy-policy"
               :target "_blank"} "Privacy Policy"]
          "."]]
        [:div {:style ($/combine $/flex-row {:justify-content "center"})}
         [:span
          [:label {:class (<class $/p-form-button)
                   :style {:padding-left  "1.75rem"
                           :padding-right "1.75rem"}
                   :on-click #(u-browser/jump-to-url! "https://pyregence.org/")}
           "Decline"]
          [:label {:class (<class $/p-form-button)
                   :style {:margin        ".5rem"
                           :padding-left  "1.75rem"
                           :padding-right "1.75rem"}
                   :on-click #(reset! show-me? false)}
           "Accept"]]]]])))

(defn loading-modal []
  [:div#message-modal {:style ($/modal)}
   [:div {:style ($message-modal true)}
    [:h3 {:style {:margin-bottom "0"
                  :padding       "1rem"
                  :text-align    "center"}}
     "Loading..."]]])

(defn root-component
  "The defintion for the \"Nearterm Forecast Page\" component. Composes navbart with selectable
  clickable tabs to specify a particular forecast tab for dispaly"
  [{:keys [user-id] :as params}]
  (let [height (r/atom "100%")]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (let [update-fn (fn [& _]
                          (-> js/window (.scrollTo 0 0))
                          (reset! !/mobile? (> 800.0 (.-innerWidth js/window)))
                          (reset! height  (str (- (.-innerHeight js/window)
                                                  (-> js/document
                                                      (.getElementById "header")
                                                      .getBoundingClientRect
                                                      (aget "height")))
                                               "px"))
                          (js/setTimeout mb/resize-map! 50))]
          (pprint params)
          (-> js/window (.addEventListener "touchend" update-fn))
          (-> js/window (.addEventListener "resize"   update-fn))
          (initialize! params)
          (update-fn)))

      :reagent-render
      (fn [_]
        [:div#near-term-forecast
         {:style ($/combine $/root {:height @height :padding 0 :position "relative"})}
         [message-box-modal]
         (when @!/loading? [loading-modal])
         [message-modal]
         [nav-bar { :user-id user-id }]
         [:div {:style {:height "100%" :position "relative" :width "100%"}}
          (when (and @mb/the-map
                     (not-empty @!/capabilities)
                     (not-empty @!/*params))
            [control-layer user-id])
          [map-layer]
          [pop-up]]])})))
