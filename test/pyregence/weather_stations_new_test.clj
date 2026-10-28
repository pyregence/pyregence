(ns pyregence.weather-stations-new-test
  (:require [clojure.test                  :refer [deftest is testing use-fixtures]]
            [pyregence.weather-stations-new :as ws]))

;; The functions under test are private, so they're called through their vars:
;; #'ws/source-cache-status is the var, and calling it calls the function.

(def ^:private source-cache @#'ws/source-cache)
(def ^:private observation-cache @#'ws/observation-cache)

(defn- reset-caches
  "Empties both caches before each test, so tests don't see each other's values."
  [run-test]
  (reset! source-cache {})
  (reset! observation-cache {})
  (run-test))

(use-fixtures :each reset-caches)

(deftest source-cache-status-test
  (testing "each state of a source cache entry"
    (is (= "empty" (#'ws/source-cache-status nil)))
    (is (= "hit: 1970-01-01T00:00:01Z" (#'ws/source-cache-status {:timestamp 1000})))
    (is (= "stale: 1970-01-01T00:00:01Z" (#'ws/source-cache-status {:timestamp 1000 :failed-at 2000})))
    (is (= "error: 1970-01-01T00:00:02Z" (#'ws/source-cache-status {:failed-at 2000})))))

(deftest build-points-cache-header-test
  (is (= "us=hit: 1970-01-01T00:00:01Z, ca=empty, ca-xml-index=empty"
         (#'ws/build-points-cache-header {:us {:timestamp 1000}}))))

(deftest refresh-source!-test
  (testing "a successful refresh replaces the entry"
    (is (true? (#'ws/refresh-source! :us (constantly {:features [:a]}))))
    (is (= [:a] (get-in @source-cache [:us :features]))))

  (testing "a failed refresh keeps the previous value and records :failed-at"
    (is (false? (#'ws/refresh-source! :us #(throw (ex-info "upstream down" {})))))
    (is (= [:a] (get-in @source-cache [:us :features])))
    (is (int? (get-in @source-cache [:us :failed-at]))))

  (testing "an empty result counts as a failure"
    (is (false? (#'ws/refresh-source! :ca (constantly {:features []}))))
    (is (nil? (get-in @source-cache [:ca :features]))))

  (testing "the next successful refresh clears :failed-at"
    (#'ws/refresh-source! :us (constantly {:features [:b]}))
    (is (nil? (get-in @source-cache [:us :failed-at])))))

(deftest observation-fresh?-test
  (is (true? (#'ws/observation-fresh? {:timestamp 1000} 300 (+ 1000 299999))))
  (is (false? (#'ws/observation-fresh? {:timestamp 1000} 300 (+ 1000 300000))))
  (is (false? (#'ws/observation-fresh? nil 300 1000))))

(deftest get-observation-cached!-test
  ;; with-redefs replaces a function for the duration of its body: here, the
  ;; config lookup, so the test doesn't depend on config.edn.
  (with-redefs [ws/get-source-config (constantly {:observation-ttl-seconds 300})]
    (let [observation {:station {:id "KSFO"} :observations {}}
          fetches     (atom 0)
          fetch!      (fn []
                        (swap! fetches inc)
                        {:observation observation :via "rest" :upstream-status "200"})]
      (testing "a miss fetches and caches"
        (is (= "miss" (:cache (#'ws/get-observation-cached! :us "KSFO" fetch!))))
        (is (= 1 @fetches)))

      (testing "a hit doesn't fetch"
        (let [result (#'ws/get-observation-cached! :us "KSFO" fetch!)]
          (is (re-find #"^hit: " (:cache result)))
          (is (= observation (:observation result)))
          (is (= 1 @fetches))))

      (testing "an expired entry whose refetch fails falls back to the stale observation"
        (swap! observation-cache assoc-in [[:us "KSFO"] :timestamp] 0)
        (let [result (#'ws/get-observation-cached! :us "KSFO" (constantly {:upstream-status "503"}))]
          (is (re-find #"^stale: " (:cache result)))
          (is (= observation (:observation result)))
          (is (= "503" (:upstream-status result)))))

      (testing "a failed fetch with nothing cached is a miss without observation, and isn't cached"
        (let [result (#'ws/get-observation-cached! :us "KOAK" #(throw (ex-info "boom" {})))]
          (is (= "miss" (:cache result)))
          (is (nil? (:observation result)))
          (is (= "error: boom" (:upstream-status result)))
          (is (not (contains? @observation-cache [:us "KOAK"]))))))))
