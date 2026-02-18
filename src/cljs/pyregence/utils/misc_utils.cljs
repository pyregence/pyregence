(ns pyregence.utils.misc-utils
  (:require
   [clojure.set :as sets]
   [clojure.string :as cstr]
   [pyregence.components.mapbox :as mb]
   [pyregence.state :as !]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility Functions - Misc Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn intersects?
  "Checks whether or not two sets intersect."
  [s1 s2]
  {:pre [(every? set? [s1 s2])]}
  (-> (sets/intersection s1 s2)
      (count)
      (pos?)))

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

(defn call-when
  "Returns a function calls `f` only when `x` passes `pred`. Can be used in
   mapping over a collection like so:
   `(map (only even? #(* % 2)) xs)`"
  [pred f]
  (fn [x]
    (if (pred x) (f x) x)))

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

(defn camel->text
  "Transforms camelCase strings into normal text"
  [string]
  (-> string
      (cstr/replace #"[A-Z]" #(str " " %))
      (cstr/capitalize)))

;;I don't fully understand what were trying to do here..
(defn on-mount-defaults
  [_]
  (let [update-fn (fn [& _]
                    (-> js/window (.scrollTo 0 0))
                    (reset! !/mobile? (> 800.0 (.-innerWidth js/window)))
                    (js/setTimeout mb/resize-map! 50))]
    (-> js/window (.addEventListener "touchend" update-fn))
    (-> js/window (.addEventListener "resize"   update-fn))
    (update-fn)))
