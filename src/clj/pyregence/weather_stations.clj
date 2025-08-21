(ns pyregence.weather-stations
  (:require [clj-http.client :as client]))

(defonce observation-stations (atom []))

;;TODO consider turning this isn't a transducer, that way i think limit and sleep wouldn't have to be passed in as arguments.
;;TODO before release to prod we should see how this behaves in dev over time and potentially have it handle failures that come up!
;;TODO consider adding a connect-timeout and socket-timeout
;;TODO concerning adding something to check if this timesout, returns a none 200, doesn't reach the end, in some
;;set amount of time.
;; that takes roughly an hour
;;TODO consider handling 504/timeout
;;TODO consider having this be async...
(defn get-observation-stations!
  "Returns a list of observation stations from the weather.gov api which you can limit by total urls or fetches per second."
  [& {:keys [total-station-limit rate-limit-per-second]
      :or {rate-limit-per-second 10 total-station-limit Double/POSITIVE_INFINITY connection-timeout 10}}]
  (loop [url "https://api.weather.gov/stations"
         observation-stations []]
    (let [{{new-observation-stations        :features
            {next-batch-of-stations-url :next} :pagination} :body
           status :status
           :as response} (client/get url {:as :json
                                          :headers {"User-Agent" "support@sig-gis.com"}})
          no-new-observation-stations? (zero? (count new-observation-stations))
          observation-stations (concat observation-stations new-observation-stations)
          station-cap-hit? (<= total-station-limit (count observation-stations))]
      (Thread/sleep (* rate-limit-per-second 1000))
      (if (or no-new-observation-stations? station-cap-hit?)
        observation-stations
        (recur next-batch-of-stations-url observation-stations)))))

;;TODO this needs a way to refresh the observation-stations atom with new data on some trigger: time or user event
(defonce f
  (future
    (loop [url "https://api.weather.gov/stations"]
      (let [{{new-observation-stations           :features
              {next-batch-of-stations-url :next} :pagination} :body}
            (client/get url {:as      :json
                             :headers {"User-Agent" "support@sig-gis.com"}})]
        (when (seq new-observation-stations)
          observation-stations (swap! observation-stations concat new-observation-stations)
          (Thread/sleep 2000)
          (recur next-batch-of-stations-url))))))

;; example time based trigger
(comment
  (defonce counter (atom 0))

  (def scheduled-counter
    (future
      (loop [n 0]
        (Thread/sleep (* 2000 ;; 1s
                         ;;60   ;; 1m
                         ;;60   ;; 1h
                         ;;24   ;; 1d
                         ;;7    ;; 1w
                         )    ;; => 604800000
                      )

        (recur (reset! counter (inc n))))))

  (def scheduled-counter
    (future
      (loop [n 0]
        (Thread/sleep 2000)
        (recur (reset! counter (inc n))))))
  )

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
   :features (->> @observation-stations
                  (map ws->camera-format))})
