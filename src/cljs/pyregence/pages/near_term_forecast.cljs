(ns pyregence.pages.near-term-forecast
  (:require [reagent.core :as r]
            [reagent.dom :as rd]
            [herb.core :refer [<class]]
            [cognitect.transit :as t]
            [clojure.edn        :as edn]
            [clojure.spec.alpha :as s]
            [clojure.string     :as str]
            [clojure.core.async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [pyregence.styles :as $]
            [pyregence.utils  :as u]
            [pyregence.config :as c]
            [pyregence.components.fire-popup   :as fp]
            [pyregence.components.map-controls :as mc]
            [pyregence.components.mapbox       :as mb]
            [pyregence.components.common    :refer [radio tool-tip-wrapper]]
            [pyregence.components.messaging :refer [message-box-modal
                                                    toast-message
                                                    toast-message!
                                                    process-toast-messages!]]
            [pyregence.components.svg-icons :refer [pin]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Spec
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def ::opt-label    string?)
(s/def ::filter       string?)
(s/def ::units        string?)
(s/def ::layer-config (s/keys :req-un [::opt-label ::filter] :opt-un [::units ::clear-point?]))
(s/def ::layer-path   (s/and (s/coll-of keyword? :kind vector? :min-count 3)
                             (s/cat :forecast #{:fire-risk :active-fire :fire-weather}
                                    :params   #(= % :params)
                                    :etc      (s/+ keyword?))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce options            (r/atom {}))
(defonce mobile?            (r/atom false))
(defonce legend-list        (r/atom []))
(defonce last-clicked-info  (r/atom []))
(defonce show-utc?          (r/atom true))
(defonce show-info?         (r/atom false))
(defonce show-match-drop?   (r/atom false))
(defonce show-camera?       (r/atom false))
(defonce show-red-flag?     (r/atom false))
(defonce show-fire-history? (r/atom false))
(defonce active-opacity     (r/atom 100.0))
(defonce capabilities       (r/atom []))
(defonce *forecast          (r/atom nil))
(defonce processed-params   (r/atom []))
(defonce *params            (r/atom {}))
(defonce param-layers       (r/atom []))
(defonce *layer-idx         (r/atom 0))
(defonce the-cameras        (r/atom nil))
(defonce loading?           (r/atom true))
(defonce terrain?           (r/atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Data Processing Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-forecast-opt [key-name]
  (get-in @capabilities [@*forecast key-name]))

(defn process-model-times! [model-times]
  (let [processed-times (into (u/reverse-sorted-map)
                              (map (fn [utc-time]
                                     [(keyword utc-time)
                                      {:opt-label (u/time-zone-iso-date utc-time @show-utc?)
                                       :utc-time  utc-time ; TODO is utc-time redundant?
                                       :filter    utc-time}])
                                   model-times))]
    (reset! processed-params
            (assoc-in (get-forecast-opt :params)
                      [:model-init :options]
                      processed-times))
    (swap! *params assoc-in [@*forecast :model-init] (ffirst processed-times))))

(defn get-layers! [get-model-times?]
  (go
    (let [params       (dissoc (get @*params @*forecast) (when get-model-times? :model-init))
          selected-set (or (some (fn [[key {:keys [options]}]]
                                   (get-in options [(params key) :filter-set]))
                                 @processed-params)
                           (into #{(get-forecast-opt :filter)}
                                 (->> @processed-params
                                      (map (fn [[key {:keys [options]}]]
                                             (get-in options [(params key) :filter])))
                                      (remove nil?))))
          {:keys [layers model-times]} (t/read (t/reader :json)
                                               (:body (<! (u/call-clj-async! "get-layers"
                                                                             (pr-str selected-set)))))]
      (when model-times (process-model-times! model-times))
      (reset! param-layers layers)
      (swap! *layer-idx #(max 0 (min % (- (count @param-layers) 1))))
      (when-not (seq @param-layers)
        (toast-message! "There are no layers available for the selected parameters. Please try another combination.")))))

(defn current-layer []
  (get @param-layers @*layer-idx))

(defn get-current-layer-name []
  (:layer (current-layer) ""))

(defn get-current-layer-hour []
  (:hour (current-layer) 0))

(defn get-current-layer-full-time []
  (if-let [sim-time (:sim-time (current-layer))]
    (u/time-zone-iso-date sim-time @show-utc?)
    ""))

(defn get-current-layer-extent []
  (:extent (current-layer) c/california-extent))

(defn get-current-layer-group []
  (:layer-group (current-layer) ""))

(defn get-current-layer-key [key-name]
  (->> (get-forecast-opt :params)
       (map (fn [[key {:keys [options]}]]
              (get-in options [(get-in @*params [@*forecast key]) key-name])))
       (remove nil?)
       (first)))

(defn get-current-option-key
  "Retreive the value for a particular parameter's option."
  [param-key option-key key-name]
  (get-in (get-forecast-opt :params) [param-key :options option-key key-name]))

(defn get-options-key [key-name]
  (some #(get % key-name)
        (vals (get-forecast-opt :params))))

;; TODO, can we make this the default everywhere?
(defn get-any-level-key [key-name]
  (let [layer-key (get-current-layer-key key-name)]
    (if (nil? layer-key)
      (get-options-key key-name)
      layer-key)))

(defn create-share-link
  "Generates a link with forecast and parameters encoded in a URL"
  []
  (let [center          (mb/get-center)
        zoom            (get (mb/get-zoom-info) 0)
        selected-params (get @*params @*forecast)
        page-params     (merge selected-params
                               center
                               {:forecast  @*forecast
                                :layer-idx @*layer-idx
                                :zoom      zoom})]
    (->> page-params
         (map (fn [[k v]] (cond
                            (keyword? v)
                            (str (name k) "=" (name v))

                            (or (string? v) (number? v))
                            (str (name k) "=" v))))
         (str/join "&")
         (str js/location.origin js/location.pathname "?"))))

(defn get-data
  "Asynchronously fetches the JSON or XML resource at url. Returns a
   channel containing the result of calling process-fn on the response
   or nil if an error occurred."
  [process-fn url]
  (u/fetch-and-process url
                       {:method "get"
                        :headers {"Accept" "application/json, text/xml"
                                  "Content-Type" "application/json"}}
                       process-fn))

(defn wrap-wms-errors [type response success-fn]
  (go
    (let [json-res (<p! (.json response))]
      (if-let [exceptions (u/try-js-aget json-res "exceptions")]
        (do
          (println exceptions)
          (toast-message! (str "Error retrieving " type ". See console for more details.")))
        (success-fn json-res)))))

(defn process-legend! [json-res]
  (reset! legend-list
          (as-> json-res data
            (u/try-js-aget data "Legend" 0 "rules" 0 "symbolizers" 0 "Raster" "colormap" "entries")
            (js->clj data)
            (remove (fn [leg] (= "nodata" (get leg "label"))) data)
            (doall data))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn get-legend! [layer]
  (reset! legend-list [])
  (when (u/has-data? layer)
    (get-data #(wrap-wms-errors "legend" % process-legend!)
              (c/legend-url (str/replace layer #"tlines|liberty|pacificorp" "all"))))) ; TODO make a more generic way to do this.

(defn process-point-info! [json-res]
  (reset! last-clicked-info [])
  (reset! last-clicked-info
          (as-> json-res pi
            (u/try-js-aget pi "features")
            (map (fn [pi-layer]
                   {:band   (u/to-precision 1 (first (.values js/Object (u/try-js-aget pi-layer "properties"))))
                    :vec-id (peek  (str/split (u/try-js-aget pi-layer "id") #"\."))})
                 pi)
            (filter (fn [pi-layer] (= (:vec-id pi-layer) (:vec-id (first pi))))
                    pi)
            (mapv (fn [pi-layer {:keys [sim-time hour]}]
                    (let [js-time (u/js-date-from-string sim-time)]
                      (merge {:js-time js-time
                              :date    (u/get-date-from-js js-time @show-utc?)
                              :time    (u/get-time-from-js js-time @show-utc?)
                              :hour    hour}
                             pi-layer)))
                  pi
                  @param-layers))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn get-point-info! [point-info]
  (reset! last-clicked-info nil)
  (let [layer-group (get-current-layer-group)]
    (when-not (u/missing-data? layer-group point-info)
      (get-data #(wrap-wms-errors "point information" % process-point-info!)
                (c/point-info-url layer-group
                                  (str/join "," point-info))))))

(defn- reset-underlays! []
  (doseq [[_ {:keys [name show?]}] (get-in @*params [@*forecast :underlays])]
    (when (some? name)
      (mb/set-visible-by-title! name show?))))

(defn select-layer! [new-layer]
  (reset! *layer-idx new-layer)
  (mb/swap-active-layer! (get-current-layer-name) (/ @active-opacity 100))
  (reset-underlays!))

(defn select-layer-by-hour! [hour]
  (select-layer! (first (keep-indexed (fn [idx layer]
                                        (when (= hour (:hour layer)) idx))
                                      @param-layers))))

(defn clear-info! []
  (mb/clear-point!)
  (reset! last-clicked-info [])
  (when (get-forecast-opt :block-info?)
    (reset! show-info? false)))

(declare select-param!) ;; FIXME: Look at ways to decouple callbacks

(defn- forecast-exists? [fire-name]
  (contains? (get-in @capabilities [:active-fire :params :fire-name :options])
             (keyword fire-name)))

(defn- init-fire-popup! [feature _]
  (let [properties (-> feature (aget "properties") (js->clj))
        lnglat     (-> properties (select-keys ["longitude" "latitude"]) (vals))
        {:strs [name prettyname containper acres]} properties
        body       (fp/fire-popup prettyname
                                  containper
                                  acres
                                  #(select-param! (keyword name) :fire-name)
                                  (forecast-exists? name))]
    (mb/init-popup! lnglat body {:width "200px"})
    (mb/set-center! lnglat 0)))

(defn change-type!
  "Changes the type of data that is being shown on the map."
  [get-model-times? clear? zoom? max-zoom]
  (go
    (<! (get-layers! get-model-times?))
    (let [source   (get-current-layer-name)
          style-fn (get-current-layer-key :style-fn)]
      (mb/reset-active-layer! source style-fn (/ @active-opacity 100))
      (mb/clear-popup!)
      (reset-underlays!)
      (when (some? style-fn)
        (mb/add-feature-highlight! "fire-active" "fire-active" init-fire-popup!))
      (get-legend! source))
    (if clear?
      (clear-info!)
      (get-point-info! (mb/get-overlay-bbox)))
    (when zoom?
      (mb/zoom-to-extent! (get-current-layer-extent) max-zoom))))

(defn select-param! [val & keys]
  (swap! *params assoc-in (cons @*forecast keys) val)
  (when-not ((set keys) :underlays)
    (let [main-key (first keys)]
      (change-type! (not (#{:burn-pct :model-init} main-key)) ;; TODO: Make this a config
                    (get-current-layer-key :clear-point?)
                    (get-current-option-key main-key val :auto-zoom?)
                    (get-any-level-key     :max-zoom)))))

(defn select-forecast! [key]
  (go
    (doseq [[_ {:keys [name]}] (get-in @*params [@*forecast :underlays])]
      (when (some? name)
        (mb/set-visible-by-title! name false)))
    (reset! *forecast key)
    (reset! processed-params (get-forecast-opt :params))
    (reset-underlays!)
    (<! (change-type! true
                      true
                      (get-any-level-key :auto-zoom?)
                      (get-any-level-key :max-zoom)))))

(defn set-show-info! [show?]
  (if (and show? (get-forecast-opt :block-info?))
    (toast-message! "There is currently no point information available for this layer.")
    (do (reset! show-info? show?)
        (clear-info!))))

(defn select-time-zone! [utc?]
  (reset! show-utc? utc?)
  (swap! last-clicked-info #(mapv (fn [{:keys [js-time] :as layer}]
                                    (assoc layer
                                           :date (u/get-date-from-js js-time @show-utc?)
                                           :time (u/get-time-from-js js-time @show-utc?)))
                                  @last-clicked-info))
  (swap! processed-params  #(update-in %
                                       [:model-init :options]
                                       (fn [options]
                                         (u/mapm (fn [[k {:keys [utc-time] :as v}]]
                                                   [k (assoc v
                                                             :opt-label
                                                             (u/time-zone-iso-date utc-time @show-utc?))])
                                                 options)))))

;;; Capabilities

(defn process-capabilities! [fire-names user-layers & [selected-options]]
  (reset! capabilities
          (-> (reduce (fn [acc {:keys [layer_path layer_config]}]
                        (let [layer-path   (edn/read-string layer_path)
                              layer-config (edn/read-string layer_config)]
                          (if (and (s/valid? ::layer-path   layer-path)
                                   (s/valid? ::layer-config layer-config))
                            (assoc-in acc layer-path layer-config)
                            acc)))
                      @options
                      user-layers)
              (update-in [:active-fire :params :fire-name :options]
                         merge
                         fire-names)))
  (reset! *params (u/mapm
                   (fn [[forecast _]]
                     (let [params (get-in @capabilities [forecast :params])]
                       [forecast (merge (u/mapm (fn [[k v]]
                                                  [k (or (get-in selected-options [forecast k])
                                                         (:default-option v)
                                                         (ffirst (:options v)))])
                                                params)
                                        {:underlays (->> params
                                                         (mapcat (fn [[_ v]] (:underlays v)))
                                                         (u/mapm (fn [[k _]] [k {:show? false
                                                                                 :name  nil}])))})]))
                   @options)))

(defn refresh-fire-names! [user-id]
  (go
    (as-> (u/call-clj-async! "get-fire-names" user-id) fire-names
      (<! fire-names)
      (:body fire-names)
      (edn/read-string fire-names)
      (swap! capabilities update-in [:active-fire :params :fire-name :options] merge fire-names))))

(defn- params->selected-options
  "Parses url query parameters to into the selected options"
  [options-config forecast params]
  {forecast (as-> options-config oc
              (get-in oc [forecast :params])
              (keys oc)
              (select-keys params oc)
              (u/mapm (fn [[k v]] [k (keyword v)]) oc))})

(defn initialize! [{:keys [user-id forecast-type forecast layer-idx lat lng zoom] :as params}]
  (go
    (let [{:keys [options-config default]} (c/get-forecast forecast-type)
          user-layers-chan (u/call-clj-async! "get-user-layers" user-id)
          fire-names-chan  (u/call-clj-async! "get-fire-names" user-id)
          fire-cameras     (u/call-clj-async! "get-cameras")]
      (reset! options options-config)
      (reset! *forecast (or (keyword forecast) default))
      (reset! *layer-idx (if layer-idx (js/parseInt layer-idx) 0))
      (mb/init-map! "map" (if (every? nil? [lng lat zoom]) {} {:center [lng lat] :zoom zoom}))
      (process-capabilities! (edn/read-string (:body (<! fire-names-chan)))
                             (edn/read-string (:body (<! user-layers-chan)))
                             (params->selected-options @options @*forecast params))
      (<! (select-forecast! @*forecast))
      (reset! the-cameras (edn/read-string (:body (<! fire-cameras))))
      (reset! loading? false))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $app-header []
  {:align-items     "center"
   :display         "flex"
   :justify-content "center"
   :position        "relative"
   :width           "100%"})

(defn $forecast-label [selected?]
  (merge
   {:cursor "pointer"
    :margin "0 1rem 0 1rem"}
   (when selected? {:color "white"})))

(defn $control-layer []
  {:height   "100%"
   :position "absolute"
   :width    "100%"})

(defn $message-modal [mobile?]
  {:background-color "white"
   :border-radius    "3px"
   :display          "flex"
   :flex-direction   "column"
   :margin           (if mobile? ".25rem" "8rem auto")
   :overflow         "hidden"
   :max-height       (if mobile? "calc(100% - .5rem)" "50%")
   :width            (if mobile? "unset" "25rem")})

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
         [mc/collapsible-panel
          (get @*params @*forecast)
          select-param!
          active-opacity
          @processed-params
          @mobile?]
         (when (aget @my-box "height")
           [:<>
            (when @show-info?
              [mc/information-tool
               get-point-info!
               @my-box
               *layer-idx
               select-layer-by-hour!
               (get-current-layer-key :units)
               (get-current-layer-hour)
               @legend-list
               @last-clicked-info
               #(set-show-info! false)])
            (when @show-match-drop?
              [mc/match-drop-tool @my-box #(reset! show-match-drop? false) refresh-fire-names! user-id])
            (when @show-camera?
              [mc/camera-tool @the-cameras @my-box terrain? #(reset! show-camera? false)])])
         [mc/legend-box @legend-list (get-forecast-opt :reverse-legend?) @mobile?]
         [mc/tool-bar
          show-info?
          show-match-drop?
          show-camera?
          show-red-flag?
          show-fire-history?
          set-show-info!
          @mobile?
          user-id]
         [mc/scale-bar @mobile?]
         (when-not @mobile? [mc/mouse-lng-lat])
         [mc/zoom-bar get-current-layer-extent @mobile? create-share-link terrain?]
         [mc/time-slider
          param-layers
          *layer-idx
          (get-current-layer-full-time)
          select-layer!
          show-utc?
          select-time-zone!
          @mobile?]])})))

(defn pop-up []
  [:div#pin {:style ($/fixed-size "2rem")}
   [pin]])

(defn map-layer []
  (r/with-let [mouse-down? (r/atom false)
               cursor-fn   #(cond
                              @mouse-down?                       "grabbing"
                              (or @show-info? @show-match-drop?) "crosshair" ; TODO get custom cursor image from Ryan
                              :else                              "grab")]
    [:div#map {:class (<class $p-mb-cursor)
               :style {:height "100%" :position "absolute" :width "100%" :cursor (cursor-fn)}
               :on-mouse-down #(reset! mouse-down? true)
               :on-mouse-up #(reset! mouse-down? false)}]))

(defn theme-select []
  [:div {:style {:position "absolute" :left "3rem" :display "flex"}}
   [:label {:style {:margin "4px .5rem 0"}} "Theme:"]
   [radio "Light" $/light? true  #(reset! $/light? %)]
   [radio "Dark"  $/light? false #(reset! $/light? %)]])

(defn message-modal []
  (r/with-let [show-me? (r/atom (not @c/dev-mode?))]
    (when @show-me?
      [:div#message-modal {:style ($/modal)}
       [:div {:style ($message-modal @mobile?)}
        [:div {:class "bg-yellow"
               :style {:width "100%"}}
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
          "Please see our"
          [:a {:style {:margin-right ".25rem"}
               :href "/terms-of-use"
               :target "_blank"} "Terms of Use"]
          "and"
          [:a {:style {:margin-left ".25rem"}
               :href "/privacy-policy"
               :target "_blank"} "Privacy Policy"]]]
        [:div {:style ($/combine $/flex-row {:justify-content "center"})}
         [:span
          [:label {:class (<class $/p-form-button)
                   :style {:padding-left  "1.75rem"
                           :padding-right "1.75rem"}
                   :on-click #(u/jump-to-url! "/")}
           "Decline"]
          [:label {:class (<class $/p-form-button)
                   :style {:margin        ".5rem"
                           :padding-left  "1.75rem"
                           :padding-right "1.75rem"}
                   :on-click #(reset! show-me? false)}
           "Accept"]]]]])))

(defn loading-modal []
  [:div#message-modal {:style ($/modal)}
   [:div {:style ($message-modal false)}
    [:h3 {:style {:padding "1rem"}} "Loading..."]]])

(defn root-component [{:keys [user-id] :as params}]
  (let [height (r/atom "100%")]
    (r/create-class
     {:component-did-mount
      (fn [_]
        (let [update-fn (fn [& _]
                          (-> js/window (.scrollTo 0 0))
                          (reset! mobile? (> 700.0 (.-innerWidth js/window)))
                          (reset! height  (str (- (.-innerHeight js/window)
                                                  (-> js/document
                                                      (.getElementById "header")
                                                      .getBoundingClientRect
                                                      (aget "height")))
                                               "px"))
                          (js/setTimeout mb/resize-map! 50))]
          (-> js/window (.addEventListener "touchend" update-fn))
          (-> js/window (.addEventListener "resize"   update-fn))
          (process-toast-messages!)
          (initialize! params)
          (update-fn)))

      :reagent-render
      (fn [_]
        [:div {:style ($/combine $/root {:height @height :padding 0 :position "relative"})}
         [toast-message]
         [message-box-modal]
         (when @loading? [loading-modal])
         [message-modal]
         [:div {:class "bg-yellow"
                :style ($app-header)}
          (when-not @mobile? [theme-select])
          [:span {:style {:display "flex" :padding ".25rem 0"}}
           (doall (map (fn [[key {:keys [opt-label hover-text]}]]
                         ^{:key key}
                         [tool-tip-wrapper
                          hover-text
                          :top
                          [:label {:style ($forecast-label (= @*forecast key))
                                   :on-click #(select-forecast! key)}
                           opt-label]])
                       @capabilities))]
          (when-not @mobile?
            (if user-id
              [:span {:style {:position "absolute" :right "3rem" :display "flex"}}
               [:label {:style {:margin-right "1rem" :cursor "pointer"}
                        :on-click (fn []
                                    (go (<! (u/call-clj-async! "log-out"))
                                        (-> js/window .-location .reload)))}
                "Log Out"]]
              [:span {:style {:position "absolute" :right "3rem" :display "flex"}}
               ;; Remove for the time being
               ;; [:label {:style {:margin-right "1rem" :cursor "pointer"}
               ;;          :on-click #(u/jump-to-url! "/register")} "Register"]
               [:label {:style {:cursor "pointer"}
                        :on-click #(u/jump-to-url! "/login")} "Log In"]]))]
         [:div {:style {:height "100%" :position "relative" :width "100%"}}
          (when @mb/the-map [control-layer user-id])
          [map-layer]
          [pop-up]]])})))
