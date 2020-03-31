(ns pyregence.utils
  (:require [cljs.reader :as edn]
            [clojure.string :as str]
            [clojure.core.async :refer [chan go >! <! close!]]))

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

;; FIXME: There are no /file/* routes in pyregence.handler/routing-handler. These should be managed via pyregence.remote-api/clj-handler.
;; (defn download-file! [file-name]
;;   (jump-to-url! (str "/file/download?auth-token=KJlkjhasduewlkjdyask-dsf&file-name=" file-name)))

;;; Fetch results

(defmulti call-remote! (fn [method url data] method))

(defmethod call-remote! :get [_ url data]
  (let [query-string (->> data
                          (map (fn [[k v]] (str (pr-str k) "=" (pr-str v))))
                          (str/join "&")
                          (js/encodeURIComponent))
        fetch-params {:method "get"
                      :headers {"Accept" "application/edn"
                                "Content-Type" "application/edn"}}
        result-chan  (chan)]
    (-> (.fetch js/window
                (str url
                     "?auth-token=KJlkjhasduewlkjdyask-dsf"
                     (when (= query-string "") (str "&" query-string)))
                (clj->js fetch-params))
        (.then  (fn [response]   (if (.-ok response) (.text response) (.reject js/Promise response))))
        (.then  (fn [edn-string] (go (>! result-chan (or (edn/read-string edn-string) :no-data)))))
        (.catch (fn [response]   (.log js/console response) (close! result-chan))))
    result-chan))

;; Combines status and error message into return value
(defmethod call-remote! :post [_ url data]
  (let [fetch-params {:method "post"
                      :headers (merge {"Accept" "application/edn"}
                                      (when-not (= (type data) js/FormData)
                                        {"Content-Type" "application/edn"}))
                      :body (cond
                              (= js/FormData (type data)) data
                              data                        (pr-str data)
                              :else                       nil)}
        result-chan  (chan)]
    (-> (.fetch js/window
                (str url "?auth-token=KJlkjhasduewlkjdyask-dsf")
                (clj->js fetch-params))
        (.then  (fn [response] (.then (.text response)
                                      (fn [text]
                                        (go (>! result-chan {:success (.-ok response)
                                                             :status  (.-status response)
                                                             :message (or (edn/read-string text) "")}))))))
        (.catch (fn [edn-string] (go (>! result-chan {:success false :message (or (edn/read-string edn-string) "")})))))
    result-chan))

(defmethod call-remote! :default [method _ _]
  (throw (str "No such method (" method ") defined for pyregence.utils/call-remote!")))

(defn call-clj-async! [clj-fn-name & args]
  (call-remote! :post
                (str "/clj/" clj-fn-name)
                {:clj-args args}))

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

;; FIXME: There are no /file/* routes in pyregence.handler/routing-handler. These should be managed via pyregence.remote-api/clj-handler.
;; (defn call-file-async! [file-fn-name & args]
;;   (call-remote! :post
;;                 (str "/file/" file-fn-name)
;;                 (if (= js/FormData (type (first args))) (first args) {:file-args args})))

;;; Process Returned Results

(def sql-primitive (comp val first first))

;; FIXME: set-message-box-content! is not yet defined.
;; (defn set-message-box-error! [last-step error]
;;   (set-message-box-content!
;;    {:body (concat
;;            [(str "Failed: " last-step)
;;             ""]
;;            (if (coll? error) error [error]))
;;     :button :close}))

;; (defn set-message-box-warning!
;;   ([warnings]
;;    (set-message-box-content!
;;     {:body (concat
;;             [(str "Finished With Warnings:")
;;              ""]
;;             (if (coll? warnings) warnings [warnings]))})))

;; (defn results-check
;;   ([step response-chan]
;;    (results-check step response-chan nil))
;;   ([step response-chan error-fn]
;;    (go
;;      (set-message-box-content! {:body step})
;;      (let [{:keys [status message]} (<! response-chan)]
;;        (condp = status
;;          200 message
;;          210 (set-message-box-warning! message)
;;          (do (set-message-box-error! step message)
;;              (when error-fn (<! (error-fn message)))))))))

;; (defn sql-check [step response-chan]
;;   (go
;;     (set-message-box-content! {:body step})
;;     (let [val (sql-primitive (<! response-chan))]
;;       (if (or (= val 0) (true? val))
;;         true
;;         (do (set-message-box-error! step "SQL validation failed")
;;             false)))))

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
