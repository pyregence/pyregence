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

(defn- get-partner-stations!
  []
  (letfn [(url->urls [url]
            (some-> (url->response-body! url)
                    html-str->hrefs))

          (fetch-station [[network date station]]
            (let [network-url (str ca-partner-url network)
                  station-url (str network-url "/" date "/" station)
                  xml-file (->> (url->urls station-url)
                                (filter #(str/ends-with? % ".xml"))
                                sort
                                last)]
              (when xml-file
                (when-let [{:keys [msc_id lat long stn_id]}
                           (some-> (str station-url "/" xml-file)
                                   url->response-body!
                                   xml-str->safe-parse-str
                                   xml->obs-k->val&uom)]
                  {:geometry {:coordinates [(:value long)
                                            (:value lat)]
                              :type "Point"}
                   :properties
                   {:msc_id    (:value msc_id)
                    :network   network
                    :stn_id    (:value stn_id)
                    :latitude  (:value lat)
                    :longitude (:value long)}}))))]
    (let [station-tasks
          (for [network (url->urls ca-partner-url)
                :let [network-url (str ca-partner-url network)]
                date (url->urls network-url)
                :let [date-url (str network-url "/" date)]
                station (url->urls date-url)]
            [network date station])]
      (->> station-tasks
           (partition-all 3)
           (mapcat #(doall (pmap fetch-station %)))
           (remove nil?)
           vec))))

(defn- get-non-partner-stations!
  []
  (let [xml->obs-name->obs
        (fn [xml]
          (let [m (atom {})]
            (walk/postwalk
             (fn [x]
               (when (and (map? x) (= :element (:tag x)))
                 (swap! m assoc (-> x :attrs :name keyword) (:attrs x))
                 x))
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
             (group-by :msc_id)
             (reduce-kv (fn [l _ ids] (conj l (->> ids (sort-by :url) first))) [])
               ;; follow spec client expects
             (mapv (fn [{:keys [lat long url msc_id]}]
                     {:geometry {:coordinates [long lat] :type "Point"}
                      :properties {:msc_id msc_id :url url :latitude lat :longitude long}})))))

(defn get-observation-stations!
  []
  (vec (concat (get-partner-stations!) (get-non-partner-stations!))))

(defn get-observations
  [_ {:keys [network stn_id url]}]
  (let [verify-url #(when (str/starts-with? % ca-non-partner-url) %)
        url->val&uom
        (fn [url]
          (some->> url
                   url->response-body!
                   xml-str->safe-parse-str
                   xml->obs-k->val&uom))
        observations
        (if url
          (-> url verify-url url->val&uom url)
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
