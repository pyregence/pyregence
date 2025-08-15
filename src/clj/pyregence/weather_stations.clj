(ns pyregence.weather-stations)

(require '[clj-http.client :as client]
         '[clojure.string :as str])

(def url "https://api.weather.gov/")
(defn getj [u] (client/get u {:as :json-string-keys}))
(def stations (:body (getj "https://api.weather.gov/stations")))
(def station-url (first (stations "observationStations")))
(def station (:body (getj station-url)))
station
{"@context"
 ["https://geojson.org/geojson-ld/geojson-context.jsonld"
  {"s" "https://schema.org/",
   "bearing" {"@type" "s:QuantitativeValue"},
   "city" "s:addressLocality",
   "wx" "https://api.weather.gov/ontology#",
   "county" {"@type" "@id"},
   "geo" "http://www.opengis.net/ont/geosparql#",
   "forecastOffice" {"@type" "@id"},
   "@version" "1.1",
   "value" {"@id" "s:value"},
   "geometry" {"@id" "s:GeoCoordinates", "@type" "geo:wktLiteral"},
   "publicZone" {"@type" "@id"},
   "distance" {"@id" "s:Distance", "@type" "s:QuantitativeValue"},
   "@vocab" "https://api.weather.gov/ontology#",
   "state" "s:addressRegion",
   "unit" "http://codes.wmo.int/common/unit/",
   "forecastGridData" {"@type" "@id"},
   "unitCode" {"@id" "s:unitCode", "@type" "@id"}}],
 "id" "https://api.weather.gov/stations/0007W",
 "type" "Feature",
 "geometry" {"type" "Point", "coordinates" [-84.1787 30.53099]},
 "properties"
 {"forecast" "https://api.weather.gov/zones/forecast/FLZ017",
  "@id" "https://api.weather.gov/stations/0007W",
  "elevation" {"unitCode" "wmoUnit:m", "value" 49.0728},
  "timeZone" "America/New_York",
  "county" "https://api.weather.gov/zones/county/FLC073",
  "stationIdentifier" "0007W",
  "name" "Montford Middle",
  "fireWeatherZone" "https://api.weather.gov/zones/fire/FLZ017",
  "@type" "wx:ObservationStation"}}

(select-keys station ["properties" "geometry"])

(def url "https://api.weather.gov/stations/0007W/observations/latest")
(defn getj [u] (client/get u {:as :json-string-keys}))
(def obs (get-in (getj url) [:body "properties"]))

(select-keys obs ["windSpeed" "windDirection" "windGust" "temperature" "relativeHumidity" "dewpoint"])
{"windSpeed"
 {"unitCode" "wmoUnit:km_h-1", "value" 8.028, "qualityControl" "V"},
 "windDirection"
 {"unitCode" "wmoUnit:degree_(angle)", "value" 323, "qualityControl" "V"},
 "windGust" {"unitCode" "wmoUnit:km_h-1", "value" nil, "qualityControl" "Z"},
 "temperature" {"unitCode" "wmoUnit:degC", "value" nil, "qualityControl" "Z"},
 "relativeHumidity"
 {"unitCode" "wmoUnit:percent", "value" nil, "qualityControl" "Z"},
 "dewpoint" {"unitCode" "wmoUnit:degC", "value" 23.23, "qualityControl" "V"}}

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
