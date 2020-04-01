(ns pyregence.handler
  (:require [clojure.data.json :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [pyregence.database :refer [sql-handler]]
            [pyregence.logging :refer [log-str]]
            [pyregence.remote-api :refer [clj-handler]]
            [pyregence.views :refer [render-page data-response]]
            [ring.middleware.absolute-redirects :refer [wrap-absolute-redirects]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.default-charset :refer [wrap-default-charset]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.x-headers :refer [wrap-frame-options wrap-content-type-options wrap-xss-protection]]
            [ring.util.codec :refer [url-decode]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Routing Handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def static-routs #{"/"
                    "/data"
                    "/documents"
                    "/team"})

;; FIXME: Fill these in as you make pages.
(def app-routes #{"/tool"})

(defn bad-uri? [uri] (str/includes? (str/lower-case uri) "php"))

(defn forbidden-response [_]
  {:status 403
   :body "Forbidden"})

(defn token-resp [{:keys [auth-token]} handler]
  (if (= auth-token "KJlkjhasduewlkjdyask-dsf")
    handler
    forbidden-response))

(defn routing-handler [{:keys [uri params] :as request}]
  (let [next-handler (cond
                       (bad-uri? uri)                  forbidden-response
                      ;;  (static-routs uri)              (static-route-handler) TODO hook this in
                       (app-routes uri)                (render-page (app-routes uri))
                       (str/starts-with? uri "/clj/")  (token-resp params clj-handler)
                       (str/starts-with? uri "/sql/")  (token-resp params sql-handler)
                       :else                           (render-page false))] ;; TODO, a static not-found page is probably more appropriate here
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler Stacks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def production-app (-> routing-handler
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
                        wrap-exceptions))

(def development-app (-> production-app
                         wrap-reload))
