(ns pyregence.components.mapbox
  (:require [clojure.core.async          :refer [chan go >! <!]]
            [clojure.string              :as str]
            [goog.dom                    :as dom]
            [pyregence.config            :as c]
            [pyregence.geo-utils         :as g]
            [pyregence.state             :as !]
            [pyregence.utils.async-utils :as u-async]
            [pyregence.utils.data-utils  :as u-data]
            [pyregence.utils.misc-utils  :as u-misc]
            [reagent.core                :as r]
            [reagent.dom                 :refer [render]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mapbox Aliases
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private mapbox       js/mapboxgl)
(def ^:private Map          js/mapboxgl.Map)
(def ^:private LngLatBounds js/mapboxgl.LngLatBounds)
(def ^:private Marker       js/mapboxgl.Marker)
(def ^:private Popup        js/mapboxgl.Popup)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Mapbox map JS instance. See: https://docs.mapbox.com/mapbox-gl-js/api/map/
(defonce ^{:doc "A reference to the mapbox object rendered on the \"map\" element by `init-map` function below."}
  the-map (r/atom nil))
;; Project layers (and their associated metadata) for a forecast as defined in `config.cljs`
(defonce ^{:doc "Contains the project layers defined by forecast type."}
  project-layers (r/atom nil))
(def ^{:private true :doc "A reference to the popup object creaet with `init-popup!`."}
  the-popup (r/atom nil))
(def ^{:private true :doc "A map of events to event listeners on an associated layer."}
  events (atom {}))
(def ^{:private true :doc "A vector of marker objects to track a current set of displayed markers on the map."}
  markers (atom []))
(def ^{:private true :doc "A map to track the interactive state of a feature: i.e. hovered, clicked, etc."}
  feature-state (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private fire-active "fire-active")
(def ^:private mapbox-dem  "mapbox-dem")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Layer Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-layer
  "Gets a specific layer object by id."
  [id]
  (.getLayer @the-map id))

(defn- get-layer-type
  "Returns the layer's type from its id string. Example:
   fire-detections_active-fires:active-fires_20210929_155400 => fire-detections"
  [layer-id]
  {:pre [layer-id]}
  (if (str/includes? layer-id "isochrones")
    "isochrones"
    (first (str/split layer-id #"_"))))

(defn- get-layer-metadata
  "Gets the value of a specified property of a layer's metadata."
  [layer property]
  (or (get-in layer ["metadata" property])
      (get-in layer [:metadata (keyword property)])
      (u-misc/try-js-aget layer "metadata" property)))

(defn- get-layer-type-metadata-property
  "Gets the specified metadata property (originally set in config.cljs) based on a layer's type."
  [type metadata-property]
  (get-in @project-layers [(keyword type) metadata-property]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Map Information
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-style
  "Returns the Mapbox style object."
  []
  (when @the-map
    (-> @the-map .getStyle (js->clj))))

(defn- index-of
  "Returns first index of item in collection that matches predicate."
  [pred xs]
  (->> xs
       (keep-indexed (fn [idx x] (when (pred x) idx)))
       (first)))

(defn- get-layer-idx-by-id
  "Returns index of layer with matching id."
  [id layers]
  (index-of #(= id (get % "id")) layers))

(defn get-zoom-info
  "Get zoom information. Returns [zoom min-zoom max-zoom]."
  []
  (let [m @the-map]
    [(.getZoom m)
     (.getMinZoom m)
     (.getMaxZoom m)]))

(defn layer-exists?
  "Returns true if the layer with matching id exists."
  [id]
  (get-layer id))

(defn get-distance-meters
  "Returns distance in meters between center of the map and 100px to the right.
   Used to define the scale-bar map control."
  []
  (let [y     (-> @the-map .getContainer .-clientHeight (/ 2.0))
        left  (.unproject @the-map #js [0.0 y])
        right (.unproject @the-map #js [100.0 y])]
    (.distanceTo left right)))

(defn get-center
  "Retrieves center as `{:lat ## :lon ##}`"
  []
  (let [center (.getCenter @the-map)]
    {:lat (aget center "lat")
     :lng (aget center "lng")}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Modify Map
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-zoom!
  "Sets the zoom level of the map to `zoom`."
  [zoom]
  (.easeTo @the-map (clj->js {:zoom zoom :animate true})))

(defn zoom-to-extent!
  "Pans/zooms the map to the provided extents."
  [[minx miny maxx maxy] current-layer & [max-zoom]]
  (.fitBounds @the-map
              (LngLatBounds. (clj->js [[minx miny] [maxx maxy]]))
              (-> {:linear  true
                   :padding (if (#{"fire-active" "fire-risk-forecast" "fire-detections"} (get-layer-type (:layer current-layer)))
                              {:top 150 :bottom 150 :left 150 :right 150}
                              0)}
                  (merge (when max-zoom {:maxZoom max-zoom}))
                  (clj->js))))

(defn set-center!
  "Centers the map on `center` with a minimum zoom value of `min-zoom`."
  [center min-zoom]
  (let [zoom (max (first (get-zoom-info)) min-zoom)]
    (.easeTo @the-map (clj->js {:zoom zoom :center center :animate true}))))

(defn ease-to!
  "Changes the position of the map to `center` given `zoom`, `pitch`, and `bearing`.
   Can also supply `min-zoom`.."
  [{:keys [zoom min-zoom] :as location}]
  (let [new-zoom (or zoom
                     (max (first (get-zoom-info)) (or min-zoom 0)))]
    (.easeTo @the-map (clj->js (-> location
                                   (assoc :zoom new-zoom)
                                   (assoc :animate (or (:animate location) true))
                                   (dissoc :min-zoom))))))

(defn fly-to!
  "Flies the map view to `center` at `zoom` with `bearing` and `pitch`."
  [new-location]
  (.flyTo @the-map (clj->js (merge {:bearing 0 :pitch 0 :zoom 0 :center [0 0]} new-location))))

(defn center-on-overlay!
  "Centers the map on the marker."
  []
  (when-let [the-marker (first @markers)]
    (-> the-marker
        :marker
        (.getLngLat)
        (set-center! 12.0))))

(defn set-center-my-location!
  "Sets the center of the map using a geo-location event."
  [event]
  (let [coords (.-coords event)
        lng    (.-longitude coords)
        lat    (.-latitude  coords)]
    (set-center! [lng lat] 12.0)))

(defn resize-map!
  "Resizes the map."
  []
  (when @the-map
    (.resize @the-map)))

(defn- upsert-layer
  "Inserts `new-layer` into `v` if the 'id' does not already exist, or updates
   the matching row if it does exist."
  [v {:keys [id] :as new-layer}]
  (if-let [idx (get-layer-idx-by-id id v)]
    (assoc v idx new-layer)
    (conj v new-layer)))

(defn- merge-layers [v new-layers]
  (reduce (fn [acc cur] (upsert-layer acc cur)) (vec v) new-layers))

(defn- process-layer-order!
  "Takes in layers and arranges them in the proper order as
   specified by the z-index. By default, all Mapbox layers are added first."
  [layers]
  (sort-by #(get-layer-metadata % "z-index") layers))

(defn- update-style!
  "Updates the Mapbox Style object. Takes in the current Mapbox Style object
   and optionally updates the `sources` and `layers` keys."
  [style & {:keys [sources layers new-sources new-layers]}]
  (let [new-style (cond-> style
                    sources     (assoc "sources" sources)
                    layers      (assoc "layers" layers)
                    new-sources (update "sources" merge new-sources)
                    new-layers  (update "layers" merge-layers new-layers)
                    :always     (update "layers" process-layer-order!)
                    :always     (clj->js))]
    (-> @the-map (.setStyle new-style))))

(defn- add-icon! [icon-chan icon-id url & [colorize?]]
  (go
    (if (.hasImage @the-map icon-id)
      (>! icon-chan icon-id)
      (.loadImage @the-map
                  url
                  (fn [_ img]
                    (go
                      (.addImage @the-map
                                 icon-id
                                 img
                                 (if colorize?
                                   #js {:sdf true}
                                   #js {}))
                      (>! icon-chan icon-id)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Markers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-overlay-center
  "Returns marker lng/lat coordinates in the form `[lng lat]`."
  []
  (when-let [the-marker (first @markers)]
    (-> the-marker
        :marker
        .getLngLat
        .toArray
        (js->clj))))

(defn get-overlay-bbox
  "Converts marker lng/lat coordinates to EPSG:3857, finds the current
   resolution and returns a bounding box."
  []
  (when (and (seq @markers)
             (first @markers))
    (let [[lng lat] (get-overlay-center)
          [x y]     (g/EPSG:4326->3857 [lng lat])
          zoom      (get (get-zoom-info) 0)
          res       (g/resolution zoom lat)]
      [x y (+ x res) (+ y res)])))

(defn- add-marker-to-map!
  "Adds a marker at the lon-lat coordinates of the click event."
  [[lng lat]]
  (let [new-marker (Marker. #js {:color "#FF0000"})]
    (doto new-marker
      (.setLngLat #js [lng lat])
      (.addTo @the-map))))

(defn- remove-marker-from-map!
  "Removes a marker from the map."
  [{:keys [marker]}]
  (.remove marker))

(defmulti enqueue-marker
  (fn [_ queue-options]
    (get queue-options :queue-type)))

;; A vector with either :fifo or :lifo queue-ing behavior
(defmethod enqueue-marker :fifo [marker {:keys [queue-size] :or {queue-size 1}}] (
  (let [first-of-queue (first @markers)
        rest-of-queue  (vec (rest @markers))]
    (cond
      (< (count @markers) queue-size)
      (swap! markers conj marker)

      (>= (count @markers) queue-size)
      (do
        (remove-marker-from-map! first-of-queue)
        (reset! markers (conj rest-of-queue marker)))))))

(defmethod enqueue-marker :lifo [marker {:keys [queue-size] :or {queue-size 1}}]
  (let [last-of-queue    (peek @markers)
        butlast-of-queue (vec (butlast @markers))]
    (cond
      (< (count @markers) queue-size)
      (swap! markers conj marker)

      (>= (count @markers) queue-size)
      (do
        (remove-marker-from-map! last-of-queue)
        (reset! markers (conj butlast-of-queue marker))))))

(defn remove-markers!
  "Removes the collection of markers that were added to the map"
  []
  (run! #(.remove (% :marker)) @markers)
  (reset! markers []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Popup
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn clear-popup!
  "Remove a popup from the map."
  [& [popup-type]]
  (when (and @the-popup
             (or (nil? popup-type)
                 (= popup-type (.. @the-popup -options -type))))
    (.remove @the-popup)
    (reset! the-popup nil)))

(defn init-popup!
  "Creates a popup at `[lng lat]`, with `body` as the contents. `body` can
   be either HTML string a hiccup style vector."
  [popup-type [lng lat] body {:keys [classname width] :or {width "200px" classname ""}}]
  (clear-popup!)
  (let [popup (Popup. #js {:className classname :maxWidth width :type popup-type})]
    (doto popup
      (.setLngLat #js [lng lat])
      (.setHTML "<div id='mb-popup'></div>")
      (.addTo @the-map))
    (render body (dom/getElement "mb-popup"))
    (reset! the-popup popup)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Events
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- add-event!
  "Adds a listener for `event` with callback `f`. Returns the function `f`, which
   must be stored and passed to `remove-event!` when removing the listener.
   Warning: Only one listener per global/layer event can be added."
  [event f & {:keys [layer]}]
  (swap! events assoc (hash f) {:event event :layer layer :func f})
  (if layer
    (.on @the-map event layer f)
    (.on @the-map event f))
  f)

(defn remove-event!
  "Removes the listener for function `f`."
  [f]
  (let [{:keys [event layer func]} (get @events (hash f))]
    (if layer
      (.off @the-map event layer func)
      (.off @the-map event func))
    (swap! events dissoc (hash f))))

(defn- remove-events!
  "Removes all listeners matching `event-name`. Can also supply `layer-name` to
   only remove events for specific layers."
  [event-name & [layer-name]]
  (doseq [[_ {:keys [event layer func]}] @events
          :when (and (= event event-name)
                     (or (nil? layer-name) (= layer layer-name)))]
    (remove-event! func)))

(defn- event->lnglat [e]
  (-> e (aget "lngLat") .toArray (js->clj)))

(defn enqueue-marker-on-click!
  "Tracks a queue of visible markers on the map."
  [queue-options callback]
  (add-event! "click" (fn [e]
                        (let [lnglat     (event->lnglat e)
                              new-marker (add-marker-to-map! lnglat)]
                          (enqueue-marker {:lnglat lnglat :marker new-marker} queue-options)

                          ;; apply callback to a vector of lnglat coordinates
                          (callback (mapv #(% :lnglat) @markers))))))

(defn add-mouse-move-xy!
  "Passes `[lng lat]` to `f` on mousemove event."
  [f]
  (add-event! "mousemove" (fn [e] (-> e (event->lnglat) (f)))))

(defn clear-highlight!
  "Clears the appropriate highlight of WFS features."
  [source state-tag & [source-layer]]
  (when-let [id (get-in @feature-state [source state-tag])]
    (.setFeatureState @the-map
                      (clj->js (merge {:source source :id id}
                                      (when source-layer {:sourceLayer source-layer})))
                      (clj->js {state-tag false}))
    (swap! feature-state assoc-in [source state-tag] nil)))

(defn- feature-highlight!
  "Sets the appropriate highlight of WFS features."
  [source feature-id state-tag & [source-layer]]
  (clear-highlight! source state-tag source-layer)
  (swap! feature-state assoc-in [source state-tag] feature-id)
  (.setFeatureState @the-map
                    (clj->js (merge {:source source :id feature-id}
                                    (when source-layer {:sourceLayer source-layer})))
                    (clj->js {state-tag true})))

(defn add-feature-highlight!
  "Adds events to highlight WFS features. Optionally can provide a function `click-fn`,
   which will be called on click as `(click-fn <feature-js-object> [lng lat])`"
  [layer source & {:keys [click-fn source-layer]}]
  (remove-events! "mousemove" layer)
  (remove-events! "mouseleave" layer)
  (remove-events! "click" layer)
  (when-not @!/mobile?
    (add-event! "mouseenter"
                (fn [e]
                  (when-let [feature (-> e (aget "features") (first))]
                    (feature-highlight! source (aget feature "id") :hover source-layer)))
                :layer layer)
    (add-event! "mouseleave"
                #(clear-highlight! source :hover source-layer)
                :layer layer)
    (add-event! "mouseout"
                #(clear-highlight! source :hover source-layer)
                :layer layer))
  (add-event! "click"
              (fn [e]
                (when-let [feature (-> e (aget "features") (first))]
                  (feature-highlight! source (aget feature "id") :selected source-layer)
                  (when (fn? click-fn) (click-fn feature (event->lnglat e)))))
              :layer layer))

(defn add-map-zoom-end!
  "Passes current zoom level to `f` on zoom-end event."
  [f]
  (add-event! "zoomend" #(f (get (get-zoom-info) 0))))

;; TODO: Implement
(defn- add-layer-load-fail! [f])

(defn add-map-move!
  "Calls `f` on 'move' event."
  [f]
  (add-event! "move" f))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Modify Layer Properties
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- symbol-opacity [opacity]
  {"text-opacity" ["step" ["zoom"] 0 6 opacity 22 opacity]
   "icon-opacity" opacity})

(defn- circle-opacity [opacity]
  {"circle-opacity"        opacity
   "circle-stroke-opacity" opacity})

(defn- raster-opacity [opacity]
  {"raster-opacity" opacity})

(defn- set-opacity
  "Returns layer with opacity set to `opacity`."
  [layer opacity]
  {:pre [(map? layer) (number? opacity) (<= 0.0 opacity 1.0)]}
  (let [layer-type (get layer "type")
        new-paint  (condp = layer-type
                     "raster" (raster-opacity opacity)
                     "circle" (circle-opacity opacity)
                     "symbol" (symbol-opacity opacity)
                     {})]
    (update layer "paint" merge new-paint)))

(defn set-opacity-by-title!
  "Sets the opacity of all layers whose opacity should change."
  [id opacity] ;TODO, this function doesn't make sense as is because it sets the opacity of all layers currently active, not just one layer by id.
  {:pre [(string? id) (number? opacity) (<= 0.0 opacity 1.0)]}
  (let [style      (get-style)
        new-layers (map (u-misc/call-when #(-> % (get-layer-metadata "type") (get-layer-type-metadata-property :forecast-layer?))
                                          #(set-opacity % opacity))
                        (get style "layers"))]
    (update-style! style :layers new-layers)))

(defn- set-visible
  "Returns layer with visibility set to `visible?`."
  [layer visible?]
  (assoc-in layer ["layout" "visibility"] (if visible? "visible" "none")))

(defn- is-layer-visible?
  "Based on a layer's id, returns whether or not that layer is visible."
  [layer-id]
  (let [layers (get (get-style) "layers")]
    (when-let [idx (get-layer-idx-by-id layer-id layers)]
      (= (get-in layers [idx "layout" "visibility"]) "visible"))))

(defn set-visible-by-title!
  "Sets a layer's visibility."
  [id visible?]
  {:pre [(string? id) (boolean? visible?)]}
  (let [style  (get-style)
        layers (get style "layers")]
    (when-let [idx (get-layer-idx-by-id id layers)]
      (let [new-layers (assoc-in layers [idx "layout" "visibility"] (if visible? "visible" "none"))]
        (update-style! style :layers new-layers)))))

(defn set-multiple-layers-visibility!
  "Sets multiple layers' visibility based on a regex pattern."
  [pattern visible?]
  (let [style      (get-style)
        layers     (get style "layers")
        visibility (if visible? "visible" "none")
        new-layers (mapv (fn [layer]
                           (if (re-find pattern (get layer "id"))
                             (assoc-in layer ["layout" "visibility"] visibility)
                             layer))
                         layers)]
    (update-style! style :layers new-layers)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; WMS Layers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- wms-source [layer-name geoserver-key style]
  {:type     "raster"
   :tileSize 256
   :tiles    [(c/wms-layer-url layer-name geoserver-key style)]})

(defn- wms-layer [layer-name source-name opacity visible? & [z-index]]
  {:id       layer-name
   :type     "raster"
   :source   source-name
   :layout   {:visibility (if visible? "visible" "none")}
   :metadata {:type    (get-layer-type layer-name)
              :z-index (or z-index 1)}
   :paint    {:raster-opacity opacity}})

(defn- build-wms
  "Returns new WMS source and layer in the form `[source [layer]]`.
   `source` must be a valid WMS layer in the geoserver,
   `opacity` must be a float between 0.0 and 1.0."
  [id source geoserver-key opacity visibile? & {:keys [z-index style]}]
  (let [new-source {id (wms-source source geoserver-key style)}
        new-layer  (wms-layer id id opacity visibile? z-index)]
    [new-source [new-layer]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; WFS Layers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- wfs-source [layer-name geoserver-key]
  {:type       "geojson"
   :data       (c/wfs-layer-url layer-name geoserver-key)
   :generateId true})

(defn- zoom-interp
  "Interpolates a value (vmin to vmax) based on zoom value (from zmin to zmax)."
  [vmin vmax zmin zmax]
  ["interpolate" ["linear"] ["zoom"] zmin vmin zmax vmax])

(defn- on-hover [on off]
  ["case" ["boolean" ["feature-state" "hover"] false]
   on
   off])

(defn- on-selected [selected hovered off]
  ["case"
   ["boolean" ["feature-state" "selected"] false] selected
   ["boolean" ["feature-state" "hover"] false] hovered
   off])

(defn- add-fire-icons-to-map! []
  (go
    (let [icon-chan (chan 4)]
      (add-icon! icon-chan "fire-icon-0"   "./images/Active_Fire_0.png")
      (add-icon! icon-chan "fire-icon-50"  "./images/Active_Fire_50.png")
      (add-icon! icon-chan "fire-icon-90"  "./images/Active_Fire_90.png")
      (add-icon! icon-chan "fire-icon-100" "./images/Active_Fire_100.png")
      (dotimes [_ 4]
        (<! icon-chan)))))

(defn- incident-layer [layer-name source-name opacity]
  (go
    (<! (add-fire-icons-to-map!))
    {:id       layer-name
     :type     "symbol"
     :source   source-name
     :layout   {:icon-allow-overlap true
                :icon-image         ["step" ["get" "containper"]
                                     "fire-icon-0"
                                     50  "fire-icon-50"
                                     90  "fire-icon-90"
                                     100 "fire-icon-100"]
                :icon-size          ["interpolate" ["linear"] ["get" "acres"]
                                     1000   0.5
                                     10000  0.75
                                     300000 1.0]
                :text-anchor        "top"
                :text-allow-overlap true
                :text-field         ["to-string" ["get" "prettyname"]]
                :text-font          ["Open Sans Semibold" "Arial Unicode MS Regular"]
                :text-offset        [0 0.8]
                :text-size          16
                :visibility         "visible"}
     :metadata {:type    (get-layer-type layer-name)
                :z-index 2000}
     :paint    {:icon-opacity    opacity
                :text-color      "#000000"
                :text-halo-color (on-hover "#FFFF00" "#FFFFFF")
                :text-halo-width 1.5
                :text-opacity    ["step" ["zoom"] (on-hover opacity 0.0) 6 opacity 22 opacity]}}))

(defn- build-wfs
  "Returns a new WFS source and layers in the form `[source layers]`.
   `source` must be a valid WFS layer in the geoserver
   `z-index` allows layers to be rendered on-top (positive z-index) or below
   (negative z-index) Mapbox base map layers."
  [id source geoserver-key opacity]
  (go
    (let [new-source {id (wfs-source source geoserver-key)}
          new-layers [(<! (incident-layer id id opacity))]]
     [new-source new-layers])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Terrain and 3D Viewing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private terrain-source
  {mapbox-dem {:type     "raster-dem"
               :url      c/mapbox-dem-url
               :tileSize 512
               :maxzoom  14}})

(def ^:private terrain-layer
  {:source mapbox-dem :exaggeration 1.5})

(def ^:private sky-source
  {:id    "sky"
   :type  "sky"
   :paint {:sky-type                     "atmosphere"
           :sky-atmosphere-sun           [0.0, 0.0]
           :sky-atmosphere-sun-intensity 15}})

(defn- is-terrain? [s]
  (= s mapbox-dem))

(defn- toggle-rotation!
  "Toggles whether the map can be rotated via right-click or touch."
  [enabled?]
  (let [toggle-drag-rotate-fn  (if enabled? #(.enable %) #(.disable %))
        toggle-touch-rotate-fn (if enabled? #(.enableRotation %) #(.disableRotation %))]
    (doto @the-map
      (-> .-dragRotate (toggle-drag-rotate-fn))
      (-> .-touchZoomRotate (toggle-touch-rotate-fn)))))

(defn- toggle-pitch!
  "Toggles whether changing pitch via touch is enabled."
  [enabled?]
  (let [toggle-fn (if enabled? #(.enable %) #(.disable %))]
    (-> @the-map .-touchPitch (toggle-fn))))

(defn- toggle-terrain!
  "Toggles terrain DEM source, sky atmosphere layers."
  [enabled?]
  (update-style! (get-style) :new-sources terrain-source :new-layers [sky-source])
  (-> @the-map (.setTerrain (when enabled? (clj->js terrain-layer)))))

(defn toggle-dimensions!
  "Toggles whether the map is in 2D or 3D mode. When `three-dimensions?` is true,
   terrain is added to the base map and rotation/pitch is enabled."
  [enabled?]
  (toggle-terrain! enabled?)
  (toggle-rotation! enabled?)
  (toggle-pitch! enabled?))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Manage Layers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-base-map-source!
  "Sets the base map source."
  [source]
  (go
    (let [style-chan  (u-async/fetch-and-process source {} (fn [res] (.json res)))
          cur-style   (get-style)
          cur-sources (->> (get cur-style "sources")
                           (u-data/filterm (fn [[k _]]
                                            (let [sname (name k)]
                                              (or (is-terrain? sname)
                                                  (get-layer-metadata (get-layer sname) "type"))))))
          cur-layers  (->> (get cur-style "layers")
                           (filter #(get-layer-metadata % "type")))
          new-style   (-> (<! style-chan)
                          (js->clj))]
      (update-style! cur-style
                     :sources (merge (get new-style "sources") cur-sources)
                     :layers  (concat (get new-style "layers") cur-layers)))))

(defn- hide-forecast-layers
  "Given layers, hides any layer that is in the forecast-layers set."
  [layers]
  (map (u-misc/call-when #(-> % (get-layer-metadata "type") (get-layer-type-metadata-property :forecast-layer?))
                         #(set-visible % false))
       layers))

(defn swap-active-layer!
  "Swaps the active layer. Used to scan through time-series WMS layers."
  [geo-layer geoserver-key opacity css-style]
  {:pre [(string? geo-layer) (number? opacity) (<= 0.0 opacity 1.0)]}
  (let [style  (get-style)
        layers (hide-forecast-layers (get style "layers"))
        [new-sources new-layers] (build-wms geo-layer geo-layer geoserver-key opacity true :style css-style)]
    (update-style! style
                   :layers      layers
                   :new-sources new-sources
                   :new-layers  new-layers)))

(defn reset-active-layer!
  "Resets the active layer source (e.g. from WMS to WFS). To reset to WFS layer,
   `style-fn` must not be nil."
  [geo-layer style-fn geoserver-key opacity css-style]
  {:pre [(string? geo-layer) (number? opacity) (<= 0.0 opacity 1.0)]}
  (go
    (let [style                    (get-style)
          layers                   (hide-forecast-layers (get style "layers"))
          [new-sources new-layers] (if style-fn
                                     (<! (build-wfs fire-active geo-layer geoserver-key opacity))
                                     (build-wms geo-layer geo-layer geoserver-key opacity true :style css-style))]
      (update-style! style
                     :layers      layers
                     :new-sources new-sources
                     :new-layers  new-layers))))

(defn create-wms-layer!
  "Adds WMS layer to the map. This is currently only used to add optional layers to the map."
  [id source geoserver-key visible? & [z-index]]
  (when id
    (if (layer-exists? id)
      (set-visible-by-title! id visible?)
      (let [[new-source new-layer] (build-wms id source geoserver-key 1.0 visible? :z-index z-index)]
        (update-style! (get-style)
                       :new-sources new-source
                       :new-layers  new-layer)))))

(defn create-camera-layer!
  "Adds wildfire camera layer to the map."
  [id]
  (go
    (let [new-source {id {:type       "geojson"
                          :data       (clj->js @!/the-cameras)
                          :generateId true}}
          new-layers [{:id       id
                       :source   id
                       :type     "symbol"
                       :layout   {:icon-image              "video-icon"
                                  :icon-rotate             ["-" ["get" "pan"] 90]
                                  :icon-rotation-alignment "map"
                                  :icon-size               0.5}
                       :metadata {:type    (get-layer-type id)
                                  :z-index 1001}
                       :paint    {:icon-color (on-selected "#f47a3e" "#c24b29" "#000000")}}]
          icon-chan  (chan)]
      (add-icon! icon-chan "video-icon" "./images/video.png" true)
      (<! icon-chan)
      (update-style! (get-style) :new-sources new-source :new-layers new-layers))))

(defn create-red-flag-layer!
  "Adds red flag warning layer to the map."
  [id data]
  (let [color      ["get" "color"]
        new-source {id {:type "geojson" :data data :generateId true}}
        new-layers [{:id       id
                     :source   id
                     :type     "fill"
                     :metadata {:type    (get-layer-type id)
                                :z-index 1000}
                     :paint    {:fill-color         color
                                :fill-outline-color (on-hover "#000000" color)
                                :fill-opacity       (on-hover 1 0.4)}}]]
    (update-style! (get-style) :new-sources new-source :new-layers new-layers)))

(defn- mvt-source [layer-name geoserver-key]
  {:type  "vector"
   :tiles [(c/mvt-layer-url layer-name geoserver-key)]})

(defn create-fire-history-layer!
  "Adds a Fire History layer to the map."
  [id layer-name geoserver-key]
  (let [color      ["step" ["get" "Decade"]
                    "#cccccc" ; Default
                    2000 "#fecc5c"
                    2010 "#fd8d3c"
                    2020 "#f03b20"]
        new-source {id (mvt-source layer-name geoserver-key)}
        new-layer  [{:id           id
                     :source       id
                     :source-layer id
                     :type         "fill"
                     :metadata     {:type    (get-layer-type id)
                                    :z-index 1002}
                     :paint        {:fill-color         color
                                    :fill-opacity       (on-hover 1 0.4)
                                    :fill-outline-color (on-hover "#000000" color)}}]]
    (update-style! (get-style) :new-sources new-source :new-layers new-layer)))

(defn create-fire-history-label-layer!
  "Adds a layer with labels for the Fire History layer to the map."
  [id layer-name geoserver-key]
  (let [new-source {id (mvt-source layer-name geoserver-key)}
        new-layer  [{:id           id
                     :source       id
                     :source-layer id
                     :type         "symbol"
                     :minzoom      7
                     :metadata     {:type (get-layer-type id)
                                    :z-index 1003}
                     :layout       {:text-allow-overlap false
                                    :text-anchor        "top"
                                    :text-field         ["concat" ["to-string" ["get" "incidentna"]]
                                                                  " ("
                                                                  ["to-string" ["get" "fireyear"]]
                                                                  ")"]
                                    :text-font          ["Open Sans Semibold" "Arial Unicode MS Regular"]
                                    :text-size          12
                                    :visibility         "visible"}
                     :paint        {:text-color      "#000000"
                                    :text-halo-color (on-hover "#FFFF00" "#FFFFFF")
                                    :text-halo-width 1.5}}]]
    (update-style! (get-style) :new-sources new-source :new-layers new-layer)))

(defn remove-layer!
  "Removes layer that matches `id`"
  [id]
  (let [cur-style       (get-style)
        layers          (get cur-style "layers")
        filtered-layers (remove #(= id (get % "id")) layers)]
    (update-style! cur-style :layers filtered-layers)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Map Creation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init-map!
  "Initializes the Mapbox map inside of `container` (e.g. \"map\").
   Specifies the proper project layers based on the forecast type."
  [container-id layers & [opts]]
  (set! (.-accessToken mapbox) @!/mapbox-access-token)
  (when-not (.supported mapbox)
    (js/alert (str "Your browser does not support Pyregence Forecast.\n"
                   "Please use the latest version of Chrome, Safari, or Firefox.")))
  (reset! project-layers layers)
  (reset! the-map
          (Map.
           (clj->js (merge {:container   container-id
                            :dragRotate  false
                            :maxZoom     20
                            :minZoom     3
                            :style       (-> (c/base-map-options) c/base-map-default :source)
                            :touchPitch  false
                            :trackResize true
                            :transition  {:duration 500 :delay 0}}
                           (when-not (:zoom opts)
                             {:bounds c/california-extent})
                           opts)))))
