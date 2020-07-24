(ns pyregence.views
  (:require [clojure.data.json :as json]
            [cognitect.transit :as transit]
            [hiccup.page :refer [html5 include-css include-js]]
            [pl.danieljanus.tagsoup :refer [parse]])
  (:import java.io.ByteArrayOutputStream))

(defn render-dynamic []
  (fn [request]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (html5
               [:head
                (slurp "resources/html/~head.html")
                [:title "Near Term Forecasting Tool - Pyregence"]
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
   "/data"              "data.html"
   "/documents"         "documents.html"
   "/extreme-weather"   "extreme-weather.html"
   "/fire-behavior"     "fire-behavior.html"
   "/forecast-tools"    "forecast-tools.html"
   "/not-found"         "not-found.html"
   "/scenario-analyses" "scenario-analyses.html"
   "/team"              "team.html"})

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

(defn body->transit [body]
  (let [out    (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)]
    (transit/write writer body)
    (.toString out)))

(defn data-response
  "Create a response object.
   Body is required. Status, type, and session are optional.
   When a type keyword is passed, the body is converted to that type,
   otherwise the body and type are passed through."
  ([body]
   (data-response body {}))
  ([body {:keys [status type session]
          :or {status 200 type :edn}
          :as params}]
   (merge (when (contains? params :session) {:session session})
          {:status  status
           :headers {"Content-Type" (condp = type
                                      :edn     "application/edn"
                                      :transit "application/transit+json"
                                      :json    "application/json"
                                      type)}
           :body    (condp = type
                      :edn     (pr-str         body)
                      :transit (body->transit  body)
                      :json    (json/write-str body)
                      body)})))
