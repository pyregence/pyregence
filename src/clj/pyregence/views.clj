(ns pyregence.views
  (:require [clojure.data.json :as json]
            [hiccup.page :refer [html5 include-css include-js]]))

(defn render-dynamic []
  (fn [request]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (html5
               [:head
                (slurp "resources/html/~head.html")
                (include-css "/css/ol.css")
                (include-js "/js/ol.js" "/cljs/app.js")]
               [:body
                [:div#near-term-forecast
                 (slurp "resources/html/~header.html")
                 [:div#app]]
                [:script {:type "text/javascript"}
                 (str "window.onload = function () { pyregence.client.init("
                      (json/write-str (:params request))
                      "); };")]])}))

(def uri->html
  {"/"                  "home.html"
   "/data"              "data.html"
   "/documents"         "documents.html"
   "/extreme-weather"   "extreme-weather.html"
   "/fire-behavior"     "fire-behavior.html"
   "/forecast-tools"    "forecast-tools.html"
   "/not-found"         "not-found.html"
   "/scenario-analyses" "scenario-analyses.html"
   "/team"              "team.html"})

(defn render-static [uri]
  (fn [_]
    {:status  (if (= uri "/not-found") 404 200)
     :headers {"Content-Type" "text/html"}
     :body    (html5
               [:head
                (slurp "resources/html/~head.html")]
               [:body
                (slurp "resources/html/~header.html")
                (slurp (str "resources/html/" (uri->html uri)))
                [:footer {:class "jumbotron bg-brown mb-0 py-3"}
                 [:p {:class "text-white text-center mb-0 smaller"}
                  (str "\u00A9 "
                       (+ 1900 (.getYear (java.util.Date.)))
                       " Pyregence - All Rights Reserved | Terms")]]])}))

(defn data-response
  ([status body]
   (data-response status body true))
  ([status body edn?]
   {:status  status
    :headers {"Content-Type" (if edn? "application/edn" "application/json")}
    :body    (if edn? (pr-str body) (json/write-str body))}))
