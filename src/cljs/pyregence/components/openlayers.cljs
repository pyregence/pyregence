(ns pyregence.components.openlayers
  (:require [reagent.core :as r]
            [pyregence.config :as c]
            [pyregence.utils  :as u]
            [pyregence.components.messaging :refer [toast-message!]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; OpenLayers aliases
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def Map          js/ol.Map)
(def View         js/ol.View)
(def Overlay      js/ol.Overlay)
(def Attribution  js/ol.control.Attribution)
(def ScaleLine    js/ol.control.ScaleLine)
(def ImageLayer   js/ol.layer.Image)
(def TileLayer    js/ol.layer.Tile)
(def VectorLayer  js/ol.layer.Vector)
(def GeoJSON      js/ol.format.GeoJSON)
(def fromLonLat   js/ol.proj.fromLonLat)
(def toLonLat     js/ol.proj.toLonLat)
(def TileWMS      js/ol.source.TileWMS)
(def Raster       js/ol.source.Raster)
(def VectorSource js/ol.source.Vector)
(def Style        js/ol.style.Style)
(def Stroke       js/ol.style.Stroke)
(def Circle       js/ol.style.Circle)
(def Fill         js/ol.style.Fill)
(def Text         js/ol.style.Text)
(def unByKey      js/ol.Observable.unByKey)

(def the-map         (r/atom nil))
(def loading-errors? (atom false))
(def cur-highlighted (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Creating objects
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO this might be more efficient as an atom that's set once on zoom
(defn zoom-size-ratio [resolution]
  (-> resolution (- 50) (* -0.3) (/ 1500) (+ 1.3) (max 0.1)))

;; TODO each vector layer will have its own style
(defn get-incident-style [obj resolution]
  (let [containper (.get obj "containper")
        acres      (.get obj "acres")
        prettyname (.get obj "prettyname")
        highlight  (.get obj "highlight")
        z-ratio    (zoom-size-ratio resolution)
        font-size  (* z-ratio 14)
        radius     (-> acres (/ 100000.0) (* 30.0) (max 5.0) (min 30.0) (* z-ratio))]
    (Style.
     #js {:text  (when (or highlight (< resolution 700))
                   (Text.
                    #js {:text    (subs prettyname 0 30)
                         :font    (str "bold " font-size "px Avenir")
                         :fill    (Fill. #js {:color "black"})
                         :stroke  (Stroke. #js {:color (if highlight "yellow" "white")
                                                :width (if highlight 6.0 3.0)})
                         :offsetY (+ 12 radius)}))
          :image (Circle.
                  #js {:radius radius
                       :stroke (Stroke.
                                #js {:color (if highlight "yellow " "black")
                                     :width (if highlight 4.0 2.0)})
                       :fill   (Fill.
                                #js {:color (u/interp-color "#FF0000"
                                                            "#000000"
                                                            (/ containper 100.0))})})})))

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
                                                    :operation (fn [pixel]
                                                                 (if (< 0 (aget pixel 0 3))
                                                                   #js [0 0 0 (- 255 (aget pixel 0 0))]
                                                                   #js [0 0 0 0]))})})]
                :controls #js [(ScaleLine. #js {:units "us"}) (Attribution.)]
                :view     (View.
                           #js {:projection "EPSG:3857"
                                :center     (fromLonLat #js [-119.509444 37.229722] "EPSG:3857")
                                :minZoom    3.5
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

(defn get-feature-at-pixel [pixel]
  (aget (.getFeaturesAtPixel @the-map pixel) 0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Modifying objects
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn remove-event! [evt-key]
  (unByKey evt-key))

(defn add-single-click-popup! [call-back]
  (.on @the-map
       "singleclick"
       (fn [evt]
         (let [map-overlay (.getOverlayById @the-map "popup")
               coord       (.-coordinate evt)
               [x y]       coord
               res         (-> @the-map .getView .getResolution)]
           (.setPosition map-overlay coord)
           (call-back [x y (+ x res) (+ y res)])))))

(defn add-mouse-move-xy! [call-back]
  (.on @the-map
       "pointermove"
       (fn [evt]
         (call-back (-> (.-coordinate evt)
                        (toLonLat "EPSG:3857")
                        (js->clj))))))

(defn add-mouse-move-feature-highlight! []
  (.on @the-map
       "pointermove"
       (fn [evt]
         (when-not (.-dragging evt)
           (let [feature (->> evt
                              (.-originalEvent)
                              (.getEventPixel @the-map)
                              (get-feature-at-pixel))]
             (when-not (= @cur-highlighted feature)
               (when @cur-highlighted (.set @cur-highlighted "highlight" false))
               (when feature (.set feature "highlight" true))
               (reset! cur-highlighted feature)))))))

(defn add-map-zoom-end! [call-back]
  (.on @the-map
       "moveend"
       (fn [_]
         (-> @the-map
             .getView
             .getZoom
             call-back))))

(defn add-layer-load-fail! [call-back]
  (.on (.getSource (get-layer-by-title "active"))
       "tileloaderror"
       #(reset! loading-errors? true))
  (.on @the-map
       "rendercomplete"
       (fn [_]
         (when @loading-errors? (call-back))
         (reset! loading-errors? false))))

(defn set-base-map-source! [source]
  (-> (get-layer-by-title "basemap")
      (.setSource source)))

(defn create-wms-layer! [ol-layer geo-layer z-index]
  (if-let [layer (get-layer-by-title ol-layer)]
    (.setVisible layer true)
    (-> @the-map
        (.addLayer (TileLayer.
                    #js {:title  ol-layer
                         :zIndex z-index
                         :source (TileWMS.
                                  #js {:url         c/wms-url
                                       :params      #js {"LAYERS" geo-layer}
                                       :crossOrigin "anonymous"
                                       :serverType  "geoserver"})})))))

;; TODO only WMS layers have a time slider for now. This might eventually need to accommodate Vector sources
(defn swap-active-layer! [geo-layer]
  (when-let [source (.getSource (get-layer-by-title "active"))]
    (.updateParams source #js {"LAYERS" geo-layer})))

(defn reset-active-layer! [geo-layer style-fn]
  (when-let [active-layer (get-layer-by-title "active")]
    (-> @the-map (.removeLayer active-layer)))
  (when geo-layer
    (-> @the-map
        (.addLayer (if style-fn
                     (VectorLayer.
                      #js {:title       "active"
                           :zIndex      50
                           :source      (VectorSource.
                                         #js {:format (GeoJSON.)
                                              :url    (fn [extent]
                                                        (c/get-wfs-feature geo-layer (js->clj extent)))})
                           :renderOrder (fn [a b]
                                          (- (.get b "acres") (.get a "acres")))
                           :style       get-incident-style})
                     (TileLayer.
                      #js {:title  "active"
                           :zIndex 50
                           :source (TileWMS.
                                    #js {:url         c/wms-url
                                         :params      #js {"LAYERS" geo-layer}
                                         :crossOrigin "anonymous"
                                         :serverType  "geoserver"})}))))
    (add-layer-load-fail! #(toast-message! "One or more of the map tiles has failed to load."))))

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
