(ns pyregence.weather-stations
  (:require
   [clj-http.client                          :as client]
   [pyregence.weather-stations.CA.servers    :as ca-servers]
   [triangulum.config                        :refer [get-config]]
   [triangulum.logging                       :refer [log log-str]]))

(defonce observation-stations (atom []))

(defn- get-US-observation-stations!
  []
  (->>
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
         observation-stations)))
   (filter (fn [{{provider :provider} :properties}]
             (#{"MesoWest" "RAWS" "ASOS"} provider)))
   (remove (fn [{:keys [id]}] (= id "https://api.weather.gov/stations/0007W")))))

(defn- get-observation-stations!
  []
  (let [select-relevent-properties
        (fn [{{[lon lat] :coordinates} :geometry :as ws}]
          (-> ws
              (update :properties select-keys [:name
                                               :stationIdentifier
                                               :msc_id
                                               :name_en
                                               :network
                                               :iata_id])
              (update :properties assoc :longitude lon :latitude lat)))]
    (reset! observation-stations
            (->> (get-US-observation-stations!)
                 (concat (ca-servers/get-observation-stations!))
                 ;;TODO consider moving `select-relevent-properties` into two fns attached to our get-us get-ca
                 (map select-relevent-properties)))))

(defn periodically-get-observation-stations-in-the-background!
  []
  (future
    (loop []
      (try
        ;;TODO consider a way to hydrate per page and/or save cache between server restarts.
        (get-observation-stations!)
        (log-str "weather-stations-updated")
        (catch Exception ex (log (ex-data ex) :truncate? false)))
      (Thread/sleep (* 1000 ;; 1s
                       60   ;; 1m
                       (get-config ::get-observation-stations-every-n-minutes)))
      (recur))))

(defn get-weather-stations
  [_]
  {:type     "FeatureCollection"
   :features @observation-stations})
