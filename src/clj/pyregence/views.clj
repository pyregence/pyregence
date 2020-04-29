(ns pyregence.views
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [hiccup.page :refer [html5 include-css include-js]]))

(defn combine-head []
  [:head
   (slurp "resources/html/~head.html")
   (include-css "/css/ol.css")
   (include-js "/js/ol.js" "/cljs/app.js")])

(defn render-dynamic []
  (fn [request]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (html5
               (combine-head)
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
   "/extreme-weather"   "extreme-weather.html"
   "/not-found"         "not-found.html"
   "/fire-behavior"     "fire-behavior.html"
   "/forecast-tools"    "forecast-tools.html"
   "/scenario-analyses" "scenario-analyses.html"
   "/team"              "team.html"})

(defn separate-scripts [uri]
  (reduce (fn [acc cur]
            (if (or (str/includes? cur "<link href")
                    (str/includes? cur "<script src"))
              (update acc 1 conj cur)
              (update acc 0 conj cur)))
          [[] []]
          (str/split-lines (slurp (str "resources/html/" (uri->html uri))))))

(defn render-static [uri]
  (fn [_]
    (let [[content scripts] (separate-scripts uri)]
      {:status  (if (= uri "/not-found") 404 200)
       :headers {"Content-Type" "text/html"}
       :body    (html5
                 [:head
                  (slurp "resources/html/~head.html")
                  (str/join "\n" scripts)]
                 [:body
                  (slurp "resources/html/~header.html")
                  (str/join "\n" content)
                  [:footer {:class "jumbotron bg-brown mb-0 py-3"}
                   [:p {:class "text-white text-center mb-0 smaller"}
                    (str "\u00A9 "
                         (+ 1900 (.getYear (java.util.Date.)))
                         " Pyregence - All Rights Reserved | Terms")]]])})))

(defn data-response
  ([status body]
   (data-response status body true))
  ([status body edn?]
   {:status  status
    :headers {"Content-Type" (if edn? "application/edn" "application/json")}
    :body    (if edn? (pr-str body) (json/write-str body))}))
