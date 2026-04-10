(ns pyregence.marketplace.util
  (:require [clojure.string :as str])
  (:import [java.io InterruptedIOException]))

(defn interrupted-cause?
  "Walks the cause chain for InterruptedException or InterruptedIOException."
  [^Throwable e]
  (cond
    (nil? e) false
    (instance? InterruptedException e) true
    (instance? InterruptedIOException e) true
    (.getCause e) (recur (.getCause e))
    :else false))

(defn strip-resource-prefix
  "Strips prefix from a resource ID. Returns nil for blank input."
  [prefix raw]
  (when-not (or (str/blank? raw) (str/blank? prefix))
    (let [idx (str/index-of raw prefix)]
      (if idx
        (let [bare (subs raw (+ idx (count prefix)))]
          (when-not (str/blank? bare) bare))
        raw))))

(defn normalize-account-id
  "Returns \"accounts/X\" form, or nil."
  [raw-id]
  (some->> (strip-resource-prefix "accounts/" raw-id) (str "accounts/")))

(defn bare-account-id
  "Returns the bare ID without \"accounts/\" prefix, or nil."
  [raw-id]
  (strip-resource-prefix "accounts/" raw-id))

^:rct/test
(comment
  (strip-resource-prefix "accounts/" "providers/p/accounts/A-1")         ;=> "A-1"
  (strip-resource-prefix "accounts/" "accounts/")                        ;=> nil
  (strip-resource-prefix "accounts/" "A-1")                              ;=> "A-1"
  (strip-resource-prefix "entitlements/" "providers/P/entitlements/E-1") ;=> "E-1"
  (strip-resource-prefix "entitlements/" "E-1")                          ;=> "E-1"
  (strip-resource-prefix "entitlements/" nil)                            ;=> nil

  (normalize-account-id "providers/p/accounts/A-1") ;=> "accounts/A-1"
  (normalize-account-id "accounts/A-1")             ;=> "accounts/A-1"
  (normalize-account-id "A-1")                      ;=> "accounts/A-1"
  (normalize-account-id "accounts/")                ;=> nil
  (normalize-account-id "")                         ;=> nil
  (normalize-account-id nil)                        ;=> nil

  (bare-account-id "providers/p/accounts/A-1")      ;=> "A-1"
  (bare-account-id "accounts/A-1")                  ;=> "A-1"
  (bare-account-id "A-1")                           ;=> "A-1"
  (bare-account-id "accounts/")                     ;=> nil
  (bare-account-id "")                              ;=> nil
  (bare-account-id nil)                             ;=> nil

  (interrupted-cause? nil)                                      ;=> false
  (interrupted-cause? (Exception. "x"))                         ;=> false
  (interrupted-cause? (InterruptedException.))                  ;=> true
  (interrupted-cause? (Exception. "x" (InterruptedException.))) ;=> true
  (interrupted-cause? (Exception. "x" (Exception. "y")))        ;=> false
  )
