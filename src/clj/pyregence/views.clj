(ns pyregence.views
  (:require [clojure.data.json :as json]
            [hiccup.page :refer [html5 include-css include-js]]))

(defn render-dynamic []
  (fn [request]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (html5
               (slurp "resources/html/head.html")
               [:body
                (include-css "/css/ol.css")
                [:div#near-term-forecast
                 (slurp "resources/html/header.html")
                 [:div#app]]
                 ;; Script tags
                (include-js "/js/ol.js" "/cljs/app.js")
                (slurp "resources/html/scripts.html")
                [:script {:type "text/javascript"}
                 (str "window.onload = function () { pyregence.client.init("
                      (json/write-str (:params request))
                      "); };")]])}))

(def uri->html
  {"/"          "home.html"
   "/not-found" "not-found.html"})

(defn render-static [uri]
  (fn [_]
    {:status  (if (= uri "/not-found") 404 200)
     :headers {"Content-Type" "text/html"}
     :body    (slurp (str "resources/html/" (uri->html uri)))}))

(defn data-response
  ([status body]
   (data-response status body true))
  ([status body edn?]
   {:status  status
    :headers {"Content-Type" (if edn? "application/edn" "application/json")}
    :body    (if edn? (pr-str body) (json/write-str body))}))
