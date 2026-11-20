(ns pyregence.weather-stations.CA.rest-server
  (:require
   [clj-http.client     :as client]))

(defn get-observation-stations!
  []
  (let [get-CA-observation-stations-from-url!
        (fn
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
                observation-stations))))]
    (reduce (fn [l url] (concat l (get-CA-observation-stations-from-url! url))) []
            ["https://api.weather.gc.ca/collections/swob-partner-stations/items?lang=en&limit=5000"
             "https://api.weather.gc.ca/collections/swob-stations/items?lang=en&limit=5000"])))
