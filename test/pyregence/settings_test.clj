(ns pyregence.settings-test
  "That a named file is the file triangulum actually reads, and that naming none
   changes nothing. `from-args` parses; these say the parse reaches triangulum."
  (:require [clojure.test      :refer [deftest is]]
            [pyregence.settings :as settings]
            [triangulum.config  :as config]))

(deftest the-file-a-run-names-is-the-file-triangulum-is-told-to-read
  (let [told (atom nil)]
    (with-redefs [config/load-config #(reset! told %)]
      (settings/in-force! (settings/from-file "/etc/pyrecast.edn")))
    (is (= "/etc/pyrecast.edn" @told))))

;; Not "reads config.edn": nothing is said at all, so a process already holding
;; a configuration keeps it rather than being reset to its working directory.
(deftest naming-no-file-says-nothing-to-triangulum
  (let [told (atom nil)]
    (with-redefs [config/load-config #(reset! told %)]
      (settings/in-force! (settings/from-working-directory)))
    (is (nil? @told))))

(deftest the-arguments-a-dispatch-gets-come-back-from-the-call-that-settled-them
  (let [told (atom nil)
        left (with-redefs [config/load-config #(reset! told %)]
               (settings/in-force-from-args!
                ["server" "start" "--config" "/etc/pyrecast.edn" "--http-port" "13371"]))]
    (is (= "/etc/pyrecast.edn" @told))
    (is (= ["server" "start" "--http-port" "13371"] left))))
