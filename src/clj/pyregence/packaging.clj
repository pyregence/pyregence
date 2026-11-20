(ns pyregence.packaging
  (:require
   [clojure.java.io        :as io]
   [clojure.java.shell     :refer [sh]]
   [pyregence.compile-cljs :as compile-cljs]
   [triangulum.packaging   :as packaging]))

(defn- extract-sourcemap!
  "Moves the chained CLJS/Webpack sourcemap out of target/ so it is not shipped
   inside the public UberJAR, and stages app.js + app.js.map in sourcemaps/ for
   Sentry upload (adding sourceMappingURL only to the staged copy)."
  []
  (let [js-src  (io/file "target/public/cljs/app.js")
        map-src (io/file "target/public/cljs/app.js.map")
        js-dst  (io/file "sourcemaps/app.js")
        map-dst (io/file "sourcemaps/app.js.map")]
    (when (.exists map-src)
      (io/make-parents map-dst)
      (io/copy map-src map-dst)
      (io/delete-file map-src)
      (when (.exists js-src)
        (io/copy js-src js-dst)
        (spit js-dst "\n//# sourceMappingURL=app.js.map\n" :append true)))))

(defn build-uberjar!
  "Build an uberjar with compiled clj and cljs."
  [m]
  (sh "npm" "install")
  (compile-cljs/-main "compile-prod.cljs.edn")
  (extract-sourcemap!)
  (packaging/build-uberjar
   (update m :manifest merge {"Implementation-Version" (packaging/get-calendar-commit-version)})))
