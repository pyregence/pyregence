(ns pyregence.views
  (:require [clojure.data.json :as json]
            [hiccup.page :refer [html5 include-css include-js]]
            [pl.danieljanus.tagsoup :refer [parse]]))

(defn render-dynamic []
  (fn [request]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (html5
               [:head
                (slurp "resources/html/~head.html")
                [:title "Wildfire Forecasts - Pyregence"]
                [:meta {:name "description"
                        :content "Open source wildfire forecasting tool to assess wildfire risk for electric grid safety."}]
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
   "/about"             "about.html"
   "/data"              "data.html"
   "/extreme-weather"   "extreme-weather.html"
   "/fire-behavior"     "fire-behavior.html"
   "/forecast-tools"    "forecast-tools.html"
   "/not-found"         "not-found.html"
   "/privacy-policy"    "privacy-policy.html"
   "/scenario-analyses" "scenario-analyses.html"
   "/terms-of-use"      "terms-of-use.html"})

(defn recur-separate-tags [hiccup]
  (if (vector? hiccup)
    (let [[tag meta & children] hiccup]
      (cond
        (#{:script :link :title :meta} tag)
        {:head-tags [hiccup] :body-tags nil}

        children
        (let [x (map recur-separate-tags children)]
          {:head-tags (apply concat (map :head-tags x))
           :body-tags (into [tag meta] (keep :body-tags x))})

        :else
        {:head-tags nil :body-tags hiccup}))
    {:head-tags nil
     :body-tags hiccup}))

(defn render-static [uri]
  (fn [_]
    (let [{:keys [head-tags body-tags]} (recur-separate-tags (parse (str "resources/html/" (uri->html uri))))]
    {:status  (if (= uri "/not-found") 404 200)
     :headers {"Content-Type" "text/html"}
     :body    (html5
               [:head
                (slurp "resources/html/~head.html")
                head-tags]
               [:body
                (slurp "resources/html/~header.html")
                body-tags
                [:footer {:class "jumbotron bg-brown mb-0 py-3"}
                 [:p {:class "text-white text-center mb-0 smaller"}
                  (str "\u00A9 "
                       (+ 1900 (.getYear (java.util.Date.)))
                       " Pyregence - All Rights Reserved | Terms")]]])})))
