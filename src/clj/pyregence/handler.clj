(ns pyregence.handler
  (:require [clojure.edn       :as edn]
            [clojure.data.json :as json]
            [clojure.string    :as str]
            [ring.middleware.absolute-redirects :refer [wrap-absolute-redirects]]
            [ring.middleware.content-type       :refer [wrap-content-type]]
            [ring.middleware.default-charset    :refer [wrap-default-charset]]
            [ring.middleware.gzip               :refer [wrap-gzip]]
            [ring.middleware.keyword-params     :refer [wrap-keyword-params]]
            [ring.middleware.nested-params      :refer [wrap-nested-params]]
            [ring.middleware.not-modified       :refer [wrap-not-modified]]
            [ring.middleware.multipart-params   :refer [wrap-multipart-params]]
            [ring.middleware.params             :refer [wrap-params]]
            [ring.middleware.resource           :refer [wrap-resource]]
            [ring.middleware.reload             :refer [wrap-reload]]
            [ring.middleware.ssl                :refer [wrap-ssl-redirect]]
            [ring.middleware.x-headers          :refer [wrap-frame-options wrap-content-type-options wrap-xss-protection]]
            [ring.util.codec                    :refer [url-decode]]
            [pyregence.database   :refer [sql-handler]]
            [pyregence.logging    :refer [log-str]]
            [pyregence.remote-api :refer [clj-handler]]
            [pyregence.views      :refer [data-response render-dynamic render-static]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Routing Handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; FIXME: Fill these in as you make static html pages.
(def static-routes #{"/"})

;; FIXME: Fill these in as you make app pages.
(def dynamic-routes #{"/tool"})

(defn bad-uri? [uri] (str/includes? (str/lower-case uri) "php"))

(defn token-resp [{:keys [auth-token]} handler]
  (if (= auth-token "883kljlsl36dnll9s9l2ls8xksl")
    handler
    (constantly (data-response 403 "Forbidden"))))

(defn routing-handler [{:keys [uri params] :as request}]
  (let [next-handler (cond
                       (bad-uri? uri)                 (constantly (data-response 403 "Forbidden"))
                       (contains? static-routes uri)  (render-static uri)
                       (contains? dynamic-routes uri) (render-dynamic uri)
                       (str/starts-with? uri "/clj/") (token-resp params clj-handler)
                       (str/starts-with? uri "/sql/") (token-resp params sql-handler)
                       :else                          (render-dynamic false))] ;; TODO, a static not-found page is probably more appropriate here
    (next-handler request)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Custom Middlewares
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn wrap-request-logging [handler]
  (fn [request]
    (let [{:keys [uri request-method params]} request
          param-str (pr-str (dissoc params :auth-token))]
      (log-str "Request(" (name request-method) "): \"" uri "\" " param-str)
      (handler request))))

(defn wrap-response-logging [handler]
  (fn [request]
    (let [{:keys [status headers body] :as response} (handler request)
          content-type (headers "Content-Type")]
      (log-str "Response(" status "): "
               (cond
                 (str/includes? content-type "text/html")
                 "<html>...</html>"

                 (= content-type "application/edn")
                 (binding [*print-length* 2] (print-str (edn/read-string body)))

                 (= content-type "application/json")
                 (binding [*print-length* 2] (print-str (json/read-str body)))

                 :else
                 body))
      response)))

(defn parse-query-string [query-string]
  (let [keyvals (-> (url-decode query-string)
                    (str/split #"&"))]
    (reduce (fn [params keyval]
              (->> (str/split keyval #"=")
                   (map edn/read-string)
                   (apply assoc params)))
            {}
            keyvals)))

(defn wrap-edn-params [handler]
  (fn [{:keys [content-type request-method query-string body params] :as request}]
    (if (= content-type "application/edn")
      (let [get-params (when (and (= request-method :get)
                                  (not (str/blank? query-string)))
                         (parse-query-string query-string))
            post-params (when (and (= request-method :post)
                                   (not (nil? body)))
                          (edn/read-string (slurp body)))]
        (handler (assoc request :params (merge params get-params post-params))))
      (handler request))))

(defn wrap-exceptions [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (let [{:keys [data cause]} (Throwable->map e)
              status (:status data)]
          (log-str "Error: " cause)
          (data-response (or status 500) cause))))))

(defn wrap-common [handler]
  (-> handler
      wrap-request-logging
      wrap-keyword-params
      wrap-edn-params
      wrap-nested-params
      wrap-multipart-params
      wrap-params
      wrap-absolute-redirects
      (wrap-resource "public")
      wrap-content-type
      (wrap-default-charset "utf-8")
      wrap-not-modified
      (wrap-xss-protection true {:mode :block})
      (wrap-frame-options :sameorigin)
      (wrap-content-type-options :nosniff)
      wrap-response-logging
      wrap-gzip
      wrap-exceptions))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler Stacks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def production-app (-> routing-handler
                        ;; wrap-ssl-redirect ; TODO enable once HTTPs is implimented
                        wrap-common))

(def development-app (-> routing-handler
                         wrap-common
                         wrap-reload))
