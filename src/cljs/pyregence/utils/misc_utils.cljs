(ns pyregence.utils.misc-utils
    (:require [clojure.set        :as sets]
              [clojure.string     :as str]
              [decimal.core       :as dc]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility Functions - Miscellaneous
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; ->map HOF

(defn mapm
  "A version of `map` that uses transients."
  [f coll]
  (persistent!
   (reduce (fn [acc cur]
             (conj! acc (f cur)))
           (transient {})
           coll)))

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

;;; Colors

(defn- to-hex-str [num]
  (let [hex-num (.toString (.round js/Math num) 16)]
    (if (= 2 (count hex-num))
      hex-num
      (str "0" hex-num))))

(defn interp-color
  "Returns a hex-code representing the interpolation of
   two provided colors and an interpolation ratio."
  [from to ratio]
  (when (and from to)
    (let [fr (js/parseInt (subs from 1 3) 16)
          fg (js/parseInt (subs from 3 5) 16)
          fb (js/parseInt (subs from 5 7) 16)
          tr (js/parseInt (subs to   1 3) 16)
          tg (js/parseInt (subs to   3 5) 16)
          tb (js/parseInt (subs to   5 7) 16)]
      (str "#"
           (to-hex-str (+ fr (* ratio (- tr fr))))
           (to-hex-str (+ fg (* ratio (- tg fg))))
           (to-hex-str (+ fb (* ratio (- tb fb))))))))

;;; Misc Functions

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

(defn- is-numeric? [v]
  (if (string? v)
    (re-matches #"^-?([\d]+[\d\,]*\.*[\d]+)$|^-?([\d]+)$" v)
    (number? v)))

(defn intersects?
  "Checks whether or not two sets intersect."
  [s1 s2]
  {:pre [(every? set? [s1 s2])]}
  (-> (sets/intersection s1 s2)
      (count)
      (pos?)))

(defn num-str-compare
  "Compares two strings as numbers if they are numeric."
  [asc x y]
  (let [both-numbers? (and (is-numeric? x) (is-numeric? y))
        sort-x        (if both-numbers? (js/parseFloat x) x)
        sort-y        (if both-numbers? (js/parseFloat y) y)]
    (if asc
      (compare sort-x sort-y)
      (compare sort-y sort-x))))

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

(defn try-js-aget
  "Trys to call `aget` on the specified object."
  [obj & values]
  (try
    (reduce
     (fn [acc cur]
       (if (and acc (.hasOwnProperty acc cur))
         (aget acc cur)
         (reduced nil)))
     obj
     values)
    (catch js/Error e (js/console.log e))))

(defn to-precision
  "Rounds a double to n significant digits."
  [n dbl]
  (let [factor (.pow js/Math 10 n)]
    (/ (Math/round (* dbl factor)) factor)))

(defn call-when
  "Returns a function calls `f` only when `x` passes `pred`. Can be used in
   mapping over a collection like so:
   `(map (only even? #(* % 2)) xs)`"
  [pred f]
  (fn [x]
    (if (pred x) (f x) x)))

(defn reverse-sorted-map
  "Creates a sorted-map where the keys are sorted in reverse order."
  []
  (sorted-map-by (fn [a b] (* -1 (compare a b)))))

(defn direction
  "Converts degrees to a direction."
  [degrees]
  (condp >= degrees
    22.5  "North"
    67.5  "Northeast"
    112.5 "East"
    157.5 "Southeast"
    202.5 "South"
    247.5 "Southwest"
    292.5 "West"
    337.5 "Northwest"
    360   "North"
    ""))

(defn find-boundary-values
  "Returns the two values from a sorted collection that bound v."
  [v coll]
  (loop [coll coll]
    (let [s (second coll)]
      (and s
           (if (< v s)
             (take 2 coll)
             (recur (next coll)))))))

(defn clean-units
  "Cleans units by adding/not adding a space when needed for units."
  [units]
  (if (#{"%" "\u00B0F" "\u00B0"} units)
    units
    (str " " units)))

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

(defn round-last-clicked-info
  "Rounds a point info value to the proper number of digits for rendering."
  [last-clicked-info-val]
  (when (some? last-clicked-info-val)
    (if (>= last-clicked-info-val 1)
      (to-precision 1 last-clicked-info-val)
      (-> last-clicked-info-val
          (dc/decimal)
          (dc/to-significant-digits 2)
          (dc/to-number)))))

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
