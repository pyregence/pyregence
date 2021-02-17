(ns pyregence.database-test
  (:use [clojure.test])
  (:require [pyregence.database :as db]))

(deftest ^:unit kebab->snake
  (testing "Hyphens are replaced with underscores"
    (is (= "hello_world" (db/kebab->snake "hello-world")))
    (is (= "hello_big_wide_world" (db/kebab->snake "hello-big-wide-world")))))

(deftest ^:unit str-places
  (testing "Creates SQL placeholder updating/inserting multiple rows"
    (is (= "(?)" (db/str-places [[1]])))
    (is (= "(?, ?), (?, ?)" (db/str-places [[1 2] [3 4]])))))
