(ns pyregence.state
  (:require [reagent.core :as r]))

; TODO: Add detailed docstrings to each of the state atoms.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Forecast/Layer State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce *forecast        (r/atom nil))
(defonce *layer-idx       (r/atom 0))
(defonce *params          (r/atom {}))
(defonce active-opacity   (r/atom 100.0))
(defonce capabilities     (r/atom []))
(defonce options          (r/atom {}))
(defonce param-layers     (r/atom []))
(defonce processed-params (r/atom []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Show/Hide State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce show-camera?       (r/atom false))
(defonce show-fire-history? (r/atom false))
(defonce show-info?         (r/atom false))
(defonce show-legend?       (r/atom true))
(defonce show-match-drop?   (r/atom false))
(defonce show-panel?        (r/atom true))
(defonce show-red-flag?     (r/atom false))
(defonce show-utc?          (r/atom true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Point Information State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce last-clicked-info   (r/atom []))
(defonce ^{:doc "A set containing all of the quantities associated with `nodata` points."}
  no-data-quantities (r/atom #{}))
(defonce legend-list         (r/atom []))
(defonce point-info-loading? (r/atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Miscellaneous State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "For the currently logged in user, stores a list of all of the organizations that they belong to."}
  user-org-list (r/atom []))
(defonce animate?    (r/atom false))
(defonce loading?    (r/atom true))
(defonce mobile?     (r/atom false))
(defonce terrain?    (r/atom false))
(defonce the-cameras (r/atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State Setters
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-state-legend-list!
  "A function to set the state of the legend-list atom and all other dependent atoms."
  [new-legend-list]
  (reset! legend-list new-legend-list)
  (reset! no-data-quantities
          (into #{}
                (for [entry @legend-list
                      :when (= (get entry "label") "nodata")]
                  (get entry "quantity")))))

; TODO: Add in more state setters here.
