(ns pyregence.views
  (:import java.io.ByteArrayOutputStream)
  (:require [clojure.edn       :as edn]
            [clojure.string    :as str]
            [clojure.data.json :as json]
            [clojure.java.io   :as io]
            [cognitect.transit :as transit]
            [hiccup.page      :refer [html5 include-css include-js]]
            [pyregence.config :refer [get-config]]))

(defn- find-app-js []
  (as-> (slurp "target/public/cljs/manifest.edn") app
    (edn/read-string app)
    (get app "target/public/cljs/app.js")
    (str/split app #"/")
    (last app)
    (str "/cljs/" app)))

(defn head-meta-css
  "Specifies head tag elements."
  []
  [:head
   [:title "Wildfire Forecasts"]
   [:meta {:name    "description"
           :content "Open source wildfire forecasting tool to assess wildfire risk for electric grid safety."}]
   [:meta {:name "robots" :content "index, follow"}]
   [:meta {:charset "utf-8"}]
   [:meta {:name    "viewport"
           :content "width=device-width, initial-scale=1, shrink-to-fit=no"}]
   [:meta {:property "og:title" :content "Pyrecast"}]
   [:meta {:property "og:description"
           :content  "Open source wildfire forecasting tool to assess wildfire risk for electric grid safety."}]
   [:meta {:property "og:image" :content "/images/pyrecast-logo-social-media.png"}]
   [:meta {:property "og:url" :content "https://pyrecast.org/"}]
   [:meta {:property "twitter:title" :content "Pyrecast"}]
   [:meta {:property "twitter:image" :content "https://pyrecast.org/images/pyrecast-logo-social-media.png"}]
   [:meta {:property "twitter:card" :content "summary_large_image"}]
   [:link {:rel "icon" :type "image/png" :href "/images/favicon.png"}]
   [:script {:async true :src "https://www.googletagmanager.com/gtag/js?id UA-168639214-1"}]
   [:script "window.name = 'pyrecast'"]
   [:script "window.dataLayer = window.dataLayer || []; function gtag(){dataLayer.push(arguments);} gtag('js', new Date()); gtag('config', 'UA-168639214-1')"]
   (include-css "/css/style.css" "/css/mapbox-gl-v2.3.1.css")
   (include-js "/js/mapbox-gl-v2.3.1.js" (find-app-js))])

(defn- announcement-banner []
  (let [announcement (slurp "announcement.txt")]
    [:div#banner {:style {:background-color "#f96841"
                          :box-shadow       "3px 1px 4px 0 rgb(0, 0, 0, 0.25)"
                          :color            "#ffffff"
                          :display          (when (zero? (count announcement)) "none")
                          :margin           "0px"
                          :padding          "5px"
                          :position         "fixed"
                          :text-align       "center"
                          :top              "0"
                          :width            "100vw"
                          :z-index          100}}
     [:p {:style {:font-size    "18px"
                  :font-weight "bold"
                  :margin      "0 30px 0 0"}}
      announcement]
     [:button {:style   {:background-color "transparent"
                         :border-color     "#ffffff"
                         :border-radius    "50%"
                         :border-style     "solid"
                         :border-width     "2px"
                         :cursor           "pointer"
                         :display          "flex"
                         :height           "25px"
                         :padding          "0"
                         :position         "fixed"
                         :right            "10px"
                         :top              "5px"
                         :width            "25px"}
               :onClick "document.getElementById('banner').style.display='none'"}
      [:svg {:viewBox "0 0 48 48" :fill "#ffffff"}
       [:path {:d "M38 12.83l-2.83-2.83-11.17 11.17-11.17-11.17-2.83 2.83 11.17 11.17-11.17 11.17 2.83 2.83
                 11.17-11.17 11.17 11.17 2.83-2.83-11.17-11.17z"}]]]]))

(defn- cljs-init
  "A JavaScript script that calls the `init` function in `client.cljs`.
   Provides the entry point for rendering the content on a page."
  [params]
  [:script {:type "text/javascript"}
   (str "window.onload = function () {
         setTimeout(function () {document.getElementById('banner').style.display='none'}, 10000);
         pyregence.client.init("
        (json/write-str (assoc params
                               :dev-mode  (get-config :dev-mode)
                               :mapbox    (get-config :mapbox)
                               :features  (get-config :features)
                               :geoserver (get-config :geoserver)))
        "); };")])

(defn render-page [valid?]
  (fn [{:keys [params server-name]}]
    {:status  (if valid? 200 404)
     :headers {"Content-Type" "text/html"}
     :body    (html5
               (head-meta-css)
               [:body
                [:div#app]
                ;; TODO announcement-banner will be moved to the wrap-page-ha function.
                (when (.exists (io/as-file "announcement.txt"))
                  (announcement-banner))
                (cljs-init params)])}))

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
