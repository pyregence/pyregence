(ns pyregence.weather-stations
  (:require [clj-http.client     :as client]
            [triangulum.logging  :refer [log log-str]]
            [triangulum.config   :refer [get-config]]))

(defonce observation-stations (atom []))

;;NOTE this takes roughly 5-10 minutes
(defn- get-all-observation-stations!
  []
  (loop [url                  "https://api.weather.gov/stations"
         observation-stations []]
    (Thread/sleep 2000)
    (let [{{new-observation-stations           :features
            {next-batch-of-stations-url :next} :pagination} :body}
          (client/get url {:as      :json
                           :headers {"User-Agent"    "support@sig-gis.com"
                                     "Feature-Flags" "obs_station_provider"}
                           :connection-timeout (* 1000 60 3)})]
      (if (seq new-observation-stations)
        (recur next-batch-of-stations-url (concat observation-stations new-observation-stations))
        observation-stations))))

(defn periodically-get-observation-stations-in-the-background!
  []
  (future
    (loop []
      (try
        ;;TODO consider a way to hydrate per page and/or save cache between server restarts.
        (reset! observation-stations (get-all-observation-stations!))
        (log-str "weather-stations-updated")
        (catch Exception ex (log (ex-data ex) :truncate? false)))
      (Thread/sleep (* 1000 ;; 1s
                       60   ;; 1m
                       (get-config :pyregence.weather-stations/get-observation-stations-every-n-minutes)))
      (recur))))

(defn- select-relevent-properties
  [{{[lon lat] :coordinates} :geometry :as ws}]
  (-> ws
      (update :properties select-keys [:name :stationIdentifier])
      (update :properties assoc :longitude lon :latitude lat)))

(defn get-weather-stations
  [_]
  {:type "FeatureCollection"
   :features (->> @observation-stations
                  (filter (fn [{{provider :provider} :properties}]
                            (#{"MesoWest" "RAWS" "ASOS"} provider)))
                  (map select-relevent-properties))})
