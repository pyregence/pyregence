(ns pyregence.weather-stations.ca
  (:require [clj-http.client                   :as client]
            [clojure.data.json                 :as json]
            [clojure.string                    :as str]
            [pyregence.weather-stations.ca.xml :as xml]
            [triangulum.logging                :refer [log-str]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Canadian weather stations (Environment and Climate Change Canada)
;; REST API: https://api.weather.gc.ca/openapi
;; Stations come in two collections: non-partner stations (swob-stations),
;; run by ECCC, and partner stations (swob-partner-stations), run by
;; provinces, utilities and others.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private rest-api-url "https://api.weather.gc.ca")

;; Station lists are a few MB and the API can be slow to start sending them
(def ^:private station-list-timeouts {:connection-timeout 10000
                                      :socket-timeout     120000})

;; Bound how long a stalled upstream can hold an observation request, in ms
(def ^:private observation-timeouts {:connection-timeout 5000
                                     :socket-timeout     10000})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Formatting
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn format-name
  "Title-cases a station name, eg \"TRIAL ISLAND\" -> \"Trial Island\"."
  [station-name]
  (->> (str/split (str station-name) #" ")
       (map str/capitalize)
       (str/join " ")))

(def ^:private display-name->preferred-keys
  "The observation values shown for each display name: the first key with a
  value wins. Every key has a matching <key>-uom (unit of measurement) key."
  {"Air Temperature"                       [:air_temp :avg_air_temp_pst2mts :avg_air_temp_pst1hr :max_air_temp_pst1hr]
   "Cloud Base Height"                     [:cld_bas_hgt_1 :cld_bas_hgt_2 :cld_bas_hgt_3 :cld_bas_hgt_4]
   "Dew Point Temperature"                 [:dwpt_temp :avg_dwpt_temp_pst1hr]
   "Fuel Moisture"                         [:fuel_moist]
   "Fuel Temperature"                      [:fuel_temp]
   "Fwi Buildup Index"                     [:bldup_indx]
   "Fwi Daily Severity Rating"             [:dly_svrty_ratng]
   "Fwi Drought Code"                      [:drght_code]
   "Fwi Duff Moisture Code"                [:dff_moist_code]
   "Fwi Fine Fuel Moisture Code"           [:fine_fuel_moist_code]
   "Fwi Fire Weather Index"                [:fire_wx_indx]
   "Fwi Initial Spread Index"              [:initl_sprd_indx]
   "Global Solar Radiation"                [:globl_solr_radn :avg_globl_solr_radn_pst1mt :avg_globl_solr_radn_pst1hr :tot_globl_solr_radn_pst1mt]
   "Mean Sea Level Pressure"               [:mslp :avg_mslp_pst1hr]
   "Peak Wind Speed"                       [:max_pk_wnd_spd_10m_pst1hr]
   "Precipitation Amount"                  [:pcpn_amt_pst1hr :rnfl_amt_pst1hr :pcpn_amt_pst10mts :pcpn_amt_pst5mts]
   "Precipitation Rate"                    [:pcpn_rt :pcpn_rt_pst1hr]
   "Precipitation Since Last Gauge Reset"  [:rnfl_snc_last_reset :pcpn_snc_last_reset]
   "Present Weather"                       [:prsnt_wx_1 :prsnt_wx_2 :prsnt_wx_3]
   "Reflected Shortwave Radiation"         [:refltd_shrtwv_radn]
   "Relative Humidity"                     [:rel_hum :avg_rel_hum_pst2mts :avg_rel_hum_pst5mts :avg_rel_hum_pst1hr]
   "Snow Depth"                            [:snw_dpth :avg_snw_dpth_pst5mts :medn_snw_dpth_pst1mt :snw_dpth_1]
   "Snow Water Equivalent"                 [:snw_dpth_wtr_equiv]
   "Snowfall Amount"                       [:snwfl_amt_pst1hr]
   "Station Pressure"                      [:stn_pres :avg_stn_pres_pst1hr]
   "Surface Freezing Point"                [:sfc_frzng_pt_1 :sfc_frzng_pt_2]
   "Surface Temperature"                   [:sfc_temp]
   "Vapour Pressure"                       [:avg_vpr_pres_pst1hr]
   "Visibility"                            [:vis :avg_vis_pst10mts :max_vis_pst10mts :min_vis_pst10mts]
   "Wave Height"                           [:wv_hgt]
   "Wet Bulb Temperature"                  [:wetblb_temp]
   "Wind Direction"                        [:avg_wnd_dir_10m_pst10mts :avg_wnd_dir_10m_pst2mts :avg_wnd_dir_pst10mts :avg_wnd_dir_10m_pst1mt]
   "Wind Direction At Peak Speed"          [:wnd_dir_max_spd_2m_pst1hr :avg_wnd_dir_max_spd_10m_pst10mts :max_wnd_dir_max_spd_10m_pst1hr :avg_wnd_dir_max_spd_10m_pst10mts_sensor1]
   "Wind Direction At Precipitation Gauge" [:avg_wnd_dir_pcpn_gag_pst10mts :avg_wnd_dir_pcpn_gag_pst1hr]
   "Wind Gust Speed"                       [:max_wnd_gst_spd_10m_pst10mts :max_wnd_spd_10m_pst10mts :max_pk_wnd_spd_10m_pst1hr :max_wnd_spd_10m_pst1hr]
   "Wind Speed"                            [:avg_wnd_spd_10m_pst10mts :wnd_spd :avg_wnd_spd_10m_pst2mts :avg_wnd_spd_pst10mts]})

(defn- pick-value-with-uom
  "The first of preferred-keys with a value in properties, as
  \"<value><uom>\", eg \"14.2°C\", or just the value when its uom is
  \"unitless\" (eg fire weather indexes). nil when none of them has a value."
  [properties preferred-keys]
  (some (fn [k]
          (let [value (get properties k)
                uom   (get properties (keyword (str (name k) "-uom")))]
            (when-not (str/blank? (str value))
              (if (= "unitless" uom)
                (str value)
                (str value uom)))))
        preferred-keys))

(defn properties->observations
  "Maps display names to \"<value><uom>\" strings, for the display names that
  have a value in properties. properties uses the REST API's keys: :air_temp,
  :air_temp-uom, etc."
  [properties]
  (into {}
        (for [[display-name preferred-keys] display-name->preferred-keys
              :let [value (pick-value-with-uom properties preferred-keys)]
              :when value]
          [display-name value])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Stations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- fetch-station-collection!
  "Every feature of a REST station collection (eg \"swob-stations\"),
  following its next links. Throws when a page can't be fetched."
  [collection]
  (loop [url      (str rest-api-url "/collections/" collection "/items?f=json&limit=10000")
         features []]
    (let [{:keys [status body]} (client/get url (merge station-list-timeouts {:throw-exceptions false}))]
      (when (not= 200 status)
        (throw (ex-info (str "Request for " collection " failed with status " status) {:url url})))
      (let [{page :features links :links} (json/read-str body :key-fn keyword)
            next-url (some (fn [{:keys [rel href]}] (when (= rel "next") href)) links)
            features (into features page)]
        (if (and next-url (seq page))
          (recur next-url features)
          features)))))

(defn rest-station->station
  "Station details from a REST station feature. Besides what the map shows,
  keeps what the XML fill-in needs: iata_id, auto_man and whether it's a
  partner station. Non-partner stations use name, partner stations name_en."
  [partner? {{:keys [iata_id auto_man name_en] station-name :name} :properties
             {[longitude latitude] :coordinates}     :geometry}]
  {:name      (format-name (or station-name name_en))
   :latitude  latitude
   :longitude longitude
   :iata-id   iata_id
   :auto-man  auto_man
   :partner?  partner?})

(defn rest-stations->stations
  "Maps msc_id to station details, skipping stations without an msc_id or
  coordinates."
  [partner? rest-stations]
  (into {}
        (for [{{:keys [msc_id]} :properties :as rest-station} rest-stations
              :let [station (rest-station->station partner? rest-station)]
              :when (and msc_id (:latitude station) (:longitude station))]
          [msc_id station])))

(defn fetch-stations!
  "Fetches the non-partner and partner station lists. Returns a map of
  :stations, msc_id to station details. Takes about a minute."
  []
  (let [non-partner (rest-stations->stations false (fetch-station-collection! "swob-stations"))
        partner     (rest-stations->stations true (fetch-station-collection! "swob-partner-stations"))]
    {:stations (merge non-partner partner)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Observations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn rest-response->observation
  "The latest observation in a REST swob-realtime response, as
  {:station {:name :id :timestamp} :observations {display-name value}}, or nil
  when the response has none."
  [{[{properties :properties}] :features}]
  (when properties
    {:station      {:name      (format-name (:stn_nam-value properties))
                    :id        (:msc_id-value properties)
                    :timestamp (:date_tm-value properties)}
     :observations (properties->observations properties)}))

(defn- fetch-rest-observation!
  "Fetches the latest observation of a station from the REST API. Returns a
  map of :observation (nil when there's none) and :status, the API's response
  status (nil when the request didn't complete)."
  [msc-id]
  (let [url                   (str rest-api-url
                                   "/collections/swob-realtime/items?"
                                   (client/generate-query-string {:f            "json"
                                                                  :sortby       "-date_tm-value"
                                                                  :limit        1
                                                                  :msc_id-value msc-id}))
        {:keys [status body]} (try
                                (client/get url (merge observation-timeouts {:throw-exceptions false}))
                                (catch Exception e
                                  (log-str "WARN: request for CA station " msc-id " observation failed: " (ex-message e))
                                  nil))]
    {:observation (when (= 200 status)
                    (rest-response->observation (json/read-str body :key-fn keyword)))
     :status      status}))

(defn xml-elements->observation
  "An observation from the elements of a SWOB XML (see
  pyregence.weather-stations.ca.xml/parse-swob-elements), in the same shape
  as rest-response->observation. XML element names are the REST property
  names without their -value suffix. date_tm comes as
  2026-10-27T18:00:00.000Z or, from some networks, 2026-10-27T18:00:00Z; both
  parse on the client."
  [elements]
  (let [properties (reduce-kv (fn [properties element-name {:keys [value uom]}]
                                (assoc properties
                                       (keyword element-name)               value
                                       (keyword (str element-name "-uom")) uom))
                              {}
                              elements)]
    {:station      {:name      (format-name (get-in elements ["stn_nam" :value]))
                    :id        (get-in elements ["msc_id" :value])
                    :timestamp (get-in elements ["date_tm" :value])}
     :observations (properties->observations properties)}))

(defn fetch-observation!
  "Fetches the latest observation of a CA station, identified by its msc_id
  (the REST observation query filters on it). Falls back to the station's
  SWOB XML when the REST API returns no observation, which is common for
  partner stations. station is the station's details from fetch-stations!,
  and partner-folder its entry in the XML partner folder index (nil when it
  has none). Returns a map of:
  - :observation, or nil when there's none
  - :via, where it came from: \"rest\" or \"xml\"
  - :upstream-status, the status of each upstream request
  - :xml-path, the XML's path on the server, when one was found"
  [msc-id station partner-folder]
  (let [rest-result (fetch-rest-observation! msc-id)
        rest-status (str "rest=" (or (:status rest-result) "error")
                         (when (and (= 200 (:status rest-result)) (nil? (:observation rest-result)))
                           " (no observation)"))]
    ;; TODO also fall back to the XML when the REST observation is older than N hours
    (if (:observation rest-result)
      {:observation     (:observation rest-result)
       :via             "rest"
       :upstream-status rest-status}
      (let [{:keys [elements status path]} (xml/fetch-station-elements! msc-id station partner-folder)
            observation                    (when elements
                                             (xml-elements->observation elements))]
        {:observation     observation
         :via             (when observation "xml")
         :upstream-status (str rest-status ", " status)
         :xml-path        path}))))
