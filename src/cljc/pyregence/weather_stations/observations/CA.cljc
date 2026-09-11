(ns pyregence.weather-stations.observations.CA
  (:require
   [clojure.string :as str]))

(defn stn_name-value->format
  [stn_name-value]
  (->> (str/split stn_name-value #" ")
       (map str/lower-case)
       (map str/capitalize)
       (str/join " ")))

(defn station-id->url
  [station-id]
  ;;TODO consider adding query params to get only what the server needs (see preferred properties)
  (str "https://api.weather.gc.ca/collections/swob-realtime/items?f=json&sortby=-date_tm-value&limit=1&msc_id-value=" station-id))

(comment
  ;; example response
  (def response
    {:type "FeatureCollection",
     :features
     [{:id "2026-09-10-1500-CYOD-MAN-swob.xml",
       :type "Feature",
       :geometry {:type "Point", :coordinates [-110.28 54.41 541]},
       :properties
       {:altmetr_setng 29.62,
        :avg_wnd_dir_10m_pst2mts 260,
        :vis-qa 100,
        :air_temp 14.2,
        :cld_typ_2-uom "code",
        :cor-value "orig",
        :rpt_typ-uom "code",
        :cld_amt_code_2-qa 100,
        :avg_wnd_spd_10m_pst2mts-qa 100,
        :avg_wnd_spd_10m_pst2mts-uom "km/h",
        :avg_wnd_spd_10m_pst2mts 14.8,
        :pres_tend_char_pst3hrs-uom "code",
        :rpt_typ-value "0",
        :mslp-qa 100,
        :cld_amt_code_2 1,
        :cld_typ_2-qa 100,
        :stn_typ-value "14",
        :-code-src "std_code_src",
        :msc_id-uom "unitless",
        :air_temp-uom "°C",
        :mslp-uom "hPa",
        :cld_typ_1-uom "code",
        :tc_id-value "YOD",
        :vis 24.14,
        :wmo_synop_id-uom "unitless",
        :cld_typ_2 5,
        :altmetr_setng-uom "inHg",
        :avg_wnd_dir_10m_pst2mts-uom "°",
        :tc_id-uom "unitless",
        :pres_tend_amt_pst3hrs-qa 100,
        :air_temp-qa 100,
        :stn_typ-uom "code",
        :stn_nam-value "Cold Lake",
        :cld_amt_code_1 1,
        :cld_bas_hgt_1-uom "m",
        :obs_date_tm "2026-09-10T15:00:00.000Z",
        :cld_amt_code_2-uom "code",
        :altmetr_setng-qa 100,
        :rel_hum-qa 100,
        :mslp 1003.8,
        :cld_bas_hgt_1-qa 100,
        :avg_wnd_dir_10m_pst2mts-qa 100,
        :rel_hum 72,
        :_is-minutely_obs-value false,
        :dwpt_temp-uom "°C",
        :cld_typ_1 0,
        :clim_id-value "3081680",
        :stn_pres-uom "hPa",
        :pres_tend_amt_pst3hrs 0.3,
        :vis-uom "km",
        :processed_date_tm "2026-09-10T15:14:33.009Z",
        :dwpt_temp 9.2,
        :cld_bas_hgt_2-uom "m",
        :pres_tend_char_pst3hrs-qa 100,
        :dwpt_temp-qa 100,
        :id "2026-09-10-1500-CYOD-MAN-swob.xml",
        :cld_typ_1-qa 100,
        :cld_bas_hgt_2 6300,
        :wmo_synop_id-value "71120",
        :url
        "https://dd.weather.gc.ca//20260910/WXO-DD/observations/swob-ml/20260910/CYOD/2026-09-10-1500-CYOD-MAN-swob.xml",
        :msc_id-value "3081680",
        :cld_bas_hgt_1 5400,
        :stn_pres-qa 100,
        :cor-uom "unitless",
        :cld_amt_code_1-uom "code",
        :pres_tend_char_pst3hrs 2,
        :pres_tend_amt_pst3hrs-uom "hPa",
        :rel_hum-uom "%",
        :stn_nam-uom "unitless",
        :date_tm-value "2026-09-10T15:00:00.000Z",
        :stn_pres 940.4,
        :rmk "DENSITY ALT 2597FT",
        :cld_amt_code_1-qa 100,
        :date_tm-uom "datetime",
        :clim_id-uom "unitless",
        :-code-type "report_type",
        :dataset "msc-observation-atmospheric-surface_weather-winide_fm12-1.0-xml",
        :cld_bas_hgt_2-qa 100}}],
     :numberMatched 762,
     :numberReturned 1,
     :links
     [{:type "application/geo+json",
       :rel "self",
       :title "This document as GeoJSON",
       :href
       "https://api.weather.gc.ca/collections/swob-realtime/items?f=json&sortby=-date_tm-value&limit=1&msc_id-value=3081680"}
      {:rel "alternate",
       :type "application/ld+json",
       :title "This document as RDF (JSON-LD)",
       :href
       "https://api.weather.gc.ca/collections/swob-realtime/items?f=jsonld&sortby=-date_tm-value&limit=1&msc_id-value=3081680"}
      {:type "text/html",
       :rel "alternate",
       :title "This document as HTML",
       :href
       "https://api.weather.gc.ca/collections/swob-realtime/items?f=html&sortby=-date_tm-value&limit=1&msc_id-value=3081680"}
      {:type "application/geo+json",
       :rel "next",
       :title "Items (next)",
       :href
       "https://api.weather.gc.ca/collections/swob-realtime/items?offset=1&sortby=-date_tm-value&limit=1&msc_id-value=3081680"}
      {:type "application/json",
       :title "SWOB - Real-Time Data",
       :rel "collection",
       :href "https://api.weather.gc.ca/collections/swob-realtime"}],
     :timeStamp "2026-09-10T21:48:11.859052Z"})
  ;;
  )

(def display-name->perferred-properties-order
  {"FWI​ fire weather index" [:fire_wx_indx],
   "wind speed"
   [:avg_wnd_spd_10m_pst10mts
    :wnd_spd
    :avg_wnd_spd_10m_pst2mts
    :avg_wnd_spd_pst10mts],
   "wind gust speed"
   [:max_wnd_gst_spd_10m_pst10mts
    :max_wnd_spd_10m_pst10mts
    :max_pk_wnd_spd_10m_pst1hr
    :max_wnd_spd_10m_pst1hr],
   "wave height" [:wv_hgt],
   "dew point temperature" [:dwpt_temp :avg_dwpt_temp_pst1hr],
   "reflected shortwave radiation" [:refltd_shrtwv_radn],
   "global solar radiation"
   [:globl_solr_radn
    :avg_globl_solr_radn_pst1mt
    :avg_globl_solr_radn_pst1hr
    :tot_globl_solr_radn_pst1mt],
   "peak wind speed" [:max_pk_wnd_spd_10m_pst1hr],
   "FWI​ duff moisture code" [:dff_moist_code],
   "relative humidity"
   [:rel_hum :avg_rel_hum_pst2mts :avg_rel_hum_pst5mts :avg_rel_hum_pst1hr],
   "surface freezing point" [:sfc_frzng_pt_1 :sfc_frzng_pt_2],
   "snow water equivalent" [:snw_dpth_wtr_equiv],
   "snowfall amount" [:snwfl_amt_pst1hr],
   "vapour pressure" [:avg_vpr_pres_pst1hr],
   "FWI​ fine fuel moisture code" [:fine_fuel_moist_code],
   "FWI​ initial spread index" [:initl_sprd_indx],
   "fuel temperature" [:fuel_temp],
   "visibility" [:vis :avg_vis_pst10mts :max_vis_pst10mts :min_vis_pst10mts],
   "precipitation rate" [:pcpn_rt :pcpn_rt_pst1hr],
   "surface temperature" [:sfc_temp],
   "fuel moisture" [:fuel_moist],
   "mean sea level pressure" [:mslp :avg_mslp_pst1hr],
   "snow depth"
   [:snw_dpth :avg_snw_dpth_pst5mts :medn_snw_dpth_pst1mt :snw_dpth_1],
   "cloud base height"
   [:cld_bas_hgt_1 :cld_bas_hgt_2 :cld_bas_hgt_3 :cld_bas_hgt_4],
   "FWI​ daily severity rating" [:dly_svrty_ratng],
   "air temperature"
   [:air_temp :avg_air_temp_pst2mts :avg_air_temp_pst1hr :max_air_temp_pst1hr],
   "precipitation amount"
   [:pcpn_amt_pst1hr :rnfl_amt_pst1hr :pcpn_amt_pst10mts :pcpn_amt_pst5mts],
   "wet bulb temperature" [:wetblb_temp],
   "FWI​ buildup index" [:bldup_indx],
   "wind direction at peak speed"
   [:wnd_dir_max_spd_2m_pst1hr
    :avg_wnd_dir_max_spd_10m_pst10mts
    :max_wnd_dir_max_spd_10m_pst1hr
    :avg_wnd_dir_max_spd_10m_pst10mts_sensor1],
   "station pressure" [:stn_pres :avg_stn_pres_pst1hr],
   "wind direction at precipitation gauge"
   [:avg_wnd_dir_pcpn_gag_pst10mts :avg_wnd_dir_pcpn_gag_pst1hr],
   "FWI​ drought code" [:drght_code],
   "wind direction"
   [:avg_wnd_dir_10m_pst10mts
    :avg_wnd_dir_10m_pst2mts
    :avg_wnd_dir_pst10mts
    :avg_wnd_dir_10m_pst1mt],
   "precipitation since last gauge reset"
   [:rnfl_snc_last_reset :pcpn_snc_last_reset],
   "present weather" [:prsnt_wx_1 :prsnt_wx_2 :prsnt_wx_3]})

(defn properties->display-name->value-with-uom
  [properties]
  (->>
   display-name->perferred-properties-order
   (reduce-kv
    (fn [display-name->value-with-metric-unit
        display-name
        keys-ordered-by-business-preference-for-selection-to-show]
      (if-let [value (some
                      #(and
                           ;; filter out display-names without keys that have values
                        (not (str/blank? (-> % properties str)))
                        (str (-> % properties)
                              ;; NOTE Every key has a
                              ;; corresponding key-uom (unit of measurement)
                             (-> %
                                 name
                                 (str "-uom")
                                 keyword
                                 properties)))
                      keys-ordered-by-business-preference-for-selection-to-show)]
        (assoc display-name->value-with-metric-unit display-name value)
        display-name->value-with-metric-unit))
    {})))
