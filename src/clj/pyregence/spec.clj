(ns pyregence.spec
  (:require [clojure.spec.alpha :as s]
            [herb.core :as herb]))

(s/def ::style (s/spec
                (s/cat :identifier   (s/+ (s/alt :kw keyword? :str string?))
                       :style        (s/alt :map map? :subselector ::style)
                       :child-styles (s/* ::style))))

(s/fdef herb/defglobal
  :args (s/cat :name symbol? :styles (s/+ ::style))
  :ret any?)
