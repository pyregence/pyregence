(ns pyregence.weather-stations.CA.xml-server
  (:require
   [clojure.string      :as str]
   [clojure.walk        :as walk]
   [hickory.core        :as hick]
   [pyregence.utils     :refer [url->response-body! xml-str->safe-parse-str]]
   [triangulum.response :refer [data-response]])
  (:import
   [java.time ZoneOffset ZonedDateTime]
   [java.time.format DateTimeFormatter]))

(def ca-partner-url "https://dd.weather.gc.ca/today/observations/swob-ml/partners/")
(def ca-non-partner-url  "https://dd.weather.gc.ca/today/observations/swob-ml/latest/")

(defn- html-str->hrefs
  [html-str]
  (let [links (atom #{})]
    (walk/postwalk
     (fn [{:keys [href] :as x}]
       (when
        (and
         href
         (not (str/includes? href "today"))
         (or
          (str/ends-with? href "xml")
          (str/ends-with? href "/")))
         (swap! links conj (str/replace href "/" "")))
       x)
     (-> html-str hick/parse hick/as-hiccup))
    @links))

(defn- xml->obs-k->val&uom  [xml]
  (let [m (atom {})]
    (walk/postwalk
     (fn [x]
       (when (and (map? x) (= :element (:tag x))
                  (let [{{name  :name :as attrs} :attrs} x]
                    (swap! m assoc (keyword name) attrs)))))
     xml)
    @m))

(defn get-observation-stations!
  []
  (let [non-partner-xml-stations
        (let [xml->obs-name->obs
              (fn [xml]
                (let [m (atom {})]
                  (walk/postwalk
                   (fn [x]
                     (when (and (map? x) (= :element (:tag x)))
                       (swap! m assoc (-> x :attrs :name keyword) (:attrs x))))
                   xml)
                  @m))]
          (some->> ca-non-partner-url
                   url->response-body!
                   html-str->hrefs
                   (map #(-> % (str/split #"-") first)) ;; TODO record minute info so we can potentially use it
                   (map (fn [iata_id] {:properties {:iata_id iata_id}}))
                   set))
        partner-xml-stations
        (->>
         (let [url->urls #(some->> % url->response-body! html-str->hrefs)]
           (->> (url->urls ca-partner-url)
                (pmap (fn [network]
                        (let [network-url (str ca-partner-url network)]
                          (->> (url->urls network-url)
                               (pmap (fn [date]
                                       (let [date-url (str network-url "/" date)]
                                         (->> (url->urls date-url)
                                              (pmap (fn [iata_id]
                                                      {:properties
                                                       {:network network
                                                        :iata_id (str/upper-case iata_id)}}))))))))))))
         flatten
         (remove nil?)
         set)]
    (concat partner-xml-stations non-partner-xml-stations)))

(defn get-observations
  [_ {:keys [network iata_id]}]
  (let [iata_id (str/lower-case iata_id)
        url->val&uom
        (fn [url]
          (some->> (str url)
                   url->response-body!
                   xml-str->safe-parse-str
                   xml->obs-k->val&uom))
        observations
        (if-not network
          (->  (str ca-non-partner-url iata_id "-AUTO-swop.xml") url->val&uom)
          (let [partial-partner-url
                (str
                 ca-partner-url
                 network
                 "/"
                 (.format (ZonedDateTime/now ZoneOffset/UTC) (DateTimeFormatter/ofPattern "yyyyMMdd"))
                 "/"
                 iata_id
                 "/")]
            (some->>
             partial-partner-url
             url->response-body!
             html-str->hrefs
             sort ;; sort to get latest date
             last
             (str partial-partner-url)
             url->val&uom)))]
    (data-response observations {:status (if observations 200 404)})))
