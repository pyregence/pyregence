(ns pyregence.state
  (:require [reagent.core :as r]))

; TODO: Add detailed docstrings to each of the state atoms.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Forecast/Layer State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "A keyword containing the currently selected forecast. The possible
options come from the -options maps in config.cljs, e.g. `:active-fire`."}
  *forecast (r/atom nil))
(defonce ^{:doc "The index/hour of the currently selected layer's forecast. Corresponds with the `:hour`
field associated with each layer, starts at 0, and is an integer."}
  *layer-idx (r/atom 0))
(defonce ^{:doc "A map containing the selected parameters/inputs from each forecast tab.
Ex: {:fuels {:layer :fbfm40, :model :landfire, :model-init :20210407_000000} ... }"}
  *params (r/atom {}))
(defonce ^{:doc "An integer value, from 0 to 100, that designates the opacity for the active layer. Defaults to 0"}
  active-opacity (r/atom 100.0))
(defonce ^{:doc "A `map` that combines the configuration in `config.cljs` and the processed output
from a call to `set-all-capabilities`. The toplevel keys correspond to a particular `Map Tab` and
the values correspond to a nested structure containing layer and feature data,
as well the supporting data to configure and drive the visual interactive elements on the active tab."}
  capabilities (r/atom []))
(defonce ^{:doc "A map containing configuration options for the current forecast."}
  options (r/atom {}))
(defonce ^{:doc "A vector of layers that match the set of strings to filter layers by."}
  param-layers (r/atom []))
(defonce ^{:doc "Contains the populated form field values to populate the `Layer Selection` form on the side-panel."}
  processed-params (r/atom []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Show/Hide State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the Wildfire Camera Feature."}
  show-camera? (r/atom false))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the Fire History Feature."}
  show-fire-history? (r/atom false))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the Point Information Tool."}
  show-info? (r/atom false))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the Legend Box."}
  show-legend? (r/atom true))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the Match Drop Feature."}
  show-match-drop? (r/atom false))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the Side Panel."}
  show-panel? (r/atom true))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the Red Flag Warning Feature."}
  show-red-flag? (r/atom false))
(defonce ^{:doc "A boolean that maintains UTC or local time display preference."}
  show-utc? (r/atom true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Point Information State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "Contains the processed output from a GetFeatureInfo call. Note that atom may contain either
single-point-info or timeline-point-info, depending on whether the layer has multiple columns per layer."}
  last-clicked-info (r/atom []))
(defonce ^{:doc "A set containing all of the quantities associated with `nodata` points."}
  no-data-quantities (r/atom #{}))
(defonce ^{:doc "A sequence of data maps to render the Legend for the active layer:
label, quantity, color, and opacity"}
  legend-list (r/atom []))
(defonce ^{:doc "A boolean letting us know if the point info is loading or not."}
  point-info-loading? (r/atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Miscellaneous State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "For the currently logged in user, stores a list of all of the
organizations that they belong to."}
  user-org-list (r/atom []))
(defonce ^{:doc "A boolean that enables time-step animation for the Time Slider when true."}
  animate? (r/atom false))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the modal dialog."}
  loading? (r/atom true))
(defonce ^{:doc "A boolean that designates if the browser's innerwidth reaches below the 800 pixels wide breakpoint."}
  mobile? (r/atom false))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the 3D Terrain Feature."}
  terrain? (r/atom false))
(defonce ^{:doc "The geojson feature collection of Wildfire Cameras."}
  the-cameras (r/atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; config.edn State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "A map that defines the default tab to display for the near-term and long-term forecast pages."}
  default-forecasts (atom {}))
(defonce ^{:doc "A boolean that dictates the development or production mode of the application."}
  dev-mode? (atom nil))
(defonce ^{:doc "A boolean map describing what features are enabled or disabled."}
  feature-flags (atom nil))
(defonce ^{:doc "A map of Geoserver URLs that are used to query layer data from."}
  geoserver-urls (atom nil))
(defonce ^{:doc "The string value of the mapbox access token that is declared in config.edn"}
  mapbox-access-token (atom nil))
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
