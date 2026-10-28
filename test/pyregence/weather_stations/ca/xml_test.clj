(ns pyregence.weather-stations.ca.xml-test
  (:require [clojure.java.io                   :as io]
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
