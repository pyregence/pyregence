(ns pyregence.weather-stations.ca-test
  (:require [clojure.data.json             :as json]
            [clojure.java.io               :as io]
            [clojure.test                  :refer [deftest is testing]]
            [pyregence.weather-stations.ca :as ca]))

;; Fixtures are small responses saved once from the REST API. `test` is on the
;; classpath in the :test alias, so io/resource finds them.

(defn- read-fixture
  [file-name]
  (-> (io/resource (str "pyregence/weather_stations/fixtures/" file-name))
      slurp
      (json/read-str :key-fn keyword)))

(deftest format-name-test
  (is (= "Trial Island" (ca/format-name "TRIAL ISLAND")))
  (is (= "St Alban's" (ca/format-name "ST ALBAN'S"))))

(deftest rest-stations->stations-test
  (testing "non-partner stations use name"
    (is (= {"8400416" {:name      "St Alban's"
                       :latitude  47.85773
                       :longitude -55.861122
                       :iata-id   "CAAB"
                       :auto-man  "AUTO"
                       :partner?  false}}
           (select-keys (ca/rest-stations->stations false (:features (read-fixture "ca-rest-swob-stations.json")))
                        ["8400416"]))))

  (testing "partner stations use name_en"
    (is (= "Eastern South Gulf"
           (get-in (ca/rest-stations->stations true (:features (read-fixture "ca-rest-swob-partner-stations.json")))
                   ["DFO_AZMP-ESG" :name]))))

  (testing "stations without an msc_id or coordinates are skipped"
    (is (= {} (ca/rest-stations->stations false [{:properties {:msc_id nil :name "X"}
                                                   :geometry   {:coordinates [1.0 2.0]}}
                                                  {:properties {:msc_id "1" :name "Y"}
                                                   :geometry   nil}])))))

(deftest properties->observations-test
  (testing "the first key with a value wins, with its unit"
    (is (= {"Air Temperature" "14.2°C"}
           (ca/properties->observations {:avg_air_temp_pst1hr     13.0
                                         :avg_air_temp_pst1hr-uom "°C"
                                         :air_temp                14.2
                                         :air_temp-uom            "°C"}))))

  (testing "blank values are skipped"
    (is (= {"Air Temperature" "13.0°C"}
           (ca/properties->observations {:air_temp                ""
                                         :avg_air_temp_pst2mts    "13.0"
                                         :avg_air_temp_pst2mts-uom "°C"})))))

(deftest rest-response->observation-test
  (testing "a response with an observation"
    (let [{:keys [station observations]} (ca/rest-response->observation (read-fixture "ca-rest-observation.json"))]
      (is (= {:name "Cold Lake" :id "3081680" :timestamp "2026-10-28T01:00:00.000Z"} station))
      (is (= "0.2°C" (get observations "Air Temperature")))))

  (testing "a response without observations"
    (is (nil? (ca/rest-response->observation (read-fixture "ca-rest-no-observation.json"))))))

(deftest xml-elements->observation-test
  (let [{:keys [station observations]} (ca/xml-elements->observation
                                        {"stn_nam"  {:value "ALGAR AUTO" :uom "unitless"}
                                         "msc_id"   {:value "AB-MAF_ALA" :uom "unitless"}
                                         "date_tm"  {:value "2026-10-27T18:00:00.000Z" :uom "datetime"}
                                         "air_temp" {:value "2.6" :uom "°C"}
                                         "rel_hum"  {:value "76" :uom "%"}})]
    (is (= {:name "Algar Auto" :id "AB-MAF_ALA" :timestamp "2026-10-27T18:00:00.000Z"} station))
    (is (= {"Air Temperature" "2.6°C" "Relative Humidity" "76%"} observations))))
