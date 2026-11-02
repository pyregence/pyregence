(ns pyregence.weather-stations.CA.analytics
  (:require
   [pyregence.utils :as u]
   [clojure.data.json :as j]
   [clojure.set :as set]))

(comment
  ;; conclusion
  ;; By reading the xml files we gain around 1,000 more stations.

  ;; First will read the Rest API stations
  (require '[pyregence.weather-stations.CA.rest-server :as rest-server])
  (def rest-server-observation-stations (rest-server/get-observation-stations!))
  (count rest-server-observation-stations)
  ;; => 2030
  (def rest-msc (->> rest-server-observation-stations (map (comp :msc_id :properties)) set))
  (first rest-msc)
  ;; => "DFO_AZMP-ESG"
  (count rest-msc)
  ;; => 2030

;; how many of those actually return an observation?
  (require '[pyregence.utils :as u])
  (def rest-server-observations
    (->> rest-msc
         (mapv (fn [msc]
                 (u/url->response-body! (str "https://api.weather.gc.ca/collections/swob-realtime/items?f=json&sortby=-date_tm-value&limit=1&msc_id-value=" msc)
                                        {:as :json})))))
  ;; lets only count those that returned observations
  (def rest-server-observations-success (->> rest-server-observations (filter identity) (filter #(<= 1 (:numberReturned %)))))

;; XML SERVER
  ;; our current implementation reads the xml files
  (require '[pyregence.weather-stations.CA.xml-server :as x])
  (def xml-server-observation-stations-success (x/get-observation-stations!))
  (count xml-server-observation-stations-success)
  ;; => 2905

  ;; => 2905

  ;; how many xml servers aren't in the rest-api regardless of success
  (def xml-msc (->> xml-server-observation-stations-success (map #(-> %  :properties :msc_id)) set))
  (def rest-msc (->> rest-server-observation-stations (map #(-> %  :properties :msc_id)) set))

  (count (set/difference xml-msc rest-msc))
  ;; => 994 stations gained by reading the xml file vs just the path

;;
  )
