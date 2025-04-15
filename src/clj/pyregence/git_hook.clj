;; #!/usr/bin/env bb
;; NOTE this file must be self-contained - it will be executed as a standalone script.
(ns pyregence.git-hook
  "A custom git-credential executable friendly to our Gitlab-Kubernetes combination,
  which resolves Gitlab Project Tokens from an EDN-encoded file."
  (:require
   [clojure.data         :as data]
   [clojure.edn          :as edn]
   [clojure.string       :as str]
   [triangulum.config    :as config]))
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
  (filter map? v))
(comment
  (collect-maps [1 2 3 {:x {:y [1 2 3 {:r 4}]}} 4]))

;; not perfect but hey
(defn collect-keys [m]
  (let [root-keys (set (keys m))]
    (set (for [k    root-keys
               :let [the-val (k m)]]
           (if (coll? the-val)
             (cond (vector? the-val)
                   (map  collect-keys (collect-maps the-val))
                   (map? the-val)
                   (collect-keys the-val))
             k)))))
(comment (collect-keys {:x 3}))
(comment (collect-keys {:x 3 :z {:y 4}}))
(comment (collect-keys {:x 3 :z {:y 4} :w [1 2 3]}))
(comment (collect-keys {:x 3 :z {:y 4} :w [1 2 3 {:o 23}]}))

(defn config-diffs []
  (data/diff
   (set (keys (read-config
               "/home/danielhabib/sig/pyregence/config.default.edn")))
   (set (keys (read-config
               "/home/danielhabib/sig/pyregence/config.edn")))))

(defn -main
  [args]
  (try
    (println "HI")
    (catch Exception e
      (println e))))

#_(when (= *file* (System/getProperty "babashka.file"))
    (apply -main *command-line-args*))
