(ns pyregence.geo-utils)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Projections
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn EPSG:4326->3857
  "Convert wgs84(4326) lon/lat coordinates to web mercator(3857) x/y coordinates"
  [[lon lat]]
  {:pre [(number? lon) (<= -180.0 lon 180.0) (number? lat) (<= -90.0 lat 90.0)]}
  (let [x (* lon 111319.490778)
        y (-> lat (+ 90.0) (/ 360.0) (* Math/PI) (Math/tan) (Math/log) (* 6378136.98))]
    [x y]))

(defn EPSG:3857->4326
  "Convert web mercator(3857) x/y coordinates to wgs84(4326) lon/lat coordinates"
  [[x y]]
  {:pre [(number? x) (number? y)]}
  (let [lon (/ x 111319.490778)
        lat (-> y (/ 6378136.98) (Math/exp) (Math/atan) (/ Math/PI) (* 360.0) (- 90.0))]
    [lon lat]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resolution
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Reference: https://docs.microsoft.com/en-us/bingmaps/articles/bing-maps-tile-system
(defn resolution
  "Calculates ground resolution in meters using zoom and latitude using the
  following equation:
    resolution = cos(latitude * pi/180) * earth-circumference / map-width
    where: map-width = 256 * 2^zoom"
  [zoom latitude]
  {:pre [(number? zoom) (<= 0 zoom 28)
         (number? latitude) (<= -90.0 latitude 90.0)]}
  (let [earth-diameter 6378137
        t1 (Math/cos (* latitude (/ Math/PI 180)))
        earth-circumference (* 2 Math/PI earth-diameter)
        map-width (* 256 (Math/pow 2 zoom))]
    (/ (* t1 earth-circumference) map-width)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Scale
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- scale-decimal-number [d]
  (let [m1         (-> d Math/log (* -1.0) (/ Math/LN10))
        multiplier (->> m1 Math/ceil (Math/pow 10.0))]
    (-> multiplier (* d) Math/round (/ multiplier))))

(defn- scale-round-num [n]
  (let [pow10 (->> n Math/floor str count (Math/pow 10.0))
        d     (/ n pow10)]
    (* pow10 (cond
               (>= d 10.0) 10
               (>= d 5.0) 5
               (>= d 3.0) 3
               (>= d 2.0) 2
               (>= d 1.0) 1
               :else (scale-decimal-number d)))))

(defn- scale [max-dist units]
  (let [dist  (scale-round-num max-dist)
        ratio (/ dist max-dist)]
    {:distance dist :ratio ratio :units units}))

(defn imperial-scale
  "Produces a scale in imperial units.
  Returns a map of: `{:distance (mi/ft) :ratio (0-1) :units ['mi', 'ft']}`"
  [meters]
  (let [feet  (* 3.2808 meters)
        miles (/ feet 5280.0)]
    (if (> feet 5280.0)
      (scale miles "mi")
      (scale feet "ft"))))
