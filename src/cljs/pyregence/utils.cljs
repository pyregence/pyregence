(ns pyregence.utils
  (:require [cljs.core.async.interop        :refer-macros [<p!]]
            [cljs.reader                    :as edn]
            [clojure.core.async             :refer [alts! go <! timeout go-loop chan put!]]
            [clojure.set                    :as sets]
            [clojure.string                 :as str]
            [pyregence.components.messaging :refer [toast-message!]]
            [pyregence.state                :as !]))

;; Session

(def session-key "pyregence")

(defn- save-session-storage! [data]
  (.setItem (.-sessionStorage js/window) session-key (pr-str data)))

(defn get-session-storage
  "Gets the pyregence session storage data."
  []
  (edn/read-string (.getItem (.-sessionStorage js/window) session-key)))

(defn set-session-storage!
  "Sets the pyregence session storage given data to store."
  [data]
  (save-session-storage! (merge (get-session-storage) data)))

(defn remove-session-storage!
  "Removes the specified pyregence session storage data given keywords."
  [& keys]
  (let [data (get-session-storage)]
    (save-session-storage! (apply dissoc data keys))))

(defn clear-session-storage!
  "Clears the pyregence session storage data."
  []
  (save-session-storage! {}))

;;; Local Storage

(defn- save-local-storage! [data]
  (.setItem (.-localStorage js/window) session-key (pr-str data)))

(defn get-local-storage
  "Gets the pyregence local storage data."
  []
  (edn/read-string (.getItem (.-localStorage js/window) session-key)))

(defn set-local-storage!
  "Sets the pyregence local storage given data to store."
  [data]
  (save-local-storage! (merge (get-session-storage) data)))

(defn remove-local-storage!
  "Removes the specified pyregence local storage data given keywords."
  [& keys]
  (let [data (get-local-storage)]
    (save-local-storage! (apply dissoc data keys))))

(defn clear-local-storage!
  "Clears the pyregence local storage data."
  []
  (save-local-storage! {}))

;;; Browser Management

(defn jump-to-url!
  "Redirects the current window to the given URL."
  ([url]
   (let [origin  (.-origin (.-location js/window))
         cur-url (str/replace (.-href (.-location js/window)) origin "")]
     (when-not (= cur-url url) (set! (.-location js/window) url))))
  ([url window-name]
   (if window-name
     (.open js/window url window-name)
     (jump-to-url! url))))

(defn redirect-to-login! [from-page]
  (set-session-storage! {:redirect-from from-page})
  (jump-to-url! "/login"))

;;; Fetch results

(defn- chan? [c]
  (= (type c) cljs.core.async.impl.channels/ManyToManyChannel))

(defn- promise? [p]
  (instance? js/Promise p))

(defn- fetch
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
          fetch-params {:method  "get"
                        :headers {"Accept" "application/edn"
                                  "Content-Type" "application/edn"}}
          edn-string   (<! (fetch-and-process (str url
                                                   "?auth-token="
                                                   @!/pyr-auth-token
                                                   (when (not= query-string "") (str "&" query-string)))
                                              fetch-params
                                              (fn [response] (.text response))))]
      (or (edn/read-string edn-string) :no-data))))

;; Combines status and error message into return value
(defmethod call-remote! :post [_ url data]
  (go
    (let [fetch-params {:method  "post"
                        :headers (merge {"Accept" "application/edn"}
                                        (when-not (= (type data) js/FormData)
                                          {"Content-Type" "application/edn"}))
                        :body    (cond
                                   (= js/FormData (type data)) data
                                   data                        (pr-str data)
                                   :else                       nil)}
          response     (<! (fetch (str url "?auth-token=" @!/pyr-auth-token)
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
    (let [fetch-params {:method  "post"
                        :headers (merge {"Accept" "application/transit+json"}
                                        (when-not (= (type data) js/FormData)
                                          {"Content-Type" "application/edn"}))
                        :body    (cond
                                   (= js/FormData (type data)) data
                                   data                        (pr-str data)
                                   :else                       nil)}
          response     (<! (fetch (str url "?auth-token=" @!/pyr-auth-token)
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
    (let [fetch-params {:method  "post"
                        :headers (merge {"Accept" "application/transit+json"}
                                        (when-not (= (type data) js/FormData)
                                          {"Content-Type" "application/edn"}))
                        :body    (cond
                                   (= js/FormData (type data)) data
                                   data                        (pr-str data)
                                   :else                       nil)}
          response     (<! (fetch (str url "?auth-token=" @!/pyr-auth-token)
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

;; TODO This whole routing should be more generic
(def ^:private post-options #{:get :post :post-text :post-blob})

(defn- show-sql-error! [error]
  (toast-message!
   (cond
     (str/includes? error "duplicate key")
     "This action cannot be completed because it would create a duplicate entry where this is prohibited."

     (and (str/includes? error "violates foreign key")
          (str/includes? error "still referenced from table"))
     (let [message-start (+ (str/index-of error "from table \"") 11)
           message-end   (+ 1 (str/index-of error "\"" (+ 1 message-start)))
           table-str     (subs error message-start message-end)]
       (str "This action cannot be completed because this value is being referenced by table " table-str "."))

     (str/includes? error "violates foreign key")
     "This action cannot be completed because the value selected is not valid."

     :else
     error)))

(defn call-sql-async! [sql-fn-name & args]
  (go
    (let [[schema function]         (str/split sql-fn-name #"\.")
          {:keys [success message]} (<! (call-remote! :post
                                                      (str "/sql/" schema "/" function)
                                                      {:sql-args args}))]
      (if success message (do (show-sql-error! message) [{}])))))

(defn call-clj-async!
  "Calls a given function from the backend and returns a go block
   containing the function's response."
  [clj-fn-name & args]
  (let [first-arg (first args)
        method    (or (post-options first-arg) :post-text)
        data      (cond
                    (= js/FormData (type first-arg))
                    first-arg

                    (= :post-text method)
                    {:clj-args args}

                    :else
                    {:clj-args (rest args)})]
    (call-remote! method
                  (str "/clj/" clj-fn-name)
                  data)))

;;; Process Returned Results

(def sql-primitive (comp val first first))

;;; Colors

(defn- to-hex-str [num]
  (let [hex-num (.toString (.round js/Math num) 16)]
    (if (= 2 (count hex-num))
      hex-num
      (str "0" hex-num))))

(defn interp-color
  "Returns a hex-code representing the interpolation of
   two provided colors and an interpolation ratio."
  [from to ratio]
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

(defn intersects?
  "Checks whether or not two sets intersect."
  [s1 s2]
  {:pre [(every? set? [s1 s2])]}
  (-> (sets/intersection s1 s2)
      (count)
      (pos?)))

(defn try-js-aget
  "Trys to call `aget` on the specified object."
  [obj & values]
  (try
    (reduce
     (fn [acc cur]
       (if (and acc (.hasOwnProperty acc cur))
         (aget acc cur)
         (reduced nil)))
     obj
     values)
    (catch js/Error e (js/console.log e))))

(defn call-when
  "Returns a function calls `f` only when `x` passes `pred`. Can be used in
   mapping over a collection like so:
   `(map (only even? #(* % 2)) xs)`"
  [pred f]
  (fn [x]
    (if (pred x) (f x) x)))

(defn refresh-on-interval!
  "Refreshes the specified function every specified interval (ms) of time.
   Exit the go-loop by doing `put! exit-chan :exit` elsewhere in the code.
   Use stop-refresh! for simplicity"
  [on-refresh-fn interval]
  (let [exit-chan (chan)]
    (go-loop []
      (let [[result _] (alts! [(timeout interval) exit-chan])]
        (when-not (= :exit result)
          (on-refresh-fn)
          (recur))))
    exit-chan))

(defn stop-refresh!
  "Take a chan from refresh-on-interval! and stops the refresh."
  [exit-chan]
  (when (chan? exit-chan)
    (put! exit-chan :exit)))

(defn direction
  "Converts degrees to a direction."
  [degrees]
  (condp >= degrees
    22.5  "North"
    67.5  "Northeast"
    112.5 "East"
    157.5 "Southeast"
    202.5 "South"
    247.5 "Southwest"
    292.5 "West"
    337.5 "Northwest"
    360   "North"
    ""))
