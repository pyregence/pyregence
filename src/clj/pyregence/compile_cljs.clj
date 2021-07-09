(ns pyregence.compile-cljs
  (:require [clojure.java.io :as io]
            [clojure.edn     :as edn]
            [cljs.main       :as compiler]))

(defn- delete-folder
  "Empty contents of all folders, then delete folders"
  [folder-name]
  (let [folder (io/file folder-name)]
    (when (.exists folder)
      (doseq [file (reverse (file-seq folder))]
        (io/delete-file file)))))

(defn- pre-compile [opts-file]
  (if-let [dir (as-> (slurp opts-file) target
                 (edn/read-string target)
                 (get target :output-dir))]
    (do (delete-folder dir)
        :success)
    (println opts-file "is missing :output-dir")))

(defn -main
  "A custom wrapper for compilation that allows pre-compile actions."
  [& [opts-file]]
  (if opts-file ;; TODO, when moving to triangulum, use triangulum.cli/get-cli-options
    (let [opts-file-io (io/file (-> opts-file))]
      (cond
        (not (.exists opts-file-io))
        (println "The supplied compiler options file does not exist.")

        (pre-compile opts-file)
        (compiler/-main "-co" opts-file "-c")))
    (println "A compiler options file is required to compile.\n\n   Usage: clojure -M:compile-cljs compile-prod.cljs.edn"))
  (shutdown-agents))
