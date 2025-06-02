(use-modules ((gnu packages) #:select (specifications->manifest)))

(specifications->manifest
 (list "clojure-tools@1.11" ;; to build and run clojure
       "openjdk@21:jdk" ;; to build, to run clojure
       "node@22"        ;; to build and run clojurescript
       "git"       ;; source control
       "nss-certs" ;; for build certs
       "bash"      ;; for sh (only for dev?)
       "coreutils" ;; for mkdir which is used in build
       ))
