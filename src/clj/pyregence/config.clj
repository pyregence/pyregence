(ns pyregence.config
  (:require [clojure.java.io :as io]
            [clojure.edn     :as edn]))

;;; Private vars

(def ^:private config-file "config.edn")
(def ^:private config-cache (atom nil))

;;; Helper Fns

(defn- read-config []
  (if (.exists (io/file config-file))
    (edn/read-string (slurp config-file))
    (do (println "Error: Cannot find file config.edn.")
        {})))

(defn- cache-config []
  (or @config-cache
      (reset! config-cache (read-config))))

;;; Public Fns

(defn get-config
  "Retrieves the key `k` from the config.edn file.
   Can also be called with the keys leading to a config.
   Examples:
     (get-config :mail) -> {:host \"google.com\" :port 543}
     (get-config :mail :host) -> \"google.com\""
  [k & all-keys]
  (get-in (cache-config)
          (conj (if (seq? all-keys) all-keys '()) k)))
