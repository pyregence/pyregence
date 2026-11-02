(ns pyregence.weather-stations.CA.analytics
  (:require
   [pyregence.utils :as u]
   [pyregence.weather-stations.CA.rest-server :as rest-server]
   [pyregence.weather-stations.CA.xml-server :as x]
   [clojure.set :as set]))

(comment
  ;; By reading the xml files we gain around around a thousand stations more then if we just read the file path.
  ;; This number doubles to two thousand compare it to only those REST stations that actually returned an observation.
  ;; To discover this first will read the REST API stations, you can see below we get 2030
  (def rest-server-observation-stations (rest-server/get-observation-stations!))
  (count rest-server-observation-stations) ;; => 2030 stations returned
  (def rest-msc (->> rest-server-observation-stations (map (comp :msc_id :properties)) set))
  (def rest-server-observations
    (->> rest-msc
         (mapv (fn [msc]
                 (u/url->response-body! (str "https://api.weather.gc.ca/collections/swob-realtime/items?f=json&sortby=-date_tm-value&limit=1&msc_id-value=" msc)
                                        {:as :json})))))
  (def rest-server-observations-success (->> rest-server-observations (filter identity) (filter #(<= 1 (:numberReturned %)))))
  (count rest-server-observations-success);; => 695 stations returned observations

  ;; Lets turn our attention to the XML Server and get all the station information and observations in one call:
  (def xml-server-observation-stations-success (x/get-observation-stations!))
  (count xml-server-observation-stations-success) ;; => 2905 stations returned, as we noted, more the the rest api.

  ;; How many xml stations aren't in the rest-api regardless of success? To discover that compare across the set of msc-ids.
  (def xml-msc (->> xml-server-observation-stations-success (map #(-> %  :properties :msc_id)) set))
  (def rest-msc-success (->> rest-server-observations-success (map #(-> %  :properties :msc_id)) set))
  (count (set/difference xml-msc rest-msc)) ;; => 994 stations gained by reading the xml file vs just the path
  (def rest-success-msc (->> rest-server-observations-success (map #(-> % :features first :properties :msc_id-value)) set))
  (count (set/difference xml-msc rest-success-msc)) ;; => 1930 stations more by reading the xml files then if we use the rest system

  ;;
  )
