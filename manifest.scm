(use-modules ((gnu packages) #:select (specifications->manifest)))

(specifications->manifest
 (list "clojure-tools@1.11" ;; to build and run clojure.
       "openjdk@21:jdk"     ;; to build and to run clojure.
       "node@22"            ;; to build and run clojurescript.
       "git"                ;; to build the jar we use git information.
       "nss-certs"          ;; to build we need certs.
       "bash"               ;; for sh (TODO double check this is needed...).
       "coreutils"          ;; to build the jar it needs mkdir.
       ))
