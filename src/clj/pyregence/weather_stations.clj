(ns pyregence.weather-stations
  (:require
   [clj-http.client     :as client]
   [triangulum.config   :refer [get-config]]
   [triangulum.logging  :refer [log log-str]]
   [clojure.walk        :as walk]
   [clojure.data.xml    :as xml]
   [clojure.string      :as str]
   [hickory.core        :as hick]
   [clojure.set         :as set]
   [pyregence.utils     :refer [url->response-body! xml-str->safe-parse-str]]))

(defonce observation-stations (atom []))

(defn- get-US-observation-stations!
  []
  (->>
   (loop [url                  "https://api.weather.gov/stations"
          observation-stations []]
     (Thread/sleep 2000)
     (let [{{new-observation-stations           :features
             {next-batch-of-stations-url :next} :pagination} :body}
           (client/get url {:as      :json
                            :headers {"User-Agent"    "support@sig-gis.com"
                                      "Feature-Flags" "obs_station_provider"}
                            :connection-timeout (* 1000 60 3)})]
       (if (seq new-observation-stations)
         (recur next-batch-of-stations-url (concat observation-stations new-observation-stations))
         observation-stations)))
   (filter (fn [{{provider :provider} :properties}]
             (#{"MesoWest" "RAWS" "ASOS"} provider)))
   (remove (fn [{:keys [id]}] (= id "https://api.weather.gov/stations/0007W")))))

(defn- get-CA-observation-stations!
  "Joins the rest-api and xml server stations on msc_id so that the client can try both."
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
        partner-xml-stations
        (let [partner-url "https://dd.weather.gc.ca/today/observations/swob-ml/latest/"
              html-body-str->urls
              (fn
                [html-body-str]
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
                   (-> html-body-str hick/parse hick/as-hiccup))
                  @links))
              xml->obs-name->obs
              (fn [xml]
                (let [m (atom {})]
                  (walk/postwalk
                   (fn [x]
                     (when (and (map? x) (= :element (:tag x)))
                       (swap! m assoc (-> x :attrs :name keyword) (:attrs x))))
                   xml)
                  @m))]
          (some->> partner-url
                   url->response-body!
                   html-body-str->urls
                   (reduce
                    (fn [l id]
                      (conj l
                            (let [url  (str partner-url id)
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
        non-partner-xml-stations
        (->>
         (let [non-partner-url     "https://dd.weather.gc.ca/today/observations/swob-ml/partners"
               html-str->urls-in-html
               (fn
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
               xml->ids
               (fn [xml]
                 (let [m (atom {})]
                   (walk/postwalk
                    (fn [x]
                      (when (and (map? x) (= :element (:tag x))
                                 (let [{{name  :name
                                         value :value} :attrs} x]
                                   (swap! m assoc (keyword name) value)))))
                    xml)
                   @m))

               url->urls #(some->> % url->response-body! html-str->urls-in-html)]
           (->> (url->urls non-partner-url)
                (pmap (fn [network]
                        (let [network-url (str non-partner-url "/" network)]
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
                                                                     xml->ids)]
                                                        (when r
                                                          {:geometry {:coordinates [long lat]
                                                                      :type "Point"}
                                                           :properties
                                                           {:msc_id    msc_id
                                                            :network   network
                                                            :stn_id    stn_id
                                                            :latitude  lat
                                                            :longitude long}}))))))))))))))
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

(defn- get-observation-stations!
  []
  (let [
        ;;TODO question if selecting properties is worth it, it reduces the package size but each
        ;; kind of observation provider has to add their relevent properties here which is confusing.
        ;; Again, we shouldn't probably even put it in this schema e.g :geometry :properties and
        ;; and instead do that on the front end where it's actually used.

        select-relevent-properties
        (fn [{{[lon lat] :coordinates} :geometry :as ws}]
          (-> ws
              (update :properties select-keys [:name
                                               :stationIdentifier
                                               :msc_id
                                               :name_en
                                               :network
                                               :stn_id
                                               :url])
              (update :properties assoc :longitude lon :latitude lat)))]
    (reset! observation-stations
            (->> (get-US-observation-stations!)
                 (concat (get-CA-observation-stations!))
                 ;;TODO consider moving `select-relevent-properties` into two fns attached to our get-us get-ca
                 (map select-relevent-properties)))))

(defn periodically-get-observation-stations-in-the-background!
  []
  (future
    (loop []
      (try
        ;;TODO consider a way to hydrate per page and/or save cache between server restarts.
        (get-observation-stations!)
        (log-str "weather-stations-updated")
        (catch Exception ex (log (ex-data ex) :truncate? false)))
      (Thread/sleep (* 1000 ;; 1s
                       60   ;; 1m
                       (get-config ::get-observation-stations-every-n-minutes)))
      (recur))))

(defn get-weather-stations
  [_]
  {:type     "FeatureCollection"
   :features @observation-stations})
