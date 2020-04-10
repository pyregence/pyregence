(ns pyregence.pages.near-term-forecast
  (:require [pyregence.styles :as $]
            [reagent.core :as r]))

;; OpenLayers aliases
(def Map        js/ol.Map)
(def View       js/ol.View)
(def defaults   js/ol.control.defaults)
(def fromLonLat js/ol.proj.fromLonLat)
(def TileLayer  js/ol.layer.Tile)
(def OSM        js/ol.source.OSM)
(def TileWMS    js/ol.source.TileWMS)

(def layers-list ["fire-area_20171006_070000"
                  "fire-area_20171007_070000"
                  "fire-area_20171008_070000"
                  "fire-area_20171009_070000"
                  "fire-area_20171010_070000"])

(defonce the-map   (atom nil))
(defonce cur-layer (atom 0))

(defn init-map! []
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
                                                    :params #js {"LAYERS" (str "demo:" (get layers-list @cur-layer))}
                                                    :serverType "geoserver"})})]
                :controls (defaults)
                :view     (View.
                           #js {:projection "EPSG:3857"
                                :center     (fromLonLat #js [-120.8958 38.8375])
                                :zoom       10})})))

(defn next-layer! [map]
  (reset! cur-layer (if (= 4 @cur-layer) 0 (inc @cur-layer)))
  (-> map
      .getLayers
      .getArray
      (get 1)
      .getSource
      (.updateParams #js {"LAYERS" (str "demo:" (get layers-list @cur-layer)) "TILED" "true"})))

(defn root-component [_]
  (r/create-class
   {:component-did-mount #(init-map!)
    :reagent-render (fn [_]
                      [:div {:style ($/combine $/root {:height "100%" :padding 0})}
                       [:div {:style {:display "flex"}}
                        [:h1 "Click next to see the next time step fire area layer."]
                        [:button {:style {:padding ".25rem" :margin-left "1rem"}
                                  :type "button"
                                  :on-click #(next-layer! @the-map)}
                         "Next"]]
                       [:div#map {:style {:height "100%" :width "100%"}}]])}))
