(ns pyregence.pages.near-term-forecast
  (:require [reagent.core :as r]
            [reagent.dom :as rd]
            [herb.core :refer [<class]]
            [cognitect.transit :as t]
            [clojure.string :as str]
            [clojure.core.async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [pyregence.styles :as $]
            [pyregence.utils  :as u]
            [pyregence.config :as c]
            [pyregence.components.map-controls :as mc]
            [pyregence.components.openlayers   :as ol]
            [pyregence.components.common    :refer [radio]]
            [pyregence.components.messaging :refer [toast-message
                                                    toast-message!
                                                    process-toast-messages!]]
            [pyregence.components.svg-icons :refer [pin]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce legend-list       (r/atom []))
(defonce last-clicked-info (r/atom []))
(defonce show-utc?         (r/atom true))
(defonce show-info?        (r/atom false))
(defonce show-measure?     (r/atom false))
(defonce capabilities      (r/atom []))
(defonce *forecast         (r/atom :active-fire))
(defonce processed-params  (r/atom []))
(defonce *params           (r/atom {}))
(defonce param-layers      (r/atom []))
(defonce *layer-idx        (r/atom 0))
(defonce loading?          (r/atom true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-forecast-opt [key-name]
  (get-in @capabilities [@*forecast key-name]))

(defn process-params! []
  (reset! processed-params
          (update-in (get-forecast-opt :params)
                     [:model-init :options]
                     (fn [options]
                       (u/mapm (fn [[k {:keys [utc-time] :as v}]]
                                 [k (assoc v
                                           :opt-label
                                           (u/time-zone-iso-date utc-time @show-utc?))])
                               options))))
  ;; TODO, maybe we should remember the params between forecast types so the user can switch between to comapre
  (reset! *params (u/mapm (fn [[k v]]
                            [k
                             (or (:default-option v)
                                 (ffirst (:options v)))])
                          @processed-params)))

(defn get-layers! []
  (go
    (let [selected-set (into #{(get-forecast-opt :filter)}
                             (map (fn [[key {:keys [options]}]]
                                    (get-in options [(@*params key) :filter])))
                             @processed-params)]
      (reset! param-layers
              (t/read (t/reader :json) (:message (<! (u/call-clj-async! "get-layers" (pr-str selected-set))))))
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
  (:extent (current-layer) [-124.83131903974008 32.36304641169675 -113.24176261416054 42.24506977982483]))

(defn get-current-layer-group []
  (:layer-group (current-layer) ""))

(defn get-current-layer-key [key-name]
  (some (fn [[key {:keys [options]}]]
          (get-in options [(@*params key) key-name]))
        (get-forecast-opt :params)))

(defn get-options-key [key-name]
  (some #(get % key-name)
        (vals (get-forecast-opt :params))))

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

(defn select-layer! [new-layer]
  (reset! *layer-idx new-layer)
  (ol/swap-active-layer! (get-current-layer-name)))

(defn select-layer-by-hour! [hour]
  (select-layer! (first (keep-indexed (fn [idx layer]
                                        (when (= hour (:hour layer)) idx))
                                      @param-layers))))

(defn clear-info! []
  (ol/clear-point!)
  (reset! last-clicked-info [])
  (when (get-forecast-opt :block-info?)
    (reset! show-info? false)))

(defn check-param-filter []
  (swap! *params
         (fn [params]
           (u/mapm (fn [[k v]]
                     (let [{:keys [filter-on filter-key options]} (@processed-params k)
                           filter-set (get-in @processed-params [filter-on :options (params filter-on) filter-key])]
                       [k
                        (if (and filter-on filter-set (not (filter-set (get-in options [v :filter]))))
                          (keyword (last (sort filter-set)))
                          v)]))
                   params))))

(defn change-type! [clear? zoom?]
  (go
    (check-param-filter)
    (<! (get-layers!))
    (ol/reset-active-layer! (get-current-layer-name))
    (get-legend!            (get-current-layer-name))
    (if clear?
      (clear-info!)
      (get-point-info! (ol/get-overlay-bbox)))
    (when zoom?
      (ol/zoom-to-extent! (get-current-layer-extent)))))

(defn select-param! [key val]
  (swap! *params assoc key val)
  (change-type! (get-current-layer-key :clear-point?)
                (get-in @processed-params [key :auto-zoom?])))

(defn select-forecast! [key]
  (go (reset! *forecast key)
      (process-params!)
      (<! (change-type! true (get-options-key :auto-zoom?)))))

(defn set-show-info! [show?]
  (if (get-forecast-opt :block-info?)
    (toast-message! "There is currently no point information available for this layer.")
    (do (reset! show-info? show?)
        (if show?
          (ol/add-popup-on-single-click! get-point-info!)
          (do (ol/remove-popup-on-single-click!)
              (clear-info!))))))

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

(defn init-map! [user-id]
  (go
    (let [capabilities-chan (u/call-clj-async! "get-capabilities" user-id)] ; TODO get user-id from session on backend
      (ol/init-map!)
      (reset! capabilities (t/read (t/reader :json) (:message (<! capabilities-chan))))
      (<! (select-forecast! @*forecast))
      (ol/add-layer-load-fail! #(toast-message! "One or more of the map tiles has failed to load."))
      (ol/set-visible-by-title! "active" true)
      (reset! loading? false))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $app-header []
  {:align-items     "center"
   :display         "flex"
   :height          "2.5rem"
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

(defn $message-modal []
  {:background-color "white"
   :border-radius    "3px"
   :margin           "15% auto"
   :overflow         "hidden"
   :width            "25rem"})

(def ol-scale-line
  {:background-color ($/color-picker :bg-color)
   :border           (str "1px solid " ($/color-picker :border-color))
   :bottom           "36px"
   :box-shadow       (str "0 0 0 2px " ($/color-picker :bg-color))
   :left             "auto"
   :height           "28px"
   :right            "64px"})

(def ol-scale-line-inner
  {:border-color ($/color-picker :border-color)
   :color        ($/color-picker :border-color)
   :font-size    ".75rem"})

(defn $p-ol-control []
  (with-meta
    {}
    {:combinators {[:descendant :.ol-scale-line]       ol-scale-line
                   [:descendant :.ol-scale-line-inner] ol-scale-line-inner}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn control-layer []
  (let [my-box (r/atom #js {})]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (let [this-node      (rd/dom-node this)
              update-my-box! (fn [& _] (reset! my-box (.getBoundingClientRect this-node)))]
          (update-my-box!)
          (-> (js/ResizeObserver. update-my-box!)
              (.observe this-node))))

      :render
      (fn []
        [:div {:style ($control-layer)}
         [mc/collapsible-panel @*params select-param! @processed-params]
         (when (and @show-info? (aget @my-box "height"))
           [mc/information-tool
            @my-box
            *layer-idx
            select-layer-by-hour!
            (get-current-layer-key :units)
            (get-current-layer-hour)
            @legend-list
            @last-clicked-info
            #(set-show-info! false)])
         (when (and @show-measure? (aget @my-box "height"))
           [mc/measure-tool @my-box #(reset! show-measure? false)])
         [mc/legend-box @legend-list (get-forecast-opt :reverse-legend?)]
         [mc/zoom-bar get-current-layer-extent]
         [mc/tool-bar show-info? show-measure? set-show-info!]
         [mc/time-slider
          param-layers
          *layer-idx
          (get-current-layer-full-time)
          select-layer!
          show-utc?
          select-time-zone!]])})))

(defn pop-up []
  [:div#pin {:style ($/fixed-size "2rem")}
   [pin]])

(defn map-layer []
  (r/with-let [mouse-down? (r/atom false)
               cursor-fn   #(cond
                              @mouse-down?                    "grabbing"
                              (or @show-info? @show-measure?) "crosshair" ; TODO get custom cursor image from Ryan
                              :else                           "grab")]
    [:div#map {:class (<class $p-ol-control)
               :style {:height "100%" :position "absolute" :width "100%" :cursor (cursor-fn)}
               :on-mouse-down #(reset! mouse-down? true)
               :on-mouse-up #(reset! mouse-down? false)}]))

(defn theme-select []
  [:div {:style {:position "absolute" :left "3rem" :display "flex"}}
   [:label {:style {:margin "4px .5rem 0"}} "Theme:"]
   [radio "Light" $/light? true  #(reset! $/light? %)]
   [radio "Dark"  $/light? false #(reset! $/light? %)]])

(defn message-modal []
  (r/with-let [show-me? (r/atom (not (str/includes? (-> js/window .-location .-origin) "local")))]
    (when @show-me?
      [:div#message-modal {:style ($/modal "absolute")}
       [:div {:style ($message-modal)}
        [:div {:class "bg-yellow"
               :style {:width "100%"}}
         [:label {:style {:padding ".5rem 0 0 .5rem" :font-size "1.5rem"}}
          "Disclaimer"]]
        [:label {:style {:padding ".5rem"}}
         "Pyregence fire forecast tools are experimental and should not be relied upon
          for fire management operations or fire safety decisions. These tools
          are offered as-is without warranty, and liability for damages is expressly disclaimed."]
        [:div {:style ($/combine $/flex-row {:justify-content "flex-end"})}
         [:span
          [:label {:class "btn border-yellow text-brown"
                   :on-click #(u/jump-to-url! "/")}
           "Decline"]
          [:label {:class "btn border-yellow text-brown"
                   :style {:margin ".5rem"}
                   :on-click #(reset! show-me? false)}
           "Accept"]]]]])))

(defn loading-modal []
  [:div#message-modal {:style ($/modal "absolute")}
   [:div {:style ($message-modal)}
    [:h3 {:style {:padding "1rem"}} "Loading..."]]])

(defn root-component [{:keys [user-id]}]
  (r/create-class
   {:component-did-mount
    (fn [_]
      (process-toast-messages!)
      (init-map! user-id))

    :reagent-render
    (fn [_]
      [:div {:style ($/combine $/root {:height "100%" :padding 0 :position "relative"})}
       [toast-message]
       (when @loading? [loading-modal])
       [message-modal]
       [:div {:class "bg-yellow"
              :style ($app-header)}
        [theme-select]
        [:span
         (doall (map (fn [[key {:keys [opt-label]}]]
                       [:label {:key key
                                :style ($forecast-label (= @*forecast key))
                                :on-click #(select-forecast! key)}
                        opt-label])
                     @capabilities))]
        (if user-id
          [:span {:style {:position "absolute" :right "3rem" :display "flex"}}
           [:label {:style {:margin-right "1rem" :cursor "pointer"}
                    :on-click (fn []
                                (go (<! (u/call-clj-async! "log-out"))
                                    (-> js/window .-location .reload)))}
            "Log Out"]]
          [:span {:style {:position "absolute" :right "3rem" :display "flex"}}
           ;; TODO, this is commented out until we are ready for users to create an account
           ;;  [:label {:style {:margin-right "1rem" :cursor "pointer"}
           ;;           :on-click #(u/jump-to-url! "/register")} "Register"]
           [:label {:style {:cursor "pointer"}
                    :on-click #(u/jump-to-url! "/login")} "Log In"]])]
       [:div {:style {:height "100%" :position "relative" :width "100%"}}
        (when @ol/the-map [control-layer])
        [map-layer]
        [pop-up]]])}))
