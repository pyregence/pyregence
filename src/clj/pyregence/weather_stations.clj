(ns pyregence.weather-stations
  (:require [clj-http.client :as client]))

;;TODO consider turning this isn't a transducer, that way i think limit and sleep wouldn't have to be passed in as arguments.
;;NOTE this makes no attempt to gracefully handler failures
;;TODO before release to prod we should see how this behaves in dev over time and potentially have it handle failures that come up!
;;TODO consider adding a connect-timeout and socket-timeout
;;TODO add something to check if this timesout, returns a none 200, doesn't reach the end, in some
;;set amount of time.
;;NOTE there are roughly 63500 stations at 30 seconds, which is a safe pull-rate given the implicit rate limit,
;; that takes roughly an hour
;;TODO handle if this throws due to a 504/timeout
(defn get-observation-stations!
  "Returns a list of observation stations from the weather.gov api which you can limit by total urls or fetches per second."
  [& {:keys [total-station-limit rate-limit-per-second] :or {rate-limit-per-second 30 total-station-limit Double/POSITIVE_INFINITY}}]
  (println "start---")
  (loop [url "https://api.weather.gov/stations"
         observation-stations []]
    (let [{{new-observation-stations        :features
            {next-batch-of-stations-url :next} :pagination} :body
           :as response} (client/get url {:as :json})
          ;;NOTE it's unclear how to tell we reached the end as the next-batch-of-stations-url isn't nil even after it runs out.
          ;;so we count the stations instead, which should be just as accurate.
          no-new-observation-stations? (zero? (count new-observation-stations))
          observation-stations (concat observation-stations new-observation-stations)
          station-cap-hit? (<= total-station-limit (count observation-stations))]
      (println {:status (:status response) :count (count observation-stations) })
      (Thread/sleep (* rate-limit-per-second 1000))
      (if (or no-new-observation-stations? station-cap-hit?)
        observation-stations
        (recur next-batch-of-stations-url observation-stations)))))

;;(defonce observation-stations (get-observation-stations! {:total-station-limit 4000}))


(def observation-stations
  (future
    (time (get-observation-stations! {:rate-limit-per-second 130}))))


;;TODO rethink this idea because were changing the ws to be the the camera and it might not need
;; everything like pan and tilt.
(defn ws->camera-format
  [{{[lon lat] :coordinates} :geometry :as ws}]
  (-> ws
      (update :properties select-keys [:name])
      (update :properties assoc :longitude lon :latitude lat)))

(defn get-weather-stations
  [_]
  {:type "FeatureCollection"
   :features [] #_(->> observation-stations
                  (map ws->camera-format))})
