(ns pyregence.components.layers
  (:require [clojure.spec.alpha :as s]))


(defonce ^:private types
  #{"fill" "line" "symbol" "circle" "heatmap" "fill-extrusion" "raster" "hillshade" "background" "sky"})

(s/def ::id string?)
(s/def ::type (s/and string? types))
(s/def ::source string?)
(s/def ::paint map?)
(s/def ::layout map?)
(s/def ::layer (s/keys :req-un [::id ::type ::source] :opt-un [::paint ::layout]))
(s/def ::layers (s/coll-of ::layer))

(s/fdef add-layer
        :args (s/cat ::layers ::layer)
        :ret ::layers)

(defn add-layer
  "Adds a layer to layers"
  [layers layer]
  {:pre [(s/valid? ::layers layers) (s/valid? ::layer layer)]}
  (conj layers layer))

(defn remove-layer
  [layers layer-id]
  {:pre [(s/valid? ::layers layers) (s/valid? ::id layer-id)]}
  (remove #(= (:id %) layer-id) layers))

(defn reorder
  [layers layer-id position]
  (filter #(not (= (:id %) layer-id)) layers))
