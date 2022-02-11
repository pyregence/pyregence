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
(defonce geoserver-key    (r/atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Show/Hide State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce show-camera?       (r/atom false))
(defonce show-fire-history? (r/atom false))
(defonce show-info?         (r/atom false))
(defonce show-match-drop?   (r/atom false))
(defonce show-red-flag?     (r/atom false))
(defonce show-utc?          (r/atom true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Point Information State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce last-clicked-info   (r/atom []))
(defonce legend-list         (r/atom []))
(defonce point-info-loading? (r/atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Miscellaneous State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce animate?    (r/atom false))
(defonce loading?    (r/atom true))
(defonce mobile?     (r/atom false))
(defonce terrain?    (r/atom false))
(defonce the-cameras (r/atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State Setters
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; TODO: Add in state setters here.
