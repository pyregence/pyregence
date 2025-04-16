#!/usr/bin/env bb

;; NOTE this file must be self-contained - it will be executed as a standalone script.

(ns pyregence.git-hook
  (:require
   [lambdaisland.deep-diff2     :as ddiff]
   [clojure.edn                 :as edn]))

(defn read-config
  "From an edn file to clj data"
  [config-file]
  (-> config-file
      slurp
      edn/read-string))

(defn- update-map-keys-with-index [index m]
  (update-keys m
               #(keyword (str index "." (name %)))))

(defn deep-merge-maps
  "Builds a map from a coll of maps (arbitrary deepness)"
  [coll]
  (reduce
   (fn [acc item]
     (cond (map? item)
           (merge acc item)
           :else
           (merge acc (deep-merge-maps item))))
   {}
   coll))
(comment (deep-merge-maps [{:__vec-0.0.x 2} [[{:__vec-0.2.x 3}]] [{:.1 2} [[[{:__vec-0.4.innie 42}]]]]]))
(comment (deep-merge-maps [{:__vec-0.0.x 2}]))

(defn normalize-vector
  "Transforms a vector into a coll of maps whose keys describes the 'locations'"
  [v index]
  (map-indexed (fn [the-index the-value]
                 (cond (map? the-value)
                       (update-map-keys-with-index (str "__vec-" the-index "." index) the-value)
                       (vector? the-value)
                       (normalize-vector the-value (inc index))
                       :else
                       {(keyword (str "__plain." index)) the-value}))
               v))
(comment (normalize-vector [[{:x 0} 1]] 0))
;; FIXME the example is buggy so fix the code. It's good enough for me though
(comment (normalize-vector [{:x [[[{:inner 42}]]]}] 0))
;; FIXME the example is buggy so fix the code. It's good enough for me though
(comment (normalize-vector [1 2 3 {:x 42 :y [[[43 {:innie "hi, outie!"}]]]}] 0))

(defn normalize
  "Main function: normalizes the config map"
  [m]
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
;; FIXME buggy. Where is :x?
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
