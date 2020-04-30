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

(defn input-file
  "Return the file of the target property of an event."
  [event]
  (-> event .-target .-files (aget 0)))

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
         :message (or (edn/read-string (<p! (.text response))) "")}
        {:success false
         :status  nil
         :message ""}))))

(defmethod call-remote! :default [method _ _]
  (throw (ex-info (str "No such method (" method ") defined for pyregence.utils/call-remote!") {})))

(defn call-clj-async! [clj-fn-name & args]
  (call-remote! :post
                (str "/clj/" clj-fn-name)
                (if (= js/FormData (type (first args))) (first args) {:clj-args args})))

;; FIXME: toast-message is not yet defined.
(defn- show-sql-error! [error]
  ;; (toast-message!
  ;;  (cond
  ;;    (str/includes? error "duplicate key")
  ;;    "This action cannot be completed because it would create a duplicate entry where this is prohibited."

  ;;    (and (str/includes? error "violates foreign key")
  ;;         (str/includes? error "still referenced from table"))
  ;;    (let [message-start (+ (str/index-of error "from table \"") 11)
  ;;          message-end   (+ 1 (str/index-of error "\"" (+ 1 message-start)))
  ;;          table-str     (subs error message-start message-end)]
  ;;      (str "This action cannot be completed because this value is being referenced by table " table-str "."))

  ;;    (str/includes? error "violates foreign key")
  ;;    "This action cannot be completed because the value selected is not valid."

  ;;    :else
  ;;    error))
  (js/alert error))

(defn call-sql-async! [sql-fn-name & args]
  (go
    (let [[schema function]         (str/split sql-fn-name #"\.")
          {:keys [success message]} (<! (call-remote! :post
                                                      (str "/sql/" schema "/" function)
                                                      {:sql-args args}))]
      (if success message (do (show-sql-error! message) [{}])))))

;;; Process Returned Results

(def sql-primitive (comp val first first))

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

(defn is-numeric? [str]
  (if (string? str)
    (re-matches #"^-?([\d]+[\d\,]*\.*[\d]+)$|^-?([\d]+)$" str)
    (number? str)))

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
  ([list id]
   (find-key-by-id list id :opt_label))
  ([list id key]
   (some #(when (= (:opt_id %) id) (get % key)) list)))

(defn find-by-id [list id]
  (some #(when (= (:opt_id %) id) %) list))

(defn try-get-js [obj & values]
  (try
    (reduce
     (fn [acc cur]
       (if (and acc (.hasOwnProperty acc cur))
         (aget acc cur)
         nil))
     obj
     values)
    (catch js/Error e (js/console.log e))))
