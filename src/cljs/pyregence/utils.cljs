(ns pyregence.utils
  (:require [cljs.reader :as edn]
            [clojure.string :as str]
            [clojure.core.async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]))

(defn input-value
  "Return the value property of the target property of an event."
  [event]
  (-> event .-target .-value))

(defn input-int-value
  [event]
  (js/parseInt (input-value event)))

(defn input-float-value
  [event]
  (js/parseFloat (input-value event)))

(defn input-keyword
  [event]
  (keyword (input-value event)))

(defn input-file
  "Return the file of the target property of an event."
  [event]
  (-> event .-target .-files (aget 0)))

;; Text

(defn ->kebab
  "Converts string to kebab-string"
  [string]
  (as-> string s
    (str/split s #"[\s-\.]")
    (map str/lower-case s)
    (str/join "-" s)))

;; Session

(def session-key "pyregence")

(defn- save-session-storage! [data]
  (.setItem (.-sessionStorage js/window) session-key (pr-str data)))

(defn get-session-storage []
  (edn/read-string (.getItem (.-sessionStorage js/window) session-key)))

(defn set-session-storage! [data]
  (save-session-storage! (merge (get-session-storage) data)))

(defn remove-session-storage! [& keys]
  (let [data (get-session-storage)]
    (save-session-storage! (apply dissoc data keys))))

(defn clear-session-storage! []
  (save-session-storage! {}))

(defn jump-to-url!
  ([url]
   (let [origin (.-origin (.-location js/window))
         cur-url (str/replace (.-href (.-location js/window)) origin "")]
     (when-not (= cur-url url) (set! (.-location js/window) url))))
  ([url window-name]
   (if window-name
     (.open js/window url window-name)
     (jump-to-url! url))))

;;; Fetch results

(defn chan? [c]
  (= (type c) cljs.core.async.impl.channels/ManyToManyChannel))

(defn promise? [p]
  (instance? js/Promise p))

(defn fetch
  "Launches a js/window.fetch operation. Returns a channel that will
  receive the response or nil if a network error occurs. The options
  map will be automatically converted to a JS object for the fetch
  call."
  ([url]
   (fetch url {}))
  ([url options]
   (go
     (try
       (<p! (.fetch js/window url (clj->js options)))
       (catch ExceptionInfo e (js/console.log "Network Error:" (ex-cause e)))))))

(defn fetch-and-process
  "Launches a js/window.fetch operation and runs process-fn on the
  successful result. HTTP Errors and Network Errors raised by the
  fetch are printed to the console. The options map will be
  automatically converted to a JS object for the fetch call. Returns a
  channel with the result of process-fn. If process-fn returns a
  channel or promise, these will be taken from using <! or <p!
  respectively."
  [url options process-fn]
  (go
    (when-let [response (<! (fetch url options))]
      (if (.-ok response)
        (try
          (let [result (process-fn response)]
            (cond (chan? result)    (<! result)
                  (promise? result) (<p! result)
                  :else             result))
          (catch ExceptionInfo e (js/console.log "Error in process-fn:" (ex-cause e))))
        (js/console.log "HTTP Error:" response)))))

(defmulti call-remote! (fn [method url data] method))

(defmethod call-remote! :get [_ url data]
  (go
    (let [query-string (->> data
                            (map (fn [[k v]] (str (pr-str k) "=" (pr-str v))))
                            (str/join "&")
                            (js/encodeURIComponent))
          fetch-params {:method "get"
                        :headers {"Accept" "application/edn"
                                  "Content-Type" "application/edn"}}
          edn-string   (<! (fetch-and-process (str url
                                                   "?auth-token=883kljlsl36dnll9s9l2ls8xksl"
                                                   (when (not= query-string "") (str "&" query-string)))
                                              fetch-params
                                              (fn [response] (.text response))))]
      (or (edn/read-string edn-string) :no-data))))

;; Combines status and error message into return value
(defmethod call-remote! :post [_ url data]
  (go
    (let [fetch-params {:method "post"
                        :headers (merge {"Accept" "application/edn"}
                                        (when-not (= (type data) js/FormData)
                                          {"Content-Type" "application/edn"}))
                        :body (cond
                                (= js/FormData (type data)) data
                                data                        (pr-str data)
                                :else                       nil)}
          response      (<! (fetch (str url "?auth-token=883kljlsl36dnll9s9l2ls8xksl")
                                   fetch-params))]
      (if response
        {:success (.-ok response)
         :status  (.-status response)
         :body    (or (edn/read-string (<p! (.text response))) "")}
        {:success false
         :status  nil
         :body    ""}))))

(defmethod call-remote! :post-text [_ url data]
  (go
    (let [fetch-params {:method "post"
                        :headers (merge {"Accept" "application/transit+json"}
                                        (when-not (= (type data) js/FormData)
                                          {"Content-Type" "application/edn"}))
                        :body (cond
                                (= js/FormData (type data)) data
                                data                        (pr-str data)
                                :else                       nil)}
          response      (<! (fetch (str url "?auth-token=883kljlsl36dnll9s9l2ls8xksl")
                                   fetch-params))]
      (if response
        {:success (.-ok response)
         :status  (.-status response)
         :body    (or (<p! (.text response)) "")}
        {:success false
         :status  nil
         :body    ""}))))

(defmethod call-remote! :post-blob [_ url data]
  (go
    (let [fetch-params {:method "post"
                        :headers (merge {"Accept" "application/transit+json"}
                                        (when-not (= (type data) js/FormData)
                                          {"Content-Type" "application/edn"}))
                        :body (cond
                                (= js/FormData (type data)) data
                                data                        (pr-str data)
                                :else                       nil)}
          response      (<! (fetch (str url "?auth-token=883kljlsl36dnll9s9l2ls8xksl")
                                   fetch-params))]
      (if response
        {:success (.-ok response)
         :status  (.-status response)
         :body    (or (<p! (.blob response)) "")}
        {:success false
         :status  nil
         :body    ""}))))

(defmethod call-remote! :default [method _ _]
  (throw (ex-info (str "No such method (" method ") defined for pyregence.utils/call-remote!") {})))

(defn call-clj-async! [clj-fn-name & args]
  (call-remote! :post-text
                (str "/clj/" clj-fn-name)
                (if (= js/FormData (type (first args))) (first args) {:clj-args args})))

;;; Process Returned Results

(def sql-primitive (comp val first first))

;;; Time processing

(defn pad-zero [num]
  (let [num-str (str num)]
    (if (= 2 (count num-str))
      num-str
      (str "0" num-str))))

(defn get-time-zone
  "Returns the string code for the local timezone."
  [js-date]
  (-> js-date
      (.toLocaleTimeString "en-us" #js {:timeZoneName "short"})
      (str/split " ")
      (peek)))

(defn get-date-from-js
  "Formats the date portion of JS Date as the date portion of an ISO string."
  [js-date show-utc?]
  (if show-utc?
    (subs (.toISOString js-date) 0 10)
    (str (.getFullYear js-date)
         "-"
         (pad-zero (+ 1 (.getMonth js-date)))
         "-"
         (pad-zero (.getDate js-date)))))

(defn get-time-from-js
  "Formats the time portion of JS Date as the time portion of an ISO string."
  [js-date show-utc?]
  (if show-utc?
    (str (subs (.toISOString js-date) 11 16) " UTC")
    (str (pad-zero (.getHours js-date))
         ":"
         (pad-zero (.getMinutes js-date))
         " "
         (get-time-zone js-date))))

(defn- model-format->js-format
  "Formats date string given from GeoServer to one that can be used by JS Date."
  [date-str]
  (let [minutes (subs date-str 11 13)]
    (str (subs date-str 0 4) "-"
         (subs date-str 4 6) "-"
         (subs date-str 6 8) "T"
         (subs date-str 9 11) ":"
         (if (= 2 (count minutes)) minutes "00")
         ":00.000Z")))

(defn js-date-from-string
  "Converts a date string to a JS Date object."
  [date-str]
  (js/Date. (if (re-matches #"\d{8}_\d{2,6}" date-str)
              (model-format->js-format date-str)
              date-str)))

(defn time-zone-iso-date
  "Returns a ISO date-time string for a given date string in local or UTC timezone."
  [date-str show-utc?]
  (let [js-date (js-date-from-string date-str)]
    (str (get-date-from-js js-date show-utc?) " " (get-time-from-js js-date show-utc?))))

;;; ->map HOF

(defn mapm [f coll]
  (persistent!
   (reduce (fn [acc cur]
             (conj! acc (f cur)))
           (transient {})
           coll)))

(defn filterm [f coll]
  (persistent!
   (reduce (fn [acc cur]
             (if (f cur)
               (conj! acc cur)
               acc))
           (transient {})
           coll)))

;;; Colors

(defn to-hex-str [num]
  (let [hex-num (.toString (.round js/Math num) 16)]
    (if (= 2 (count hex-num))
      hex-num
      (str "0" hex-num))))

(defn interp-color [from to ratio]
  (when (and from to)
    (let [fr (js/parseInt (subs from 1 3) 16)
          fg (js/parseInt (subs from 3 5) 16)
          fb (js/parseInt (subs from 5 7) 16)
          tr (js/parseInt (subs to   1 3) 16)
          tg (js/parseInt (subs to   3 5) 16)
          tb (js/parseInt (subs to   5 7) 16)]
      (str "#"
           (to-hex-str (+ fr (* ratio (- tr fr))))
           (to-hex-str (+ fg (* ratio (- tg fg))))
           (to-hex-str (+ fb (* ratio (- tb fb))))))))

;;; Misc Functions

(defn no-data? [x]
  (or
   (and (number? x) (.isNaN js/Number x))
   (and (string? x)
        (re-matches #"\d{4,}-\d{2}-\d{2}" x)
        (not (< 1990 (js/parseInt (first (str/split x #"-"))) 2200)))
   (and (string? x) (str/blank? x))
   (and (coll?   x) (empty? x))
   (nil? x)))

(defn has-data? [x] (not (no-data? x)))

(defn missing-data? [& args]
  (some no-data? args))

(defn is-numeric? [v]
  (if (string? v)
    (re-matches #"^-?([\d]+[\d\,]*\.*[\d]+)$|^-?([\d]+)$" v)
    (number? v)))

(defn num-str-compare
  "Compare two strings as numbers if they are numeric"
  [asc x y]
  (let [both-numbers? (and (is-numeric? x) (is-numeric? y))
        sort-x        (if both-numbers? (js/parseFloat x) x)
        sort-y        (if both-numbers? (js/parseFloat y) y)]
    (if asc
      (compare sort-x sort-y)
      (compare sort-y sort-x))))

(defn find-key-by-id
  ([coll id]
   (find-key-by-id coll id :opt-label))
  ([coll id k]
   (some #(when (= (:opt-id %) id) (get % k)) coll)))

(defn find-by-id [coll id]
  (some #(when (= (:opt-id %) id) %) coll))

(defn try-js-aget [obj & values]
  (try
    (reduce
     (fn [acc cur]
       (if (and acc (.hasOwnProperty acc cur))
         (aget acc cur)
         (reduced nil)))
     obj
     values)
    (catch js/Error e (js/console.log e))))

(defn to-precision
  "Round a double to n significant digits."
  [n dbl]
  (let [factor (.pow js/Math 10 n)]
    (/ (Math/round (* dbl factor)) factor)))

(defn only
  "Returns a function calls `f` only when `x` passes `pred`. Can be used in
   mapping over a collection like so:
   `(map (only even? #(* % 2)) xs)`"
  [pred f]
  (fn [x]
    (if (pred x) (f x) x)))

(defn copy-input-clipboard!
  "Copies the contents of `element-id` into the user's clipboard. `element-id` must
   be the ID of an HTML element in the document."
  [element-id]
  {:pre [(string? element-id)]}
  (doto (js/document.getElementById element-id)
    (.focus)
    (.select))
  (js/document.execCommand "copy"))
