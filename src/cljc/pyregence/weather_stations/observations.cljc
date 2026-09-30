(ns pyregence.weather-stations.observations
  (:require
   [clojure.string :as str]
   [pyregence.weather-stations.observations.CA    :as observations-ca]
   [pyregence.weather-stations.observations.US    :as observations-us])
  #?(:clj
     (:import [java.time ZonedDateTime ZoneOffset]
              [java.time.format DateTimeFormatter])))

(defn partner-ids->partner-url
  [{:keys [network stn_id]}]
  (let [get-yyyymmdd
        (fn
          []
          #?(:clj
             (let [now (ZonedDateTime/now ZoneOffset/UTC)]
               (.format now (DateTimeFormatter/ofPattern "yyyyMMdd")))

             :cljs
             (let [now (js/Date.)
                   pad #(if (< % 10) (str "0" %) (str %))
                   year (.getUTCFullYear now)
                   month (pad (inc (.getUTCMonth now)))
                   day (pad (.getUTCDate now))]
               (str year month day))))]
    (str
     "https://dd.weather.gc.ca/today/observations/swob-ml/partners/"
     network
     "/"
     (get-yyyymmdd)
     "/"
     (str/lower-case stn_id))))

(defn weather-station-response->observation-url
  [{:keys [stationIdentifier msc_id]}]
  (if stationIdentifier
    (-> stationIdentifier observations-us/station-id->url)
    (-> msc_id observations-ca/station-id->url)))

(defn weather-station->cmpt-info
  [{:keys [stationIdentifier msc_id name_en name]}]
  {:id (or stationIdentifier msc_id)
   :name (observations-ca/format-string (or name name_en))})

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

    properties-ca
    {:station {:name      (-> properties-ca
                              :stn_nam-value
                              observations-ca/format-string)
               :id        (properties-ca :msc_id-value)
               :timestamp (properties-ca :date_tm-value)}
     :observations (-> properties-ca
                       observations-ca/properties->display-name->value-with-uom)}))
