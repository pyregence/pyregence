(ns pyregence.state
  (:require [reagent.core :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Forecast/Layer State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "A keyword containing the currently selected forecast. The possible
options come from the -options maps in config.cljs, e.g. `:active-fire`."}
  *forecast (r/atom nil))
(defonce ^{:doc "A keyword identifying the current forecast-type:
either :near-term or :long-term"}
  *forecast-type (r/atom nil))
(defonce ^{:doc "The index/hour of the currently selected layer's forecast. Corresponds with the `:hour`
field associated with each layer from the `param-layers`, starts at 0, and is an integer."}
  *layer-idx (r/atom 0))
(defonce ^{:doc "A map containing the selected parameters/inputs from each forecast tab.
Ex: {:fuels {:layer :fbfm40, :model :landfire-2.2.0, :model-init :20210407_000000} ... }"}
  *params (r/atom {}))
(defonce ^{:doc "A map containing the selected optional layers.
Ex: {:us-trans-lines {:opt-label \"Transmission lines\", :z-index 107, ...},
     :conus-buildings {:opt-label \"Structures\", :z-index 104, ...}}"}
  *optional-layers (r/atom {}))
(defonce ^{:doc "The most recently enabled optional layer."}
  most-recent-optional-layer (r/atom {}))
(defonce ^{:doc "A UTC keyword value, corresponding to the last selected \"Forecast Start Time\""}
  *last-start-time (r/atom nil))
(defonce ^{:doc "An integer value, from 0 to 100, that designates the opacity for the active layer. Defaults to 100"}
  active-opacity (r/atom 70.0))
(defonce ^{:doc "A map that combines the values from the `options-config` and the processed output from a call to process-capabilities! in near_term_forecast.cljs.
The `options-config`:
Holds the value of either the `near-term-forecast-options` or `long-term-forecast-options` map as defined in `config.cljs`.
The top level keys correspond to a particular `Map Tab` and the values correspond to a nested structure containing configuration options for that tab.
Important options include the `:opt-label` string (which describes the label of the tab on the front-end),
the `:underlays` map (which defines the optional layers available on the given tab), and the `:params` map
(which is used to define each input box and the options available in that input box on the side-panel for the given tab).
The processed output from `process-capabilities!`:
Notably contains any fire names and/or user layers (obtained from the back-end) that are added to the capabilities atom
(since the fire names and user layers are not defined initially in config.cljs."}
  capabilities (r/atom {}))
(defonce ^{:doc "Contains the map associated with the :params key inside of the currently selected forecast in the capabilities atom.
For example, the processed-params for a user on the Weather tab would be a map containing the :band, :model, and :model-init maps."}
  processed-params (r/atom []))
(defonce ^{:doc " A vector of maps containing all of the layers for the current forecast sorted by hour. These layers are obtained from
the back-end through the capabilities.clj/get-layers function which returns all layers that match a given set of strings for filtering.
For example, if the user is on the Weather tab looking at the Temperature (F) forecast, param-layers will be a vector of length 145
where each entry in the vector (which is a map) contains information about one Temperature (F) layer. A length of 145 also indicates
that there are 145 different time steps in this specific forecast."}
  param-layers (r/atom []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Show/Hide State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the Wildfire Camera Tool."}
  show-camera? (r/atom false))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the Fire History Tool."}
  show-fire-history? (r/atom false))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the Point Information Tool."}
  show-info? (r/atom false))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the Legend Box."}
  show-legend? (r/atom true))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the Match Drop Tool."}
  show-match-drop? (r/atom false))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the Mesaure Distance Tool."}
  show-measure-tool? (r/atom false))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the Side Panel."}
  show-panel? (r/atom true))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the Red Flag Warning Tool."}
  show-red-flag? (r/atom false))
(defonce ^{:doc "A boolean that maintains UTC or local time display preference."}
  show-utc? (r/atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Point Information State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "The atom used to populate the point information tool for a layer. Contains the processed output from a GetFeatureInfo call.
Note that atom may contain either single-point-info (in which case last-clicked-info is an integer or double) or
timeline-point-info (in which case last-clicked-info is a vector of maps where each entry in the vector has the point info
for one time step in the forecast)."}
  last-clicked-info (r/atom []))
(defonce ^{:doc "A set containing all of the quantities associated with `nodata` points."}
  no-data-quantities (r/atom #{}))
(defonce ^{:doc "The atom used to populate the legend for a layer. Contains the processed output from a GetLegendGraphic call.
The legend-list is a vector of maps where each map contains an entry in the legend for the active layer.
Each entry in the legend contains the legend's label, value, color, and opacity."}
  legend-list (r/atom []))
(defonce ^{:doc "A boolean letting us know if the point info is loading or not."}
  point-info-loading? (r/atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Miscellaneous State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "For the currently logged in user, stores a list of all of the organizations
that they belong to as an Admin or a Member. Will be bound to an empty vector if
the user is not an Admin or Member of at least one organization."}
  user-orgs-list (r/atom []))
(defonce ^{:doc "Stores a list of all organizations that have PSPS data."}
  psps-orgs-list (r/atom []))
(defonce ^{:doc "Stores a list of all PSPS organizations that a user belongs to."}
  user-psps-orgs-list (r/atom []))
(defonce ^{:doc "A boolean that enables time-step animation for the Time Slider when true."}
  animate? (r/atom false))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the loading modal dialog."}
  loading? (r/atom true))
(defonce ^{:doc "A boolean that designates if the browser's innerwidth reaches below the 800 pixel wide breakpoint.
A browser more narrow than 800 pixels enables a special mobile styling across the front-end."}
  mobile? (r/atom false))
(defonce ^{:doc "A boolean that maintains the hide/show toggle state of the 3D Terrain Tool."}
  terrain? (r/atom false))
(defonce ^{:doc "The GeoJSON response from pinging the ALERT Wildfire and ALERTCalifornia camera APIs.
Returns all cameras currently available in ALERT Wildfire and ALERTCalifornia. Note that all ALERT Wildfire
cameras from California are filtered out since they do not work anymore. We instead use ALERTCalifornia for
California cameras. This atom is used to create the camera layer in mapbox.cljs."}
  the-cameras (r/atom nil))
(defonce ^{:doc "The set of avialable weather dates for Match Drop."}
  md-available-dates (r/atom {}))
(defonce ^{:doc "An integer that keeps track of the number of active fires."}
 active-fire-count (r/atom 0))
(defonce ^{:doc "An Integer that keeps track of the number of times the active fires tab has been clicked."}
 active-fire-tab-click-count (r/atom 0))

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
