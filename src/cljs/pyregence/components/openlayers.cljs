(ns pyregence.components.openlayers)

;; OpenLayers aliases
(def Map             js/ol.Map)
(def View            js/ol.View)
(def Overlay         js/ol.Overlay)
(def defaults        js/ol.control.defaults)
(def fromLonLat      js/ol.proj.fromLonLat)
(def TileLayer       js/ol.layer.Tile)
(def OSM             js/ol.source.OSM)
(def TileWMS         js/ol.source.TileWMS)
(def WMSCapabilities js/ol.format.WMSCapabilities)

(defonce the-map      (atom nil))
(defonce active-layer (atom nil))

(defn init-map! [layer post-fn]
  (reset! active-layer
          (TileLayer.
           #js {:title   "active"
                :visible true
                :source  (TileWMS.
                          #js {:url        "https://californiafireforecast.com:8443/geoserver/demo/wms"
                               :params     #js {"LAYERS" (str "demo:" layer)}
                               :serverType "geoserver"})}))
  (reset! the-map
          (Map.
           #js {:target   "map"
                :layers   #js [(TileLayer.
                                #js {:title   "OpenStreetMap"
                                     :visible true
                                     :source  (OSM.)})
                               @active-layer]
                :controls (defaults)
                :view     (View.
                           #js {:projection "EPSG:3857"
                                :center     (fromLonLat #js [-120.8958 38.8375])
                                :zoom       10})
                :overlays #js [(Overlay.
                                #js {:id               "popup"
                                     :element          (.getElementById js/document "popup")
                                     :position         nil
                                     :positioning      "bottom-center"
                                     :offset           #js [0 -4]
                                     :autoPan          true
                                     :autoPanAnimation #js {:duration 250}})]}))
  (post-fn))

(defn add-map-single-click! [call-back]
  (.on @the-map
       "singleclick"
       (fn [evt]
         (let [map-view    (.getView @the-map)
               map-overlay (.getOverlayById @the-map "popup")
               coord       (.-coordinate evt)
               [x y]       coord
               res         (.getResolution map-view)]
           (.setPosition map-overlay coord)
           (call-back {:bbox [x y (+ x res) (+ y res)]
                       :crs  (-> map-view .getProjection .getCode)})))))

(defn swap-active-layer! [layer]
  (-> @active-layer
      .getSource
      (.updateParams #js {"LAYERS" (str "demo:" layer) "TILED" "true"})))

(defn set-active-layer-opacity! [opacity]
  (-> @active-layer
      (.setOpacity opacity)))

(defn wms-capabilities
  "Converts capabilities xml to a js object"
  [text]
  (.read (WMSCapabilities.) text))
