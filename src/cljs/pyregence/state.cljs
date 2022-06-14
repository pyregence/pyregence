(ns pyregence.state
  (:require [reagent.core :as r]))

; TODO: Add detailed docstrings to each of the state atoms.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Forecast/Layer State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "A keyword containing the currently selected forecast. The possible
options come from the -options maps in config.cljs,  e.g. `:active-fire`."}
  *forecast (r/atom nil))
(defonce ^{:doc "The index/hour of the currently selected layer's forecast. Corresponds with the `:hour`
field associated with each layer, starts at 0, and is an integer."}
  *layer-idx (r/atom 0))
(defonce ^{:doc "A map containing the selected parameters/inputs from each forecast tab.
Ex: {:fuels {:layer :fbfm40, :model :landfire, :model-init :20210407_000000} ... }"}
  *params (r/atom {}))
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
(defonce ^{:doc "A boolean letting us know if the point info is loading or not."}
  point-info-loading? (r/atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Miscellaneous State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "For the currently logged in user, stores a list of all of the
organizations that they belong to."}
  user-org-list (r/atom []))
(defonce animate?    (r/atom false))
(defonce loading?    (r/atom true))
(defonce mobile?     (r/atom false))
(defonce terrain?    (r/atom false))
(defonce the-cameras (r/atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; config.edn State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce default-forecasts (atom {}))
(defonce dev-mode? (atom nil))
(defonce mapbox-access-token (atom nil))

(defonce ^{:doc "The Pyrecast auth token for making API requests."}
  pyr-auth-token (r/atom nil))

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
