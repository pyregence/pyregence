(ns pyregence.components.openlayers)

;; OpenLayers aliases
(def Map        js/ol.Map)
(def View       js/ol.View)
(def defaults   js/ol.control.defaults)
(def fromLonLat js/ol.proj.fromLonLat)
(def TileLayer  js/ol.layer.Tile)
(def OSM        js/ol.source.OSM)
(def TileWMS    js/ol.source.TileWMS)

(defonce the-map   (atom nil))

(defn init-map! [layer]
  (reset! the-map
          (Map.
           #js {:target   "map"
                :layers   #js [(TileLayer.
                                #js {:title   "OpenStreetMap"
                                     :visible true
                                     :opacity 0.3
                                     :source  (OSM.)})
                               (TileLayer.
                                #js {:title  "Active WMS Layer"
                                     :visible true
                                     :source  (TileWMS.
                                               #js {:url "http://californiafireforecast.com:8181/geoserver/demo/wms"
                                                    :params #js {"LAYERS" (str "demo:" layer)}
                                                    :serverType "geoserver"})})]
                :controls (defaults)
                :view     (View.
                           #js {:projection "EPSG:3857"
                                :center     (fromLonLat #js [-120.8958 38.8375])
                                :zoom       10})})))

(defn swap-active-layer! [layer]
  (-> @the-map
      .getLayers
      .getArray
      (get 1)
      .getSource
      (.updateParams #js {"LAYERS" (str "demo:" layer) "TILED" "true"})))
