(ns pyregence.components.openlayers)

;; OpenLayers aliases
(def Map             js/ol.Map)
(def View            js/ol.View)
(def fromLonLat      js/ol.proj.fromLonLat)
(def TileLayer       js/ol.layer.Tile)
(def OSM             js/ol.source.OSM)
(def TileWMS         js/ol.source.TileWMS)
(def WMSCapabilities js/ol.format.WMSCapabilities)

(defonce the-map      (atom nil))
(defonce active-layer (atom nil))

;; Creating objects

(defn init-map! [layer]
  (reset! active-layer (TileLayer.
                        #js {:title   "active"
                             :visible true
                             :source  (TileWMS.
                                       #js {:url "https://californiafireforecast.com:8443/geoserver/demo/wms"
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
                :controls #js []
                :view     (View.
                           #js {:projection "EPSG:3857"
                                :center     (fromLonLat #js [-120.8958 38.8375])
                                :minZoom    6
                                :maxZoom    18
                                :zoom       10})})))

;; Modifying objects

(defn add-map-single-click! [call-back]
  (let [map-view (.getView @the-map)]
    (.on @the-map
         "singleclick"
         (fn [evt]
           (let [[x y] (.-coordinate evt)
                 res   (.getResolution map-view)]
             (call-back {:bbox [x y (+ x res) (+ y res)]
                         :crs  (-> map-view .getProjection .getCode)}))))))

(defn swap-active-layer! [layer]
  (-> @active-layer
      .getSource
      (.updateParams #js {"LAYERS" (str "demo:" layer) "TILED" "true"})))

(defn set-active-layer-opacity! [opacity]
  (-> @active-layer
      (.setOpacity opacity)))

(defn set-zoom [zoom]
  (-> @the-map .getView (.setZoom zoom)))

;; Getting object information

(defn get-zoom-info []
  (let [map-view (.getView @the-map)]
    [(.getZoom    map-view)
     (.getMinZoom map-view)
     (.getMaxZoom map-view)]))

(defn wms-capabilities
  "Converts capabilities xml to a js object"
  [text]
  (.read (WMSCapabilities.) text))
