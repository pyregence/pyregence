(ns pyregence.components.openlayers)

;; OpenLayers aliases
(def Map             js/ol.Map)
(def View            js/ol.View)
(def defaults        js/ol.control.defaults)
(def fromLonLat      js/ol.proj.fromLonLat)
(def TileLayer       js/ol.layer.Tile)
(def OSM             js/ol.source.OSM)
(def TileWMS         js/ol.source.TileWMS)
(def WMSCapabilities js/ol.format.WMSCapabilities)

(defonce the-map      (atom nil))
(defonce active-layer (atom nil))

(defn add-map-single-click [call-back]
  (let [mapView (.getView @the-map)]
    (.on @the-map
         "singleclick"
         (fn [evt]
           (let [x   (nth (.-coordinate evt) 0)
                 y   (nth (.-coordinate evt) 1)
                 res (.getResolution mapView)]
             (call-back {:bbox [x y (+ x res) (+ y res)]
                         :crs  (-> mapView .getProjection .getCode)}))))))

(defn init-map! [layer post-fn]
  (reset! active-layer (TileLayer.
                        #js {:title  "active"
                             :visible true
                             :source  (TileWMS.
                                       #js {:url "http://californiafireforecast.com:8181/geoserver/demo/wms"
                                            :params #js {"LAYERS" (str "demo:" layer)}
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
                                :zoom       10})}))
  (post-fn))

(defn swap-active-layer! [layer]
  (-> @active-layer
      .getSource
      (.updateParams #js {"LAYERS" (str "demo:" layer) "TILED" "true"})))

(defn set-active-layer-opacity! [opacity]
  (-> @active-layer
      (.setOpacity opacity)))

(defn wms-capabilities
  "Converts capbilites xml to a js object"
  [text]
  (.read (WMSCapabilities.) text))
