(ns pyregence.views
  (:import java.io.ByteArrayOutputStream)
  (:require [clojure.edn       :as edn]
            [clojure.string    :as str]
            [clojure.data.json :as json]
            [cognitect.transit :as transit]
            [hiccup.page            :refer [html5 include-css include-js]]
            [pl.danieljanus.tagsoup :refer [parse]]
            [pyregence.config       :refer [get-config]]))

(defn- find-app-js []
  (as-> (slurp "target/public/cljs/manifest.edn") app
    (edn/read-string app)
    (get app "target/public/cljs/app.js")
    (str/split app #"/")
    (last app)
    (str "/cljs/" app)))

(defn- parse-page [uri]
  (edn/read-string (slurp (str "resources/pages/" uri ".edn"))))

(defn head-meta-css []
  [:head
   [:meta {:name "robots" :content "index, follow"}]
   [:meta {:charset "utf-8"}]
   [:meta {:name    "viewport"
           :content "width=device-width, initial-scale=1, shrink-to-fit=no"}]
   (include-css "css/style.css")
   [:link {:rel "icon" :type "image/png" :href "/images/favicon.png"}]
   [:script {:async true :src "https://www.googletagmanager.com/gtag/js?id UA-168639214-1"}]
   [:script "window.name = 'pyrecast'"]
   [:script "window.dataLayer = window.dataLayer || []; function gtag(){dataLayer.push(arguments);} gtag('js', new Date()); gtag('config', 'UA-168639214-1')"]])

(defn header [server-name]
  (let [pyrecast? (str/ends-with? server-name "pyrecast.org")]
    [:div {:id    "header"
           :style {:align-items     "center"
                   :display         "flex"
                   :justify-content "space-between"}}
      [:a {:rel   "home"
           :href  (if pyrecast? "/" "https://pyregence.org")
           :title "Pyregence"
           :style {:margin-bottom "0.3125rem"
                   :margin-left   "10%"
                   :margin-top    "0.3125rem"}}
       [:img {:src   (str "/images/" (if pyrecast? "pyrecast" "pyregence") "-logo.svg")
              :alt   "Pyregence Logo"
              :style {:height "40px"
                      :width  "auto"}}]]
      (when pyrecast?
        [:a {:href   "https://pyregence.org"
             :target "pyregence"
             :style  {:margin-right "5%"}}
         [:img {:src   "/images/powered-by-pyregence.svg"
                :alt   "Powered by Pyregence Logo"
                :style {:height "1.25rem"
                        :width  "auto"}}]])]))

(defn render-dynamic []
  (fn [{:keys [params server-name]}]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (html5
               [:head
                (head-meta-css)
                [:title "Wildfire Forecasts"]
                [:meta {:name    "description"
                        :content "Open source wildfire forecasting tool to assess wildfire risk for electric grid safety."}]
                (include-css "/css/mapbox-gl-v2.3.1.css")
                (include-js "/js/mapbox-gl-v2.3.1.js" (find-app-js))]
               [:body
                [:div#near-term-forecast
                 (header server-name)
                 [:div#app]]
                [:script {:type "text/javascript"}
                 (str "window.onload = function () { pyregence.client.init("
                      (json/write-str (assoc params
                                             :dev-mode  (get-config :dev-mode)
                                             :mapbox    (get-config :mapbox)
                                             :features  (get-config :features)
                                             :geoserver (get-config :geoserver)))
                      "); };")]])}))

(defn render-static [uri]
  (fn [{:keys [server-name]}]
    (let [{:keys [title body]} (parse-page uri)]
      {:status  (if (= uri "/not-found") 404 200)
       :headers {"Content-Type" "text/html"}
       :body    (html5
                 [:head
                  [:title title]
                  (head-meta-css)]
                 [:body
                  (header server-name)
                  body
                  [:footer {:style {:background    "#60411f"
                                    :margin-bottom "0"
                                    :padding       "1rem"}}
                   [:p {:style {:color          "white"
                                :font-size      "0.9rem"
                                :margin-bottom  "0"
                                :text-align     "center"
                                :text-transform "uppercase"}}
                    (str "\u00A9 "
                         (+ 1900 (.getYear (java.util.Date.)))
                         " Pyregence - All Rights Reserved | ")
                    [:a {:href  "/terms-of-use"
                         :style {:border-bottom "none"
                                 :color         "#ffffff"
                                 :font-weight   "400"}}
                     "Terms"]]]])})))

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
          :or   {status 200 type :edn}
          :as   params}]
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
