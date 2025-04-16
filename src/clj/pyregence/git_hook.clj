#!/usr/bin/env bb

;; NOTE this file must be self-contained - it will be executed as a standalone script.

(ns pyregence.git-hook
  (:require
   [lambdaisland.deep-diff2     :as ddiff]
   [clojure.edn                 :as edn]))

(defn read-config [config-file]
  (-> config-file
      slurp
      edn/read-string))

(defn- update-map-keys-with-index [index m]
  (update-keys m
               #(keyword (str index "." (name %)))))

(defn deep-merge-maps [coll]
  (reduce
   (fn [acc item]
     (cond (map? item)
           (merge acc item)
           :else
           (merge acc (deep-merge-maps item))))
   {}
   coll))
(comment (deep-merge-maps (normalize-vector [{:x 2}
                                             [[{:x 3}]]
                                             [2 [[[{:innie 42}]]]]]
                                            0)))
(comment (deep-merge-maps (normalize-vector [{:x 2}] 0)))

(defn normalize-vector [v index]
  (map-indexed (fn [the-index the-value]
                 (cond (map? the-value)
                       (update-map-keys-with-index (str "pyr-cfg-vec-" the-index "." index) the-value)
                       (vector? the-value)
                       (normalize-vector the-value (inc index))
                       :else
                       {(keyword (str "." index)) the-value}))
               v))
(comment (deep-merge-maps (normalize-vector [{:x [[[{:inner 42}]]]}] 0)))

(defn normalize [m]
  (reduce (fn [acc [k the-value]]
            (cond (map? the-value)
                  (merge acc (deep-merge-maps (normalize-vector [{k the-value}] 0)))
                  (vector? the-value)
                  (merge acc (deep-merge-maps (normalize-vector the-value 0)))
                  :else
                  (assoc acc k the-value)))
          {}
          m))
(comment (normalize {:x {:inner 42}}))
(comment (normalize {:x [[[{:inner 42}]]]}))
(comment (normalize {:x [1 [{:y 0}]]}))

(defn- select-config-keys [config]
  (-> config read-config normalize keys set))

(defn config-diffs
  ([config1 config2]
   (-> (ddiff/diff (select-config-keys config1)
                   (select-config-keys config2))
       (ddiff/minimize)
       (ddiff/pretty-print)))
  ([]
   (config-diffs "config.default.edn" "config.edn")))

(comment (config-diffs))

(defn -main
  [& [config1 config2]]
  (if (and config1 config2)
    (do
      (println "Comparing files"  config1  "and"  config2)
      (config-diffs config1 config2))
    (config-diffs)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
