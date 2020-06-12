(ns pyregence.pages.near-term-forecast
  (:require [reagent.core :as r]
            [reagent.dom :as rd]
            [clojure.string :as str]
            [clojure.core.async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [pyregence.styles :as $]
            [pyregence.utils  :as u]
            [pyregence.config :as c]
            [pyregence.components.common       :refer [radio]]
            [pyregence.components.map-controls :as mc]
            [pyregence.components.messaging    :refer [toast-message
                                                       toast-message!
                                                       process-toast-messages!]]
            [pyregence.components.openlayers   :as ol]
            [pyregence.components.svg-icons    :refer [pin]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce legend-list       (r/atom []))
(defonce layer-list        (r/atom []))
(defonce last-clicked-info (r/atom []))
(defonce show-utc?         (r/atom true))
(defonce show-info?        (r/atom false))
(defonce show-measure?     (r/atom false))
(defonce *forecast         (r/atom :fire-risk))
(defonce processed-params  (r/atom []))
(defonce *params           (r/atom {}))
(defonce filtered-layers   (r/atom []))
(defonce *layer-idx        (r/atom 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API Calls
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-forecast-opt [key-name]
  (get-in c/forecast-options [@*forecast key-name]))

(defn get-fire-names [forecast-layers]
  (->> forecast-layers
       (group-by :fire-name)
       (u/mapm (fn [[fire-name opt-vec]]
                 [(keyword fire-name)
                  {:opt-label  fire-name
                   :filter     fire-name
                   :model-init (into #{} (map :model-init) opt-vec)}]))))

(defn get-model-times [forecast-layers]
  (->> forecast-layers
       (map :model-init)
       (distinct)
       (sort)
       (reverse)
       (u/mapm (fn [option]
                 (let [model-js-time (apply u/js-date-from-string (str/split option #"_"))
                       date          (u/get-date-from-js model-js-time @show-utc?)
                       time          (u/get-time-from-js model-js-time @show-utc?)]
                   [(keyword option)
                    {:opt-label (str date "-" time)
                     :js-time   model-js-time
                     :date      date
                     :time      time
                     :filter    option}])))))

(defn process-params! []
  (reset! processed-params
          (let [forecast-filter (get-forecast-opt :filter)
                forecast-layers (filter (fn [layer]
                                          (= forecast-filter (:forecast layer)))
                                        @layer-list)
                params          (get-forecast-opt :params)
                has-fire-name?  (get-in params [:fire-name])]
            (cond-> params
              has-fire-name? (assoc-in [:fire-name  :options] (get-fire-names  forecast-layers))
              :always        (assoc-in [:model-init :options] (get-model-times forecast-layers)))))
  (reset! *params (u/mapm (fn [[k v]]
                            [k
                             (or (:default-option v)
                                 (ffirst (:options v)))])
                          @processed-params)))

(defn filter-layers! []
  (let [selected-set (into #{(get-forecast-opt :filter)}
                           (map (fn [[key {:keys [options]}]]
                                  (get-in options [(@*params key) :filter])))
                           @processed-params)]
    (reset! filtered-layers
            (filterv (fn [{:keys [filter-set]}] (= selected-set filter-set))
                     @layer-list))))

(defn current-layer []
  (get @filtered-layers @*layer-idx))

(defn get-current-layer-name []
  (:layer (current-layer) ""))

(defn get-current-layer-hour []
  (:hour (current-layer) 0))

(defn get-current-layer-full-time []
  (if-let [js-time (:js-time (current-layer))]
    (str (u/get-date-from-js js-time @show-utc?) "-" (u/get-time-from-js js-time @show-utc?))
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

(defn process-legend! [response]
  (go
    (reset! legend-list
            (as-> (<p! (.json response)) data
              (u/try-js-aget data "Legend" 0 "rules" 0 "symbolizers" 0 "Raster" "colormap" "entries")
              (js->clj data)
              (remove (fn [leg] (= "nodata" (get leg "label"))) data)
              (doall data)))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn get-legend! [layer]
  (reset! legend-list [])
  (when (u/has-data? layer)
    (get-data process-legend!
              (c/legend-url (str/replace layer "tlines" "all")))))

(defn split-risk-layer-name [name-string]
  (let [[workspace layer]           (str/split name-string #":")
        [forecast init-timestamp]   (str/split workspace   #"_(?=\d{8}_)")
        [layer-group sim-timestamp] (str/split layer       #"_(?=\d{8}_)")
        init-js-time                (apply u/js-date-from-string (str/split init-timestamp #"_"))
        sim-js-time                 (apply u/js-date-from-string (str/split sim-timestamp  #"_"))]
    {:layer-group (str workspace ":" layer-group)
     :forecast    forecast
     :filter-set  (into #{forecast init-timestamp} (str/split layer-group #"_"))
     :model-init  init-timestamp
     :js-time     sim-js-time
     :hour        (- (/ (- sim-js-time init-js-time) 1000 60 60) 6)}))

(defn split-active-layer-name [name-string]
  (let [[workspace layer]    (str/split name-string #":")
        [forecast fire-name] (str/split workspace   #"_")
        params-str           (str/split layer       #"_")
        init-timestamp       (str (get params-str 0) "_" (get params-str 1))
        sim-js-time          (u/js-date-from-string (get params-str 6) (get params-str 7))]
    {:layer-group ""
     :forecast    forecast
     :fire-name   fire-name
     :filter-set  (into #{forecast fire-name init-timestamp} (subvec params-str 2 6))
     :model-init  init-timestamp
     :js-time     sim-js-time
     :hour        0}))

(defn process-capabilities! [response]
  (go
    (reset! layer-list
            (as-> (<p! (.text response)) xml
              (str/replace xml "\n" "")
              (re-find #"<Layer>.*(?=</Layer>)" xml)
              (str/replace-first xml "<Layer>" "")
              (re-seq #"<Layer.+?</Layer>" xml)
              (keep (fn [layer]
                      (let [full-name (->  (re-find #"<Name>.+?(?=</Name>)" layer)
                                           (str/replace #"<Name>" ""))
                            coords    (->> (re-find #"<BoundingBox CRS=\"CRS:84.+?\"/>" layer)
                                           (re-seq #"[\d|\.|-]+")
                                           (rest)
                                           (vec))
                            merge-fn  #(merge % {:layer full-name :extent coords})]
                        (cond
                          (re-matches #"([a-z|-]+_)\d{8}_\d{2}:([a-z|-]+_){4}\d{8}_\d{6}" full-name)
                          (merge-fn (split-risk-layer-name full-name))

                          (re-matches #"([a-z|-]+_)[a-z|-|\d]+:\d{8}_\d{6}_([a-z|-]+_){2}\d{2}_([a-z|-]+_)\d{8}_\d{6}" full-name)
                          (merge-fn (split-active-layer-name full-name)))))
                    xml)))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn get-layers! []
  (reset! layer-list [])
  (get-data process-capabilities! c/capabilities-url))

(defn process-point-info! [response]
  (go
    (reset! last-clicked-info
            (as-> (<p! (.json response)) pi
              (u/try-js-aget pi "features")
              (map (fn [pi-layer]
                     {:band   (first (.values js/Object (u/try-js-aget pi-layer "properties")))
                      :vec-id (peek  (str/split (u/try-js-aget pi-layer "id") #"\."))})
                   pi)
              (filter (fn [pi-layer] (= (:vec-id pi-layer) (:vec-id (first pi))))
                      pi)
              (mapv (fn [pi-layer {:keys [js-time hour]}]
                      (merge {:js-time js-time
                              :date    (u/get-date-from-js js-time @show-utc?)
                              :time    (u/get-time-from-js js-time @show-utc?)
                              :hour    hour}
                             pi-layer))
                    pi
                    @filtered-layers)))))

;; Use <! for synchronous behavior or leave it off for asynchronous behavior.
(defn get-point-info! [point-info]
  (reset! last-clicked-info nil)
  (let [layer-group (get-current-layer-group)]
    (when-not (u/missing-data? layer-group point-info)
      (get-data process-point-info!
                (c/point-info-url layer-group
                                  (str/join "," point-info))))))

(defn select-layer! [new-layer]
  (reset! *layer-idx new-layer)
  (ol/swap-active-layer! (get-current-layer-name)))

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
                        (if (and filter-on (not (filter-set (get-in options [v :filter]))))
                          (keyword (first filter-set))
                          v)]))
                   params))))

(defn change-type! [clear? zoom?]
  (check-param-filter)
  (filter-layers!)
  (ol/swap-active-layer! (get-current-layer-name))
  (get-legend!           (get-current-layer-name))
  (if clear?
    (clear-info!)
    (get-point-info! (ol/get-overlay-bbox)))
  (when zoom?
    (ol/zoom-to-extent! (get-current-layer-extent))))

(defn select-param! [key val]
  (swap! *params assoc key val)
  (change-type! (get-current-layer-key :clear-point?)
                (get-in @processed-params [key :auto-zoom?])))

(defn select-forecast! [key]
  (reset! *forecast key)
  (process-params!)
  (change-type! true (get-options-key :auto-zoom?)))

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
                                    (let [date (u/get-date-from-js js-time @show-utc?)
                                          time (u/get-time-from-js js-time @show-utc?)]
                                      (assoc layer
                                             :date      (u/get-date-from-js js-time @show-utc?)
                                             :time      (u/get-time-from-js js-time @show-utc?)
                                             :opt-label (str date "-" time))))
                                  @last-clicked-info))
  (swap! processed-params #(update-in %
                                      [:model-init :options]
                                      (fn [options]
                                        (u/mapm (fn [[k {:keys [js-time] :as v}]]
                                                  [k (assoc v
                                                            :opt-label
                                                            (str (u/get-date-from-js js-time @show-utc?)
                                                                 "-"
                                                                 (u/get-time-from-js js-time @show-utc?)))])
                                                options)))))

(defn init-map! []
  (go
    (let [layers-chan (get-layers!)]
      (ol/init-map!)
      (<! layers-chan)
      (select-forecast! @*forecast)
      (ol/set-visible-by-title! "active" true))))

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
            select-layer!
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
          (count @filtered-layers)
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
    [:div#map {:style {:height "100%" :position "absolute" :width "100%" :cursor (cursor-fn)}
               :on-mouse-down #(reset! mouse-down? true)
               :on-mouse-up #(reset! mouse-down? false)}]))

(defn theme-select []
  [:div {:style {:position "absolute" :right "3rem" :display "flex"}}
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
         "Forecast Tools are open source, offered as-is, without warranty and liability for damages is expressly disclaimed. \n\n
          At this time, the Forecast Tools are under development and should not be relied on for any fire-safety decision."]
        [:div {:style ($/combine $/flex-row {:justify-content "flex-end"})}
         [:span
          [:label {:class "btn border-yellow text-brown"
                   :on-click #(u/jump-to-url! "/")}
           "Decline"]
          [:label {:class "btn border-yellow text-brown"
                   :style {:margin ".5rem"}
                   :on-click #(reset! show-me? false)}
           "Accept"]]]]])))

(defn root-component [_]
  (r/create-class
   {:component-did-mount
    (fn [_]
      (process-toast-messages!)
      (init-map!))

    :reagent-render
    (fn [_]
      [:div {:style ($/combine $/root {:height "100%" :padding 0 :position "relative"})}
       [toast-message]
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
                     c/forecast-options))]]
       [:div {:style {:height "100%" :position "relative" :width "100%"}}
        (when @ol/the-map [control-layer])
        [map-layer]
        [pop-up]]])}))
