(ns pyregence.pages.near-term-forecast
  (:require [pyregence.styles :as $]
            ["ol"         :refer [Map View]]
            ["ol/control" :refer [defaults]]
            ["ol/proj"    :refer [fromLonLat]]
            ["ol/layer"   :refer [Tile]]
            ["ol/source"  :refer [OSM]]))

(defonce the-map (atom nil))

(defn init-map! []
  (reset! the-map
          (Map.
           #js {:target   "map"
                :layers   #js [(Tile.
                                #js {:title   "OpenStreetMap"
                                     :visible true
                                     :source  (OSM.)})]
                :controls (defaults)
                :view     (View.
                           #js {:projection "EPSG:3857"
                                :center     (fromLonLat #js [-120.8958 38.8375])
                                :zoom       10})})))

(defn root-component [_]
  (init-map!)
  (fn [_]
    [:div {:style ($/root)}
     [:h1 "This is a map. Put something cool on it."]]))
