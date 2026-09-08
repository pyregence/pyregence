(ns pyregence.weather-stations
  (:require [clj-http.client     :as client]
            [clojure.data.csv    :as csv]
            [semantic-csv.core :as sc]
            [triangulum.logging  :refer [log log-str]]
            [triangulum.config   :refer [get-config]]
            [clojure.data.xml :as xml]
            [clojure.walk :as walk]
            [clojure.string :as str]
            [clojure.set :as set]))

(defonce observation-stations (atom []))

;;NOTE this takes roughly 5-10 minutes
(defn- get-US-observation-stations!
  []
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
        observation-stations))))

(defn- select-relevent-properties
  [{{[lon lat] :coordinates} :geometry :as ws}]
  (-> ws
      (update :properties select-keys [:name :stationIdentifier :msc_id])
      (update :properties assoc :longitude lon :latitude lat)))

(defn get-CA-observation-stations-from-url!
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
        observation-stations))))

(defn get-CA-observation-stations!
  []
  (reduce
   (fn [l url]
     (concat l (get-CA-observation-stations-from-url! url)))
   []
   ["https://api.weather.gc.ca/collections/swob-partner-stations/items?lang=en&limit=5000"
    "https://api.weather.gc.ca/collections/swob-stations/items?lang=en&limit=5000"]))

(defn periodically-get-observation-stations-in-the-background!
  []
  (future
    (loop []
      (try
        ;;TODO consider a way to hydrate per page and/or save cache between server restarts.
        (reset! observation-stations
                (->> (get-US-observation-stations!)
                     (filter (fn [{{provider :provider} :properties}]
                               (#{"MesoWest" "RAWS" "ASOS"} provider)))
                     (remove (fn [{:keys [id]}] (= id "https://api.weather.gov/stations/0007W")))
                     (concat (get-CA-observation-stations!))
                     (map select-relevent-properties)))
        (log-str "weather-stations-updated")
        (catch Exception ex (log (ex-data ex) :truncate? false)))
      (Thread/sleep (* 1000 ;; 1s
                       60   ;; 1m
                       (get-config ::get-observation-stations-every-n-minutes)))
      (recur))))

(defn get-weather-stations
  [_]
  {:type "FeatureCollection"
   :features @observation-stations})

(comment
  ;; transform weather station api results

  ;;
  (-> partner-weather-stations first :properties keys)
  ;; => (:std_time
  ;;     :data_attribution_notice_fr
  ;;     :msc_id
  ;;     :data_provider_en
  ;;     :icao_id
  ;;     :data_attribution_notice_en
  ;;     :name_en
  ;;     :wmo_id
  ;;     :data_provider_fr
  ;;     :name_fr
  ;;     :auto_man
  ;;     :dst_time
  ;;     :province_territory
  ;;     :iata_id)

  (->> main-weather-stations first :properties keys)
  ;; => (:iata_id
  ;;     :name
  ;;     :wmo_id
  ;;     :msc_id
  ;;     :data_provider
  ;;     :dataset_network
  ;;     :auto_man
  ;;     :province_territory)

  (-> us-weather-stations first :properties keys)
  ;; => (:timeZone
  ;;     :elevation
  ;;     :fireWeatherZone
  ;;     :subProvider
  ;;     :name
  ;;     :forecast
  ;;     :county
  ;;     :stationIdentifier
  ;;     :@id
  ;;     :@type
  ;;     :provider)

  ;; need url for for
  ;; us observation url
  ;; (str "https://api.weather.gov/stations/" (:stationIdentifier weather-station) "/observations/latest")

  ;; todo canadian url observation

  :weather-api :US
  :weather-api :CA

  (str "https://api.weather.gc.ca/collections/swob-realtime/items?f=json&msc_id-value=" msc-id "&sortby=-date_tm-value&limit=10")

  (defn get-observation
    [msc-id]
    (client/get (str "https://api.weather.gc.ca/collections/swob-realtime/items?f=json&msc_id-value=" msc-id "&sortby=-date_tm-value&limit=10") {:as :json}))

  (->> main-weather-stations first :properties)
  ;; => {:iata_id "CAAB",
  ;;     :name "ST ALBAN'S",
  ;;     :wmo_id 73051,
  ;;     :msc_id "8400416",
  ;;     :data_provider "MSC",
  ;;     :dataset_network "CA",
  ;;     :auto_man "AUTO",
  ;;     :province_territory "Newfoundland and Labrador"}

  (->> main-weather-stations first :properties :msc_id)
;; => "8400416"

  ;; main-observation
  (def main-obs (get-observation "8400416"))

  (->> main-obs :body keys)
  ;; => (:type :features :numberMatched :numberReturned :links :timeStamp)

  (->> main-obs :body :features first :properties keys (take 3))
;; => (:rnfl_amt_pst1hr-uom :avg_wnd_dir_10m_pst2mts :max_batry_volt_pst1hr-uom)

  (->> partner-weather-stations
       first
       :properties
       :msc_id)
 ;; => "DFO_AZMP-ESG"

;; doesn't have data
  (->> partner-obs :body :features)

  (->> partner-weather-stations
       second
       :properties
       :msc_id)

  (->> partner-obs :body :features first :properties keys (take 3))
;; => (:avg_wnd_spd_3m_pst1mt
;;     :sea_sfc_temp_100cm_dpth-qa
;;     :CO2_conc_wtr_100cm_dpth-uom)

  (->> partner-obs :body :features first :properties :avg_wnd_spd_3m_pst1mt)
;; => 0

  (->> partner-obs :body :features first :properties :air_temp)
;; => 19.3

  (->> partner-obs :body :features first :properties :air_temp-uom)
;; => "°C"

  (->> main-obs :body :features first :properties :air_temp-uom)
;; => "°C"

  (->> partner-weather-stations
       first
       keys)

;; => (:type :id :geometry :properties)

  (->> partner-weather-stations
       first
       :properties
       keys)
;; => (:std_time
;;     :data_attribution_notice_fr
;;     :msc_id
;;     :data_provider_en
;;     :icao_id
;;     :data_attribution_notice_en
;;     :name_en
;;     :wmo_id
;;     :data_provider_fr
;;     :name_fr
;;     :auto_man
;;     :dst_time
;;     :province_territory
;;     :iata_id)

  (->> partner-weather-stations
       first
       :id)
;; => "DFO_AZMP-ESG"

;;
  )

(comment
  ;; get all rest api
  ;; rest api observation stations from two apis partner and latest
  ;; get the url for partner​

  (defn get-canadian-weather-stations-from-rest-api
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
          observation-stations))))

  ;; TODO could set a higher limit aka limit=500
  (def partner-weather-stations (get-canadian-weather-stations-from-rest-api "https://api.weather.gc.ca/collections/swob-partner-stations/items?lang=en&limit=5000"))
  (def main-weather-stations (get-canadian-weather-stations-from-rest-api "https://api.weather.gc.ca/collections/swob-stations/items?lang=en&limit=5000"))

  (defn- get-us-observation-stations!
    []
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
          observation-stations))))

  (def us-weather-stations (get-us-observation-stations!))
  (count us-weather-stations)
  ;; => 48426

;;
  )

(comment
  ;; did some stats here so moving to a new comment
  ;; rest api observation stations from two apis partner and latest
  ;; get the url for partner​

  "/collections/swob-stations/items" ;; stations
  "/collections/swob-partner-stations/items" ;; partner stations

  (def stations-np (client/get "https://api.weather.gc.ca/collections/swob-stations/items?lang=en" {:as :json}))

  (-> stations-np :body :numberReturned)
  ;; => 500

  (-> stations-np :body :numberMatched)
  ;; => 938

  (->> stations-np
       :body
       :links)
  ;; => [{:type "application/geo+json",
  ;;      :rel "self",
  ;;      :title "This document as GeoJSON",
  ;;      :href
  ;;      "https://api.weather.gc.ca/collections/swob-stations/items?f=json&lang=en"}
  ;;     {:rel "alternate",
  ;;      :type "application/ld+json",
  ;;      :title "This document as RDF (JSON-LD)",
  ;;      :href
  ;;      "https://api.weather.gc.ca/collections/swob-stations/items?f=jsonld&lang=en"}
  ;;     {:type "text/html",
  ;;      :rel "alternate",
  ;;      :title "This document as HTML",
  ;;      :href
  ;;      "https://api.weather.gc.ca/collections/swob-stations/items?f=html&lang=en"}
  ;;     {:type "application/geo+json",
  ;;      :rel "next",
  ;;      :title "Items (next)",
  ;;      :href
  ;;      "https://api.weather.gc.ca/collections/swob-stations/items?offset=500&lang=en"}
  ;;     {:type "application/json",
  ;;      :title "SWOB - Stations",
  ;;      :rel "collection",
  ;;      :href "https://api.weather.gc.ca/collections/swob-stations"}]

  "https://api.weather.gc.ca/collections/swob-stations/items?offset=500&lang=en"

  (def stations-np-1 (client/get "https://api.weather.gc.ca/collections/swob-stations/items?offset=500&lang=en" {:as :json}))

  (->> stations-np-1 :body :numberReturned)
  ;; => 438

  (->> stations-np-1 :body :numberMatched)
  ;; => 938

  (def stations-np-x (client/get "https://api.weather.gc.ca/collections/swob-stations/items?limit=2000&lang=en" {:as :json}))

  (->> stations-np-x :body :numberReturned)

  (->> stations-np-x :body :links (group-by :rel) (reduce-kv #(assoc %1 (keyword %2) %3) {}) :next)

  (->> stations-np :body :links (group-by :rel) (reduce-kv #(assoc %1 (keyword %2) %3) {}) :next)

  (def rm stations-np-x)

  (defn- get-all-canandian-non-partner-observation-stations!
    []
    (loop [url                  "https://api.weather.gc.ca/collections/swob-stations/items?limit=2000&lang=en"
           observation-stations []]
      (Thread/sleep 2000)
      (let [{{new-observation-stations           :features
              {next-batch-of-stations-url :next} :pagination} :body}
            (client/get url {:as      :json
                             :connection-timeout (* 1000 60 3)})]
        (if (seq new-observation-stations)
          (recur next-batch-of-stations-url (concat observation-stations new-observation-stations))
          observation-stations))))

  (def mc
    (reduce
     (fn [l url]
       (concat l
               (->> url
                    client/get
                    :body
                    csv/read-csv
                    (sc/mappify {:keyify false})
                    doall
                    #_(map
                       (fn [{:keys [Longitude Latitude]}]
                         {:geometry {:type "Point", :coordinates [Longitude Latitude]}
                        ;;TODO add a property we can use to identify it.. .how do we get observations?
                          :properties {}})))))
     []
     [#_"https://dd.weather.gc.ca/today/observations/doc/swob-xml_partner_station_list.csv"
      "https://dd.weather.gc.ca/today/observations/doc/swob-xml_station_list.csv"]))

  (count mc)
  ;; => 943

  (def pc
    (reduce
     (fn [l url]
       (concat l
               (->> url
                    client/get
                    :body
                    csv/read-csv
                    (sc/mappify {:keyify false})
                    doall
                    #_(map
                       (fn [{:keys [Longitude Latitude]}]
                         {:geometry {:type "Point", :coordinates [Longitude Latitude]}
                        ;;TODO add a property we can use to identify it.. .how do we get observations?
                          :properties {}})))))
     []
     ["https://dd.weather.gc.ca/today/observations/doc/swob-xml_partner_station_list.csv"
      #_"https://dd.weather.gc.ca/today/observations/doc/swob-xml_station_list.csv"]))

  (count pc)
  ;; => 1996

  (def csv-results
    (reduce
     (fn [l url]
       (concat l
               (->> url
                    client/get
                    :body
                    csv/read-csv
                    (sc/mappify {:keyify false})
                    doall
                    #_(map
                       (fn [{:keys [Longitude Latitude]}]
                         {:geometry {:type "Point", :coordinates [Longitude Latitude]}
                        ;;TODO add a property we can use to identify it.. .how do we get observations?
                          :properties {}})))))
     []
     ["https://dd.weather.gc.ca/today/observations/doc/swob-xml_partner_station_list.csv"
      "https://dd.weather.gc.ca/today/observations/doc/swob-xml_station_list.csv"]))

  "/collections/swob-partner-stations/items" ;; partner stations

  (def rp (client/get "https://api.weather.gc.ca/collections/swob-partner-stations/items?lang=en" {:as :json}))

  (-> rp :body :numberMatched)
  ;; => 2030

  (-> rp :body :numberReturned)
  ;; => 500

  (def rp (client/get "https://api.weather.gc.ca/collections/swob-partner-stations/items?limit=5000&lang=en" {:as :json}))

  (-> rp :body :numberReturned)
  ;; => 2030

  (->> rp :body :features count)

  (->> rm :body :features count)
  ;; => 938

  (def rest-api-results
    (concat
     (->> rm :body :features)
     (->> rp :body :features)))

  (def rm-ids (->> rm :body :features (map :msc_id rm) set))

  (def rp-ids (->> rp :body :features (map :msc_id rm) set))

  (set/intersection rm-ids rp-ids)
  ;; => #{}

  (def rest-ids
    (->> rest-api-results
         (map :id)
         set))

  (count rest-ids)
  ;; => 2968

  (def csv-ids
    (->> csv-results
         (map (fn [x]
                (or (get x "# MSC ID")
                    (get x "MSC_ID"))))
         set))

  (def pc-ids
    (->> pc (map #(get % "# MSC ID")) set))

  (def mc-ids
    (->> mc (map #(get % "MSC_ID")) set))

  (set/intersection mc-ids pc-ids)
  ;; => #{}

  ;; climate ids
  (take 3 pc-ids)
  ;;      province-district-station
  ;; => ("AB-MAF_WMA" "BC-ENV-ASW_1C43P" "BC_WMB_1277")

  (count csv-ids)
  ;; => 2939
;;  r    c
;;m  938  948
;;p 2030 1996
;;t 2968 2944
;;u 2968 2939

;; only in csv
  (set/difference #{1 2} #{2})
  ;; => #{1}
  (set/difference csv-ids rest-ids)
  ;; => #{"8305500"
  ;;      "8204703"
  ;;      "702S007"
  ;;      "2400697"
  ;;      "2100950"
  ;;      "2401030"
  ;;      "ON-MNRF-AFFES_4QD"
  ;;      "2100682"
  ;;      "BC-ENV-ASW_4A12P"
  ;;      "7100071"
  ;;      "ON-MNRF-AFFES_2QD"
  ;;      "ON-MNRF-AFFES_6QD"
  ;;      "7098896"
  ;;      "ON-MNRF-AFFES_0QD"
  ;;      "ON-MNRF-AFFES_5QD"
  ;;      "2303093"
  ;;      "2503651"
  ;;      "2303685"
  ;;      "2403625"
  ;;      "ON-MNRF-AFFES_3QD"
  ;;      "ON-MNRF-AFFES_1QD"
  ;;      "2202751"
  ;;      "220B68C"
  ;;      "40318MN"
  ;;      "7103328"}

  (count
   (set/difference csv-ids rest-ids))
  ;; => 25

  ;; only in rest

  (set/difference rest-ids csv-ids)
  ;; => #{"MB-SD_PISEW"
  ;;      "MB-SD_SHERR"
  ;;      "7110600"
  ;;      "MB-SD_SPIDE"
  ;;      "MB-SD_FLCNL"
  ;;      "2300556"
  ;;      "MB-SD_PROSP"
  ;;      "MB-SD_FLNDL"
  ;;      "MB-SD_GRNLK"
  ;;      "MB-SD_CRANP"
  ;;      "MB-SD_WESTR"
  ;;      "2400576"
  ;;      "MB-SD_CACHE"
  ;;      "MB-SD_HARGR"
  ;;      "MB-SD_GARYM"
  ;;      "MB-SD_KNOBH"
  ;;      "615TST2"
  ;;      "MB-SD_REED"
  ;;      "MB-SD_OXFHS"
  ;;      "MB-SD_ROSBG"
  ;;      "1161663"
  ;;      "MB-SD_HOOK"
  ;;      "MB-SD_GODSL"
  ;;      "MB-SD_SARAH"
  ;;      "2200101"
  ;;      "MB-SD_GYPSV"
  ;;      "MB-SD_SASSR"
  ;;      "1067741"
  ;;      "MB-SD_SNOW"
  ;;      "MB-SD_HARTM"
  ;;      "MB-SD_REEFL"
  ;;      "MB-SD_LKSTG"
  ;;      "MB-SD_DEVIL"
  ;;      "MB-SD_PUKAT"
  ;;      "MB-SD_LEAFR"
  ;;      "220B6Q3"
  ;;      "MB-SD_WOODR"
  ;;      "MB-SD_ATIKL"
  ;;      "MB-SD_BSSTT"
  ;;      "2203362"
  ;;      "MB-SD_EWART"
  ;;      "1054503"
  ;;      "MB-SD_HERMN"
  ;;      "7113383"
  ;;      "6014353"
  ;;      "MB-SD_LGRPD"
  ;;      "MB-SD_BURNT"
  ;;      "MB-SD_NOTIG"
  ;;      "MB-SD_KETTL"
  ;;      "MB-SD_LONGP"
  ;;      "MB-SD_GARLA"
  ;;      "MB-SD_HADAS"
  ;;      "MB-SD_ROUND"
  ;;      "MB-SD_WILL"}

  ;; only in rest
  (count
   (set/difference rest-ids csv-ids))
  ;; => 54

  rest-ids
  csv-ids

;;
  )

(comment
  ;; ok will need to pull the entire file server once a day and turn it into a msc_id to geometry and link list in properties

  (def files
    (reduce
     (fn [l url]
       (concat l
               (->> url
                    client/get
                    :body)))
     []
     ["https://dd.weather.gc.ca/today/observations/swob-ml/latest/"
      "https://dd.weather.gc.ca/today/observations/swob-ml/partners/"]))

  files

  (->
   "https://dd.weather.gc.ca/today/observations/swob-ml/latest/"
   client/get
   :body)
  (require '[clojure.repl.deps :refer [add-lib]])
  (add-lib 'org.clj-commons/hickory {:mvn/version "0.7.7"})
  (require '[hickery.core])

  (require '[hickory.core :as hick])

  (def html-data
    (-> (slurp "https://dd.weather.gc.ca/today/observations/swob-ml/partners/")
        hick/parse hick/as-hiccup))

  html-data

  (require '[clojure.walk :as walk])
  (def hrefs (atom #{}))
  (walk/postwalk
   (fn [{:keys [href] :as x}]
     (when
      (and
       href
       (str/ends-with? href "/"))
       (swap! hrefs conj href))
     x)
   html-data)

  @hrefs
  ;; => #{"dnd-ccg-lighthouse/"
  ;;      "bc-tran/"
  ;;      "nb-rwin/"
  ;;      "yt-firewx/"
  ;;      "/today/observations/swob-ml/"
  ;;      "yt-avalanche/"
  ;;      "bc-mvrd/"
  ;;      "pe-rwin/"
  ;;      "bc-forestry/"
  ;;      "on-firewx/"
  ;;      "on-grca/"
  ;;      "ns-rwin/"
  ;;      "nl-water/"
  ;;      "nl-firewx/"
  ;;      "on_water/"
  ;;      "bc-hydro/"
  ;;      "on-mto/"
  ;;      "bc-RioTinto/"
  ;;      "nt-forestry/"
  ;;      "sk-forestry/"
  ;;      "ab-firewx/"
  ;;      "ns-firewx/"
  ;;      "nt-water/"
  ;;      "qc-pom/"
  ;;      "bc-env-aq/"
  ;;      "bc-crd/"
  ;;      "mb_agriculture/"
  ;;      "dfo-moored-buoys/"
  ;;      "ab_agriculture/"
  ;;      "pc-firewx/"
  ;;      "on-trca/"
  ;;      "bc-env-snow/"}

 ;;
  )

(comment
  (def cstations (get-canadian-weather-stations))

  (let [{:keys [Longitude Latitude] :as m} (first cstations)]
    {:geometry {:type "Point", :coordinates [Longitude Latitude]}
                        ;;TODO add a property we can use to identify it.. .how do we get observations?
     :properties {:iata (or (get m :#IATA) (get m :IATA_ID))}})

;; for non-partner its just iata, for partner we need that partner, which in the csv is nothing...
;;  https://dd.weather.gc.ca/today/observations/swob-ml/partners/ab_agriculture/20260901/abee/2026-09-01-1600-ab-mai-abee-AUTO-swob.xml

;; so we need to pull the entire data set of files at least once in order to to this with the XML.

  ;; so wait pulling the csv doesn't help then right... i guess not so ditch that
  )

(comment
  ;; stats on rest api vs xml
  ;;canadian data from csv

  (def partners
    (reduce
     (fn [l url]
       (concat l
               (->> url
                    client/get
                    :body
                    csv/read-csv
                    sc/mappify
                    doall
                    #_(map
                       (fn [{:keys [Longitude Latitude]}]
                         {:geometry {:type "Point", :coordinates [Longitude Latitude]}
                        ;;TODO add a property we can use to identify it.. .how do we get observations?
                          :properties {}})))))
     []
     ["https://dd.weather.gc.ca/today/observations/doc/swob-xml_partner_station_list.csv"
      #_"https://dd.weather.gc.ca/today/observations/doc/swob-xml_station_list.csv"]))

  (count partners)
  ;; => 1996

  (def non-partners
    (reduce
     (fn [l url]
       (concat l
               (->> url
                    client/get
                    :body
                    csv/read-csv
                    sc/mappify
                    doall
                    #_(map
                       (fn [{:keys [Longitude Latitude]}]
                         {:geometry {:type "Point", :coordinates [Longitude Latitude]}
                        ;;TODO add a property we can use to identify it.. .how do we get observations?
                          :properties {}})))))
     []
     [#_"https://dd.weather.gc.ca/today/observations/doc/swob-xml_partner_station_list.csv"
      "https://dd.weather.gc.ca/today/observations/doc/swob-xml_station_list.csv"]))

  (count non-partners)
  ;; => 958

  (def canadian
    (reduce
     (fn [l url]
       (concat l
               (->> url
                    client/get
                    :body
                    csv/read-csv
                    sc/mappify
                    doall
                    #_(map
                       (fn [{:keys [Longitude Latitude]}]
                         {:geometry {:type "Point", :coordinates [Longitude Latitude]}
                        ;;TODO add a property we can use to identify it.. .how do we get observations?
                          :properties {}})))))
     []
     ["https://dd.weather.gc.ca/today/observations/doc/swob-xml_partner_station_list.csv"
      "https://dd.weather.gc.ca/today/observations/doc/swob-xml_station_list.csv"]))

  (count canadian)
  ;; => 2954

  (->> canadian first)
  ;; => {:# WMO ID "4400484",
  ;;     :FR name "Eastern South Gulf",
  ;;     :Data Provider French "Pêches et Océans Canada",
  ;;     :# MSC ID "DFO_AZMP-ESG",
  ;;     :Data Attribution Notice
  ;;     "Observational data provided by the Government of Canada: Department of Fisheries and Ocean Canada (DFO). All rights reserved.",
  ;;     :Data Provider "Department of Fisheries and Ocean Canada",
  ;;     :Longitude "-62",
  ;;     :STD Time "EST",
  ;;     :Latitude "46.8",
  ;;     :Data Attribution Notice French
  ;;     "Données d'observation fournies par le gouvernement du Canada: Pêches et Océans Canada (MPO). Tous droits réservés.",
  ;;     :Elevation "0",
  ;;     :Province "QC",
  ;;     :# ICAO ID "",
  ;;     :EN name "Eastern South Gulf",
  ;;     :DST Time "EDT",
  ;;     :AUTO/MAN "AUTO",
  ;;     :#IATA "AZMP-ESG"}

  ;; how do we use this to fetch the latest observation, ideally it has a single id in here like MSC_ID

  ;; does this url have it?
  ;; https://dd.weather.gc.ca/today/observations/swob-ml/latest/

;;
  )

(comment
  ;; dealing with the xml
  (require '[clj-http.client :as client]
           '[clojure.data.xml :as xml])

  ;; get observation from Canadian weather station

  ;; example xml file updated every min
  (-> "https://dd.weather.gc.ca/today/observations/swob-ml/latest/CWGL-AUTO-minute-swob.xml"
      (client/get)
      :body
      xml/parse-str
      keys)
  ;; => (:tag :attrs :content)

  (def observation
    (-> "https://dd.weather.gc.ca/today/observations/swob-ml/latest/CWGL-AUTO-minute-swob.xml"
        (client/get)
        :body
        xml/parse-str))

  (-> observation
      :content
      first
      :content
      first)

;;
  )

(comment
  ;; trying out the rest api

;; it's possible we can use the rest api to
  ;; this seems to be the non-partners which according to ths ite has 938 (close to above?)
  (def stations-rest
    (client/get "https://api.weather.gc.ca/collections/swob-stations/items?lang=en" {:as :json}))

  (-> stations-rest :body keys)
  ;; => (:type :features :numberMatched :numberReturned :links :timeStamp)

  (-> stations-rest :body :numberReturned)
  ;; => 500

  (-> stations-rest :body :features first keys)
  ;; => (:type :id :geometry :properties)

  (-> stations-rest :body :features first :properties)
  ;; => {:iata_id "CAAB",
  ;;     :name "ST ALBAN'S",
  ;;     :wmo_id 73051,
  ;;     :msc_id "8400416",
  ;;     :data_provider "MSC",
  ;;     :dataset_network "CA",
  ;;     :auto_man "AUTO",
  ;;     :province_territory "Newfoundland and Labrador"}

  (-> stations-rest :body :features first :id)
  ;; => "8400416"

  ;; from https://api.weather.gc.ca/openapi?f=html#/swob-partner-stations/describeSwob-partner-stationsCollection

  (def cws
    (loop [url                  "https://api.weather.gc.ca/collections/swob-stations/items?lang=en"
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
          observation-stations))))

  (def us-stations-rest
    (client/get "https://api.weather.gov/stations"))

  (-> us-stations-rest keys)
  ;; => (:cached
  ;;     :request-time
  ;;     :repeatable?
  ;;     :protocol-version
  ;;     :streaming?
  ;;     :http-client
  ;;     :chunked?
  ;;     :reason-phrase
  ;;     :headers
  ;;     :orig-content-encoding
  ;;     :status
  ;;     :length
  ;;     :body
  ;;     :trace-redirects)

;;
  )

(comment
  ;; misc but near the end

  (def boo (client/get "https://api.weather.gc.ca/collections/swob-partner-stations/items?f=json&lang=en-CA" {:as :json}))

  (->> boo
       :body
       keys)
  ;; => (:type :features :numberMatched :numberReturned :links :timeStamp)

  (->> boo
       :body
       :features
       count)
  ;; => 500

  (->> boo
       :body
       :features
       first
       keys)
  ;; => (:type :id :geometry :properties)

  (->> boo
       :body
       :features
       first
       :id)
  ;; => "DFO_AZMP-ESG"

  (def station (client/get "https://api.weather.gc.ca/collections/swob-partner-stations/items/DFO_AZMP-ESG?f=json&lang=en-CA" {:as :json}))

  (-> station keys)
  ;; => (:cached
  ;;     :request-time
  ;;     :repeatable?
  ;;     :protocol-version
  ;;     :streaming?
  ;;     :http-client
  ;;     :chunked?
  ;;     :reason-phrase
  ;;     :headers
  ;;     :orig-content-encoding
  ;;     :status
  ;;     :length
  ;;     :body
  ;;     :trace-redirects)

  (-> station :body)
  ;; => {:type "Feature",
  ;;     :id "DFO_AZMP-ESG",
  ;;     :geometry {:type "Point", :coordinates [-62.0 46.8 0.0]},
  ;;     :properties
  ;;     {:std_time "EST",
  ;;      :data_attribution_notice_fr
  ;;      "Données d'observation fournies par le gouvernement du Canada: Pêches et Océans Canada (MPO). Tous droits réservés.",
  ;;      :msc_id "DFO_AZMP-ESG",
  ;;      :data_provider_en "Department of Fisheries and Ocean Canada",
  ;;      :icao_id "",
  ;;      :data_attribution_notice_en
  ;;      "Observational data provided by the Government of Canada: Department of Fisheries and Ocean Canada (DFO). All rights reserved.",
  ;;      :name_en "Eastern South Gulf",
  ;;      :wmo_id 4400484,
  ;;      :data_provider_fr "Pêches et Océans Canada",
  ;;      :name_fr "Eastern South Gulf",
  ;;      :auto_man "AUTO",
  ;;      :dst_time "EDT",
  ;;      :province_territory "QC",
  ;;      :iata_id "AZMP-ESG"},
  ;;     :links
  ;;     [{:type "application/json",
  ;;       :rel "root",
  ;;       :title "The landing page of this server as JSON",
  ;;       :href "https://api.weather.gc.ca?f=json"}
  ;;      {:type "text/html",
  ;;       :rel "root",
  ;;       :title "The landing page of this server as HTML",
  ;;       :href "https://api.weather.gc.ca?f=html"}
  ;;      {:rel "self",
  ;;       :type "application/geo+json",
  ;;       :title "This document as JSON",
  ;;       :href
  ;;       "https://api.weather.gc.ca/collections/swob-partner-stations/items/DFO_AZMP-ESG?f=json"}
  ;;      {:rel "alternate",
  ;;       :type "application/ld+json",
  ;;       :title "This document as RDF (JSON-LD)",
  ;;       :href
  ;;       "https://api.weather.gc.ca/collections/swob-partner-stations/items/DFO_AZMP-ESG?f=jsonld"}
  ;;      {:rel "alternate",
  ;;       :type "text/html",
  ;;       :title "This document as HTML",
  ;;       :href
  ;;       "https://api.weather.gc.ca/collections/swob-partner-stations/items/DFO_AZMP-ESG?f=html"}
  ;;      {:rel "collection",
  ;;       :type "application/json",
  ;;       :title "SWOB - Stations from Partners",
  ;;       :href "https://api.weather.gc.ca/collections/swob-partner-stations"}]}

  (def observation (client/get "https://api.weather.gc.ca/collections/swob-realtime/items?f=json&msc_id-value=DFO_AZMP-ESG&sortby=-date_tm-value&limit=10" {:as :json}))
  ;; id-  DFO_AZMP-ESG

  (-> observation :body keys)
  ;; => (:type :features :numberMatched :numberReturned :links :timeStamp)

  (-> observation :body :features)
  ;; => []

  (inc 1)

  (defn get-station-from-rest-*
    [msc-id]
    (client/get
     (str "https://api.weather.gc.ca/collections/swob-partner-stations/items/" msc-id "?f=json&lang=en-CA") {:as :json}))

  (-> (get-station-from-rest-* "AB-MAF_ADA")
      :body
      :properties)

  ;; => {:std_time "MST",
  ;;     :data_attribution_notice_fr
  ;;     "Données d’observation fournies par le Gouvernement de l'Alberta: Ministère des Forêts et des Parcs (AB-MFP). Tous droits réservés.",
  ;;     :msc_id "AB-MAF_ADA",
  ;;     :data_provider_en "Government of Alberta: Ministry of Forestry and Parks",
  ;;     :icao_id "",
  ;;     :data_attribution_notice_en
  ;;     "Observational data provided by the Government of Alberta: Ministry of Forestry and Parks (AB-MFP). All rights reserved.",
  ;;     :name_en "Adair Auto",
  ;;     :wmo_id nil,
  ;;     :data_provider_fr
  ;;     "Gouvernement de l'Alberta: Ministère des Forêts et des Parcs",
  ;;     :name_fr "Adair Auto",
  ;;     :auto_man "AUTO",
  ;;     :dst_time "MDT",
  ;;     :province_territory "AB",
  ;;     :iata_id "ADA"}

  (defn get-observation
    [msc-id]
    (client/get (str "https://api.weather.gc.ca/collections/swob-realtime/items?f=json&msc_id-value=" msc-id "&sortby=-date_tm-value&limit=10") {:as :json}))

  (-> (get-observation "")
      :body
      :features)

  (defn get-observation-from-xml
    [msc-id]
    (client/get (str "https://api.weather.gc.ca/collections/swob-realtime/items?f=json&msc_id-value=" msc-id "&sortby=-date_tm-value&limit=10") {:as :json}))

  (defn build-xml-url-partners
    [{:keys [network iata]}]
    (str "https://dd.weather.gc.ca/today/observations/swob-ml/partners/"
         network ;; ab_agriculture
         "/"
         (.format (java.time.LocalDate/now)
                  (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd"))
         "/"
         iata ;; abee
         "/"))
  ;; working https://dd.weather.gc.ca/today/observations/swob-ml/partners/ab_agriculture/20260901/abee/

  ;; have
  (build-xml-url-partners {:network "ab_agriculture" :iata "abee"})
  ;; => "https://dd.weather.gc.ca/today/observations/swob-ml/partners/ab_agriculture/20260901/abee/"

  (def r
    (-> (client/get (build-xml-url-partners {:network "ab_agriculture" :iata "abee"}) {:as :html})))

  (build-xml-url-partners {:network "ab_agriculture" :iata "abee"})

  (-> r keys)
  ;; => (:cached
  ;;     :request-time
  ;;     :repeatable?
  ;;     :protocol-version
  ;;     :streaming?
  ;;     :http-client
  ;;     :chunked?
  ;;     :reason-phrase
  ;;     :headers
  ;;     :orig-content-encoding
  ;;     :status
  ;;     :length
  ;;     :body
  ;;     :trace-redirects)

  (-> r :body)

  (defn xml-files [html]
    (->> (re-seq #"href=\"([^\"]+\.xml)\"" html)
         (map second)))

  (defn latest-xml [html]
    (->> (xml-files html)
         sort
         last))

  (-> r :body latest-xml)
  ;; => "2026-09-01-1600-ab-mai-abee-AUTO-swob.xml"

;; non partner url is  https://dd.weather.gc.ca/today/observations/swob-ml/latest/
;;
  )

(comment
  ;; rest vs xml

  ;; ab-firewx	AB-MAF_ADA	Adair Auto	AB	AB-MFP	13	True	0	0	https://api.weather.gc.ca/collections/swob-partner-stations/items?msc_id=AB-MAF_ADA&f=json	https://api.weather.gc.ca/collections/swob-realtime/items?msc_id-value=AB-MAF_ADA&f=json	https://dd.weather.gc.ca/today/observations/swob-ml/partners/ab-firewx/20260830/ada/	https://dd.weather.gc.ca/20260830/WXO-DD/observations/swob-ml/partners/ab-firewx/20260830/ada/2026-08-30-1800-ab-maf-ada-AUTO-swob.xml	2026-08-30T18:00:00.000Z

;; https://dd.weather.gc.ca/today/observations/swob-ml/partners/ab-firewx/20260830/ada/
;; https://dd.weather.gc.ca/today/observations/swob-ml/partners/ab-firewx/20260902/ada/
;; current latest
;; https://dd.weather.gc.ca/today/observations/swob-ml/partners/ab-firewx/20260902/ada/2026-09-02-1800-ab-maf-ada-AUTO-swob.xml
  ;; <element name="msc_id" uom="unitless" value="AB-MAF_ADA"/>

  (defn get-observation
    [msc-id]
    (client/get (str "https://api.weather.gc.ca/collections/swob-realtime/items?f=json&msc_id-value=" msc-id "&sortby=-date_tm-value&limit=10") {:as :json}))

  (def obs (get-observation "AB-MAF_ADA"))

  (->> obs keys)
  ;; => (:cached
  ;;     :request-time
  ;;     :repeatable?
  ;;     :protocol-version
  ;;     :streaming?
  ;;     :http-client
  ;;     :chunked?
  ;;     :reason-phrase
  ;;     :headers
  ;;     :orig-content-encoding
  ;;     :status
  ;;     :length
  ;;     :body
  ;;     :trace-redirects)

  (->> obs :body keys)
  ;; => (:type :features :numberMatched :numberReturned :links :timeStamp)

  (->> obs :body)
  ;; => {:type "FeatureCollection",
  ;;     :features [], ;; <--- nothing returned
  ;;     :numberMatched 0,
  ;;     :numberReturned 0,
  ;;     :links
  ;;     [{:type "application/geo+json",
  ;;       :rel "self",
  ;;       :title "This document as GeoJSON",
  ;;       :href
  ;;       "https://api.weather.gc.ca/collections/swob-realtime/items?f=json&msc_id-value=AB-MAF_ADA&sortby=-date_tm-value&limit=10"}
  ;;      {:rel "alternate",
  ;;       :type "application/ld+json",
  ;;       :title "This document as RDF (JSON-LD)",
  ;;       :href
  ;;       "https://api.weather.gc.ca/collections/swob-realtime/items?f=jsonld&msc_id-value=AB-MAF_ADA&sortby=-date_tm-value&limit=10"}
  ;;      {:type "text/html",
  ;;       :rel "alternate",
  ;;       :title "This document as HTML",
  ;;       :href
  ;;       "https://api.weather.gc.ca/collections/swob-realtime/items?f=html&msc_id-value=AB-MAF_ADA&sortby=-date_tm-value&limit=10"}
  ;;      {:type "application/json",
  ;;       :title "SWOB - Real-Time Data",
  ;;       :rel "collection",
  ;;       :href "https://api.weather.gc.ca/collections/swob-realtime"}],
  ;;     :timeStamp "2026-09-02T22:46:29.931615Z"}

;; is our query wrong?
  (client/get (str "https://api.weather.gc.ca/collections/swob-realtime/items?f=json&msc_id-value=" "AB-MAF_ADA") {:as :json})
  ;; nope

  ;; does this work with a station kasrah said works
  (def pom_observation (client/get (str "https://api.weather.gc.ca/collections/swob-realtime/items?f=json&msc_id-value=" "POM_contrecoeur") {:as :json}))

  (->> pom_observation :body keys)
  ;; => (:type :features :numberMatched :numberReturned :links :timeStamp)

  (get-in pom_observation [:body :features] type)

  (->> pom_observation :body :features type)
;; => clojure.lang.PersistentVector

;;
  )

(comment
  ;; old csv(reduce

  (defn get-canadian-weather-stations-from-csvs
    []
    (reduce
     (fn [l url]
       (concat l
               (->> url
                    client/get
                    :body
                    csv/read-csv
                    sc/mappify
                    doall
                    (map
                     (fn [{:keys [Longitude Latitude]}]
                       {:geometry {:type "Point", :coordinates [Longitude Latitude]}
                        ;;TODO add a property we can use to identify it.. .how do we get observations?
                        :properties {}})))))
     []
     ["https://dd.weather.gc.ca/today/observations/doc/swob-xml_partner_station_list.csv"
      "https://dd.weather.gc.ca/today/observations/doc/swob-xml_station_list.csv"]))

  (fn [l url]
    (concat l
            (->> url
                 client/get
                 :body
                 csv/read-csv
                 sc/mappify
                 doall
                 (map
                  (fn [{:keys [Longitude Latitude]}]
                    {:geometry {:type "Point", :coordinates [Longitude Latitude]}
                        ;;TODO add a property we can use to identify it.. .how do we get observations?
                     :properties {}})))))
  []
  ["https://dd.weather.gc.ca/today/observations/doc/swob-xml_partner_station_list.csv"
   "https://dd.weather.gc.ca/today/observations/doc/swob-xml_station_list.csv"])
