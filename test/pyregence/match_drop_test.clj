(ns pyregence.match-drop-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [pyregence.match-drop :refer [calculate-transitions]]))

(def match-job-id 42)

(defn- make-state
  "Build a state atom with one step, overriding defaults with `overrides`."
  [step-name overrides]
  (atom {step-name (merge {"pending" false "success" false "failure" false "order" 1}
                          overrides)}))

(deftest calculate-transitions-pending
  (testing "emits pending transition when job-status is pending"
    (let [state     (make-state "mdrop-dps" {})
          job-state {"steps" {"mdrop-dps" {"job-status" "pending" "result" {}}}}
          result    (calculate-transitions state job-state match-job-id)]
      (is (= 1 (count result)))
      (is (= [1 "mdrop-dps" "pending" {} match-job-id] (first result))))))

(deftest calculate-transitions-success
  (testing "emits success transition when job-status is success and pending already seen"
    (let [state     (make-state "mdrop-dps" {"pending" true})
          job-state {"steps" {"mdrop-dps" {"job-status" "success" "result" {"some" "data"}}}}
          result    (calculate-transitions state job-state match-job-id)]
      (is (= 1 (count result)))
      (is (= "success" (nth (first result) 2))))))

(deftest calculate-transitions-failure
  (testing "emits failure transition when job-status is failure and pending already seen"
    (let [state     (make-state "mdrop-dps" {"pending" true})
          job-state {"steps" {"mdrop-dps" {"job-status" "failure" "result" {"message" "boom"}}}}
          result    (calculate-transitions state job-state match-job-id)]
      (is (= 1 (count result)))
      (is (= "failure" (nth (first result) 2)))
      (is (= "boom" (nth (first result) 3))))))

(deftest calculate-transitions-skipped-pending-success
  (testing "synthesizes pending transition when step jumps directly to success"
    (let [state     (make-state "mdrop-dps" {})
          job-state {"steps" {"mdrop-dps" {"job-status" "success" "result" {"some" "data"}}}}
          result    (calculate-transitions state job-state match-job-id)
          statuses  (mapv #(nth % 2) result)]
      (is (= 2 (count result)) "should emit both pending and success transitions")
      (is (= ["pending" "success"] statuses)))))

(deftest calculate-transitions-skipped-pending-failure
  (testing "synthesizes pending transition when step jumps directly to failure"
    (let [state     (make-state "mdrop-dps" {})
          job-state {"steps" {"mdrop-dps" {"job-status" "failure" "result" {"message" "err"}}}}
          result    (calculate-transitions state job-state match-job-id)
          statuses  (mapv #(nth % 2) result)]
      (is (= 2 (count result)) "should emit both pending and failure transitions")
      (is (= ["pending" "failure"] statuses)))))

(deftest calculate-transitions-no-duplicate-pending
  (testing "does not duplicate pending when pending already seen and step succeeds"
    (let [state     (make-state "mdrop-dps" {"pending" true})
          job-state {"steps" {"mdrop-dps" {"job-status" "success" "result" {"some" "data"}}}}
          result    (calculate-transitions state job-state match-job-id)
          statuses  (mapv #(nth % 2) result)]
      (is (= ["success"] statuses)))))

(deftest calculate-transitions-no-change
  (testing "returns empty when step has not started"
    (let [state     (make-state "mdrop-dps" {})
          job-state {"steps" {}}
          result    (calculate-transitions state job-state match-job-id)]
      (is (empty? result)))))

;;==============================================================================
;; submit-job payload
;;==============================================================================

(def ^:private base-params
  {:ignition-time "2022-12-01 18:00 UTC"
   :lat           38.0
   :lon           -120.0
   :wx-type       "forecast"
   :fuel-version  "2.5.0"
   :user-id       7})

(deftest match-drop-args->body-carries-user-and-org
  (testing "submit-job arguments carry the user and org ids for billing attribution"
    (let [{:keys [arguments]} (#'pyregence.match-drop/match-drop-args->body
                               42
                               (assoc base-params :org-id 3)
                               {:sig3-env "dev"})]
      (is (= 7 (:pyrc_user_id arguments)))
      (is (= 3 (:pyrc_org_id arguments))))))

(deftest match-drop-args->body-allows-no-org
  (testing "a user with no organization still produces a valid body"
    (let [{:keys [arguments]} (#'pyregence.match-drop/match-drop-args->body
                               42
                               (assoc base-params :org-id nil)
                               {:sig3-env "dev"})]
      (is (= 7 (:pyrc_user_id arguments)))
      (is (nil? (:pyrc_org_id arguments)))
      (is (= "md-42" (:pyrc_fire_name arguments))
          "the rest of the payload is unaffected"))))
