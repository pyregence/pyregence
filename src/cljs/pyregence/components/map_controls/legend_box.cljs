(ns pyregence.components.map-controls.legend-box
  (:require [pyregence.state            :as !]
            [pyregence.styles           :as $]
            [pyregence.utils            :as u]
            [pyregence.utils.data-utils :as u-data]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $legend-color [color opacity]
  {:background-color color
   :height           "1rem"
   :margin-right     ".5rem"
   :min-width        "1rem"
   :opacity          opacity})

(defn- $legend-box [show? time-slider?]
  {:left          (if (and show? (not @!/mobile?)) "20rem" "2rem")
   :max-height    (if (and @!/mobile? time-slider?)
                    "calc(100% - 106px)"
                    "calc(100% - 52px)")
   :overflow-x    "hidden"
   :overflow-y    "auto"
   :padding-left  ".5rem"
   :padding-right ".75rem"
   :padding-top   ".5rem"
   :top           "16px"
   :transition    "all 200ms ease-in"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn legend-box
  "A component for the legend box. The `nodata` entries are removed before the
   legend is displayed. The legend is displayed with the provided units and can
   optionally be reversed in order."
  [reverse? time-slider? units]
  (reset! !/show-legend? (not @!/mobile?))
  (fn [reverse? time-slider? units]
    (when (and @!/show-legend? (seq @!/legend-list))
      (let [processed-legend (u-data/filter-no-data @!/legend-list)]
        [:div#legend-box {:style ($/combine $/tool ($legend-box @!/show-panel? time-slider?))}
         [:div {:style {:display        "flex"
                        :flex-direction "column"}}
          (map-indexed (fn [i leg]
                         ^{:key i}
                         [:div {:style ($/combine {:display "flex" :justify-content "flex-start"})}
                          [:div {:style ($legend-color (get leg "color") (get leg "opacity"))}]
                          [:label (str (get leg "label") (u/clean-units units))]])
                       (if reverse?
                         (reverse processed-legend)
                         processed-legend))]]))))
