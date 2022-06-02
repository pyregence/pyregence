(ns pyregence.components.map-controls.tool-bar
  (:require [clojure.core.async :refer [<! go]]
            [pyregence.state    :as !]
            [pyregence.styles   :as $]
            [pyregence.utils    :as u]
            [pyregence.config   :as c]
            [pyregence.components.mapbox    :as mb]
            [pyregence.components.messaging :refer [toast-message!]]
            [pyregence.components.common    :refer [tool-tip-wrapper hs-str]]
            [pyregence.components.map-controls.tool-button :refer [tool-button]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn toggle-red-flag-layer!
  "Toggle the red-flag warning layer"
  []
  (swap! !/show-red-flag? not)
  (go
    (let [data (-> (<! (u/call-clj-async! "get-red-flag-layer"))
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
  (when (and @!/show-fire-history? (not (mb/layer-exists? "fire-history")))
    (mb/create-fire-history-layer! "fire-history"
                                   "fire-detections_fire-history%3Afire-history"
                                   :pyrecast)
    (mb/create-fire-history-label-layer! "fire-history-centroid"
                                         "fire-detections_fire-history%3Afire-history-centroid"
                                         :pyrecast))
  (mb/set-visible-by-title! "fire-history" @!/show-fire-history?)
  (mb/set-visible-by-title! "fire-history-centroid" @!/show-fire-history?)
  (mb/clear-popup! "fire-history"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tool-bar [set-show-info! get-any-level-key user-id]
  [:div#tool-bar {:style ($/combine $/tool $/tool-bar {:top "16px"})}
   (->> [(when-not @!/mobile?
           [:info
            (str (hs-str @!/show-info?) " point information")
            #(do (set-show-info! (not @!/show-info?))
                 (reset! !/show-match-drop? false)
                 (reset! !/show-camera? false))
            @!/show-info?])
         (when (and (c/feature-enabled? :match-drop) (number? user-id) (not @!/mobile?))
           [:flame
            (str (hs-str @!/show-match-drop?) " match drop tool")
            #(do (swap! !/show-match-drop? not)
                 (set-show-info! false)
                 (reset! !/show-camera? false))
            @!/show-match-drop?])
         (when-not (or @!/mobile? (get-any-level-key :hide-camera?))
           [:camera
            (str (hs-str @!/show-camera?) " cameras")
            #(do (swap! !/show-camera? not)
                 (set-show-info! false)
                 (reset! !/show-match-drop? false))
            @!/show-camera?])
         (when-not (get-any-level-key :hide-flag?)
           [:flag
            (str (hs-str @!/show-red-flag?) " red flag warnings")
            toggle-red-flag-layer!])
         (when (and (c/feature-enabled? :fire-history) (not (get-any-level-key :hide-history?)))
           [:clock
            (str (hs-str @!/show-fire-history?) " fire history")
            toggle-fire-history-layer!])
         [:legend
          (str (hs-str @!/show-legend?) " legend")
          #(swap! !/show-legend? not)
          false]]
        (remove nil?)
        (map-indexed (fn [i [icon hover-text on-click active?]]
                       ^{:key i} [tool-tip-wrapper
                                  hover-text
                                  :right
                                  [tool-button icon on-click active?]])))])
