(ns pyregence.packaging
  (:require
   [triangulum.packaging]
   [clojure.java.shell   :refer [sh]]
   [pyregence.compile-cljs :as compile-cljs]))

(defn build-uberjar
  [m]
  (sh "npm" "install")
  (compile-cljs/-main "compile-prod.cljs.edn")
  (triangulum.packaging/build-uberjar m))
