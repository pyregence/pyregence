(ns pyregence.compile-cljs
  (:require
   [cljs.main       :as compiler]
   [clojure.edn     :as edn]
   [clojure.java.io :as io]
   [clojure.string  :as str]))

(defn- delete-folder
  "Delete a folder and all of its contents recursively."
  [folder-name]
  (let [folder (io/file folder-name)]
    (when (.exists folder)
      (doseq [file (reverse (file-seq folder))]
        (io/delete-file file)))))

(defn- pre-compile-hook [opts-file-name]
  (if-let [folder-name (as-> (slurp opts-file-name) opts
                         (edn/read-string opts)
                         (get opts :output-dir))]
    (delete-folder folder-name)
    (str opts-file-name " is missing :output-dir.")))

(defn post-figwheel-hook [config]
  (let [output-to (get-in config [:options :output-to])
        bundle?   (= (get-in config [:options :target]) :bundle)]
    (if output-to
      (spit (io/file (get-in config [:options :output-dir]) "manifest.edn")
            {output-to (if bundle? (str/replace output-to #"(\.js)$" "_bundle$1") output-to)})
      (println "Error reading figwheel config."))))

(defn -main
  "A custom wrapper for compilation that allows pre-compile actions."
  [& [opts-file-name]]
  (if-not opts-file-name ; TODO, when moving to triangulum, use triangulum.cli/get-cli-options
    (println "A compiler options file is required to compile.\n\n   Usage: clojure -M:compile-cljs compile-prod.cljs.edn")
    (if-not (.exists (io/file opts-file-name))
      (println "The supplied compiler options file does not exist.")
      (let [config    (read-string (slurp opts-file-name))
            output-to (:output-to config)]
        (if-let [error-msg (pre-compile-hook opts-file-name)]
          (println error-msg)
          (do (compiler/-main "-co" opts-file-name "-c")
              (spit (io/file (:output-dir config) "manifest.edn")
                    {output-to output-to}))))))
  (shutdown-agents))
