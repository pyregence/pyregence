(ns pyregence.packaging-test
  "What must stay true about what ships.

   The acceptance suite (PYR1-1623) needs a clock it can push forward, which
   means PyreCast has to be startable with a clock that is not the wall clock.
   The concretion that allows it lives in `test-support/`, a top-level directory
   that is on the classpath under exactly one alias and under no other.

   That arrangement is only worth anything while it holds, and it is the kind of
   thing a single tidying edit to deps.edn undoes without anyone noticing --
   `test-support` added to :paths \"so the REPL can see it\" would put an
   advanceable clock inside the uberjar and inside production. Read deps.edn and
   say so out loud, so the day it stops being true is the day a test goes red
   rather than the day someone reads the deployment.

   Read rather than grepped: a grep over the file text cannot tell :paths from a
   comment mentioning it, and would report the docstring you are reading now."
  (:require [clojure.edn      :as edn]
            [clojure.java.io  :as io]
            [clojure.test     :refer [deftest is testing]]))

(def ^:private the-directory
  "The one path holding things that must never reach production."
  "test-support")

(def ^:private the-alias
  "The one alias allowed to put it on the classpath. It is also the only alias
   that enters through the composition root living there, and the two belong
   together: a launch with the path but not that root would carry the capability
   without using it, which is the shape of an accident."
  :acceptance)

(def ^:private deps
  (delay (edn/read-string (slurp (io/file "deps.edn")))))

(defn- mentions-it? [x]
  (boolean
   (cond
     (string? x) (re-find (re-pattern the-directory) x)
     (coll?   x) (some mentions-it? x)
     :else       (re-find (re-pattern the-directory) (str x)))))

(deftest the-test-support-path-is-not-on-the-default-classpath
  ;; :paths is what every plain `clojure -M...`, every uberjar and every
  ;; production start begins with. Nothing that can move a clock belongs in it.
  (is (not (mentions-it? (:paths @deps)))
      (str the-directory " is on :paths -- it would then be inside every "
           "artifact and every production JVM")))

(deftest exactly-one-alias-puts-the-test-support-path-on-the-classpath
  (let [carrying (for [[alias-name body] (:aliases @deps)
                       :when (mentions-it? (select-keys body [:paths :extra-paths
                                                              :replace-paths]))]
                   alias-name)]
    (is (= [the-alias] (vec carrying))
        (str "expected only " the-alias " to add " the-directory
             ", but found " (vec carrying)))))

(deftest the-acceptance-alias-carries-the-path-and-the-root-together
  (let [body (get-in @deps [:aliases the-alias])]
    (testing "the path"
      (is (mentions-it? (:extra-paths body))
          (str the-alias " no longer adds " the-directory
               " -- the acceptance suite cannot advance a clock without it")))
    (testing "the root it enters through"
      ;; Which clock a process runs on is decided by whichever root started it,
      ;; so the alias naming the production root is the alias getting the wall
      ;; clock. It would still boot, still serve, and still report that nothing
      ;; ever expires.
      (is (mentions-it? (:main-opts body))
          (str the-alias " no longer enters through a root in " the-directory
               " -- it would start PyreCast on the wall clock while looking "
               "like it had not")))))

(deftest the-production-entry-points-do-not-enter-through-test-support
  ;; The other side of the same fact. :acceptance is allowed to name a root in
  ;; test-support because it is the only alias that can see the directory; every
  ;; other launch must go through pyregence.cli, which builds a SystemClock.
  (doseq [alias-name [:server :cli :build-db]
          :let [body (get-in @deps [:aliases alias-name])]
          :when body]
    (is (not (mentions-it? (:main-opts body)))
        (str alias-name " enters through " the-directory))))

(deftest the-uberjar-is-built-from-directories-that-exclude-it
  ;; The build reads only what it is told to. Naming the directories here means
  ;; a fourth directory quietly added to the build has to be argued for.
  (let [{:keys [src-dirs resource-dirs]} (get-in @deps [:aliases :build-uberjar :exec-args])]
    (is (seq src-dirs)      ":build-uberjar no longer declares :src-dirs -- this test has stopped guarding anything")
    (is (seq resource-dirs) ":build-uberjar no longer declares :resource-dirs -- this test has stopped guarding anything")
    (is (not (mentions-it? src-dirs))
        (str the-directory " is compiled into the uberjar"))
    (is (not (mentions-it? resource-dirs))
        (str the-directory " is packaged into the uberjar as a resource"))))
