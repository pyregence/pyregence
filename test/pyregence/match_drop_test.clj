(ns pyregence.match-drop-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [pyregence.match-drop :refer [calculate-transitions
                                 cawfe-match-drop-args->body
                                 cawfe-sim-hours
                                 default-cawfe-artefacts-dir
                                 initiate-md!
                                 landfire-match-drop-args->body
                                 model->polling-steps]]
   [clj-http.client]
   [triangulum.config]
   [triangulum.database]))

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
                               "landfire"
                               42
                               (assoc base-params :org-id 3)
                               {:sig3-env "dev"})]
      (is (= 7 (:pyrc_user_id arguments)))
      (is (= 3 (:pyrc_org_id arguments))))))

(deftest match-drop-args->body-allows-no-org
  (testing "a user with no organization still produces a valid body"
    (let [{:keys [arguments]} (#'pyregence.match-drop/match-drop-args->body
                               "landfire"
                               42
                               (assoc base-params :org-id nil)
                               {:sig3-env "dev"})]
      (is (= 7 (:pyrc_user_id arguments)))
      (is (nil? (:pyrc_org_id arguments)))
      (is (= "md-42" (:pyrc_fire_name arguments))
          "the rest of the payload is unaffected"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Model dispatch (PYR1-1097)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private md-params
  {:ignition-time "2026-08-09 21:15 UTC"
   :lat           39.845
   :lon           -121.07
   :wx-type       "forecast"
   :fuel-version  "2.5.0"})

(def ^:private md-config
  {:sig3-env             "dev"
   :cawfe-artefacts-dir  "s3://owo/cawfe/match-drop/artefacts"})

(deftest cawfe-body-targets-the-cawfe-network
  (testing "the CAWFE builder submits the :cawfe-match-drop network with flat arguments"
    (let [{:keys [network arguments]} (cawfe-match-drop-args->body 42 md-params md-config)]
      (is (= :cawfe-match-drop network))
      (is (= "md-42" (:cawfe_run_id arguments)))
      (is (= "dev" (:env arguments)))
      (is (= "s3://owo/cawfe/match-drop/artefacts" (:cawfe_artefacts_dir arguments)))
      (is (= 39.845 (:pyrc_ignition_lat arguments) (:center_lat_deg arguments)))
      (is (= -121.07 (:pyrc_ignition_lon arguments) (:center_lon_deg arguments)))
      ;; The fuels/topography rasters come from the network's :defaults in sig3.
      (is (nil? (:input_fuel_f40 arguments))))))

(deftest cawfe-body-ends-five-hours-after-ignition
  (testing "target_interval_end is exactly cawfe-sim-hours past the ignition epoch"
    (let [{:keys [arguments]} (cawfe-match-drop-args->body 42 md-params md-config)]
      (is (= 1786310100 (:pyrc_ignition_epoch_s arguments)))
      (is (= (+ (:pyrc_ignition_epoch_s arguments) (* cawfe-sim-hours 60 60))
             (:target_interval_end arguments))))))

(deftest cawfe-workspace-has-four-underscore-parts
  (testing ":cawfe-geosync splits the workspace on _ and needs exactly four parts"
    (let [{:keys [arguments]} (cawfe-match-drop-args->body 42 md-params md-config)
          workspace           (:geoserver-workspace arguments)]
      (is (= "match-drop-forecast_md-42_20260809_211500" workspace))
      (is (= 4 (count (str/split workspace #"_")))))))

(deftest landfire-body-is-unchanged
  (testing "the LANDFIRE builder still submits the nested :match-drop body"
    (let [{:keys [network arguments]} (landfire-match-drop-args->body 42 md-params md-config)]
      (is (= :match-drop network))
      (is (= "md-42" (:pyrc_fire_name arguments)))
      (is (= "match-drop-forecast_md-42_20260809_211500" (:geoserver-workspace arguments)))
      (is (= {:pyrc_ignition_lon     -121.07
              :pyrc_ignition_lat     39.845
              :pyrc_ignition_epoch_s 1786310100}
             (:pyrc_ignition arguments)))
      (is (= {:pyrc_fuel_source "landfire" :pyrc_wx_type "forecast" :pyrc_fuel_version "2.5.0"}
             (:pyrc_inputs arguments))))))

(deftest calculate-transitions-over-cawfe-steps
  (testing "the CAWFE step map drives the same transition machinery"
    (let [state     (atom (model->polling-steps "cawfe"))
          job-state {"steps" {"cawfe-simulation-task" {"job-status" "success" "result" {"ok" true}}}}
          result    (calculate-transitions state job-state match-job-id)]
      (is (= ["pending" "success"] (mapv #(nth % 2) result)))
      (is (every? #(= "cawfe-simulation-task" (nth % 1)) result)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Per-model concurrency guard
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- initiate-md-with-running!
  "Runs `initiate-md!` against stubbed config and SQL. `running` maps a model to
   how many of that user's jobs are currently in progress. Returns
   `{:sql-calls [...] :body <response body>}`."
  [running params]
  (let [sql-calls (atom [])]
    (with-redefs [triangulum.config/get-config
                  (fn [& ks]
                    (case (vec ks)
                      [:triangulum.views/client-keys :features :match-drop]  true
                      [:triangulum.views/client-keys :features :cawfe]       true
                      [:pyregence.match-drop/match-drop :max-queue-size]     5
                      nil))

                  triangulum.database/call-sql
                  (fn [f & args]
                    (swap! sql-calls conj (vec (cons f args)))
                    (case f
                      "count_running_user_match_jobs" [{:count (get running (second args) 0)}]
                      "count_all_running_match_jobs"  [{:count 0}]
                      nil))

                  pyregence.match-drop/create-match-job!
                  (fn [p] {:started (:model p)})]
      (let [body (:body (initiate-md! {:user-id 1 :match-drop-access? true} params))]
        {:sql-calls @sql-calls
         :body      body}))))

(deftest running-job-guard-is-scoped-to-the-model
  (testing "a running LANDFIRE job does not block a CAWFE run"
    (let [{:keys [sql-calls body]} (initiate-md-with-running!
                                    {"landfire" 1}
                                    (assoc md-params :model "cawfe"))]
      (is (some #{["count_running_user_match_jobs" 1 "cawfe"]} sql-calls)
          "the running count is asked for per model")
      (is (str/includes? (str body) "cawfe")))))

(deftest running-job-guard-blocks-the-same-model
  (testing "a second CAWFE run is refused while one is in progress"
    (let [{:keys [body]} (initiate-md-with-running!
                          {"cawfe" 1}
                          (assoc md-params :model "cawfe"))]
      (is (str/includes? (str body) "already running")))))

(deftest cawfe-artefacts-dir-falls-back-to-a-default
  (testing "an unset :cawfe-artefacts-dir must not submit a nil storage path"
    (let [{:keys [arguments]} (cawfe-match-drop-args->body 42 md-params {:sig3-env "dev"})]
      (is (= default-cawfe-artefacts-dir (:cawfe_artefacts_dir arguments)))
      (is (string? (:cawfe_artefacts_dir arguments))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Failed submit handling
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest failed-submit-marks-the-job-as-errored
  (testing "a sig3 rejection records the error instead of leaving a blank row"
    (let [sql-calls (atom [])
          job-uuid  #uuid "00000000-0000-0000-0000-0000000000ff"]
      (with-redefs [triangulum.config/get-config
                    (fn [& ks]
                      (case (vec ks)
                        [:triangulum.views/client-keys :features :match-drop]    true
                        [:triangulum.views/client-keys :features :cawfe]         true
                        [:triangulum.views/client-keys :features :sig3-endpoint] "http://sig3.test"
                        [:pyregence.match-drop/match-drop :max-queue-size]       5
                        nil))

                    triangulum.database/call-sql
                    (fn [f & args]
                      (swap! sql-calls conj (vec (cons f args)))
                      (case f
                        "count_running_user_match_jobs" [{:count 0}]
                        "count_all_running_match_jobs"  [{:count 0}]
                        ;; main returns the row and renames its columns, rather than a bare id
                        "initialize_match_job"          [{:match_job_id 7 :org_id 3}]
                        "get_match_job"                 [{:match_job_id 7 :match_job_uuid job-uuid}]
                        nil))

                    clj-http.client/post
                    (fn [_ _]
                      (throw (ex-info "clj-http: status 500"
                                      {:status 500
                                       :body   "{\"error\":\"Network 'cawfe-match-drop' not found.\"}"})))]

        (let [body    (:body (initiate-md! {:user-id 1 :match-drop-access? true}
                                           (assoc md-params :model "cawfe")))
              updates (filter #(= "update_match_job" (first %)) @sql-calls)]
          (is (= 1 (count updates)) "the failure is written back to the job")
          (let [[_ _ _ md-status _ message] (first updates)]
            (is (= 1 md-status) "md-status 1 is Error")
            (is (str/includes? (str message) "cawfe-match-drop")
                "the sig3 error reaches the message"))
          (is (str/includes? (str body) (str job-uuid))
              "the browser still gets a job id to poll"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; :cawfe feature flag
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- initiate-md-with-cawfe-flag!
  "Runs `initiate-md!` with the `:cawfe` feature flag set to `flag`."
  [flag params]
  (with-redefs [triangulum.config/get-config
                (fn [& ks]
                  (case (vec ks)
                    [:triangulum.views/client-keys :features :match-drop] true
                    [:triangulum.views/client-keys :features :cawfe]      flag
                    [:pyregence.match-drop/match-drop :max-queue-size]    5
                    nil))

                triangulum.database/call-sql
                (fn [f & _]
                  (case f
                    "count_running_user_match_jobs" [{:count 0}]
                    "count_all_running_match_jobs"  [{:count 0}]
                    nil))

                pyregence.match-drop/create-match-job!
                (fn [p] {:started (:model p)})]
    (:body (initiate-md! {:user-id 1 :match-drop-access? true} params))))

(deftest cawfe-is-refused-when-the-flag-is-off
  (testing "a CAWFE submit is rejected while :cawfe is disabled"
    (let [body (initiate-md-with-cawfe-flag! false (assoc md-params :model "cawfe"))]
      (is (str/includes? (str body) "disabled")))))

(deftest landfire-still-runs-when-the-cawfe-flag-is-off
  (testing "the flag only gates CAWFE"
    (let [body (initiate-md-with-cawfe-flag! false (assoc md-params :model "landfire"))]
      (is (= {:started "landfire"} body)))))

(deftest cawfe-runs-when-the-flag-is-on
  (testing "a CAWFE submit goes through while :cawfe is enabled"
    (let [body (initiate-md-with-cawfe-flag! true (assoc md-params :model "cawfe"))]
      (is (= {:started "cawfe"} body)))))
