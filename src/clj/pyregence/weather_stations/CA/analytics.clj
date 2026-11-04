(ns pyregence.weather-stations.CA.analytics
  (:require
   [pyregence.utils :as u]
   [pyregence.weather-stations.CA.rest-server :as rest-server]
   [pyregence.weather-stations.CA.xml-server :as x]
   [clojure.set :as set]))

(comment
  (def rest-server-observation-stations (rest-server/get-observation-stations!))
  (count rest-server-observation-stations) ;; => 2968 stations returned
  (def rest-msc (->> rest-server-observation-stations (map (comp :msc_id :properties)) set))
  (def rest-server-observations
    (->> rest-msc
         (mapv (fn [msc]
                 (u/url->response-body! (str "https://api.weather.gc.ca/collections/swob-realtime/items?f=json&sortby=-date_tm-value&limit=1&msc_id-value=" msc)
                                        {:as :json})))))

  (def rest-server-observations-success (->> rest-server-observations (filter identity) (filter #(<= 1 (:numberReturned %)))))

  (count rest-server-observations-success)
  ;; => 1590 stations returned observations

  ;; Lets turn our attention to the XML Server and get all the station information and observations in one call:
  (def xml-server-observation-stations-success (x/get-observation-stations!))
  (count xml-server-observation-stations-success) ;; => 2113 so more success then the rest api

  ;; How many xml stations aren't in the rest-api regardless of success? To discover that compare across the set of msc-ids.
  (def xml-msc (->> xml-server-observation-stations-success (map #(-> %  :properties :msc_id)) set))
  (def rest-msc-success (->> rest-server-observations-success (map #(-> %  :properties :msc_id)) set))
  (count (set/difference xml-msc rest-msc)) ;; => 75

  (def rest-success-msc (->> rest-server-observations-success (map #(-> % :features first :properties :msc_id-value)) set))
  (count (set/difference xml-msc rest-success-msc)) ;; => 597 stations more by reading the xml files then if we use the rest system

  (require '[clojure.data.csv :as csv]
           '[semantic-csv.core :as sc]
           '[clojure.string :as str])

  (defn read-csv
    [url]
    (->> url  slurp csv/read-csv (sc/mappify {:transform-header (fn [h] (-> h str/lower-case (str/replace "# " "") (str/replace "_" "-") (str/replace " " "-") keyword))})))

  (def pc (read-csv "https://dd.weather.gc.ca/today/observations/doc/swob-xml_partner_station_list.csv"))
  ;; partner counts
  ;; csv
  (-> pc count)
   ;; => 1996

  ;; mine
  (def partner-stations (x/get-partner-stations))
  (count partner-stations)
  ;; => 1653

  ;; noel 1755
  ;;
  )
