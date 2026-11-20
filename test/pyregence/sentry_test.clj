(ns pyregence.sentry-test
  (:require [clojure.test     :refer [deftest is testing]]
            [pyregence.sentry :as sentry]))

(deftest report-exception-returns-original-throwable
  (let [err (ex-info "app error" {:code 500})]
    (is (identical? err (sentry/report-exception! err)))))

(deftest wrap-sentry-preserves-default-handler-behavior
  (testing "passes healthy requests through untouched"
    (let [wrapped (sentry/wrap-sentry (fn [req] {:status 200 :uri (:uri req)}))]
      (is (= {:status 200 :uri "/"} (wrapped {:uri "/"})))))

  (testing "reports unhandled exception and always re-throws original exception"
    (let [reported (atom nil)
          app-err  (ex-info "handler boom" {})]
      (with-redefs [sentry/report-exception! (fn [t tags] (reset! reported [t tags]) t)]
        (let [wrapped (sentry/wrap-sentry (fn [_] (throw app-err)))
              thrown  (try (wrapped {:request-method :post
                                     :uri            "/clj/get-cameras"
                                     :session        {:user-role :member :subscription-tier :tier1 :email "a@b.com"}})
                           (catch Throwable t t))]
          (is (identical? app-err thrown))
          (is (= [app-err {:user-role         :member
                           :subscription-tier :tier1
                           :http.method       "post"
                           :http.route        "/clj/get-cameras"}]
                 @reported)))))))
