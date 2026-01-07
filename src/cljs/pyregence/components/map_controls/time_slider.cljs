(ns pyregence.components.map-controls.time-slider
  (:require [pyregence.components.common                   :refer [radio tool-tip-wrapper]]
            [pyregence.components.map-controls.tool-button :refer [tool-button]]
            [pyregence.components.mapbox                   :as mb]
            [pyregence.config                              :as c]
            [pyregence.state                               :as !]
            [pyregence.styles                              :as $]
            [pyregence.utils.dom-utils                     :as u-dom]
            [reagent.core                                  :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $time-slider []
  {:align-items   "center"
   :border-radius "5px 5px 0 0"
   :bottom        "0"
   :display       "flex"
   :left          "0"
   :margin-left   "auto"
   :margin-right  "auto"
   :padding       ".5rem"
   :right         "0"
   :width         (if @!/mobile? "20rem" "min-content")})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn time-slider [layer-full-time select-layer! select-time-zone! get-layer-info-fn]
  (r/with-let [*speed            (r/atom 2)
               *loop-id          (r/atom nil)
               *init-id          (r/atom nil)

               step-count        (fn [] (count (or (:times (first @!/param-layers))
                                                   @!/param-layers)))

               cycle-layer!      (fn [delta]
                                   (select-layer! (mod (+ delta @!/*layer-idx) (step-count))))

               loop-animation!   (fn loop-animation! []
                                   (when @!/animate?
                                     (reset! *init-id nil)
                                     (let [delay    (get-in c/speeds [@*speed :delay])
                                           next-idx (mod (inc @!/*layer-idx) (step-count))
                                           schedule #(reset! *loop-id (js/setTimeout loop-animation! %))]
                                       (cond
                                         @!/paused-for-buffer?
                                         (if (!/buffer-healthy?)
                                           (do (reset! !/paused-for-buffer? false)
                                               (select-layer! next-idx #(when @!/animate? (schedule delay))))
                                           (schedule 100))

                                         (!/should-start-buffering?)
                                         (do (reset! !/paused-for-buffer? true)
                                             (schedule 100))

                                         :else
                                         (select-layer! next-idx #(when @!/animate? (schedule delay)))))))

               begin-animation!  (fn [initial-delay]
                                   (mb/start-buffer-polling!)
                                   (mb/update-visibility-window! @!/*layer-idx)
                                   (reset! !/animate? true)
                                   (reset! !/paused-for-buffer? true)
                                   (if initial-delay
                                     (reset! *init-id (js/setTimeout loop-animation! initial-delay))
                                     (loop-animation!)))

               start-animation!  (fn []
                                   (let [{:keys [geoserver-key style]} (get-layer-info-fn)
                                         first-play? (not @!/layers-ready?)
                                         total       (step-count)]
                                     (reset! !/total-frames total)
                                     (if first-play?
                                       (when (pos? total)
                                         (let [{:keys [succeeded]} (mb/create-animation-layers! geoserver-key style)]
                                           (if (pos? succeeded)
                                             (do (reset! !/layers-ready? true)
                                                 (begin-animation! 1000))
                                             (do (reset! !/animate? false)
                                                 (reset! !/layers-ready? false)
                                                 (mb/cleanup-animation-layers!)))))
                                       (when @!/layers-ready?
                                         (begin-animation! nil)))))

               stop-animation!   (fn []
                                   (reset! !/animate? false)
                                   (reset! !/paused-for-buffer? false)
                                   (mb/stop-buffer-polling!)
                                   (when-let [id @*loop-id]
                                     (js/clearTimeout id)
                                     (reset! *loop-id nil))
                                   (when-let [id @*init-id]
                                     (js/clearTimeout id)
                                     (reset! *init-id nil)))

               toggle-animation! (fn []
                                   (if @!/animate? (stop-animation!) (start-animation!)))]

    [:div#time-slider {:style ($/combine $/tool $time-slider)}
     (when-not @!/mobile?
       [:div {:style ($/combine $/flex-col {:align-items "flex-start"})}
        [radio "UTC"   @!/show-utc? true  select-time-zone! true]
        [radio "Local" @!/show-utc? false select-time-zone! true]])

     [:div {:style ($/flex-col)}
      [:input {:type      "range"
               :min       "0"
               :max       (dec (step-count))
               :value     (min (dec (step-count)) (or @!/*layer-idx 0))
               :style     {:width "12rem"}
               :on-change #(let [was-animating? @!/animate?]
                             (when was-animating? (stop-animation!))
                             (select-layer! (u-dom/input-int-value %))
                             (when was-animating? (start-animation!)))}]
      [:label (if (or @!/paused-for-buffer? @!/swapping?)
                "Loading..."
                layer-full-time)]]

     [:span {:style {:display     "flex"
                     :align-items "center"
                     :margin      "0 1rem"}}
      [tool-tip-wrapper "Previous layer" :bottom
       [tool-button :previous-button #(cycle-layer! -1)]]
      [tool-tip-wrapper (if @!/animate? "Pause animation" "Play animation") :bottom
       [tool-button (if @!/animate? :pause-button :play-button) toggle-animation!]]
      [tool-tip-wrapper "Next layer" :bottom
       [tool-button :next-button #(cycle-layer! 1)]]]

     (when-not @!/mobile?
       [:select {:value     (or @*speed 1)
                 :style     ($/combine $/dropdown {:padding "0 0.5rem" :width "5rem"})
                 :on-change #(reset! *speed (u-dom/input-int-value %))}
        (map-indexed (fn [idx {:keys [opt-label]}]
                       [:option {:key idx :value idx} opt-label])
                     c/speeds)])]

    (finally
      (mb/stop-buffer-polling!)
      (when-let [id @*loop-id] (js/clearTimeout id))
      (when-let [id @*init-id] (js/clearTimeout id))
      (when @!/animate? (reset! !/animate? false))
      (when @!/layers-ready?
        (mb/cleanup-animation-layers!)
        (reset! !/layers-ready? false)))))
