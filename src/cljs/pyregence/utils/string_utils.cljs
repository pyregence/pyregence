(ns pyregence.utils.string-utils
  (:require [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility Functions - String Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;p;;;;;;;;;;;;;;;;;;

(defn sentence->kebab
  "Converts a string to a kebab-case string."
  [string]
  (-> string
      (str/lower-case)
      (str/replace #"[\s-\.\,]+" "-")))

(defn end-with
  "Appends `end` to `s` as long as `s` doesn't already end with `end`."
  [s end]
  (str s
       (when-not (str/ends-with? s end)
         end)))

