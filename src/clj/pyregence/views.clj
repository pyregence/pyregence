(ns pyregence.views
  (:require [clojure.edn       :as edn]
            [clojure.string    :as str]
            [clojure.data.json :as json]
            [cognitect.transit :as transit]
            [hiccup.page :refer [html5 include-css include-js]]
            [pl.danieljanus.tagsoup :refer [parse]]
            [pyregence.config :refer [get-config]])
  (:import java.io.ByteArrayOutputStream))

(defn- find-app-js []
  (as-> (slurp "target/public/cljs/manifest.edn") app
    (edn/read-string app)
    (get app "target/public/cljs/app.js")
    (str/split app #"/")
    (last app)
    (str "/cljs/" app)))

(defn head-meta-css []
  [:head
   [:meta {:name "robots" :content "index, follow"}]
   [:meta {:charset "utf-8"}]
   [:meta {:name    "viewport"
           :content "width=device-width, initial-scale=1, shrink-to-fit=no"}]
   [:link {:rel         "stylesheet"
           :href        "https://stackpath.bootstrapcdn.com/bootstrap/4.3.1/css/bootstrap.min.css"
           :integrity   "sha384-ggOyR0iXCbMQv3Xipma34MD+dH/1fQ784/j6cY/iJTQUOhcWr7x9JvoRxT2MZw1T"
           :crossorigin "anonymous"}]
   [:link {:rel "stylesheet" :href "css/style.css"}]
   [:link {:rel "icon" :type "image/png" :href "/images/favicon.png"}]
   [:script {:async true :src "https://www.googletagmanager.com/gtag/js?id UA-168639214-1"}]
   [:script "window.name = pyrecast"]
   [:script "window.dataLayer = window.dataLayer || []; function gtag(){dataLayer.push(arguments);} gtag('js', new Date()); gtag('config', 'UA-168639214-1')"]])

(defn header [server-name]
  (let [pyrecast? (str/ends-with? server-name "pyrecast.org")]
    [:div {:class "wrapper-navbar"
           :id    "header"}
     [:a {:class "skip-link sr-only sr-only-focusable"
          :href  "#content"}
      "Skip to content"]
     [:div {:class "container"}
      [:div {:class "row align-items-center" :id "nav-row"}
       [:div {:class "col-md-3 col-6"}
        [:a {:class "navbar-brand"
             :rel   "home"
             :href  "/"
             :title "Pyregence"}
         [:img {:src   (str "/images/" (if pyrecast? "pyrecast" "pyregence") "-logo.svg")
                :alt   "Pyregence logo"
                :class "real-logo"}]
         [:img {:src   "/images/pyregence-logo-white.svg"
                :class "white-logo"
                :alt   "Pyregence logo white"}]]]
       [:div {:class "mr-auto"}]
       (when pyrecast?
         [:a {:class  "col-md-2 col-4"
              :href   "https://pyregence.org"
              :target "pyregence"}
          [:img {:class "real-logo" :src "/images/powered-by-pyregence.svg"}]])]]]))

(defn render-dynamic []
  (fn [{:keys [params server-name]}]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (html5
               [:head
                (head-meta-css)
                [:title "Wildfire Forecasts"]
                [:meta {:name "description"
                        :content "Open source wildfire forecasting tool to assess wildfire risk for electric grid safety."}]
                (include-css "/css/mapbox-gl-v2.2.0.css")
                (include-js "/js/mapbox-gl-v2.2.0.js" (find-app-js))]
               [:body
                [:div#near-term-forecast
                 (header server-name)
                 [:div#app]]
                [:script {:type "text/javascript"}
                 (str "window.onload = function () { pyregence.client.init("
                      (json/write-str (assoc params
                                             :features (get-config :features)))
                      "); };")]])}))

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
  (fn [{:keys [server-name]}]
    (let [{:keys [head-tags body-tags]} (recur-separate-tags (parse (str "resources/html/" uri ".html")))]
      {:status  (if (= uri "/not-found") 404 200)
       :headers {"Content-Type" "text/html"}
       :body    (html5
                 [:head
                  (head-meta-css)
                  head-tags]
                 [:body
                  (header server-name)
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
