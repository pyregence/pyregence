(ns pyregence.utils.dom-utils)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility Functions - Browser DOM and Event Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn input-value
  "Returns the value property of the target property of an event."
  [event]
  (-> event .-target .-value))

(defn input-int-value
  "Given an event, returns the value as an integer."
  [event]
  (js/parseInt (input-value event)))

(defn input-float-value
  "Given an event, returns the value as a float."
  [event]
  (js/parseFloat (input-value event)))

(defn input-keyword
  "Given an event, returns the value as a Clojure keyword."
  [event]
  (keyword (input-value event)))

(defn input-file
  "Returns the file of the target property of an event."
  [event]
  (-> event .-target .-files (aget 0)))

(defn- is-ios-mobile-device? []
  (re-seq #"(?i)iphone|ipad" (. js/navigator -userAgent)))

(defn- set-ios-mobile-selection [text-area]
  (let [range      (. js/document createRange)
        _          (. range selectNodeContents text-area)
        selection  (. js/window getSelection)]
    (. selection removeAllRanges)
    (. selection addRange range)
    (. text-area setSelectionRange 0 999999)))

(defn copy-input-clipboard! [element-id]
  (let [input      (.getElementById js/document element-id)
        text-area  (.createElement js/document "textarea")]
    (set! (.-value text-area) (.-value input))
    (.appendChild (. js/document -body) text-area)
    (if (is-ios-mobile-device?)
      (set-ios-mobile-selection text-area)
      (.select text-area))
    (.execCommand js/document "copy")
    (.removeChild (. js/document -body) text-area)))
