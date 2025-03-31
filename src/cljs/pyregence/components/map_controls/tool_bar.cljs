(ns pyregence.components.map-controls.tool-bar
  (:require [clojure.core.async                            :refer [<! go]]
            [pyregence.components.common                   :refer [tool-tip-wrapper hs-str]]
            [pyregence.components.map-controls.tool-button :refer [tool-button]]
            [pyregence.components.mapbox                   :as mb]
            [pyregence.components.messaging                :refer [toast-message!]]
            [pyregence.components.popups                   :refer [red-flag-popup fire-history-popup]]
            [pyregence.config                              :as c]
            [pyregence.state                               :as !]
            [pyregence.styles                              :as $]
            [pyregence.utils.async-utils                   :as u-async]
            [reagent.core                                  :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^:private ^{:doc "Whether or not the currently logged in user has match drop access."}
  match-drop-access? (r/atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn gtag-tool [show? tool-name]
  (let [event-name (str (if show? "show-" "hide-") tool-name)]
    (js/gtag "event" "registered-user" (clj->js {:tool-clicked event-name}))))

(defn- init-red-flag-popup! [feature lnglat]
  (let [properties (-> feature (aget "properties") (js->clj))
        {:strs [url prod_type onset ends]} properties
        body       (red-flag-popup url prod_type onset ends)]
    (mb/init-popup! "red-flag" lnglat body {:width "200px"})))

(defn- init-fire-history-popup! [feature lnglat]
  (let [properties (-> feature (aget "properties") (js->clj))
        {:strs [incidentna fireyear gisacres]} properties
        body       (fire-history-popup incidentna fireyear gisacres)]
    (mb/init-popup! "fire-history" lnglat body {:width "200px"})))

(defn toggle-red-flag-layer!
  "Toggles the red-flag warning layer."
  []
  (swap! !/show-red-flag? not)
  (gtag-tool @!/show-red-flag? "red-flag")
  (if @!/show-red-flag?
    (mb/add-feature-highlight! "red-flag" "red-flag" :click-fn init-red-flag-popup!)
    (mb/clear-highlight! "red-flag" :selected))
  (go
    (let [data (-> (<! (u-async/call-clj-async! "get-red-flag-layer"))
                   (:body)
                   (js/JSON.parse))]
      (if (empty? (.-features data))
        (do
          (toast-message! "There are no red flag warnings at this time.")
          (reset! !/show-red-flag? false))
        (when (and @!/show-red-flag? (not (mb/layer-exists? "red-flag")))
          (mb/create-red-flag-layer! "red-flag" data)))))
  (mb/set-visible-by-title! "red-flag" @!/show-red-flag?)
  (mb/clear-popup! "red-flag"))

(defn toggle-fire-history-layer!
  "Toggles the fire history layer."
  []
  (swap! !/show-fire-history? not)
  (gtag-tool @!/show-fire-history? "fire-history-layer")
  (if @!/show-fire-history?
    (do
      (mb/add-feature-highlight! "fire-history" "fire-history"
                                 :click-fn init-fire-history-popup!
                                 :source-layer "fire-history")
      (mb/add-feature-highlight! "fire-history-centroid" "fire-history-centroid"
                                 :click-fn init-fire-history-popup!
                                 :source-layer "fire-history-centroid"))
    (do
      (mb/clear-highlight! "fire-history" :selected)
      (mb/clear-highlight! "fire-history-centroid" :selected)))
  (when (and @!/show-fire-history? (not (mb/layer-exists? "fire-history")))
    (mb/create-fire-history-layer! "fire-history"
                                   "fire-detections_fire-history%3Afire-history"
                                   :shasta)
    (mb/create-fire-history-label-layer! "fire-history-centroid"
                                         "fire-detections_fire-history%3Afire-history-centroid"
                                         :shasta))
  (mb/set-visible-by-title! "fire-history" @!/show-fire-history?)
  (mb/set-visible-by-title! "fire-history-centroid" @!/show-fire-history?)
  (mb/clear-popup! "fire-history"))

(defn- set-match-drop-access! [user-id]
  (go
    (let [response (<! (u-async/call-clj-async! "get-user-match-drop-access" user-id))]
      (if (:success response)
        (reset! match-drop-access? true)
        (reset! match-drop-access? false)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tool-bar [set-show-info! get-any-level-key user-id]
  (set-match-drop-access! user-id)
  (fn [_]
    [:div#tool-bar {:style ($/combine $/tool $/tool-bar {:top "16px"})}
     (->> [[:info
            (str (hs-str @!/show-info?) " point information")
            #(do (set-show-info! (not @!/show-info?))
                 (reset! !/show-measure-tool? false)
                 (reset! !/show-match-drop? false)
                 (reset! !/show-camera? false)
                 (gtag-tool @!/show-info? "point-information"))
            @!/show-info?]
           (when (and (c/feature-enabled? :match-drop) ; enabled in `config.edn`
                      (number? user-id)                ; logged in user
                      @match-drop-access?              ; user has match drop access
                      (not @!/mobile?))                ; screen size isn't mobile
             [:flame
              (str (hs-str @!/show-match-drop?) " match drop tool")
              #(do (swap! !/show-match-drop? not)
                   (reset! !/show-measure-tool? false)
                   (set-show-info! false)
                   (reset! !/show-camera? false)
                   (gtag-tool @!/show-match-drop? "match-drop"))
              @!/show-match-drop?])
           (when-not (get-any-level-key :disable-camera?)
             [:camera
              (str (hs-str @!/show-camera?) " cameras")
              #(do (swap! !/show-camera? not)
                   (set-show-info! false)
                   (reset! !/show-match-drop? false)
                   (reset! !/show-measure-tool? false)
                   (gtag-tool @!/show-camera? "camera"))
              @!/show-camera?])
           (when-not (get-any-level-key :disable-flag?)
             [:flag
              (str (hs-str @!/show-red-flag?) " red flag warnings")
              toggle-red-flag-layer!])
           (when (and (c/feature-enabled? :fire-history) (not (get-any-level-key :disable-history?)))
             [:clock
              (str (hs-str @!/show-fire-history?) " fire history")
              toggle-fire-history-layer!])
           [:measure-ruler
            "Measure Distance Tool"
            #(do (set-show-info! false)
                 (reset! !/show-camera? false)
                 (reset! !/show-match-drop? false)
                 (swap! !/show-measure-tool? not)
                 (gtag-tool @!/show-measure-tool? "measure-distance"))]
           [:legend
            (str (hs-str @!/show-legend?) " legend")
            #(do (swap! !/show-legend? not)
                 (gtag-tool @!/show-legend? "legend"))
            false]]
          (remove nil?)
          (map-indexed (fn [i [icon hover-text on-click active?]]
                         ^{:key i} [tool-tip-wrapper
                                    hover-text
                                    :right
                                    [tool-button icon on-click active?]])))]))
