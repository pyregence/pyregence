(ns pyregence.utils.async-utils
  (:require [cljs.core.async.impl.channels  :refer [ManyToManyChannel]]
            [cljs.core.async.interop        :refer-macros [<p!]]
            [cljs.reader                    :as edn]
            [clojure.core.async             :refer [alts! go chan <! put! go-loop timeout]]
            [clojure.string                 :as str]
            [pyregence.components.messaging :refer [toast-message!]]
            [pyregence.session-ended        :as session-ended]
            [pyregence.state                :as !]
            [pyregence.utils.browser-utils  :as u-browser]))

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

(defn- browser-encoded-body?
  "Whether the browser will encode this body itself, rather than PyreCast
   encoding it.

   True for exactly one payload: a multipart form carrying an upload. The
   browser sets its own Content-Type with the boundary token baked into it, so a
   Content-Type of ours corrupts the request, and `pr-str` would flatten a File
   into a string. Every site below asks this only to decide not to interfere.

   Named because the class is the browser's abstraction and not PyreCast's, and
   because the question was spelled two ways -- as an equality against `type`
   and as its negation -- in seven places, one of which is not transport code at
   all."
  [body]
  (instance? js/FormData body))

(defmulti call-remote! (fn [method url data] method))

(defmethod call-remote! :get [_ url data]
  (go
    (let [query-string (->> data
                            (map (fn [[k v]] (str (pr-str k) "=" (pr-str v))))
                            (str/join "&")
                            (js/encodeURIComponent))
          ;; Add token to Authorization header
          headers      (cond-> {"Accept" "application/edn"
                                "Content-Type" "application/edn"}
                         @!/pyr-auth-token (assoc "Authorization" (str "Bearer " @!/pyr-auth-token)))
          fetch-params {:method  "get"
                        :headers headers}
          ;; Remove token from URL
          full-url     (str url (when (not= query-string "") (str "?" query-string)))
          edn-string   (<! (fetch-and-process full-url
                                              fetch-params
                                              (fn [response] (.text response))))]
      (or (edn/read-string edn-string) :no-data))))

;; Combines status and error message into return value
(defmethod call-remote! :post [_ url data]
  (go
    (let [headers (cond-> {"Accept" "application/edn"}
                    @!/pyr-auth-token (assoc "Authorization" (str "Bearer " @!/pyr-auth-token))
                    (not (browser-encoded-body? data)) (assoc "Content-Type" "application/edn"))
          fetch-params {:method  "post"
                        :headers headers
                        :body    (cond
                                   (browser-encoded-body? data)  data
                                   data                        (pr-str data)
                                   :else                       nil)}
          response     (<! (fetch url fetch-params))]
      (if response
        {:success (.-ok response)
         :status  (.-status response)
         :body    (or (edn/read-string (<p! (.text response))) "")}
        {:success false
         :status  nil
         :body    ""}))))

(defmethod call-remote! :post-text [_ url data]
  (go
    (let [headers (cond-> {"Accept" "application/edn"}
                    @!/pyr-auth-token (assoc "Authorization" (str "Bearer " @!/pyr-auth-token))
                    (not (browser-encoded-body? data)) (assoc "Content-Type" "application/edn"))
          fetch-params {:method  "post"
                        :headers headers
                        :body    (cond
                                   (browser-encoded-body? data)  data
                                   data                        (pr-str data)
                                   :else                       nil)}
          response     (<! (fetch url fetch-params))]
      (if response
        {:success (.-ok response)
         :status  (.-status response)
         :body    (or (<p! (.text response)) "")}
        {:success false
         :status  nil
         :body    ""}))))

(defmethod call-remote! :post-blob [_ url data]
  (go
    (let [headers (cond-> {"Accept" "application/edn"}
                    @!/pyr-auth-token (assoc "Authorization" (str "Bearer " @!/pyr-auth-token))
                    (not (browser-encoded-body? data)) (assoc "Content-Type" "application/edn"))
          fetch-params {:method  "post"
                        :headers headers
                        :body    (cond
                                   (browser-encoded-body? data)  data
                                   data                        (pr-str data)
                                   :else                       nil)}
          response     (<! (fetch url fetch-params))]
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

(def idle-logout-message
  "What to say to somebody the page logged out on its own.

   Not `session-ended/message`, because it is not the same event and it does not
   read the same to the person it happens to. That one answers somebody who did
   something and was turned away. This one answers somebody who did nothing at
   all, and telling them their session \"has ended\" invites them to wonder what
   they did to end it.

   No duration named, on purpose: a sentence that says fifteen minutes is a
   sentence that has to be found and changed when the config does."
  "You've been logged out due to inactivity.")

(def session-ended-param
  "Query parameter carrying the reason to the login page. A toast does not
   survive the navigation that follows it, so the explanation travels in the URL
   and is said again on arrival -- see pyregence.pages.login/root-component."
  "session-ended")

(def session-ended-reason-refused
  "PyreCast refused something and the reason was the session."
  "refused")

(def session-ended-reason-idle
  "The page gave up on its own, before anything was asked or refused."
  "idle")

(defn session-ended-explanation
  "What to tell somebody who has just arrived at the login page, or nil where
   they came here on purpose.

   The reasons travel as words rather than as a boolean because there are two of
   them and they are genuinely different sentences -- see `idle-logout-message`.
   A page asking \"was I sent here?\" and then deciding what that means would be
   asking half a question and answering the other half itself."
  []
  (case (u-browser/url-param session-ended-param)
    "refused" session-ended/message
    "idle"    idle-logout-message
    ;; What the refusal path sent before there were two reasons to be here.
    ;; Recognized so that a link somebody still has open says something rather
    ;; than arriving at a bare form with no explanation, which is most of what
    ;; made an ended session read as missing data in the first place.
    "true"    session-ended/message
    nil))

(defn- note-session-ended!
  "Notice a refusal that happened because the session ended, say so, and go to
   the page that can do something about it.

   PYR1-1623: before this, every gated call simply came back unsuccessful, and each
   caller explained the emptiness in its own terms -- most memorably as there being
   no layers available for the selected parameters. An organization read that as its
   data having disappeared. The session is the one explanation that is actually true,
   and there is no way for a caller to arrive at it on its own.

   Said once, not once per call: a page load fires several of these at the same time
   and would otherwise stack up identical toasts.

   The redirect is the other half, and the half PYR1-1623 is actually about. A
   toast explains, but it leaves the screen it appeared on intact -- still
   showing a Settings button, still offering the organization's private layers,
   still able to be clicked. The reporter's words were that you go back to the
   session and you will still see your Settings button. Only leaving the page
   answers that.

   The toast here is for the case where nothing navigates: jump-to-url! declines
   to move a window that is already at the target, so somebody refused while
   already sitting on /login is told in place."
  [response]
  (when (and (session-ended/refusal? response)
             (not @!/session-ended?))
    (reset! !/session-ended? true)
    (toast-message! session-ended/message)
    (u-browser/jump-to-url! (str "/login?" session-ended-param "=" session-ended-reason-refused))))

(defn call-clj-async!
  "Calls a given function from the backend and returns a go block
   containing the function's response."
  [clj-fn-name & args]
  (let [first-arg (first args)
        method    (or (post-options first-arg) :post-text)
        data      (cond
                    (browser-encoded-body? first-arg)
                    first-arg

                    (= :post-text method)
                    {:clj-args args}

                    :else
                    {:clj-args (rest args)})]
    (go
      (doto (<! (call-remote! method (str "/clj/" clj-fn-name) data))
        (note-session-ended!)))))

;;; Process Returned Results

(def sql-primitive (comp val first first))
