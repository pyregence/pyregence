(use-modules ((gnu packages) #:select (specifications->manifest)))

(specifications->manifest   ;; build jar | run jar | run clojure
 (list "clojure-tools@1.11" ;; T         | F       | T
       "openjdk@21:jdk"     ;; T         | T       | T
       "node@22"            ;; T         | F       | F
       "git"                ;; T         | F       | F
       "nss-certs"          ;; T         | F       | F
       "bash"               ;; T (sh)    | F       | F
       "coreutils"          ;; T (mdkir) | F       | F
       ))
