(ns pyregence.packaging
  (:require
   [clojure.java.shell     :refer [sh]]
   [pyregence.compile-cljs :as compile-cljs]
   [triangulum.packaging   :as packaging]))

(defn build-uberjar!
  "Build an uberjar with compiled clj and cljs."
  [m]
  (sh "npm" "install")
  (compile-cljs/-main "compile-prod.cljs.edn")
  (triangulum.packaging/build-uberjar m))
