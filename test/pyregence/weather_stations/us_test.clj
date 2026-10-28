(ns pyregence.weather-stations.us-test
  (:require [clojure.data.json             :as json]
            [clojure.java.io               :as io]
            [clojure.test                  :refer [deftest is testing]]
            [pyregence.weather-stations.us :as us]))

(defn- read-fixture
  [file-name]
  (-> (io/resource (str "pyregence/weather_stations/fixtures/" file-name))
      slurp
      (json/read-str :key-fn keyword)))

(deftest api-stations->stations-test
  (let [stations (us/api-stations->stations (:features (read-fixture "us-stations-page.json")))]
    (testing "0007W is excluded"
      (is (not (contains? stations "0007W"))))

    (testing "station details"
      (is (= {:name "Southside Road" :latitude 36.78921 :longitude -121.33943}
             (get stations "000PG")))))

  (testing "only the included providers"
    (is (= {} (us/api-stations->stations [{:properties {:stationIdentifier "X" :name "X" :provider "Other"}
                                           :geometry   {:coordinates [1.0 2.0]}}])))))

(deftest properties->observations-test
  (testing "Celsius is converted to Fahrenheit and rounded to one decimal"
    (is (= {"Temperature" "62.6°F"}
           (us/properties->observations {:temperature {:unitCode "wmoUnit:degC" :value 17}}))))

  (testing "other units come from the WMO labels"
    (is (= {"Relative humidity" "85.4%"}
           (us/properties->observations {:relativeHumidity {:unitCode "wmoUnit:percent" :value 85.4321}}))))

  (testing "display names split camel case and numbers"
    (is (= {"Precipitation last 3 hours" "0.0mm"}
           (us/properties->observations {:precipitationLast3Hours {:unitCode "wmoUnit:mm" :value 0}}))))

  (testing "strings, nil values and lists are skipped"
    (is (= {} (us/properties->observations {:stationName "San Francisco"
                                            :windSpeed   {:unitCode "wmoUnit:km_h-1" :value nil}
                                            :cloudLayers [{:base {:value 300}}]})))))

(deftest api-response->observation-test
  (let [{:keys [station observations]} (us/api-response->observation (read-fixture "us-observation.json"))]
    (is (= {:name      "San Francisco, San Francisco International Airport"
            :id        "KSFO"
            :timestamp "2026-10-28T00:40:00+00:00"}
           station))
    (is (= "62.6°F" (get observations "Temperature")))))
