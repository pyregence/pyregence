(ns pyregence.utils-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [pyregence.utils :as u])
  (:import
   [java.time ZonedDateTime ZoneOffset]
   [java.time.format DateTimeFormatter]
   [java.time.temporal ChronoUnit]
   [java.util TimeZone]))

(defn- under-zone
  "Runs `f` with the JVM default zone set to `zone-id`, restoring it after.
   CI runs in UTC, where a helper that reads the ambient zone and one that asks
   for UTC agree, so forcing the zone is the only way a test here can see the
   difference."
  [zone-id f]
  (let [original (TimeZone/getDefault)]
    (try
      (TimeZone/setDefault (TimeZone/getTimeZone zone-id))
      (f)
      (finally
        (TimeZone/setDefault original)))))

(defn- utc-hour-iso-string
  []
  (.format (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm'Z'")
           (.truncatedTo (ZonedDateTime/now ZoneOffset/UTC) ChronoUnit/HOURS)))

(deftest get-current-date-time-iso-string-is-utc
  (testing "the answer does not depend on the server's zone"
    (is (= (under-zone "America/Los_Angeles" u/get-current-date-time-iso-string)
           (under-zone "Asia/Tokyo" u/get-current-date-time-iso-string))))
  (testing "the Z it prints is the UTC hour, not the local one relabelled"
    (is (= (utc-hour-iso-string)
           (under-zone "America/Sao_Paulo" u/get-current-date-time-iso-string)))))
