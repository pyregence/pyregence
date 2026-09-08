(ns pyregence.weather-stations
  (:require
   [clj-http.client     :as client]
   [triangulum.config   :refer [get-config]]
   [triangulum.logging  :refer [log log-str]]))

(defonce observation-stations (atom []))

;;NOTE this takes roughly 5-10 minutes
(defn- get-US-observation-stations!
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

(defn get-CA-observation-stations-from-url!
  [url]
  (loop [url                  url
         observation-stations []]
    (Thread/sleep 2000)
    (let [{{links :links
            new-observation-stations :features} :body} (client/get url {:as :json :connection-timeout (* 1000 60 3)})
          next-batch-of-stations-url (->> links (some (fn [{:keys [rel href]}] (when (= rel "next") href))))
          observation-stations (concat observation-stations new-observation-stations)]
      (if (seq next-batch-of-stations-url)
        (recur next-batch-of-stations-url observation-stations)
        observation-stations))))

(defn get-CA-observation-stations!
  []
  (reduce
   (fn [l url]
     (concat l (get-CA-observation-stations-from-url! url)))
   []
   ["https://api.weather.gc.ca/collections/swob-partner-stations/items?lang=en&limit=5000"
    "https://api.weather.gc.ca/collections/swob-stations/items?lang=en&limit=5000"]))

(defn- select-relevent-properties
  [{{[lon lat] :coordinates} :geometry :as ws}]
  (-> ws
      (update :properties select-keys [:name :stationIdentifier :msc_id])
      (update :properties assoc :longitude lon :latitude lat)))

(defn periodically-get-observation-stations-in-the-background!
  []
  (future
    (loop []
      (try
        ;;TODO consider a way to hydrate per page and/or save cache between server restarts.
        (reset! observation-stations
                (->> (get-US-observation-stations!)
                     (filter (fn [{{provider :provider} :properties}]
                               (#{"MesoWest" "RAWS" "ASOS"} provider)))
                     (remove (fn [{:keys [id]}] (= id "https://api.weather.gov/stations/0007W")))
                     (concat (get-CA-observation-stations!))
                     (map select-relevent-properties)))
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
