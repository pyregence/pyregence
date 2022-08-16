(ns pyregence.utils.number-utils
  (:require [decimal.core :as dc]))

(defn clean-units
  "Cleans units by adding/not adding a space when needed for units."
  [units]
  (if (#{"%" "\u00B0F" "\u00B0"} units)
    units
    (str " " units)))

(defn- is-numeric? [v]
  (if (string? v)
    (re-matches #"^-?([\d]+[\d\,]*\.*[\d]+)$|^-?([\d]+)$" v)
    (number? v)))

(defn num-str-compare
  "Compares two strings as numbers if they are numeric."
  [asc x y]
  (let [both-numbers? (and (is-numeric? x) (is-numeric? y))
        sort-x        (if both-numbers? (js/parseFloat x) x)
        sort-y        (if both-numbers? (js/parseFloat y) y)]
    (if asc
      (compare sort-x sort-y)
      (compare sort-y sort-x))))

(defn to-precision
  "Rounds a double to n significant digits."
  [n dbl]
  (let [factor (.pow js/Math 10 n)]
    (/ (Math/round (* dbl factor)) factor)))

(defn round-last-clicked-info
  "Rounds a point info value to the proper number of digits for rendering."
  [last-clicked-info-val]
  (when last-clicked-info-val
    (if (>= last-clicked-info-val 1)
      (to-precision 1 last-clicked-info-val)
      (-> last-clicked-info-val
          (dc/decimal)
          (dc/to-significant-digits 2)
          (dc/to-number)))))
