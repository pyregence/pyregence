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
  (r/with-let [*speed     (r/atom 2)
               *loop-id   (r/atom nil)
               *init-id   (r/atom nil)
               step-count #(count (or (:times (first @!/param-layers))
                                      @!/param-layers))

               cycle-layer! (fn [change]
                              (select-layer! (mod (+ change @!/*layer-idx) (step-count))))

               loop-animation! (fn la []
                                 (when @!/animate?
                                   (reset! *init-id nil)
                                   (reset! !/preparing? false)
                                   (cycle-layer! 1)
                                   (reset! *loop-id
                                     (js/setTimeout la (get-in c/speeds [@*speed :delay])))))

               begin-animation! (fn [initial-delay]
                                 (mb/lock-viewport!)
                                 (reset! !/animate? true)
                                 (if initial-delay
                                   (reset! *init-id (js/setTimeout loop-animation! initial-delay))
                                   (loop-animation!)))

               start-animation! (fn []
                                 (let [{:keys [geoserver-key style]} (get-layer-info-fn)
                                       first-play? (not @!/layers-ready?)

                                       create-layers-fn (fn []
                                                         (if (zero? (step-count))
                                                           (do
                                                             (reset! !/preparing? false)
                                                             (js/console.warn "Cannot start animation: layers not loaded yet"))
                                                           (do
                                                             (let [{:keys [total succeeded failed]} (mb/create-animation-layers! geoserver-key style)]
                                                               (if (and (pos? total) (>= succeeded (/ total 2)))
                                                                 (reset! !/layers-ready? true)
                                                                 (do
                                                                   (reset! !/layers-ready? false)
                                                                   (reset! !/preparing? false)
                                                                   (js/console.error "Animation failed:" failed "of" total "layers failed")
                                                                   (mb/cleanup-animation-layers!))))

                                                             (when @!/layers-ready?
                                                               (let [initial-delay (max 1500 (min 4000 (* (step-count) 25)))]
                                                                 (begin-animation! initial-delay))))))]

                                   (if first-play?
                                     (do
                                       (reset! !/preparing? true)
                                       (if (pos? (step-count))
                                         (create-layers-fn)
                                         (js/setTimeout create-layers-fn 800)))
                                     (when @!/layers-ready?
                                       (begin-animation! nil)))))

               toggle-animation! (fn []
                                  (if (or @!/animate? @!/preparing?)
                                    (do
                                      (reset! !/animate? false)
                                      (reset! !/preparing? false)
                                      (when-let [id @*loop-id]
                                        (js/clearTimeout id)
                                        (reset! *loop-id nil))
                                      (when-let [id @*init-id]
                                        (js/clearTimeout id)
                                        (reset! *init-id nil))
                                      (mb/unlock-viewport!))
                                    (start-animation!)))]

    [:div#time-slider {:style ($/combine $/tool $time-slider)}
     (when-not @!/mobile?
       [:div {:style ($/combine $/flex-col {:align-items "flex-start"})}
        [radio "UTC"   @!/show-utc? true  select-time-zone! true]
        [radio "Local" @!/show-utc? false select-time-zone! true]])
     [:div {:style ($/flex-col)}
      [:input {:style {:width "12rem"}
               :type      "range"
               :min       "0"
               :max       (dec (step-count))
               :value     (min (dec (step-count)) (or @!/*layer-idx 0))
               :on-change #(select-layer! (u-dom/input-int-value %))}]
      [:label layer-full-time]]
     [:span {:style {:display "flex" :margin "0 1rem"}}
      [tool-tip-wrapper
       "Previous layer"
       :bottom
       [tool-button :previous-button #(cycle-layer! -1)]]
      [tool-tip-wrapper
       (cond
         @!/preparing? "Preparing animation..."
         @!/animate? "Pause animation"
         :else "Play animation")
       :bottom
       [tool-button
        (cond
          @!/preparing? :pause-button
          @!/animate? :pause-button
          :else :play-button)
        toggle-animation!]]
      [tool-tip-wrapper
       "Next layer"
       :bottom
       [tool-button :next-button #(cycle-layer! 1)]]]
     (when-not @!/mobile?
       [:select {:style     ($/combine $/dropdown {:padding "0 0.5rem" :width "5rem"})
                 :value     (or @*speed 1)
                 :on-change #(reset! *speed (u-dom/input-int-value %))}
        (map-indexed (fn [id {:keys [opt-label]}]
                       [:option {:key id :value id} opt-label])
                     c/speeds)])]
    (finally
      (when-let [id @*loop-id]
        (js/clearTimeout id))
      (when-let [id @*init-id]
        (js/clearTimeout id))
      (reset! !/preparing? false)
      (when @!/animate?
        (reset! !/animate? false)
        (mb/unlock-viewport!)))))
