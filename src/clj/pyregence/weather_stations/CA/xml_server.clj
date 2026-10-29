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
    (concat partner-xml-stations non-partner-xml-stations)))

(defn get-observations
  [_ {:keys [network stn_id url]}]
  (let [verify-url #(when (str/starts-with? % ca-non-partner-url) %)
        url->val&uom
        (fn [url]
          (some->> url
                  verify-url
                  url->response-body!
                  xml-str->safe-parse-str
                  xml->obs-k->val&uom))
        observations
        (if url
          (url->val&uom url)
          (let [partial-partner-url
                (str
                 ca-partner-url
                 network
                 "/"
                 (.format (ZonedDateTime/now ZoneOffset/UTC) (DateTimeFormatter/ofPattern "yyyyMMdd"))
                 "/"
                 (str/lower-case stn_id)
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
