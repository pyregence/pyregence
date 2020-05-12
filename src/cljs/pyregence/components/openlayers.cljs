(ns pyregence.components.openlayers)

;; OpenLayers aliases
(def Map             js/ol.Map)
(def View            js/ol.View)
(def Overlay         js/ol.Overlay)
(def MousePosition   js/ol.control.MousePosition)
(def ScaleLine       js/ol.control.ScaleLine)
(def fromLonLat      js/ol.proj.fromLonLat)
(def toLonLat        js/ol.proj.toLonLat)
(def TileLayer       js/ol.layer.Tile)
(def BingMaps        js/ol.source.BingMaps)
(def OSM             js/ol.source.OSM)
(def TileWMS         js/ol.source.TileWMS)
(def TileJSON        js/ol.source.TileJSON)
(def XYZ             js/ol.source.XYZ)
(def WMSCapabilities js/ol.format.WMSCapabilities)

(defonce the-map (atom nil))

(def base-map-options [{:opt-id    0
                        :opt-label "OpenStreetMaps"
                        :source    (OSM.)}
                       {:opt-id    1
                        :opt-label "MapTiler Topographique"
                        :source    (TileJSON.
                                    #js {:url "https://api.maptiler.com/maps/topographique/tiles.json?key=aTxMH7uEmrp1p92D9slS"
                                         :crossOrigin "anonymous"})}
                       {:opt-id    2
                        :opt-label "MapTiler Satellite"
                        :source    (TileJSON.
                                    #js {:url "https://api.maptiler.com/maps/hybrid/tiles.json?key=aTxMH7uEmrp1p92D9slS"
                                         :crossOrigin "anonymous"})}
                       {:opt-id    3
                        :opt-label "Thunderforest Landscape"
                        :source    (XYZ.
                                    #js {:url "https://tile.thunderforest.com/landscape/{z}/{x}/{y}.png?apikey=9dccbe884aab4483823c7cb6ab9b5f4d"})}
                       {:opt-id    4
                        :opt-label "BingMaps Street"
                        :source    (BingMaps.
                                    #js {:key "AjMtoHQV7tiC8tKyktN3OFi_qXUv_0i5TvtZFic3vkIOzR2iTFmYxVOPJKSZwjHq"
                                         :imagerySet "RoadOnDemand"})}
                       {:opt-id    5
                        :opt-label "BingMaps Aerial"
                        :source    (BingMaps.
                                    #js {:key "AjMtoHQV7tiC8tKyktN3OFi_qXUv_0i5TvtZFic3vkIOzR2iTFmYxVOPJKSZwjHq"
                                         :imagerySet "Aerial"})}
                       {:opt-id    6
                        :opt-label "BingMaps Aerial with Streets"
                        :source    (BingMaps.
                                    #js {:key "AjMtoHQV7tiC8tKyktN3OFi_qXUv_0i5TvtZFic3vkIOzR2iTFmYxVOPJKSZwjHq"
                                         :imagerySet "AerialWithLabelsOnDemand"})}])

;; Creating objects

(defn init-map! [layer]
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
                                                    :params     #js {"LAYERS" "0" "TRANSPARENT" "FALSE"}
                                                    :serverType "geoserver"})})
                               (TileLayer.
                                #js {:title   "active"
                                     :visible true
                                     :opacity 0.7
                                     :source  (TileWMS.
                                               #js {:url         "https://californiafireforecast.com:8443/geoserver/demo/wms"
                                                    :params      #js {"LAYERS" (str "demo:" layer)}
                                                    :crossOrigin "anonymous"
                                                    :serverType  "geoserver"})})]
                :controls #js [(ScaleLine.) (MousePosition.)]
                :view     (View.
                           #js {:projection "EPSG:3857"
                                :center     (fromLonLat #js [-120.8958 38.8375] "EPSG:3857")
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

;; Getting object information

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

(defn wms-capabilities
  "Converts capabilities xml to a js object"
  [text]
  (.read (WMSCapabilities.) text))

(defn get-layer-by-title [title]
  (-> @the-map
      .getLayers
      .getArray
      (.find (fn [layer] (= title (.get layer "title"))))))

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

(defn add-map-mouse-move! [call-back]
  (.on @the-map
       "pointermove"
       (fn [evt]
         (call-back (toLonLat (.-coordinate evt) "EPSG:3857")))))

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
  (-> (get-layer-by-title "active")
      .getSource
      (.updateParams #js {"LAYERS" (str "demo:" geo-layer)})))

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

(defn center-on-overlay! []
  (when-let [center (get-overlay-center)]
    (-> @the-map .getView (.setCenter center))))
