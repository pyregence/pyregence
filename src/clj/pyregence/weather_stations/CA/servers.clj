(ns pyregence.weather-stations.CA.servers
  (:require
   [pyregence.weather-stations.CA.rest-server :as rest-server]
   [pyregence.weather-stations.CA.xml-server  :as xml-server]))

(defn get-observation-stations!
  []
  (->> (concat (rest-server/get-observation-stations!)
               (xml-server/get-observation-stations!))
       (group-by (comp :msc_id :properties))
       (reduce-kv
        (fn [l _ [{g1 :geometry p1 :properties} {p2 :properties}]]
          (conj l {:geometry g1 :properties (merge p1 p2)}))
        [])))
