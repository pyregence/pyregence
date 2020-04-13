(ns pyregence.views
  (:require [clojure.data.json :as json]
            [hiccup.page :refer [html5 include-css include-js]]))

(defn head []
  [:head
   [:title "Pyregence"]
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:meta {:name "description" :content (str "Pyregence is a web portal for displaying near-term fire forecast "
                                             "results for the state of California. The fire spread and impact maps "
                                             "are generated by running multiple state-of-the-art simulation models "
                                             "on a continuously updated stream of weather data.")}]
   [:meta {:name "keywords" :content "pyregence california fire forecast cec epic sig reax"}]
   [:link {:rel "icon" :type "image/png" :href "../images/favicon.png"}]
   (include-css "/css/ol.css")
   (include-js "/js/ol.js")
   (include-js "/cljs/app.js")])

(defn render-dynamic []
  (fn [request]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (html5
               (head)
               [:body
                [:div#app]
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
