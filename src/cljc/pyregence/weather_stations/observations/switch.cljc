(ns pyregence.weather-stations.observations.switch
  (:require
   [pyregence.weather-stations.observations.CA    :as observations-ca]
   [pyregence.weather-stations.observations.US    :as observations-us]
   [clojure.set :as set]))

(defn weather-station-response->observation-url
  [{:keys [stationIdentifier msc_id]}]
  (if stationIdentifier
    (-> stationIdentifier observations-us/station-id->url)
    (-> msc_id observations-ca/station-id->url)))

(defn weather-station->cmpt-info
  [{:keys [stationIdentifier msc_id name_en name]}]
  {:id (or stationIdentifier msc_id)
   :name (or name name_en)})

(defn response->observations
  [{properties-us :properties
    [{properties-ca :properties} & _] :features}]
  (cond
    (not (or (seq properties-us) (seq properties-ca)))
    nil

    properties-us
    {:station {:name      (properties-us :stationName)
               :id        (properties-us :stationId)
               :timestamp (properties-us :timestamp)}
     :observations (-> properties-us
                       (dissoc :stationName :stationId :timestamp)
                       observations-us/properties->display-name->value-with-uom)}

    :canada
    {:station {:name      (-> properties-ca
                              :stn_nam-value
                              observations-ca/stn_name-value->format)
               :id        (properties-ca :msc_id-value)
               :timestamp (properties-ca :date_tm-value)}
     :observations (-> properties-ca
                       observations-ca/properties->display-name->value-with-uom)}))
