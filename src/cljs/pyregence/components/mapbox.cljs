(ns pyregence.components.mapbox
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [clojure.core.async :refer [go <!]]
            [pyregence.config :as c]
            [pyregence.utils  :as u]
            [pyregence.components.messaging :refer [toast-message!]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mapbox Aaliases
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def mapbox             js/mapboxgl)
(def Map                js/mapboxgl.Map)
(def LngLat             js/mapboxgl.LngLat)
(def LngLatBounds       js/mapboxgl.LngLatBounds)
(def Marker             js/mapboxgl.Marker)
(def Popup              js/mapboxgl.Popup)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def the-map         (r/atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Map Information
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-zoom-info
  "Get zoom information
  Returns [zoom min-zoom max-zoom]"
  []
  (let [m @the-map]
    [(.getZoom m)
     (.getMinZoom m)
     (.getMaxZoom m)]))

;; TODO: Implement
(defn get-layer-by-title [title])

;; TODO: Implement
(defn get-feature-at-pixel [pixel])

(defn get-distance-meters
  "Returns distance in meters between center of the map and 100px to the right.
  Used to define the scale-bar map control"
  []
  (let [y     (-> @the-map .getContainer .-clientHeight (/ 2.0))
        left  (.unproject @the-map #js [0.0 y])
        right (.unproject @the-map #js [100.0 y])]
    (.distanceTo left right)))

(defn get-style []
  (-> @the-map .getStyle js->clj))

(defn get-sources
  "Returns the sources map from the map"
  []
  (-> (get-style) (get "sources")))

(defn layers-by-id
  "Returns a map of the layers keyed by their ID, and with a 'position' attribute"
  []
  (let [layers (-> (get-style) (get "layers"))]
    (reduce (fn [acc layer] (assoc acc (get layer "id") (assoc layer "position" (count acc)))) {} layers)))

(defn fire-layers-by-id
  "Returns a map of the layers that begin with 'fire' from the map"
  []
  (let [layers (layers-by-id)]
    (select-keys layers (filter #(str/starts-with? % "fire") (keys layers)))))

(defn- order-layers
  "Sorts a map of layers by 'position' and returns a layers collection"
  [layers]
  {:pre (map? layers)}
  (->> layers vals (sort-by #(get % "position")) vec (map #(dissoc % "position"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Modify Map
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-zoom!
  "Sets the zoom level of the map to `zoom`"
  [zoom]
  (.easeTo @the-map (clj->js {:zoom zoom :animate true})))

(defn zoom-to-extent!
  "Pans/zooms the map to the provided extents."
  [[minx miny maxx maxy]]
  (let [bounds (LngLatBounds. (clj->js [[minx miny] [maxx maxy]]))]
    (.fitBounds @the-map bounds #js {:linear true})))

(defn set-center!
  "Centers the map on `center` with a minimum zoom value of `min-zoom`"
  [center min-zoom]
  (let [curr-zoom (get (get-zoom-info) 0)
        zoom (if (> curr-zoom min-zoom) min-zoom curr-zoom)]
    (.easeTo @the-map #js {:zoom zoom :center center :animate true})))

;; TODO: Implement
(defn center-on-overlay! [])

(defn set-center-my-location!
  "Sets the center of the map to geolocation"
  [event]
  (let [coords (.-coords event)
        lon    (.-longitude coords)
        lat    (.-latitude  coords)]
    (set-center! [lon lat] 12.0)))

(defn resize-map!
  "Resizes the map"
  []
  (.resize @the-map))

(defn- update-style! [& {:keys [sources layers] :or {sources {}}}]
  (let [new-style (-> (get-style)
                      (update "sources" merge sources)
                      (assoc "layers" (order-layers (or layers (layers-by-id))))
                      clj->js)]
    (-> @the-map (.setStyle new-style))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Popups
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Implement
(defn get-overlay-center [])

;; TODO: Implement
(defn get-overlay-bbox [])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Markers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Implement
(defn init-point! [coord])

;; TODO: Implement
(defn clear-point! [])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Events
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-event!
  ([evt-key evt-fn] (.on @the-map evt-key evt-fn))
  ([evt-key layer-name evt-fn] (.on @the-map evt-key layer-name evt-fn)))

;; TODO: Implement
(defn remove-event! [evt-key])

;; TODO: Implement
(defn add-single-click-popup! [f])

;; TODO: Implement
(defn add-mouse-move-xy! [f])

;; TODO: Implement
(defn feature-highlight! [evt])

;; TODO: Implement
(defn add-mouse-move-feature-highlight! [])

;; TODO: Implement
(defn add-single-click-feature-highlight! [])

;; TODO: Implement
(defn add-map-zoom-end!
  "Passes current zoom level to `f` on zoom-end event"
  [f]
  (add-event! "zoomend" #(f (get (get-zoom-info) 0))))

;; TODO: Implement
(defn add-layer-load-fail! [f])

(defn add-map-move!
  "Calls `f` on 'move' event"
  [f]
  (add-event! "move" f))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Modify Layer Properties
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- set-symbol-opacity! [title opacity]
  (let [labels (str title "-labels")
        layers (layers-by-id)
        new-layers (-> layers
                         (assoc-in [title "paint" "circle-opacity"] opacity)
                         (assoc-in [title "paint" "circle-stroke-opacity"] opacity)
                         (assoc-in [labels "paint" "text-opacity"] opacity))]
    (update-style! :layers new-layers)))

(defn- set-raster-opacity! [title opacity]
  (let [layers (layers-by-id)
        new-layers (-> layers
                       (assoc-in [title "paint" "raster-opacity"] opacity))]
    (update-style! :layers new-layers)))

(defn set-opacity-by-title!
  "Sets the opacity of the layer"
  [title opacity]
  {:pre [(string? title) (number? opacity) (<= 0.0 opacity 1.0)]}
  (let [layers     (layers-by-id)
        layer-type (get-in layers [title "type"])]
    (if (= "raster" layer-type)
      (set-raster-opacity! title opacity)
      (set-symbol-opacity! title opacity))))

(defn set-visible-by-title!
  "Sets a layer's visibility"
  [title visible?]
  {:pre [(string? title) (boolean? visible?)]}
  (let [layers     (layers-by-id)
        new-layers (assoc-in layers [title "layout" "visibility"] (if visible? "visible" "none"))
        new-style  (-> (get-style)
                       (assoc "layers" (order-layers new-layers))
                       clj->js)]
    (-> @the-map (.setStyle new-style))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; WMS Layers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- wms-source [layer-name]
  {:type     "raster"
   :tileSize 256
   :tiles    [(c/wms-layer-url layer-name)]})

(defn- wms-layer [layer-name source-name position]
  {"id"       layer-name
   "type"     "raster"
   "source"   source-name
   "layout"   {"visibility" "visible"}
   "paint"    {"raster-opacity" 1}
   "position" position})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; WFS Layers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- wfs-source [layer-name]
  {:type "geojson"
   :data (c/wfs-layer-url layer-name)})

(defn- zoom-interp
  "Interpolates a value (vmin to vmax) based on zoom value (from zmin to zmax)"
  [vmin vmax zmin zmax]
  ["interpolate" ["linear"] ["zoom"] zmin vmin zmax vmax])

(defonce ^:private default-circle-style
  {:circle-color        "#FF0000"
   :circle-stroke-color "#000000"
   :circle-stroke-width 3
   :circle-radius       (zoom-interp 8 14 5 20)})

(defn- incident-layer [layer-name source-name position & style]
  {"id"       layer-name
   "type"     "circle"
   "source"   source-name
   "layout"   {:visibility "visible"}
   "position" position
   "paint"    (merge default-circle-style style)})

(defonce ^:private default-text-style
  {"type"   "symbol"
   "layout" {:text-anchor "top"
             :text-field ["to-string" ["get" "prettyname"]]
             :text-font ["Open Sans Regular" "Arial Unicode MS Regular"]
             :text-size 14
             :text-offset [0 0.5]
             :visibility "visible"}
   "paint"  {:text-color "#000000"
             :text-halo-color "#ffffff"
             :text-halo-width 1}})

(defn- incident-labels-layer [layer-name source-name position & style]
  (let [info {"id" layer-name "source" source-name "position" position}]
    (merge info default-text-style style)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Manage Layers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-base-map-source!
  "Sets the Basemap source"
  [source]
  (go
    (let [style-chan (u/fetch-and-process source {} (fn [res] (.json res)))
          cur-style  (js->clj (.getStyle @the-map))
          sources    (->> (get cur-style "sources")
                          (u/filterm (fn [[k _]] (str/starts-with? (name k) "fire"))))
          layers     (->> (get cur-style "layers")
                          (filter (fn [l] (str/starts-with? (get l "id") "fire"))))
          new-style  (-> (<! style-chan)
                         (js->clj)
                         (assoc "sprite" c/default-sprite)
                         (update "sources" merge sources)
                         (update "layers" concat layers)
                         (clj->js))]
      (-> @the-map (.setStyle new-style)))))

(defn remove-layer!
  "Removes layer matching `layer-name` from the map"
  [layer-name]
  {:pre (string? layer-name)}
  (let [layers     (layers-by-id)
        new-layers (dissoc layers layer-name)]
    (update-style! :layers new-layers)))

(defn create-wms-layer!
  "Instantiates a new WMS source and layer.
  `layer` can be any string that begins with 'fire'.
  `source` must be a valid WMS layer in the geoserver
  `z-index` allows layers to be rendered on-top (positive z-index) or below
  (negative z-index) Mapbox basemap layers."
  [layer source z-index]
  (let [new-sources {source (wms-source source)}
        layers      (layers-by-id)
        new-layers  (assoc layers layer (wms-layer layer source (count layers)))]
    (update-style! :sources new-sources :layers new-layers)))

(defn create-wfs-layer!
  "Instantiates a new WFS source and layer.
  `layer` can be any string that begins with 'fire'.
  `source` must be a valid WMS layer in the geoserver
  `z-index` allows layers to be rendered on-top (positive z-index) or below
  (negative z-index) Mapbox basemap layers."
  [layer source z-index]
  {:pre [(string? layer) (string? source) (number? z-index)]}
  (let [new-sources  {source (wfs-source source)}
        labels-layer (str layer "-labels")
        layers       (layers-by-id)
        new-layers  (-> layers
                        (assoc layer (incident-layer layer source (+ (count layers) z-index)))
                        (assoc labels-layer (incident-labels-layer labels-layer source (- (count layers) z-index))))]
    (update-style! :sources new-sources :layers new-layers)))

;; Constants for active layers
(def ^:private fires-active "fire-active")
(def ^:private fires-active-labels "fire-active-labels")

(defn swap-active-layer!
  "Swaps the active layer. Used to scan through time-series WMS layers"
  [geo-layer]
  {:pre [(string? geo-layer)]}
  (let [opacity (-> (layers-by-id) (get-in [fires-active "paint" "raster-opacity"]))]
    (create-wms-layer! fires-active geo-layer 0)
    (set-opacity-by-title! fires-active opacity)))

(defn reset-active-layer!
  "Resets the active layer source (e.g. from WMS to WFS)
  To reset to WFS layer, `style-fn` must not be nil"
  [geo-layer style-fn opacity]
  {:pre [(string? geo-layer) (number? opacity) (<= 0.0 opacity 1.0)]}
  ;; Remove layers
  (remove-layer! fires-active)
  (remove-layer! fires-active-labels)
  (if (some? style-fn)
    (create-wfs-layer! fires-active geo-layer 0)
    (create-wms-layer! fires-active geo-layer 0))
  (set-opacity-by-title! fires-active opacity))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Map Creation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init-map!
  "Initializes the Mapbox map inside of container (defaults to div with id 'map')"
  ([] (init-map! "map"))
  ([container-id]
   (set! (.-accessToken mapbox) c/mapbox-access-token)
   (reset! the-map
           (Map.
             (clj->js {:container container-id
                       :minZoom 3
                       :maxZoom 20
                       :style (-> c/base-map-options c/base-map-default :source)
                       :trackResize true
                       :transition {:duration 500 :delay 0}})))))
