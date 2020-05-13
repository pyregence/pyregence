(ns pyregence.config)

;; Layer options

(def layer-types [{:opt-id 0
                   :opt-label "Fire Area"
                   :filter "fire-area"
                   :units "Acres"}
                  {:opt-id 1
                   :opt-label "Fire Volume"
                   :filter "fire-volume"
                   :units "Acre-ft"}
                  {:opt-id 2
                   :opt-label "Impacted Structures"
                   :filter "impacted-structures"
                   :units "Structures"}
                  {:opt-id 3
                   :opt-label "Times Burned"
                   :filter "times-burned"
                   :units "Times"}])

;; Scroll speeds for time slider

(def speeds [{:opt-id 0 :opt-label ".5x" :delay 2000}
             {:opt-id 1 :opt-label "1x"  :delay 1000}
             {:opt-id 2 :opt-label "2x"  :delay 500}
             {:opt-id 3 :opt-label "5x"  :delay 200}])

;; Base Map Options

(def BingMaps        js/ol.source.BingMaps)
(def OSM             js/ol.source.OSM)
(def TileWMS         js/ol.source.TileWMS)
(def TileJSON        js/ol.source.TileJSON)
(def XYZ             js/ol.source.XYZ)
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