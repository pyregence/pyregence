(ns pyregence.components.mapbox
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [clojure.core.async :refer [go <!]]
            [pyregence.config     :as c]
            [pyregence.utils      :as u]
            [pyregence.geo-utils  :as g]
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
(def the-marker      (r/atom nil))

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
(defn center-on-overlay! []
  (when-let [marker @the-marker]
    (set-center! (.getLngLat marker) 12.0)))

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
  (when @the-map
    (.resize @the-map)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Markers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-overlay-center
  "Returns marker lonlat coordinates in the form `[lon lat]`"
  []
  (when (some? @the-marker)
    (-> @the-marker .getLngLat .toArray js->clj)))

(defn get-overlay-bbox
  "Converts marker lnglat coords to EPSG:3857, finds the current resolution and
  returns a bounding box."
  []
  (when (some? @the-marker)
    (let [[lng lat] (get-overlay-center)
          [x y] (g/EPSG:4326->3857 [lng lat])
          zoom (get (get-zoom-info) 0)
          res (g/resolution zoom lat)]
      [x y (+ x res) (+ y res)])))

;; TODO: Implement
(defn clear-point! []
  (when-let [marker @the-marker]
    (.remove marker)
    (reset! the-marker nil)))

;; TODO: Implement
(defn init-point!
   "Creates a marker at lnglat"
  [lng lat]
  (clear-point!)
  (let [marker (Marker. #js {:color "#FF0000"})]
    (doto marker
      (.setLngLat #js [lng lat])
      (.addTo @the-map))
    (reset! the-marker marker)))

(defn add-point-on-click!
  "Callback for `click` listener"
  [[lon lat]]
  (init-point! lon lat)
  (center-on-overlay!))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Events
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private events (atom {}))
(defn add-event!
  "Adds a listener for `event` with callback `f`"
  ([event f]
   (let [k (hash f)]
     (swap! events assoc k [event nil])
     (.on @the-map event f)
     f))
  ([event layer f]
   (let [k (hash f)]
     (swap! events assoc k [event layer])
     (.on @the-map event layer f)
     f)))

(defn remove-event!
  "Removes the listener for function `f`"
  [f]
  (when (some? @the-map)
    (let [[event layer] (get @events (hash f))]
      (if (some? layer)
        (.off @the-map event layer f)
        (.off @the-map event f)))))

(defn- event->lnglat [e]
  (-> e .-lngLat .toArray js->clj))

(defn add-single-click-popup!
  "Creates a marker where and passes xy bounding box to `f` on click event"
  [f]
  (let [call-back (fn [e]
                    (-> e event->lnglat add-point-on-click!)
                    (f (get-overlay-bbox)))]
  (add-event! "click" call-back)))

(defn add-mouse-move-xy!
  "Passes `[lon lat]` to `f` on mousemove event"
  [f]
  (add-event! "mousemove" (fn [e] (-> e event->lnglat f))))

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

;; TODO: Implement
(defn set-opacity-by-title! [title opacity])

;; TODO: Implement
(defn set-visible-by-title! [title visible?])

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

;; TODO: Implement
(defn create-wms-layer! [ol-layer geo-layer z-index])

;; TODO: Implement
(defn swap-active-layer! [geo-layer])

;; TODO: Implement
(defn reset-active-layer! [geo-layer style-fn opacity])

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
