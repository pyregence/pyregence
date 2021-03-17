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

(defn get-style []
  (-> @the-map .getStyle js->clj))

(defn get-sources
  "Returns the sources map from the map"
  []
  (-> (get-style) (get "sources")))

(defn get-layers []
  (-> (get-style) (get "layers")))

(defn get-layer-by-title
  "Returns layer with title"
  [title]
  (let [layers (get-layers)]
    (filter (fn [l] (str/starts-with? (get l "id") title)) layers)))

(defn index-of
  "Returns first index of item in collection that matches predicate."
  [pred xs]
  (->> xs
       (keep-indexed (fn [idx x] (when (pred x) idx)))
       (first)))

(defn get-layer-idx-by-id
  "Returns index of layer with matching id"
  [id layers]
  (index-of #(= id (get % "id")) layers))

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
                      (assoc "sources" sources)
                      (assoc "layers" layers)
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

(defn- symbol-opacity [opacity]
  {"text-opacity" opacity})

(defn- circle-opacity [opacity]
  {"circle-opacity" opacity
   "circle-stroke-opacity" opacity})

(defn- raster-opacity [opacity]
  {"raster-opacity" opacity})

(defn set-opacity
  "Returns layer with opacity set to `opacity`"
  [layer opacity]
  {:pre [(map? layer) (and (number? opacity) (<= 0.0 opacity 1.0))]}
  (let [layer-type (get layer "type")
        new-paint (condp = layer-type
                    "raster" (raster-opacity opacity)
                    "circle" (circle-opacity opacity)
                    "symbol" (symbol-opacity opacity))]
    (update layer "paint" merge new-paint)))

(defn set-opacity-by-title!
  "Sets the opacity of the layer"
  [id opacity]
  {:pre [(string? id) (number? opacity) (<= 0.0 opacity 1.0)]}
  (let [layers      (get-layers)
        pred        #(-> % (get "id") (str/starts-with? "pyregence-"))
        new-layers  (map (u/only pred #(set-opacity % opacity)) layers)]
    (update-style! :sources (get-sources) :layers new-layers)))

(defn set-visible
  "Returns layer with visibility set to `visible?`"
  [layer visible?]
  (assoc-in layer ["layout" "visibility"] (if visible? "visible" "none")))

(defn set-visible-by-title!
  "Sets a layer's visibility"
  [id visible?]
  {:pre [(string? id) (boolean? visible?)]}
  (let [layers (get-layers)]
    (when-let [idx (get-layer-idx-by-id id layers)]
      (let [new-layers (assoc-in layers [idx "layout" "visibility"] (if visible? "visible" "none"))]
        (update-style! :sources (get-sources) :layers new-layers)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; WMS Layers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- wms-source [layer-name]
  {:type     "raster"
   :tileSize 256
   :tiles    [(c/wms-layer-url layer-name)]})

(defn- wms-layer [layer-name source-name opacity]
  {:id       layer-name
   :type     "raster"
   :source   source-name
   :layout   {:visibility "visible"}
   :paint    {:raster-opacity opacity}})

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

(defn- incident-layer [layer-name source-name opacity]
  {:id       layer-name
   :type     "circle"
   :source   source-name
   :layout   {:visibility "visible"}
   :paint    {:circle-color        "#FF0000"
              :circle-opacity      opacity
              :circle-radius       (zoom-interp 8 14 5 20)
              :circle-stroke-color "#000000"
              :circle-stroke-width 3}})

(defn- incident-labels-layer [layer-name source-name opacity]
  {:id     layer-name
   :type   "symbol"
   :source source-name
   :layout {:text-anchor "top"
            :text-field  ["to-string" ["get" "prettyname"]]
            :text-font   ["Open Sans Regular" "Arial Unicode MS Regular"]
            :text-offset [0 0.5]
            :text-size   14
            :visibility "visible"}
   :paint  {:text-color      "#000000"
            :text-halo-color "#ffffff"
            :text-halo-width 1
            :text-opacity    opacity}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Manage Layers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private pyregence- "pyregence-")

(defn set-base-map-source!
  "Sets the Basemap source"
  [source]
  (go
    (let [style-chan (u/fetch-and-process source {} (fn [res] (.json res)))
          cur-style  (js->clj (.getStyle @the-map))
          sources    (->> (get cur-style "sources")
                          (u/filterm (fn [[k _]] (str/starts-with? (name k) "fire"))))
          layers     (->> (get cur-style "layers")
                          (filter (fn [l] (str/starts-with? (get l "id") pyregence-))))
          new-style  (-> (<! style-chan)
                         (js->clj)
                         (assoc "sprite" c/default-sprite)
                         (update "sources" merge sources)
                         (update "layers" concat layers)
                         (clj->js))]
      (-> @the-map (.setStyle new-style)))))

(defn remove-layer!
  "Removes layer matching `layer-name` from the map"
  [id]
  {:pre (string? id)}
  (let [layers     (get-layers)
        new-layers (remove #(= id (get % "id")) layers)
        new-style  (-> (get-style) (assoc "layers" new-layers) clj->js)]
    (-> @the-map (.setStyle new-style))))

(defn build-wms
  "Returns new WMS source and layer in the form `[source [layer]]`.
  `source` must be a valid WMS layer in the geoserver
  `z-index` allows layers to be rendered on-top (positive z-index) or below
  (negative z-index) Mapbox basemap layers."
  [id source z-index opacity]
  (let [new-source {source (wms-source source)}
        new-layer  (wms-layer id source opacity)]
    [new-source [new-layer]]))

(defn build-wfs
  "Returns a new WFS source and layers in the form `[source layers]`.
  `source` must be a valid WFS layer in the geoserver
  `z-index` allows layers to be rendered on-top (positive z-index) or below
  (negative z-index) Mapbox basemap layers."
  [id source z-index opacity]
  (let [new-source {source (wfs-source source)}
        labels-id  (str id "-labels")
        new-layers [(incident-layer id source opacity)
                    (incident-labels-layer labels-id source opacity)]]
    [new-source new-layers]))

(defn- upsert-layer [v new-layer]
  (let [id (:id new-layer)]
    (if-let [idx (get-layer-idx-by-id id v)]
      (assoc v idx new-layer)
      (conj v new-layer))))

(defn- merge-layers [v new-layers]
  (reduce (fn [acc l] (upsert-layer acc l)) (vec v) new-layers))

(defn- hide-pyregence-layers [layers]
  (let [pred #(-> % (get "id") (str/starts-with? pyregence-))
        f    #(set-visible % false)]
    (map (u/only pred f) layers)))

(defn swap-active-layer!
  "Swaps the active layer. Used to scan through time-series WMS layers"
  [geo-layer opacity]
  {:pre [(string? geo-layer) (number? opacity) (<= 0.0 opacity 1.0)]}
  (let [[new-source new-layers] (build-wms (str pyregence- geo-layer) geo-layer 0 opacity)
        layers                  (hide-pyregence-layers (get-layers))]
    (update-style! :sources (merge (get-sources) new-source)
                   :layers  (merge-layers layers new-layers))))

(defn reset-active-layer!
  "Resets the active layer source (e.g. from WMS to WFS). To reset to WFS layer,
   `style-fn` must not be nil"
  [geo-layer style-fn opacity]
  {:pre [(string? geo-layer) (number? opacity) (<= 0.0 opacity 1.0)]}
  (let [id                       (str pyregence- geo-layer)
        sources                  (get-sources)
        layers                   (hide-pyregence-layers (get-layers))
        [new-sources new-layers] (if (some? style-fn)
                                   (build-wfs id geo-layer 0 opacity)
                                   (build-wms id geo-layer 0 opacity))]
    (update-style! :sources (merge sources new-sources)
                   :layers  (merge-layers layers new-layers))))

(defn create-wms-layer!
  "Adds WMS layer to the map."
  [id source z-index]
  (let [[new-source new-layers] (build-wms (str pyregence- id) source z-index 1.0)
        layers                  (get-layers)]
    (update-style! :sources (merge (get-sources) new-source)
                   :layers  (merge-layers layers new-layers))))

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
