(ns pyregence.device-session-test
  (:require [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [pyregence.authentication :as authentication]
            [pyregence.clock :as clock]
            [pyregence.marketplace :as marketplace]
            [pyregence.session :as session]
            [pyregence.throttle :as throttle]
            [triangulum.config :as config]
            [triangulum.database :as database]))

(def ^:private generation-a "11111111-1111-1111-1111-111111111111")
(def ^:private generation-b "22222222-2222-2222-2222-222222222222")
(def ^:private device-a "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
(def ^:private device-b "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
(def ^:private now 1000000)

(defn- request-session []
  {:user-id 7
   :user-email "member@example.test"
   :user-role "member"
   :session-generation generation-a
   :device-id device-a
   :request-device-id device-a
   :created-at 1
   :last-active now})

(defn- outcome [response]
  (let [body (:body response)]
    (:outcome (if (string? body) (edn/read-string body) body))))

(defn- response-data [response]
  (let [body (:body response)]
    (if (string? body) (edn/read-string body) body)))

(use-fixtures :each
  (fn [run]
    (reset! throttle/attempts {})
    (with-redefs [clock/now (constantly now)
                  config/get-config (fn [& _] nil)
                  marketplace/complete-signup! (fn [& _] nil)]
      (run))))

(deftest a-stale-generation-cannot-mutate-or-clean-up-the-current-cookie
  (let [calls (atom [])]
    (with-redefs [database/call-sql (fn [& args] (swap! calls conj args) nil)]
      (let [response (authentication/log-out
                      (request-session)
                      {:expected-generation generation-b
                       :scope :current
                       :reason :explicit})]
        (is (= :superseded (outcome response)))
        (is (empty? @calls))
        (is (not (contains? response :session)))
        (is (nil? (get-in response [:headers "Clear-Site-Data"]))))))

  (with-redefs [database/call-sql (fn [& _] nil)]
    (let [response (authentication/clean-up-ended-session
                    (request-session)
                    {:expected-generation generation-b})]
      (is (= :superseded (outcome response)))
      (is (not (contains? response :session)))
      (is (nil? (get-in response [:headers "Clear-Site-Data"]))))))

(deftest recent-sibling-activity-defeats-an-idle-callback
  (let [calls (atom [])]
    (with-redefs [database/call-sql
                  (fn [sql & args]
                    (swap! calls conj (cons sql args))
                    (case sql
                      "active_device_session_matches" [{:active_device_session_matches true}]
                      "revoke_idle_device_session" [{:revoke_idle_device_session false}]
                      nil))]
      (let [response (authentication/log-out
                      (request-session)
                      {:expected-generation generation-a
                       :expected-last-active (dec now)
                       :scope :current
                       :reason :idle})]
        (is (= :still-active (outcome response)))
        (is (= ["active_device_session_matches" "revoke_idle_device_session"]
               (mapv first @calls)))
        (is (= (dec now) (nth (second @calls) 4)))
        (is (not (contains? response :session)))))))

(deftest an-accepted-heartbeat-returns-the-authoritative-activity-version
  (with-redefs [database/call-sql
                (fn [sql & _]
                  (case sql
                    "active_device_session_matches" [{:active_device_session_matches true}]
                    "note_active_device_activity" [{:note_active_device_activity (inc now)}]
                    nil))]
    (is (= {:accepted? true :last-active-at (inc now)}
           (session/note-activity (request-session))))))

(deftest a-registry-cookie-cannot-use-the-unqualified-legacy-logout
  (let [calls (atom [])]
    (with-redefs [database/call-sql (fn [& args] (swap! calls conj args))]
      (let [response (authentication/log-out (request-session))]
        (is (= 409 (:status response)))
        (is (= :upgrade-required (outcome response)))
        (is (empty? @calls))
        (is (not (contains? response :session)))
        (is (nil? (get-in response [:headers "Clear-Site-Data"])))))))

(deftest a-legacy-logout-response-cannot-erase-a-later-cookie
  (let [calls (atom [])]
    (with-redefs [database/call-sql (fn [& args] (swap! calls conj args))]
      (let [response (authentication/log-out {:user-id 7})]
        (is (= :ended (outcome response)))
        (is (= "set_user_session_invalidated_at" (ffirst @calls)))
        (is (not (contains? response :session)))
        (is (nil? (:session-cookie-attrs response)))
        (is (nil? (get-in response [:headers "Clear-Site-Data"])))))))

(deftest an-ended-legacy-page-qualifies-cleanup-without-a-generation
  (with-redefs [database/call-sql (fn [& _] nil)]
    (let [visible (:visible (session/for-page
                             {:user-id 7 :created-at 1 :last-active 1}))
          cleaned (authentication/clean-up-ended-session
                   {:user-id 7 :created-at 1 :last-active 1}
                   {:expected-generation nil :scope :cookie-only})]
      (is (true? (:ended-session? visible)))
      (is (false? (:logged-in? visible)))
      (is (= :clean-up (outcome cleaned)))
      (is (= {} (:session cleaned))))))

(deftest explicit-current-logout-is-cookie-neutral-until-qualified-cleanup
  (with-redefs [database/call-sql
                (fn [sql & _]
                  (case sql
                    "active_device_session_matches" [{:active_device_session_matches true}]
                    "revoke_active_device_session" [{:revoke_active_device_session true}]
                    "get_active_user_session" []
                    nil))]
    (let [ended  (authentication/log-out
                  (request-session)
                  {:expected-generation generation-a
                   :scope :current
                   :reason :explicit})
          cleaned (authentication/clean-up-ended-session
                   (request-session)
                   {:expected-generation generation-a
                    :scope :browser-state})]
      (is (= :ended (outcome ended)))
      (is (not (contains? ended :session)))
      (is (= :clean-up (outcome cleaned)))
      (is (= {} (:session cleaned)))
      (is (= {:max-age 0} (:session-cookie-attrs cleaned)))
      (is (= "\"cache\", \"cookies\", \"storage\""
             (get-in cleaned [:headers "Clear-Site-Data"]))))))

(deftest an-expired-page-clears-only-the-ended-cookie
  (with-redefs [database/call-sql (fn [& _] nil)]
    (let [cleaned (authentication/clean-up-ended-session
                   (request-session)
                   {:expected-generation generation-a
                    :scope :cookie-only})]
      (is (= :clean-up (outcome cleaned)))
      (is (= {} (:session cleaned)))
      (is (= {:max-age 0} (:session-cookie-attrs cleaned)))
      (is (nil? (get-in cleaned [:headers "Clear-Site-Data"]))))))

(deftest another-device-is-staged-until-an-explicit-idempotent-transfer
  (let [active (atom {:session_generation generation-a
                      :device_id device-a
                      :session_epoch 4
                      :created_at now
                      :last_active_at now
                      :revoked_at nil})
        calls  (atom [])
        user   {:user_id 7
                :user_email "member@example.test"
                :user_name "Member"
                :user_role "organization_member"
                :organization_rid 3
                :org_membership_status "accepted"
                :subscription_tier "tier2_pro"}]
    (with-redefs [database/call-sql
                  (fn [sql & args]
                    (swap! calls conj (cons sql args))
                    (case sql
                      "verify_user_login" [user]
                      "get_user_settings" [{:settings "{}"}]
                      "get_active_user_session" [@active]
                      "replace_active_user_session"
                      (do (swap! active assoc
                                 :session_generation (nth args 3)
                                 :device_id (nth args 4)
                                 :session_epoch 5
                                 :created_at (inc now)
                                 :last_active_at (inc now))
                          [@active])
                      nil))]
      (let [staged    (authentication/log-in {:request-device-id device-b}
                                             "member@example.test" "password")
            challenge (:session staged)
            staged-data (response-data staged)
            active-after-stage @active
            completed (authentication/confirm-login-takeover
                       (assoc challenge :request-device-id device-b))
            retried   (authentication/confirm-login-takeover
                       (assoc challenge :request-device-id device-b))]
        (testing "authentication alone does not evict the active device"
          (is (= true (:takeover-required staged-data)))
          (is (= generation-a (str (:session_generation active-after-stage)))))
        (testing "confirmation installs a complete account session on device B"
          (is (= device-b (get-in completed [:session :device-id])))
          (is (= "organization_member" (get-in completed [:session :user-role])))
          (is (= 3 (get-in completed [:session :organization-id]))))
        (testing "a lost confirmation response can be retried without another CAS"
          (is (= (get-in completed [:session :session-generation])
                 (get-in retried [:session :session-generation])))
          (is (= 1 (count (filter #(= "replace_active_user_session" (first %)) @calls)))))))))

(deftest a-competing-first-login-is-never-minted-into-the-losing-browser
  (let [successor {:session_generation generation-b
                   :device_id device-a
                   :session_epoch 1
                   :created_at (inc now)
                   :last_active_at (inc now)
                   :revoked_at nil}
        reads     (atom 0)
        user      {:user_id 7
                   :user_email "member@example.test"
                   :user_name "Member"
                   :user_role "member"}]
    (with-redefs [database/call-sql
                  (fn [sql & _]
                    (case sql
                      "verify_user_login" [user]
                      "get_user_settings" [{:settings "{}"}]
                      "begin_active_user_session" []
                      "get_active_user_session"
                      (if (= 1 (swap! reads inc)) [] [successor])
                      nil))]
      (let [response  (authentication/log-in {:request-device-id device-b}
                                             "member@example.test" "password")
            challenge (get-in response [:session :pending-takeover])]
        (is (= true (:takeover-required (response-data response))))
        (is (= generation-b (:observed-generation challenge)))
        (is (= 1 (:observed-epoch challenge)))
        (is (= device-b (:candidate-device challenge)))
        (is (nil? (get-in response [:session :session-generation])))
        (is (nil? (get-in response [:session :device-id])))))))

(deftest a-pending-transfer-renders-only-the-fact-needed-by-the-prompt
  (let [user      {:user_id 7 :user_email "member@example.test"}
        active    {:session_generation generation-a
                   :device_id device-a
                   :session_epoch 4}
        challenge (session/awaiting-takeover {} user active device-b generation-b now)
        visible   (:visible (session/for-page challenge))]
    (is (true? (:device-transfer-required? visible)))
    (is (false? (:logged-in? visible)))
    (is (= device-b (:device-session-id visible)))
    (is (not (contains? visible :pending-takeover)))))

(deftest marketplace-sso-completion-routes-a-pending-transfer-to-the-login-prompt
  (let [user   {:user_id 7
                :user_email "member@example.test"
                :user_role "member"}
        active {:session_generation generation-a
                :device_id device-a
                :session_epoch 4
                :created_at now
                :last_active_at now
                :revoked_at nil}]
    (with-redefs [marketplace/sso-login
                  (fn [_] {:user user :session {}})
                  database/call-sql
                  (fn [sql & _]
                    (case sql
                      "get_user_settings" [{:settings "{}"}]
                      "get_active_user_session" [active]
                      nil))]
      (let [response (authentication/marketplace-sso-complete
                      {:session {} :headers {"x-pyrecast-device-id" device-b}})
            body     (json/read-str (:body response) :key-fn keyword)]
        (is (= 200 (:status response)))
        (is (= "/login" (:location body)))
        (is (some? (get-in response [:session :pending-takeover])))
        (is (= (get-in response [:session :pending-takeover :candidate-device])
               (get-in (session/for-page (:session response))
                       [:visible :device-session-id])))))))

(deftest marketplace-sso-entry-is-cookie-neutral-until-the-browser-lock
  (let [token    "header.payload.signature"
        response (authentication/marketplace-sso-login
                  {:form-params {"x-gcp-marketplace-token" token}})]
    (is (= 200 (:status response)))
    (is (= "no-store" (get-in response [:headers "Cache-Control"])))
    (is (not (contains? response :session)))
    (is (re-find #"navigator\.locks" (:body response)))
    (is (re-find #"pyrecast-session-transition" (:body response)))
    (is (re-find #"/marketplace-login/complete" (:body response)))
    (is (not (re-find (re-pattern token) (:body response))))))
