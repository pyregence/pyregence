(ns pyregence.components.openlayers
  (:require [pyregence.config :as c]
            [reagent.core :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; OpenLayers aliases
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def Map             js/ol.Map)
(def View            js/ol.View)
(def Overlay         js/ol.Overlay)
(def Attribution     js/ol.control.Attribution)
(def ScaleLine       js/ol.control.ScaleLine)
(def fromLonLat      js/ol.proj.fromLonLat)
(def toLonLat        js/ol.proj.toLonLat)
(def ImageLayer      js/ol.layer.Image)
(def TileLayer       js/ol.layer.Tile)
(def TileWMS         js/ol.source.TileWMS)
(def Raster          js/ol.source.Raster)
(def WMSCapabilities js/ol.format.WMSCapabilities)
(def unByKey         js/ol.Observable.unByKey)

(defonce the-map         (r/atom nil))
(defonce single-click-on (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Creating objects
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init-map! []
  (reset! the-map
          (Map.
           #js {:target   "map"
                :layers   #js [(TileLayer.
                                #js {:title   "basemap"
                                     :visible true})
                               (ImageLayer.
                                #js {:title   "hillshade"
                                     :visible false
                                     :source  (Raster.
                                               #js {:sources   #js [(TileWMS.
                                                                     #js {:url         "https://basemap.nationalmap.gov/arcgis/services/USGSShadedReliefOnly/MapServer/WMSServer"
                                                                          :params      #js {"LAYERS" "0" "TRANSPARENT" "FALSE"}
                                                                          :serverType  "geoserver"
                                                                          :crossOrigin "anonymous"})]
                                                    :operation (fn [pixel] (if (< 0 (aget pixel 0 3))
                                                                             #js [0 0 0 (- 255 (aget pixel 0 0))]
                                                                             #js [0 0 0 0]))})})

                               (TileLayer.
                                #js {:title   "active"
                                     :visible false
                                     :opacity 0.7
                                     :source  nil})]
                :controls #js [(ScaleLine.) (Attribution.)]
                :view     (View.
                           #js {:projection "EPSG:3857"
                                :center     (fromLonLat #js [-119.509444 37.229722] "EPSG:3857")
                                :minZoom    5.5
                                :maxZoom    18
                                :zoom       6.4})
                :overlays #js [(Overlay.
                                #js {:id               "popup"
                                     :element          (.getElementById js/document "pin")
                                     :position         nil
                                     :positioning      "bottom-center"
                                     :offset           #js [0 -4]
                                     :autoPan          true
                                     :autoPanAnimation #js {:duration 250}})]})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Getting object information
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-zoom-info []
  (let [map-view (.getView @the-map)]
    [(.getZoom    map-view)
     (.getMinZoom map-view)
     (.getMaxZoom map-view)]))

(defn get-overlay-center []
  (-> @the-map (.getOverlayById "popup") .getPosition))

(defn get-overlay-bbox []
  (when-let [[x y] (get-overlay-center)]
    (let [res (-> @the-map .getView .getResolution)]
      [x y (+ x res) (+ y res)])))

(defn get-layer-by-title [title]
  (-> @the-map
      .getLayers
      .getArray
      (.find (fn [layer] (= title (.get layer "title"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Modifying objects
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-popup-on-single-click! [call-back]
  (reset! single-click-on
          (.on @the-map
               "singleclick"
               (fn [evt]
                 (let [map-overlay (.getOverlayById @the-map "popup")
                       coord       (.-coordinate evt)
                       [x y]       coord
                       res         (-> @the-map .getView .getResolution)]
                   (.setPosition map-overlay coord)
                   (call-back [x y (+ x res) (+ y res)]))))))

(defn remove-popup-on-single-click! []
  (unByKey @single-click-on))

(defn add-map-mouse-move! [call-back]
  (.on @the-map
       "pointermove"
       (fn [evt]
         (call-back (-> (.-coordinate evt)
                        (toLonLat "EPSG:3857")
                        (js->clj))))))

(defn add-map-zoom-end! [call-back]
  (.on @the-map
       "moveend"
       (fn [_]
         (-> @the-map
             .getView
             .getZoom
             call-back))))

(defn set-base-map-source! [source]
  (-> (get-layer-by-title "basemap")
      (.setSource source)))

(defn swap-active-layer! [geo-layer]
  (when-let [source (.getSource (get-layer-by-title "active"))]
    (.updateParams source #js {"LAYERS" geo-layer})))

(defn reset-active-layer! [geo-layer]
  (-> (get-layer-by-title "active")
      (.setSource (if geo-layer
                    (TileWMS.
                     #js {:url         c/wms-url
                          :params      #js {"LAYERS" geo-layer}
                          :crossOrigin "anonymous"
                          :serverType  "geoserver"})
                    nil))))

(defn set-opacity-by-title! [title opacity]
  (-> (get-layer-by-title title)
      (.setOpacity opacity)))

(defn set-visible-by-title! [title visible?]
  (-> (get-layer-by-title title)
      (.setVisible visible?)))

(defn set-zoom! [zoom]
  (-> @the-map .getView (.setZoom zoom)))

(defn zoom-to-extent! [[minx miny maxx maxy]]
  (-> @the-map
      .getView
      (.fit (clj->js (concat (fromLonLat #js [minx miny])
                             (fromLonLat #js [maxx maxy]))))))

(defn set-center! [center min-zoom]
  (when center
    (-> @the-map .getView (.setCenter center))
    (when (< (get (get-zoom-info) 0) min-zoom)
      (set-zoom! min-zoom))))

(defn center-on-overlay! []
  (set-center! (get-overlay-center) 12.0))

(defn set-center-my-location! [evt]
  (let [coords (.-coords evt)
        lon    (.-longitude coords)
        lat    (.-latitude  coords)]
    (set-center! (fromLonLat #js [lon lat] "EPSG:3857") 12.0)))

(defn clear-point! []
  (-> @the-map
      (.getOverlayById "popup")
      (.setPosition nil)))
