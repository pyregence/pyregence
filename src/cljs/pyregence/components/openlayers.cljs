(ns pyregence.components.openlayers)

;; OpenLayers aliases
(def Map             js/ol.Map)
(def View            js/ol.View)
(def Overlay         js/ol.Overlay)
(def fromLonLat      js/ol.proj.fromLonLat)
(def TileLayer       js/ol.layer.Tile)
(def BingMaps        js/ol.source.BingMaps)
(def OSM             js/ol.source.OSM)
(def TileWMS         js/ol.source.TileWMS)
(def TileJSON        js/ol.source.TileJSON)
(def XYZ             js/ol.source.XYZ)
(def WMSCapabilities js/ol.format.WMSCapabilities)

(defonce the-map      (atom nil))
(defonce active-layer (atom nil))

(defonce base-map-options [{:opt_id    0
                            :opt_label "OpenStreetMaps"
                            :source    (OSM.)}
                           {:opt_id    1
                            :opt_label "MapTiler Topographique"
                            :source    (TileJSON.
                                        #js {:url "https://api.maptiler.com/maps/topographique/tiles.json?key=aTxMH7uEmrp1p92D9slS"
                                             :crossOrigin "anonymous"})}
                           {:opt_id    2
                            :opt_label "MapTiler Satellite"
                            :source    (TileJSON.
                                        #js {:url "https://api.maptiler.com/maps/hybrid/tiles.json?key=aTxMH7uEmrp1p92D9slS"
                                             :crossOrigin "anonymous"})}
                           {:opt_id    3
                            :opt_label "Thunderforest Landscape"
                            :source    (XYZ.
                                        #js {:url "https://tile.thunderforest.com/landscape/{z}/{x}/{y}.png?apikey=9dccbe884aab4483823c7cb6ab9b5f4d"})}
                           {:opt_id    4
                            :opt_label "BingMaps Street"
                            :source    (BingMaps.
                                        #js {:key "AjMtoHQV7tiC8tKyktN3OFi_qXUv_0i5TvtZFic3vkIOzR2iTFmYxVOPJKSZwjHq"
                                             :imagerySet "RoadOnDemand"})}
                           {:opt_id    5
                            :opt_label "BingMaps Areal"
                            :source    (BingMaps.
                                        #js {:key "AjMtoHQV7tiC8tKyktN3OFi_qXUv_0i5TvtZFic3vkIOzR2iTFmYxVOPJKSZwjHq"
                                             :imagerySet "Aerial"})}
                           {:opt_id    6
                            :opt_label "BingMaps Areal with Streets"
                            :source    (BingMaps.
                                        #js {:key "AjMtoHQV7tiC8tKyktN3OFi_qXUv_0i5TvtZFic3vkIOzR2iTFmYxVOPJKSZwjHq"
                                             :imagerySet "AerialWithLabelsOnDemand"})}])

;; Creating objects

(defn init-map! [layer]
  (reset! active-layer
          (TileLayer.
           #js {:title   "active"
                :visible true
                :opacity 0.7
                :source  (TileWMS.
                          #js {:url        "https://californiafireforecast.com:8443/geoserver/demo/wms"
                               :params     #js {"LAYERS" (str "demo:" layer)}
                               :serverType "geoserver"})}))
  (reset! the-map
          (Map.
           #js {:target   "map"
                :layers   #js [(TileLayer.
                                #js {:title   "basemap"
                                     :visible true
                                     :source  (OSM.)})
                               (TileLayer.
                                #js {:title   "hillshade"
                                     :visible false
                                     :opacity 0.5
                                     :source  (TileWMS.
                                               #js {:url        "https://basemap.nationalmap.gov/arcgis/services/USGSShadedReliefOnly/MapServer/WMSServer"
                                                    :params     #js {"LAYERS" "0" "bgcolor" "000000"}
                                                    :serverType "geoserver"})})
                               @active-layer]
                :controls #js []
                :view     (View.
                           #js {:projection "EPSG:3857"
                                :center     (fromLonLat #js [-120.8958 38.8375])
                                :minZoom    6
                                :maxZoom    18
                                :zoom       10})
                :overlays #js [(Overlay.
                                #js {:id               "popup"
                                     :element          (.getElementById js/document "popup")
                                     :position         nil
                                     :positioning      "bottom-center"
                                     :offset           #js [0 -4]
                                     :autoPan          true
                                     :autoPanAnimation #js {:duration 250}})]})))

;; Modifying objects

(defn add-map-single-click! [call-back]
  (.on @the-map
       "singleclick"
       (fn [evt]
         (let [map-overlay (.getOverlayById @the-map "popup")
               coord       (.-coordinate evt)
               [x y]       coord
               res         (-> @the-map .getView .getResolution)]
           (.setPosition map-overlay coord)
           (call-back [x y (+ x res) (+ y res)])))))

(defn add-map-zoom-end! [call-back]
  (.on @the-map
       "moveend"
       (fn [_]
         (-> @the-map
             .getView
             .getZoom
             call-back))))

(defn set-base-map-source! [source]
  (-> @the-map
      .getLayers
      (.forEach (fn [layer]
                  (when (= "basemap" (.get layer "title"))
                    (.setSource layer source))))))

(defn zoom-to-extent! [[minx miny maxx maxy]]
  (-> @the-map
      .getView
      (.fit (clj->js (concat (fromLonLat #js [minx miny])
                             (fromLonLat #js [maxx maxy]))))))

(defn swap-active-layer! [geo-layer]
  (-> @active-layer
      .getSource
      (.updateParams #js {"LAYERS" (str "demo:" geo-layer)})))

(defn set-opacity-by-title! [title opacity]
  (-> @the-map
      .getLayers
      (.forEach (fn [layer]
                  (when (= title (.get layer "title"))
                    (.setOpacity layer opacity))))))

(defn set-visible-by-tile! [title show?]
  (-> @the-map
      .getLayers
      (.forEach (fn [layer]
                  (when (= title (.get layer "title"))
                    (.setVisible layer show?))))))

(defn set-zoom! [zoom]
  (-> @the-map .getView (.setZoom zoom)))

;; Getting object information

(defn get-zoom-info []
  (let [map-view (.getView @the-map)]
    [(.getZoom    map-view)
     (.getMinZoom map-view)
     (.getMaxZoom map-view)]))

(defn get-selected-point []
  (when-let [[x y] (-> @the-map (.getOverlayById "popup") .getPosition)]
    (let [res (-> @the-map .getView .getResolution)]
      [x y (+ x res) (+ y res)])))

(defn wms-capabilities
  "Converts capabilities xml to a js object"
  [text]
  (.read (WMSCapabilities.) text))
