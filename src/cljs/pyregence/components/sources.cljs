(ns pyregence.components.sources
  (:require [clojure.spec.alpha :as s]))

(defonce ^:private types #{"vector" "raster" "raster-dem" "geojson" "image" "video"})

(s/def ::type (s/and string? types))
(s/def ::url uri?)
(s/def ::tiles (s/coll-of ::url))
