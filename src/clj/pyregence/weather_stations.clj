(ns pyregence.weather-stations
  (:require [clj-http.client :as client]))
(def urls (atom []))
(def cursors (atom []))

(defn get-stations-put-in-url
  []
  (loop [u "https://api.weather.gov/stations"]
    (let [{{os        :observationStations
            {p :next} :pagination} :body} (client/get u {:as :json})]
      (swap! urls concat os)
      (swap! cursors conj p)
      (println (count os))
      (println p)
      (println (count @urls))
      (Thread/sleep 5000)
      (when-not (< 2000 (count @urls))
        (recur p)))))

(defn get-some-observation-stations
  []
  (get-stations-put-in-url)
  (map
    #(client/get % {:as :json})
    (take 20 @urls)))

(defn stations
  []
  (->> (get-some-observation-stations)
       (map :body)
       (map (fn [{{i :stationIdentifier n :name} :properties
                  {c :coordinates}               :geometry}]
              [i n c]))))
(comment
  (stations)
  ;; => (["340PG" "Road to Ranches" [-122.7331 38.05648]]
  ;;     ["156SE" "SCE Bautista Creek" [-116.85673 33.70725]]
  ;;     ["612SE" "SCE Rocky Court" [-118.34749 35.08966]]
  ;;     ["049SE" "SCE Magic Mountain" [-118.37337 34.42503]]
  ;;     ["026CE" "56 Shadow Creek Ranch" [-95.39775 29.5553]])

  )
(def observations (atom []))

(defn another-get-observations-from-urls
  []
  (loop [surls (take 10 @urls)]
    (when-let [s (first surls)]
      (Thread/sleep 2000)
      (println "GET " s)
      (swap! observations conj (client/get s {:as :json}))
      (recur (rest surls)))))

(comment
  (def station-url (first (stations "observationStations")))
  (def station (:body (getj station-url)))

  station
  {"@context"
   ["https://geojson.org/geojson-ld/geojson-context.jsonld"
    {"s"                "https://schema.org/",
     "bearing"          {"@type" "s:QuantitativeValue"},
     "city"             "s:addressLocality",
     "wx"               "https://api.weather.gov/ontology#",
     "county"           {"@type" "@id"},
     "geo"              "http://www.opengis.net/ont/geosparql#",
     "forecastOffice"   {"@type" "@id"},
     "@version"         "1.1",
     "value"            {"@id" "s:value"},
     "geometry"         {"@id" "s:GeoCoordinates", "@type" "geo:wktLiteral"},
     "publicZone"       {"@type" "@id"},
     "distance"         {"@id" "s:Distance", "@type" "s:QuantitativeValue"},
     "@vocab"           "https://api.weather.gov/ontology#",
     "state"            "s:addressRegion",
     "unit"             "http://codes.wmo.int/common/unit/",
     "forecastGridData" {"@type" "@id"},
     "unitCode"         {"@id" "s:unitCode", "@type" "@id"}}],
   "id"       "https://api.weather.gov/stations/0007W",
   "type"     "Feature",
   "geometry" {"type" "Point", "coordinates" [-84.1787 30.53099]},
   "properties"
   {"forecast"          "https://api.weather.gov/zones/forecast/FLZ017",
    "@id"               "https://api.weather.gov/stations/0007W",
    "elevation"         {"unitCode" "wmoUnit:m", "value" 49.0728},
    "timeZone"          "America/New_York",
    "county"            "https://api.weather.gov/zones/county/FLC073",
    "stationIdentifier" "0007W",
    "name"              "Montford Middle",
    "fireWeatherZone"   "https://api.weather.gov/zones/fire/FLZ017",
    "@type"             "wx:ObservationStation"}})

(comment
  (select-keys station ["properties" "geometry"]))

(def url "https://api.weather.gov/stations/0007W/observations/latest")
(defn getj [u] (client/get u {:as :json-string-keys}))
(defn obs [] (get-in (getj url) [:body "properties"]))
(comment
  (select-keys (obs) ["windSpeed" "windDirection" "windGust" "temperature" "relativeHumidity" "dewpoint"])
  {"windSpeed"
   {"unitCode" "wmoUnit:km_h-1", "value" 8.028, "qualityControl" "V"},
   "windDirection"
   {"unitCode" "wmoUnit:degree_(angle)", "value" 323, "qualityControl" "V"},
   "windGust"    {"unitCode" "wmoUnit:km_h-1", "value" nil, "qualityControl" "Z"},
   "temperature" {"unitCode" "wmoUnit:degC", "value" nil, "qualityControl" "Z"},
   "relativeHumidity"
   {"unitCode" "wmoUnit:percent", "value" nil, "qualityControl" "Z"},
   "dewpoint"    {"unitCode" "wmoUnit:degC", "value" 23.23, "qualityControl" "V"}})

(defn get-weather-stations
  [_]
  {:type "FeatureCollection"
   :features [{:type     "Feature",
               :geometry {:type "Point", :coordinates [-110.340278 42.319167]},
               :properties
               {:image-url
                "https://prod.weathernode.net/data/img/15794/2025/08/15/Hogsback_1_1755288466_5020.jpg",
                :pan         32.4,
                :name        "Axis-Hogsback-mock",
                :update-time "2025-08-15T20:07:46Z",
                :tilt        0.24,
                :longitude   -110.340278,
                :state       "CA",
                :api-name    "alert-west",
                :latitude    42.319167}}]})
