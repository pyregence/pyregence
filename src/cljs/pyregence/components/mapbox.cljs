(ns pyregence.components.mapbox
  (:require [reagent.core :as r]
            [pyregence.config :as c]
            [pyregence.utils  :as u]
            [pyregence.components.messaging :refer [toast-message!]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mapbox Aaliases
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def mapbox         js/mapboxgl)
(def Map            js/mapboxgl.Map)
(def LngLat         js/mapboxgl.LngLat)
(def LngLatBounds   js/mapboxgl.LngLatBounds)

(def the-map         (r/atom nil))
(def loading-errors? (atom false))
(def cur-highlighted (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Creating objects
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO this might be more efficient as an atom that's set once on zoom
(defn zoom-size-ratio [resolution])

;; TODO each vector layer will have its own style
(defn get-incident-style [obj resolution])

(defn- on-load [f]
  (.on @the-map "load" f))

(defn- add-terrain! []
  (on-load (fn []
             (println "Adding Terrain")
             (doto @the-map
               (.addSource "mapbox-dem" (clj->js {:type "raster-dem"
                                                  :tileSize 512
                                                  :maxzoom 14}))
               (.setTerrain #js {:source "mapbox-dem" :exaggeration 1.5})
               (.addLayer (clj->js {:id "sky"
                                    :type "sky"
                                    :paint {:sky-type "atmosphere"
                                            :sky-atmosphere-sun [0.0 0.0]
                                            :sky-atmosphere-sun-intensity 15}}))))))

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
   ;;(add-terrain!)
   ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Getting object information
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-zoom-info
  "Get zoom information
  Returns [zoom min-zoom max-zoom]"
  []
  (let [m @the-map]
    [(.getZoom m)
     (.getMinZoom m)
     (.getMaxZoom m)]))

(defn get-layer
  "Retrieve a layer from the map"
  [id]
  (.getLayer @the-map id))

;; Refer to https://docs.mapbox.com/mapbox-gl-js/api/map/#map#queryrenderedfeatures
;; TODO: Need to find which layers that we want to query
(defn get-feature
  "Finds the feature at a specific XY location within the layers."
  [location layers]
  {:pre [(seq? location) (seq? layers)]}
  (.queryRenderedFeatures @the-map (clj->js location) (clj->js layers)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Popup / Overlay methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Implement
(defn get-overlay-center [])

;; TODO: Implement
(defn get-overlay-bbox [])

;; TODO: Implement
(defn clear-point! [])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Modifying objects
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Refer to https://docs.mapbox.com/mapbox-gl-js/api/map/#map#off
;; Must maintain a reference to the function to remove the event
(def ^:private events (atom {}))
(defn remove-event! [event-key]
  (when-let [event-fn (get @events event-key)]
    (.off @the-map event-key event-fn)
    (swap! events dissoc event-key)))

(defn add-event! [event-type event-fn]
  (swap! events update assoc event-type event-fn)
  (.on @the-map event-type event-fn))

;; TODO: Implement
(defn add-single-click-popup! [call-back])

;; TODO: Implement
(defn add-mouse-move-xy! [call-back])

;; TODO: Implement
(defn feature-highlight! [evt])

;; TODO: Implement
(defn add-mouse-move-feature-highlight! [])

;; TODO: Implement
(defn add-single-click-feature-highlight! [])

;; TODO: Implement
(defn add-map-zoom-end! [call-back])

;; TODO: Implement
(defn set-visible!
  "Sets a layer's visibility"
  [id visible?]
  {:pre (boolean? visible?)}
  (some-> (get-layer id)
          (.setLayoutProperty "visible" visible?)))

;; TODO: Implement
(defn add-layer-load-fail! [call-back])

(defn set-base-map-source!
  "Sets the Basemap source"
  [source]
  (.setStyle @the-map source))

;; TODO: Implement
(defn create-wms-layer! [ol-layer geo-layer z-index])

;; TODO: Implement
(defn create-wfs-layer! [ol-layer geo-layer z-index])

;; TODO only WMS layers have a time slider for now. This might eventually need to accommodate Vector sources
;; TODO: Implement
(defn swap-active-layer!
  "Swaps the layer that is visible to a new layer"
  [geo-layer])

;; TODO: Implement
;; Vector source is determined whether there is a style-fun parameter
(defn reset-active-layer!
  "Resets the active layer of the map"
  [geo-layer & {:keys [style opacity]}]
  ;; Remove the "active" layer
  ;; Determine layer type
  ;; Add the appropariate layer source with style/opacity
  (println "Reset Active layer"))

(defn set-opacity!
  "Sets the layer's opacity"
  [title opacity]
  (when-let [layer (.getLayer @the-map title)]
    (.setLayerAttribute @the-map title (str (.type layer) "-opacity") opacity)))

(defn reset-north!
  "Resets the top of the map to geographic north"
  []
  (.setBearing @the-map 0))

(defn set-pitch!
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
  (.setZoom @the-map zoom))

(defn zoom-to-extent!
  "Fits the map to the bounds in the form [minX minY maxX maxY]"
  [[minx miny maxx maxy]]
  (let [bounds (clj->js [[minx miny] [maxx maxy]])]
    (.fitBounds @the-map bounds)))

(defn set-center!
  "Sets the geographic center to center"
  [center min-zoom]
  (when center
    (.setCenter @the-map center))
    (when (< (get (get-zoom-info) 0) min-zoom)
      (set-zoom! min-zoom)))

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

