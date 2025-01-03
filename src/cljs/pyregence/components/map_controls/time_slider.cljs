(ns pyregence.components.map-controls.time-slider
  (:require [pyregence.components.common                   :refer [radio tool-tip-wrapper]]
            [pyregence.components.map-controls.tool-button :refer [tool-button]]
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

(defn time-slider [layer-full-time select-layer! select-time-zone!]
  (r/with-let [*speed          (r/atom 2)
               step-count      #(count (or (:times (first @!/param-layers))
                                           @!/param-layers))
               cycle-layer!    (fn [change]
                                 (select-layer! (mod (+ change @!/*layer-idx) (step-count))))
               loop-animation! (fn la []
                                 (when @!/animate?
                                   (cycle-layer! 1)
                                   (js/setTimeout la (get-in c/speeds [@*speed :delay]))))]
    [:div#time-slider {:style ($/combine $/tool $time-slider)}
     (when-not @!/mobile?
       [:div {:style ($/combine $/flex-col {:align-items "flex-start"})}
        [radio "UTC"   @!/timezone :utc  select-time-zone! true]
        [radio "Local" @!/timezone :local select-time-zone! true]])
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
       (str (if @!/animate? "Pause" "Play") " animation")
       :bottom
       [tool-button
        (if @!/animate? :pause-button :play-button)
        #(do (swap! !/animate? not)
             (loop-animation!))]]
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
                     c/speeds)])]))
