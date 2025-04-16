;; #!/usr/bin/env bb
;; NOTE this file must be self-contained - it will be executed as a standalone script.
(ns pyregence.git-hook
  "A custom git-credential executable friendly to our Gitlab-Kubernetes combination,
  which resolves Gitlab Project Tokens from an EDN-encoded file."
  (:require
   [clojure.data                :as data]
   [lambdaisland.deep-diff2     :as ddiff]
   [clojure.edn                 :as edn]
   [clojure.string              :as str]
   [triangulum.config           :as config]))

(def gitlab-token (System/getenv "GITLAB_TOKEN"))
(def gitlab-user  (System/getenv "USER"))
(def gitlab-url   "https://gitlab.sig-gis.com/api/v4")
(def headers      {"PRIVATE-TOKEN" gitlab-token
                   "CONTENT-TYPE" "application/json"
                   "ACCEPT" "application/json"})
(defn get-expiry []
  (-> (java.time.ZonedDateTime/now)
      (.plusDays 364)
      (.withZoneSameInstant (java.time.ZoneId/of "America/Los_Angeles"))
      (.format (java.time.format.DateTimeFormatter/ofPattern "Y-MM-d"))))

(defn get-sig-deps
  [{:keys [deps]}]
  (into {}
        (comp (filter #(cond-> (second %) :git/url (str/includes? "gitlab.sig-gis.com")))
              (map (fn [[repo spec]] [repo (:git/url spec)])))
        deps))

(defn read-config [config-file]
  (-> config-file
      slurp
      edn/read-string))

(defn collect-maps [v]
  (reduce (fn [acc item]
            (cond (map? item)
                  (conj acc item)
                  (vector? item)
                  (conj acc (collect-maps item))
                  :else
                  acc))
          []
          v))
(comment
  (collect-maps [1 2 3 {:o 23} ["a" "b" {:innnner 42}]])
  (collect-maps [1 2 3 [{:inner 2}] {:x {:y [1 2 3 {:r 4}]}} 4]))

(defn collect-keys [m]
  (reduce (fn [acc [k the-value]]
            (cond (map? the-value)
                  (conj acc collect-keys the-value)
                  (vector? the-value)
                  (conj acc (let [hi    (collect-maps the-value)
                                  debug (map collect-keys hi)]
                              (println "hi:" hi)
                              (println "debug:" debug)
                              debug))
                  :else
                  (conj acc k)))
          #{}
          m))
(comment (collect-keys {:x 3}))
(comment (collect-keys {:x 3 :z {:y 4}}))
(comment (collect-keys {:x 3 :z {:y 4} :w [1 2 3]}))
(comment (collect-keys {:x 3 :z {:y 4}
                        :w [1 2 3 {:o 23} ["a" "b" {:innnner 42}]]}))

(defn- update-map-keys-with-index [index m]
  (update-keys m
               #(keyword (str index "." (name %)))))

(defn deep-merge-maps [coll]
  (reduce
   (fn [acc item]
     (cond (map? item)
           (merge acc item)
           :else
           (merge acc (deep-merge-maps item))))
   {}
   coll))
(comment (deep-merge-maps (normalize-vector [{:x 2}
                                             [[{:x 3}]]
                                             [2 [[[{:innie 42}]]]]]
                                            0)))
(comment (deep-merge-maps (normalize-vector [{:x 2}] 0))) ;; {:0-x 2}

(defn normalize-vector [v index]
  (map-indexed (fn [the-index the-value]
                 (cond (map? the-value)
                       (update-map-keys-with-index (str "pyr-cfg-vec-" the-index "." index) the-value)
                       (vector? the-value)
                       (normalize-vector the-value (inc index))
                       :else
                       {(keyword (str "." index)) the-value}))
               v))
(comment (normalize-vector [1] 0)) ;; {}
(comment (deep-merge-maps (normalize-vector [[[{:x 1 :y 2}]]
                                             {:x 1 :y 2}]
                                            0))) ;; {:0-x 2}
(comment (deep-merge-maps (normalize-vector [[2]] 0)))
(comment (deep-merge-maps (normalize-vector [{:x [[[{:inner 42}]]]}] 0))) ;; {:0-x 2}

(defn normalize [m]
  (reduce (fn [acc [k the-value]]
            (cond (map? the-value)
                  ;; ???
                  (merge acc (deep-merge-maps (normalize-vector [{k the-value}] 0)))
                  (vector? the-value)
                  (merge acc (deep-merge-maps (normalize-vector the-value 0)))
                  :else
                  (assoc acc k the-value)))
          {}
          m))

(comment (normalize
          {:triangulum.build-db/admin-pass "<password>"
           :x                              {:triangulum.worker/name  "set-all-capabilities",
                                            :triangulum.worker/start "pyregence.jobs/start-set-all-capabilities-job!",
                                            :triangulum.worker/stop  "pyregence.jobs/stop-set-all-capabilities-job!"}
           :triangulum.handler/bad-tokens  #{".php"}}))

(comment (normalize {:x {:inner 42}})) ;; = {:x 3}
(comment (normalize {:x [[[{:inner 42}]]]})) ;; = {:x 3}
(comment (normalize {:x [1 [{:y 0}]]})) ;; = {:x [1]}

(defn- select-config-keys [config]
  (-> config read-config normalize keys set))

(defn config-diffs []
  (ddiff/pretty-print
   (ddiff/minimize (ddiff/diff
                    (select-config-keys "/home/danielhabib/sig/pyregence/config.default.edn")
                    (select-config-keys "/home/danielhabib/sig/pyregence/config.edn"))))
  #_(data/diff
     (select-config-keys "/home/danielhabib/sig/pyregence/config.default.edn")
     (select-config-keys "/home/danielhabib/sig/pyregence/config.edn")))

(comment (config-diffs))

#_(difftastic-files
   "/home/danielhabib/sig/pyregence/config.edn"
   "/home/danielhabib/sig/pyregence/config.default.edn")

(defn -main
  [args]
  (config-diffs))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
