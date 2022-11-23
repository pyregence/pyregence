(ns pyregence.geo-utils)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^{:doc "Radius of the Earth in Meters."}
  earth-radius 6378136.98)

(def ^{:doc "Radians per Degree."}
  radians-per-degree (/ Math/PI 180.0))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Projections
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn EPSG:4326->3857
  "Convert wgs84(4326) lon/lat coordinates to web mercator(3857) x/y coordinates."
  [[lon lat]]
  {:pre  [(number? lon) (<= -180.0 lon 180.0) (number? lat) (<= -90.0 lat 90.0)]
   :post [(every? number? %)]}
  (let [x (* lon 111319.490778)
        y (-> lat (+ 90.0) (/ 360.0) (* Math/PI) (Math/tan) (Math/log) (* earth-radius))]
    [x y]))

(defn EPSG:3857->4326
  "Convert web mercator(3857) x/y coordinates to wgs84(4326) lon/lat coordinates."
  [[x y]]
  {:pre  [(number? x) (number? y)]
   :post [(fn [[lon lat]] (and (number? lon)
                               (<= -180.0 lon 180.0)
                               (number? lat)
                               (<= -90.0 lat 90.0)))]}
  (let [lon (/ x 111319.490778)
        lat (-> y (/ earth-radius) (Math/exp) (Math/atan) (/ Math/PI) (* 360.0) (- 90.0))]
    [lon lat]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resolution
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Reference: https://docs.microsoft.com/en-us/bingmaps/articles/bing-maps-tile-system
(defn resolution
  "Calculates ground resolution in meters using zoom and latitude using the
   following equation: `resolution = cos(latitude * pi/180) * earth-circumference / map-width`
   where: `map-width = 256 * 2^zoom`."
  [zoom latitude]
  {:pre  [(number? zoom) (<= 0 zoom 28) (number? latitude) (<= -90.0 latitude 90.0)]
   :post [(number? %) (pos? %)]}
  (let [map-width (->> zoom (Math/pow 2) (* 256))]
    (-> latitude
        (* Math/PI)
        (/ 180)
        Math/cos
        (* 2 Math/PI earth-radius)
        (/ map-width))))

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

(defn- build-scale-params [max-dist units]
  (let [dist  (scale-round-num max-dist)
        ratio (/ dist max-dist)]
    {:distance dist :ratio ratio :units units}))

(defn imperial-scale
  "Produces a scale in imperial units. Returns a map of:
   `{:distance (mi/ft) :ratio (0-1) :units ['mi', 'ft']}`."
  [meters]
  {:pre [(number? meters) (pos? meters)]}
  (let [feet  (* 3.2808 meters)
        miles (/ feet 5280.0)]
    (if (> feet 5280.0)
      (build-scale-params miles "mi")
      (build-scale-params feet "ft"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Distance
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- haversine
  "The haversine formula is a very accurate way of computing distances between
  two points on the surface of a sphere using the latitude and longitude of the two points"
  [x]
  (let [s (Math/sin (/ (double x) 2))]
    (* s s)))

(defn distance
  "This uses the haversine formula to calculate the great-circle distance between two points."
  [p1 p2]
  (let [[lat1 lng1] p1
        [lat2 lng2] p2
        phi1        (* lat1 radians-per-degree)
        lambda1     (* lng1 radians-per-degree)
        phi2        (* lat2 radians-per-degree)
        lambda2     (* lng2 radians-per-degree)]
    (* 2 earth-radius
       (Math/asin
        (Math/sqrt (+ (haversine (- phi2 phi1))
                      (*
                       (Math/cos phi1)
                       (Math/cos phi2)
                       (haversine (- lambda2 lambda1)))))))))
