(ns pyregence.weather-stations
  (:require [clj-http.client :as client]))

(defonce observation-stations (atom []))

;;TODO consider turning this isn't a transducer, that way i think limit and sleep wouldn't have to be passed in as arguments.
;;TODO before release to prod we should see how this behaves in dev over time and potentially have it handle failures that come up!
;;TODO consider adding a connect-timeout and socket-timeout
;;TODO concerning adding something to check if this timesout, returns a none 200, doesn't reach the end, in some set amount of time.
;;TODO consider handling 504/timeout
;;TODO consider having this be clj-async...
(defn get-observation-stations!
  []
  (loop [url "https://api.weather.gov/stations"]
    (let [{{new-observation-stations           :features
            {next-batch-of-stations-url :next} :pagination} :body}
          (client/get url {:as      :json
                           :headers {"User-Agent" "support@sig-gis.com"}
                           ;; throw if we don't hear back in 3 minutes, because by then we never will.
                           :connection-timeout (* 1000 60 3)})]
      (when (seq new-observation-stations)
        (swap! observation-stations concat new-observation-stations)
        (Thread/sleep 2000)
        (recur next-batch-of-stations-url)))))

;;TODO this needs a way to refresh the observation-stations atom with new data on some trigger: time or user event
;; If it's a time based event this defonce to future layer might be ok, if user-event this logic will obviously move.
;; we have an example of user-event based cache refresh in camera.
(defonce get-observation-stations-in-the-background!
  (future (get-observation-stations!)))

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
