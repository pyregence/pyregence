(ns pyregence.components.mapbox
  (:require [reagent.core :as r]
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
(def MercatorCoordinate js/mapboxgl.MercatorCoordinate)
(def Marker             js/mapboxgl.Marker)
(def Popup              js/mapboxgl.Popup)

(def the-map         (r/atom nil))
(def loading-errors? (atom false))
(def cur-highlighted (atom nil))
(def the-marker      (r/atom nil))
(def the-popup       (r/atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Creating objects
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare get-source)
(declare loaded?)
(declare add-source!)
(declare add-layer!)
(declare set-base-map-source!)
(declare center-on-overlay!)

;; TODO this might be more efficient as an atom that's set once on zoom
(defn zoom-size-ratio [resolution])

;; TODO each vector layer will have its own style
(defn get-incident-style [obj resolution])

;; TODO: Figure out if the "load" method can only take one function?
(def ^:private on-load-events (atom []))
(defn run-on-load-events []
  (println "Running on-load events")
  (doseq [f @on-load-events] (f))
  (reset! on-load-events []))

(defn on-load [f]
  (println "Adding event: " f)
  (when (empty? @on-load-events)
    (println "Empty events")
    (.on @the-map "load" #(run-on-load-events)))
  (swap! on-load-events conj f))

(defn- add-sky! []
  (println "Adding sky")
  (if-not (loaded?)
    (on-load #(add-sky!))
    (add-layer! (clj->js {:id "sky"
                          :type "sky"
                          :paint {:sky-type "gradient"
                                  :sky-gradient ["interpolate" ["linear"]
                                                 ["sky-radial-progress"]
                                                 0.8 "rgba(135 206 235 1.0)"
                                                 1 "rgba(0000.1)"]
                                  :sky-gradient-center [0 0]
                                  :sky-gradient-radius 90
                                  :sky-opacity ["interpolate" ["exponential" 0.1]
                                                ["zoom"] 5 0 22 1 ]}}))))

(defn- add-terrain! []
  (if-not (loaded?)
    (on-load #(add-terrain!))
    (let [dem-layer "mapbox-dem"]
      (when (nil? (get-source dem-layer))
        (add-source! dem-layer (clj->js {:type "raster-dem"
                                            :url "mapbox://mapbox.mapbox-terrain-dem-v1"
                                            :tileSize 512
                                            :maxzoom 14})))
      (.setTerrain @the-map #js {:source dem-layer :exaggeration 2}))))

(defn- add-default-basemap! []
  (set-base-map-source! (get-in c/base-map-options [:mapbox-topo :source])))

(defn- set-access-token! []
  (set! (.-accessToken mapbox) c/mapbox-access-token))

(defn init-map!
  "Initializes the Mapbox map inside of container (defaults to div with id 'map')"
  ([] (init-map! "map"))
  ([container-id]
   (set-access-token!)
   (reset! the-map
           (Map.
             #js {:container container-id
                  :style (-> c/base-map-options first :source)
                  :trackReize true}))
   (js/console.log @the-map)
   (add-default-basemap!)
   (add-terrain!)
   (add-sky!)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Map information
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn loaded?
  "Returns whether the map has loaded"
  []
  (.loaded @the-map))

(defn get-zoom-info
  "Get zoom information
  Returns [zoom min-zoom max-zoom]"
  []
  (let [m @the-map]
    [(.getZoom m)
     (.getMinZoom m)
     (.getMaxZoom m)]))

(defn get-source
  "Retrieve source from the map"
  [id]
  (-> @the-map (.getSource id) u/js->kclj))

(defn get-layer
  "Retrieve layer from the map"
  [id]
  (-> @the-map (.getLayer id) u/js->kclj))

(defn get-layers
  "Retrieves all layers from the map"
  []
  (->> @the-map .getStyle .-layers u/js->kclj))

(defn get-last-layer
  "Retrieves last added layer to the map"
  []
  (-> (get-layers) last :id))

(defn get-layer-names
  "Retrieves all the names of layers on the map"
  []
  (map :id (get-layers)))

;; Refer to https://docs.mapbox.com/mapbox-gl-js/api/map/#map#queryrenderedfeatures
;; TODO: Need to find which layers that we want to query
(defn get-feature
  "Finds the feature at a specific XY location within the layers."
  [location layers]
  {:pre [(seq? location) (seq? layers)]}
  (.queryRenderedFeatures @the-map (clj->js location) (clj->js layers)))

(defn unproject
  "Returns a geographical LngLat coordinate from an graphical XY point"
  [point]
  (.unproject @the-map point))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Popup / Marker methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; https://web.archive.org/web/20190209181458/https://www.masinamichele.it/2018/05/04/gis-the-math-to-convert-from-epsg3857-to-wgs-84/
;; Reproject 4326 to 3857
;; b = 20037508.34;
;; lon = lon_wgs * b / 180
;; lat = (b/pi) * ln( tan( (pi / 360) * (lat_wgs + 90) ) )
(defn reproject [lon-wgs lat-wgs]
  (let [b 20037508.34
        pi js/Math.PI
        ln js/Math.log
        tan js/Math.tan
        lon (/ (* b lon-wgs) 180)
        lat (* (/ b pi) (ln (tan (* (/ pi 360) (+ lat-wgs 90)))))]
    [lon lat]))

;; Web Mercator Resolution taken from:
;; https://docs.microsoft.com/en-us/bingmaps/articles/bing-maps-tile-system?redirectedfrom=MSDN
;;
(defn get-resolution
  "Calculates ground resolution using zoom and latitude.
  Follows the following equation:
  resolution = cos(latitude * pi/180) * earth-circumference / map-width
  where: map-width = 256 * 2^zoom"
  [zoom latitude]
  (let [earth-diameter 6378137
        pi js/Math.PI
        t1 (js/Math.cos (* latitude (/ pi 180)))
        earth-circumference (* 2 pi earth-diameter)
        map-width (* 256 (js/Math.pow 2 zoom))]
    (/ (* t1 earth-circumference) map-width)))

(defn get-scale
  "Calculates the map scale using zoom and latitude.
  Assumes a DPI of 96 pixels per inch"
  [zoom latitude & new-dpi]
  (let [dpi (or new-dpi 96)
        meters-per-inch 0.254
        res (get-resolution zoom latitude)]
    (/ (* res dpi) meters-per-inch)))

;; TODO: Implement
(defn get-overlay-center []
  (when-let [marker @the-marker]
    (.getLngLat marker)))

;; TODO: Implement
(defn get-overlay-bbox [])

(defn clear-marker! []
  (when-let [marker @the-marker]
    (.remove marker)
    (reset! the-marker nil)))

(defn init-marker!
  "Creates a marker at latlng"
  [latlng]
  (clear-marker!)
  (let [marker (Marker. #js {:color "#FF0000"})]
    (doto marker
      (.setLngLat latlng)
      (.addTo @the-map))
    (reset! the-marker marker)))

(defn clear-popup! []
  (when-let [popup @the-popup]
    (.remove popup)
    (reset! the-popup nil)))

(defn init-popup!
  "Creates a popup at latlng"
  [latlng]
  (clear-popup!)
  (let [popup (Popup. #js {:anchor "top" :closeButton true})]
    (doto popup
      (.setLngLat latlng)
      (.addTo @the-map))
    (reset! the-popup popup)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Events
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Refer to https://docs.mapbox.com/mapbox-gl-js/api/map/#map#off
;; Must maintain a reference to the function to remove the event
(def ^:private events (atom {}))
(defn remove-event! [event-key]
  (when-let [event-fn (get @events event-key)]
    (.off @the-map event-key event-fn)
    (swap! events dissoc event-key)))

(defn add-event! [event-type event-fn]
  (swap! events assoc event-type event-fn)
  (.on @the-map event-type event-fn))

(defn add-single-click!
  "Adds callback for `click` listener"
  [call-back]
  (let [f (fn [e] (let [lnglat (-> e .-lngLat)
                        [lng lat] (-> lnglat .toArray js->clj)
                        [x y] (reproject lng lat)
                        zoom (.getZoom @the-map)
                        res (get-resolution zoom lat)]
                    (init-marker! lnglat)
                    (init-popup! lnglat)
                    (center-on-overlay!) ;; TODO: Figure out centering isn't working
                    (call-back [x y (+ x res) (+ y res)])))]
    (add-event! "click" f)))

(defn remove-single-click!
  "Removes `click` listener"
  []
  (remove-event! "click"))

;; TODO: Implement
(defn add-mouse-move-xy!
  "Adds callback to `mousemove` listener"
  [callback]
  {:pre [(fn? callback)]}
  (let [f (fn [e] (-> e .-point unproject .toArray js->clj callback))]
    (add-event! "mousemove" f)))

(defn remove-mouse-move-xy!
  "Removes `mousemove` listener"
  []
  (remove-event! "mousemove"))

;; TODO: Implement
(defn feature-highlight! [evt])

;; TODO: Implement
(defn add-mouse-move-feature-highlight! [])

;; TODO: Implement
(defn add-single-click-feature-highlight! [])

;; TODO: Implement
(defn add-map-zoom-end!
  "Registers callback on zoomend event"
  [callback]
  {:pre [(fn? callback)]}
  (let [f (fn [e] (-> e .-target .getZoom js/Math.round callback))]
    (add-event! "zoomend" f)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Source/Layer Methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Implement
(defn add-layer-load-fail! [call-back])

(defn add-source! [source-name source-params]
  (.addSource @the-map source-name source-params))

(defn remove-source! [source-name]
  (.removeSource @the-map source-name))

(defn add-layer! [layer-params]
  (.addLayer @the-map layer-params))

(defn remove-layer! [layer-name]
  (.removeLayer @the-map layer-name))

(defn- wms-source [layer-name]
  (let [url (c/wms-layer-url layer-name)]
    {:type "raster" :tileSize 256 :tiles [url]}))

(defonce ^:private default-wms-style
  {:raster-opacity 0.8
   :raster-resampling "nearest"})

(defn- wms-layer [layer-name]
  {:id layer-name
   :type "raster"
   :source layer-name
   :paint default-wms-style})

(defn add-wms-layer!
  "Adds Pyregence WMS Layer to the map"
  [layer-name z-index]
  (if-not (loaded?)
    (on-load #(add-wms-layer! layer-name z-index))
    (do
      (println "Adding WMS Layer: " layer-name)
      (when (nil? (get-source layer-name))
        (add-source! layer-name (clj->js (wms-source layer-name))))
      (when (nil? (get-layer layer-name))
        (add-layer! (clj->js (wms-layer layer-name)))))))

(defn- wfs-source [layer-name]
  (let [url (c/wfs-layer-url layer-name)]
    (println "WFS Source: " url)
    {:type "geojson" :data url}))

(defn- zoom-interp
  "Interpolates a value (vmin to vmax) based on zoom value (from zmin to zmax)"
  [vmin vmax zmin zmax]
  ["interpolate" ["linear"] ["zoom"] zmin vmin zmax vmax])

(defonce ^:private default-circle-style
  {:circle-color "#FF0000"
   :circle-stroke-color "#000000"
   :circle-stroke-width 3
   :circle-radius (zoom-interp 8 14 5 20)})

(defn- incident-layer [layer-name & style]
  {:id layer-name
   :type "circle"
   :source layer-name
   :layout {}
   :paint (merge default-circle-style style)})

(defonce ^:private default-text-style
  {:type "symbol"
   :layout {:text-anchor "top"
            :text-field ["to-string" ["get" "prettyname"]]
            :text-font ["Open Sans Regular" "Arial Unicode MS Regular"]
            :text-size 14
            :text-offset [0 0.5]}
   :paint {:text-color "#000000"
           :text-halo-color "#ffffff"
           :text-halo-width 1}})

(defn- incident-labels-layer [layer-name source-name & style]
  (merge
    {:id layer-name
     :type "symbol"
     :source source-name}
    default-text-style
    style))

(defn add-wfs-layer!
  "Adds Pyregence WFS Layer to the map"
  [layer-name z-index]
  (if-not (loaded?)
    (on-load (fn [] (add-wfs-layer! layer-name z-index)))
    (do
      (when (nil? (get-source layer-name))
        (add-source! layer-name (clj->js (wfs-source layer-name))))
      (let [labels-layer (str "labels-" layer-name)]
        (when (nil? (get-layer layer-name))
          (add-layer! (clj->js (incident-layer layer-name))))
        (when (nil? (get-layer labels-layer))
          (add-layer! (clj->js (incident-labels-layer labels-layer layer-name))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Modifying map properties
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-layout-property!
  "Sets a layer's layout property"
  [layer property value & opts]
  (.setLayoutProperty @the-map layer property value opts))

(defn set-visible!
  "Sets a layer's visibility"
  [layer-name visible?]
  (when (get-layer layer-name)
    (set-layout-property! layer-name "visibility" (if visible? "visible" "none"))))

(defn set-opacity!
  "Sets the layer's opacity"
  [layer-name opacity]
  (when-let [layer (get-layer layer-name)]
    (.setPaintProperty @the-map layer-name (str (:type layer) "-opacity") opacity)))

(defn set-base-map-source!
  "Sets the Basemap source"
  [source]
  (.setStyle @the-map source)
  (add-terrain!))

;; TODO only WMS layers have a time slider for now. This might eventually need to accommodate Vector sources
;; Only issue is that ALL other custom layers need to be turned off
(defn swap-active-layer!
  "Swaps the layer that is visible to a new layer"
  [layer-name]
  (let [last-layer (get-last-layer)
        existing-layer (get-layer layer-name)]
    (if (nil? existing-layer)
      (add-wms-layer! layer-name 0)
      (set-visible! layer-name true))
    (set-visible! last-layer false)))

;; TODO: Implement
;; Vector source is determined whether there is a style-fun parameter
(defn reset-active-layer!
  "Resets the active layer of the map"
  [layer-name & {:keys [style _opacity]}]
  ;; Remove the "active" layer
  (if (some? style)
    (add-wfs-layer! layer-name 0)
    (add-wms-layer! layer-name 0)))

(defn reset-north!
  "Resets the top of the map to geographic north"
  []
  (.setBearing @the-map 0))

(defn set-bearing!
  "Sets the bearing of the the map to bearing"
  [bearing]
  (.setBearing @the-map bearing))

(defn set-pitch!
  "Sets the pitch of the the map to pitch (0-60)"
  [pitch]
  {:pre (< 0 pitch 60)}
  (.setPitch @the-map pitch))

(defn set-zoom!
  "Sets the map zoom (0-20)"
  [^int zoom]
  {:pre (> 0 zoom 20)}
  (let [z (js/Math.round zoom)]
    (.easeTo @the-map (clj->js {:zoom z :animate true}))))

(defn zoom-to-extent!
  "Fits the map to the bounds in the form [minX minY maxX maxY]"
  [[minx miny maxx maxy]]
  (let [bounds (clj->js [[minx miny] [maxx maxy]])]
    (.fitBounds @the-map bounds)))

(defn set-center!
  "Sets the geographic center to center"
  [center min-zoom]
  (let [curr-zoom (get (get-zoom-info) 0)
        zoom (if (< curr-zoom min-zoom) min-zoom curr-zoom)]
    (.easeTo @the-map {:center center :zoom zoom})))

(defn center-on-overlay! []
  (set-center! (get-overlay-center) 12.0))

(defn resize-map!
  "Resizes the map"
  []
  (.resize @the-map))

;; TODO
(defn set-center-my-location!
  "Sets center based on a geolocation event"
  [evt]
  (let [coords (.-coords evt)
        lon    (.-longitude coords)
        lat    (.-latitude  coords)]
    (set-center! #js [lon lat] 12.0)))

