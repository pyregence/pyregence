(ns pyregence.utils.data-utils
  (:require [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility Functions - Data Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- no-data? [x]
  (or
   (and (number? x) (.isNaN js/Number x))
   (and (string? x)
        (re-matches #"\d{4,}-\d{2}-\d{2}" x)
        (not (< 1990 (js/parseInt (first (str/split x #"-"))) 2200)))
   (and (string? x) (str/blank? x))
   (and (coll?   x) (empty? x))
   (nil? x)))

(defn has-data?
  "Checks if an input of any type has data."
  [x]
  (not (no-data? x)))

(defn missing-data?
  "Checks if an input of any type is missing specific data."
  [& args]
  (some no-data? args))

(defn filter-no-data
  "Removes any nodata 'label' entries from the provided legend-list."
  [legend-list]
  (remove (fn [leg]
            (= "nodata" (get leg "label")))
          legend-list))

(defn replace-no-data-nil
  "Replaces any nodata 'band' entries from the provided last-clicked-info list
   with nil."
  [last-clicked-info no-data-quantities]
  (mapv (fn [entry]
          (let [band-val (:band entry)]
            (assoc entry :band (if (contains? no-data-quantities (str band-val))
                                 nil
                                 band-val))))
        last-clicked-info))

(defn filterm
  "A version of `filter` that uses transients."
  [f coll]
  (persistent!
   (reduce (fn [acc cur]
             (if (f cur)
               (conj! acc cur)
               acc))
           (transient {})
           coll)))

(defn mapm
  "A version of `map` that uses transients."
  [f coll]
  (persistent!
   (reduce (fn [acc cur]
             (conj! acc (f cur)))
           (transient {})
           coll)))

(defn reverse-sorted-map
  "Creates a sorted-map where the keys are sorted in reverse order."
  []
  (sorted-map-by (fn [a b] (* -1 (compare a b)))))

(defn get-changed-keys
  "Takes in two maps with the same keys and (potentially) different values.
   Determines which values are different between the two maps and returns a set
   containing the keys associated with the changed values."
  [old-map new-map]
  (reduce (fn [acc k]
            (if (not= (get old-map k) (get new-map k))
              (conj acc k)
              acc))
          #{}
          (keys old-map)))

(defn find-boundary-values
  "Returns the two values from a sorted collection that bound v."
  [v coll]
  (loop [coll coll]
    (let [s (second coll)]
      (and s
           (if (< v s)
             (take 2 coll)
             (recur (next coll)))))))

(defn find-key-by-id
  "Finds the value of a key by id if one exists."
  ([coll id]
   (find-key-by-id coll id :opt-label))
  ([coll id k]
   (some #(when (= (:opt-id %) id) (get % k)) coll)))

(defn find-by-id
  "Finds the value of a specific id if one exists."
  [coll id]
  (some #(when (= (:opt-id %) id) %) coll))
