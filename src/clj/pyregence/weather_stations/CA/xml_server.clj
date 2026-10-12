(ns pyregence.weather-stations.CA.xml-server
  (:require
   [clj-http.client     :as client]
   [clojure.string      :as str]
   [clojure.walk        :as walk]
   [hickory.core        :as hick]
   [pyregence.utils     :refer [url->response-body! xml-str->safe-parse-str]])
  (:import
   [java.time ZoneOffset ZonedDateTime]
   [java.time.format DateTimeFormatter]))

;; CA functions that i wish were somewhere else but its not clear where...
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

;;TODO move rest api into it's own namespace.
(defn get-observation-stations!
  []
  (let [rest-api-stations
        (let [get-CA-observation-stations-from-url!
              (fn
                [url]
                (loop [url                  url
                       observation-stations []]
                  (Thread/sleep 2000)
                  (let [{{links :links
                          new-observation-stations :features} :body} (client/get url {:as :json :connection-timeout (* 1000 60 3)})
                        next-batch-of-stations-url (->> links (some (fn [{:keys [rel href]}] (when (= rel "next") href))))
                        observation-stations (concat observation-stations new-observation-stations)]
                    (if (seq next-batch-of-stations-url)
                      (recur next-batch-of-stations-url observation-stations)
                      observation-stations))))]
          (reduce (fn [l url] (concat l (get-CA-observation-stations-from-url! url))) []
                  ["https://api.weather.gc.ca/collections/swob-partner-stations/items?lang=en&limit=5000"
                   "https://api.weather.gc.ca/collections/swob-stations/items?lang=en&limit=5000"]))
        non-partner-xml-stations
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
                   (reduce
                    (fn [l id]
                      (conj l
                            (let [url  (str ca-non-partner-url id)
                                  {{msc-id :value} :msc_id
                                   {lat :value} :lat
                                   {long :value} :long}
                                  (some-> url
                                          url->response-body!
                                          xml-str->safe-parse-str
                                          xml->obs-name->obs)]
                              {:msc_id      msc-id
                               :url         url
                               :lat         lat
                               :long        long})))
                    [])
               ;; prefer minute
                   (group-by :url)
                   (reduce-kv (fn [l _ ids] (conj l (->> ids (sort-by :url) first))) [])
               ;; follow spec client expects
                   (map (fn [{:keys [lat long url msc_id]}]
                          {:geometry {:coordinates [long lat] :type "Point"}
                           :properties {:msc_id msc_id :url url :latitude lat :longitude long}}))))
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
                                              (pmap (fn [stn_id]
                                                      (let [xml-files-url     (str date-url "/" stn_id)
                                                            xml-file-url      (first (url->urls xml-files-url))
                                                            full-xml-file-url (str xml-files-url "/" xml-file-url)
                                                            {:keys [msc_id lat long stn_id] :as r}
                                                            (some->> xml-file-url
                                                                     (str xml-files-url "/")
                                                                     url->response-body!
                                                                     xml-str->safe-parse-str
                                                                     xml->obs-k->val&uom)]
                                                        (when r
                                                          {:geometry {:coordinates [long lat]
                                                                      :type "Point"}
                                                           :properties
                                                           {:msc_id    (:value msc_id)
                                                            :network   network
                                                            :stn_id    (:value stn_id)
                                                            :latitude  (:value lat)
                                                            :longitude (:value long)}}))))))))))))))
         flatten
         (remove nil?))]
    (->> (concat rest-api-stations
                 partner-xml-stations
                 non-partner-xml-stations)
         (group-by (comp :msc_id :properties))
         (reduce-kv
          (fn [l _ [{g1 :geometry p1 :properties} {p2 :properties}]]
            (conj l {:geometry g1 :properties (merge p1 p2)}))
          []))))

(defn get-observations
  ;;TODO consider calling `:url` the `:non-partner-url` so it's clear
  ;;TODO also consider calling `:network` `:partner` despite chatgpt thinking thats the correct name because it makes the whole partner vs non-partner narrative clearer
  [_ {:keys [network stn_id url]}]
  (if url
    (->> url
         url->response-body!
         xml-str->safe-parse-str
         xml->obs-k->val&uom)
    (let [partial-partner-url
          (str
           ca-partner-url
           network
           "/"
           (.format (ZonedDateTime/now ZoneOffset/UTC) (DateTimeFormatter/ofPattern "yyyyMMdd"))
           "/"
           (str/lower-case stn_id)
           "/")]
      (->>
       partial-partner-url
       url->response-body!
       html-str->hrefs
       sort ;; sort to get latest date
       first
       (str partial-partner-url)
       url->response-body!
       xml-str->safe-parse-str
       xml->obs-k->val&uom))))
