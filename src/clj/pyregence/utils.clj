(ns pyregence.utils)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro nil-on-error
  "A macro that encloses the given `body` in a try-catch that returns `nil` on exception."
  [& body]
  (let [_ (gensym)]
    `(try ~@body (catch Exception ~_ nil))))
