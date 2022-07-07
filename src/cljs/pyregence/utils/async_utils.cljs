(ns pyregence.utils.async-utils
  (:require [cljs.core.async.impl.channels  :refer [ManyToManyChannel]]
            [cljs.core.async.interop        :refer-macros [<p!]]
            [cljs.reader                    :as edn]
            [clojure.core.async             :refer [alts! go chan <! put! go-loop timeout]]
            [clojure.string                 :as str]
            [pyregence.components.messaging :refer [toast-message!]]
            [pyregence.state                :as !]
            [pyregence.utils.misc-utils     :as u-misc]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility Functions - Asynchronous Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- chan? [c]
  (= (type c) ManyToManyChannel))

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

(defn get-data
  "Asynchronously fetches the JSON or XML resource at url. Returns a
   channel containing the result of calling process-fn on the response
   or nil if an error occurred. Takes in a loading-atom which should be true when
   passed in. The loading-atom is set to false after the resource has been fetched."
  [process-fn url & [loading-atom]]
  (go
   (<! (fetch-and-process url
                            {:method "get"
                             :headers {"Accept" "application/json, text/xml"
                                       "Content-Type" "application/json"}}
                            process-fn))
   (when (some? loading-atom)
     (reset! loading-atom false))))

(defn wrap-wms-errors [type response success-fn]
  (go
    (let [json-res (<p! (.json response))]
      (if-let [exceptions (u-misc/try-js-aget json-res "exceptions")]
        (do
          (println exceptions)
          (toast-message! (str "Error retrieving " type ". See console for more details.")))
        (success-fn json-res)))))

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

