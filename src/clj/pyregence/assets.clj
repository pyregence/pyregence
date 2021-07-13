(ns pyregence.assets
  (:require [clojure.java.io :as io]
            [clojure.string  :as str]
            [clojure.edn     :as edn]))

(def ^:private assets-dir "target/public/cljs/")
(def ^:private manifest-file (str assets-dir "manifest.edn"))

;;; Cache

(def manifest-cache (atom nil))

;;; Helper Fns

(defn- read-manifest []
  (if (nil? @manifest-cache)
    (when (.exists (io/file manifest-file))
      (->> (slurp manifest-file)
           (edn/read-string)
           (reset! manifest-cache)))
    @manifest-cache))

(defn- fingerprinted-asset [f]
  (get (read-manifest) f f))

(defn- restructure-path [path]
  (str "/cljs/" (last (str/split path #"/"))))

;;; Public Fns

(defn app-js []
  (-> (fingerprinted-asset (str assets-dir "app.js"))
      (restructure-path)))

