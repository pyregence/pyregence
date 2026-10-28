(ns pyregence.weather-stations.ca.xml-test
  (:require [clojure.java.io                   :as io]
            [clojure.string                    :as str]
            [clojure.test                      :refer [deftest is testing]]
            [pyregence.weather-stations.ca.xml :as xml]))

(defn- read-fixture
  [file-name]
  (slurp (io/resource (str "pyregence/weather_stations/fixtures/" file-name))))

(deftest parse-listing-test
  (testing "a station folder listing: files only, without sort or parent links"
    (is (= {:dirs  []
            :files ["2026-10-27-1200-ab-maf-ala-AUTO-swob.xml"
                    "2026-10-27-1800-ab-maf-ala-AUTO-swob.xml"]}
           (xml/parse-listing (read-fixture "dd-station-folder-listing.html")))))

  (testing "directories lose their trailing slash"
    (is (= {:dirs ["ab-firewx" "bc-tran"] :files []}
           (xml/parse-listing "<a href=\"?C=N;O=D\">Name</a> <a href=\"/today/\">Parent</a>
                               <a href=\"ab-firewx/\">ab-firewx/</a> <a href=\"bc-tran/\">bc-tran/</a>")))))

(deftest paths-test
  (testing "the current date is under today/, past dates under <date>/WXO-DD/"
    (is (= "/today/observations/swob-ml/partners/ab-firewx/20261028/"
           (xml/network-path "20261028" "20261028" "ab-firewx")))
    (is (= "/20261027/WXO-DD/observations/swob-ml/partners/ab-firewx/20261027/"
           (xml/network-path "20261027" "20261028" "ab-firewx")))))

(deftest match-partner-folder-test
  (let [folder->networks {"ala"         #{"ab-firewx"}
                          "trial_is"    #{"dnd-ccg-lighthouse"}
                          "pom_quai_42" #{"qc-pom"}}]
    (testing "rule 1: lowercase iata_id"
      (is (= {:folder "ala" :networks #{"ab-firewx"}}
             (xml/match-partner-folder folder->networks "AB-MAF_ALA" {:iata-id "ALA"}))))

    (testing "rule 2: lowercase iata_id with spaces as _"
      (is (= "trial_is" (:folder (xml/match-partner-folder folder->networks "1018238" {:iata-id "TRIAL IS"})))))

    (testing "rule 3: lowercase msc_id"
      (is (= "pom_quai_42" (:folder (xml/match-partner-folder folder->networks "POM_quai_42" {:iata-id "quai_42"})))))

    (testing "no folder"
      (is (nil? (xml/match-partner-folder folder->networks "MB-SD_SNOW" {:iata-id "SNOW"}))))))

(deftest msc-id->provider-test
  (is (= "BC_ENV-AQ" (xml/msc-id->provider "BC_ENV-AQ_110")))
  (is (= "AB-MAF" (xml/msc-id->provider "AB-MAF_ALA")))
  (is (= "" (xml/msc-id->provider "1018238"))))

(deftest build-partner-folders-test
  ;; Folder 110 exists in two networks; the provider's other stations are all
  ;; in bc-env-aq. Folder 201 only exists in mb_agriculture, but BC_WMB
  ;; stations (ten of them here) live in bc-forestry, so that match is a
  ;; same-name folder of another network.
  (let [bc-wmb-folders   (map str (range 1001 1011))
        folder->networks (merge {"104" #{"bc-env-aq"}
                                 "105" #{"bc-env-aq"}
                                 "110" #{"bc-env-aq" "bc-forestry"}
                                 "201" #{"mb_agriculture"}}
                                (zipmap bc-wmb-folders (repeat #{"bc-forestry"})))
        stations         (merge {"BC_ENV-AQ_104" {:iata-id "104" :partner? true}
                                 "BC_ENV-AQ_105" {:iata-id "105" :partner? true}
                                 "BC_ENV-AQ_110" {:iata-id "110" :partner? true}
                                 "BC_WMB_201"    {:iata-id "201" :partner? true}
                                 "1012475"       {:iata-id "104" :partner? false}}
                                (into {}
                                      (for [folder bc-wmb-folders]
                                        [(str "BC_WMB_" folder) {:iata-id folder :partner? true}])))
        partner-folders  (xml/build-partner-folders stations folder->networks)]
    (testing "a folder in several networks is narrowed to the provider's network"
      (is (= {:network "bc-env-aq" :folder "110"} (get partner-folders "BC_ENV-AQ_110"))))

    (testing "a match outside the provider's networks is left out"
      (is (not (contains? partner-folders "BC_WMB_201"))))

    (testing "non-partner stations are left out"
      (is (not (contains? partner-folders "1012475"))))

    (testing "plain matches"
      (is (= {:network "bc-forestry" :folder "1002"} (get partner-folders "BC_WMB_1002"))))))

(deftest observation-paths-test
  (is (= "/today/observations/swob-ml/partners/ab-firewx/20261028/ala/"
         (xml/partner-folder-path "20261028" "20261028" "ab-firewx" "ala")))
  (is (= "/today/observations/swob-ml/latest/CAAB-AUTO-swob.xml"
         (xml/latest-file-path "CAAB" "AUTO"))))

(deftest pick-newest-xml-test
  (testing "the newest file sorts last"
    (is (= "2026-10-27-1800-ab-maf-ala-AUTO-swob.xml"
           (xml/pick-newest-xml ["2026-10-27-1800-ab-maf-ala-AUTO-swob.xml"
                                 "2026-10-27-1200-ab-maf-ala-AUTO-swob.xml"]))))

  (testing "lighthouse naming"
    (is (= "20261027T2340Z_DND-CCG_SWOB_1018238.xml"
           (xml/pick-newest-xml ["20261027T2320Z_DND-CCG_SWOB_1018238.xml"
                                 "20261027T2340Z_DND-CCG_SWOB_1018238.xml"]))))

  (testing "only XML files, nil when there's none"
    (is (nil? (xml/pick-newest-xml ["readme.txt"])))
    (is (nil? (xml/pick-newest-xml [])))))

(deftest parse-swob-elements-test
  (let [elements (xml/parse-swob-elements (read-fixture "ca-swob-partner.xml"))]
    (testing "identification and observation elements, values as strings"
      (is (= {:value "AB-MAF_ALA" :uom "unitless"} (get elements "msc_id")))
      (is (= {:value "2026-10-27T18:00:00.000Z" :uom "datetime"} (get elements "date_tm")))
      (is (= {:value "2.6" :uom "°C"} (get elements "air_temp")))))

  (testing "invalid XML throws"
    (is (thrown? Exception (xml/parse-swob-elements "<not xml")))))

(deftest fetch-station-elements!-test
  ;; with-redefs replaces the request function, so no request leaves the test.
  (let [partner-xml (read-fixture "ca-swob-partner.xml")]
    (testing "a non-partner station reads its latest/ file"
      (with-redefs [xml/fetch-dd! (fn [path]
                                    (is (= "/today/observations/swob-ml/latest/ALA-AUTO-swob.xml" path))
                                    {:status 200 :body partner-xml})]
        (let [result (xml/fetch-station-elements! "AB-MAF_ALA" {:partner? false :iata-id "ALA" :auto-man "AUTO"} nil)]
          (is (= "2.6" (get-in result [:elements "air_temp" :value])))
          (is (= "xml=200" (:status result))))))

    (testing "an XML of another station is rejected"
      (with-redefs [xml/fetch-dd! (constantly {:status 200 :body partner-xml})]
        (let [result (xml/fetch-station-elements! "2100159" {:partner? false :iata-id "CYXQ" :auto-man "AUTO"} nil)]
          (is (nil? (:elements result)))
          (is (= "xml=200 (msc_id is AB-MAF_ALA)" (:status result))))))

    (testing "a missing file"
      (with-redefs [xml/fetch-dd! (constantly {:status 404 :body "Not Found"})]
        (let [result (xml/fetch-station-elements! "8400416" {:partner? false :iata-id "CAAB" :auto-man "AUTO"} nil)]
          (is (nil? (:elements result)))
          (is (= "xml=404" (:status result))))))

    (testing "a partner station without a folder in the index makes no request"
      (with-redefs [xml/fetch-dd! (fn [_] (throw (ex-info "unexpected request" {})))]
        (is (= {:status "xml=no folder in the index"}
               (xml/fetch-station-elements! "MB-SD_SNOW" {:partner? true :iata-id "SNOW"} nil)))))

    (testing "a partner station falls back to yesterday's folder when today's has no XML"
      (let [listing (read-fixture "dd-station-folder-listing.html")]
        (with-redefs [xml/fetch-dd! (fn [path]
                                      (cond
                                        (str/starts-with? path "/today/") {:status 404}
                                        (str/ends-with? path "/")         {:status 200 :body listing}
                                        :else                             {:status 200 :body partner-xml}))]
          (let [result (xml/fetch-station-elements! "AB-MAF_ALA" {:partner? true} {:network "ab-firewx" :folder "ala"})]
            (is (str/ends-with? (:path result) "/ala/2026-10-27-1800-ab-maf-ala-AUTO-swob.xml"))
            (is (str/includes? (:path result) "/WXO-DD/"))
            (is (= "2.6" (get-in result [:elements "air_temp" :value])))))))))
