#!/usr/bin/env bb
;; Can we find the SWOB XML file for a Canadian weather station without
;; crawling the whole XML server (dd.weather.gc.ca)?
;;
;; The REST API (api.weather.gc.ca) lists every station, but often returns no
;; observation for partner stations. Their observations do exist as XML files
;; on dd.weather.gc.ca, under folders whose names don't appear in the REST
;; station records. This script measures how many stations can be mapped to
;; their XML folder (or file) using only directory listings, and then proves a
;; handful of those mappings by fetching the XML and comparing its msc_id with
;; the station's.
;;
;; Steps:
;;   1. Fetch the REST station lists (partner and non-partner): 2 requests.
;;   2. Fetch the dd listings, one request at a time:
;;        partners/                   (1 request, gives the networks)
;;        partners/<net>/<date>/      (1 request per network, about 30)
;;        today/.../latest/           (1 request, the non-partner files)
;;   3. Match partner stations to station folders with a few naming rules
;;      (see partner-rules), and non-partner stations to
;;      latest/<iata_id>-<auto_man>-swob.xml. Some folder names exist in more
;;      than one network; those are narrowed to the networks the station's
;;      provider (msc_id prefix) maps to elsewhere.
;;   4. Spot-check --checks partner stations for which the REST API returns no
;;      observation: list the station folder, fetch the newest XML, parse it,
;;      and check that its msc_id is the station's and that it has values.
;;      Up to 2 dd requests per check, plus REST queries to find candidates.
;;      --core-checks does the same for non-partner stations (1 dd request each).
;;
;; Reading the report: "matched" means the station's folder (or file) exists on
;; dd. A station that isn't matched, while all its network's folders are
;; matched to other stations, had no XML on that date (it isn't reporting), so
;; no naming rule could find it. The spot checks are the proof that matches
;; are right: the XML's msc_id must be the station's.
;;
;; Partner listings are read from --date (default: yesterday, UTC). Today's
;; folders fill up during the day (just after 00:00 UTC most are empty), while
;; yesterday's are complete. Station folder names don't change from day to day.
;;
;; Usage:
;;   bb scratchpad/ca-xml-coverage.bb [--checks 15] [--core-checks 3]
;;      [--date YYYYMMDD] [--unmatched-sample 30] [--delay-ms 500]
;;      [--timeout-s 20] [--cache-dir DIR] [--out FILE.edn]
;;
;; --cache-dir saves every response and reuses it on later runs, so the report
;; can be re-run (or re-read) without hitting the servers again.
;; --out writes the full results (every match and non-match) as EDN.

(require '[babashka.cli :as cli]
         '[babashka.fs :as fs]
         '[babashka.http-client :as http]
         '[cheshire.core :as json]
         '[clojure.data.xml :as xml]
         '[clojure.pprint :refer [pprint print-table]]
         '[clojure.string :as str])

(import '[java.time LocalDate ZoneOffset]
        '[java.time.format DateTimeFormatter])

(def cli-spec
  {:checks           {:coerce :long :default 15}
   :core-checks      {:coerce :long :default 3}
   :date             {}
   :unmatched-sample {:coerce :long :default 30}
   :delay-ms         {:coerce :long :default 500}
   :timeout-s        {:coerce :long :default 20}
   :cache-dir        {}
   :out              {}})

(def api-base "https://api.weather.gc.ca")
(def dd-base "https://dd.weather.gc.ca")
(def user-agent "pyregence-ca-xml-coverage (support@sig-gis.com)")

;;; Requests

(def request-counts (atom {}))

(defn- url->cache-file
  [cache-dir url]
  (fs/path cache-dir (str/replace url #"[^A-Za-z0-9.-]" "_")))

(defn- get-once!
  [url {:keys [timeout-s]}]
  (try
    (let [{:keys [status body]} (http/get url {:headers {"User-Agent" user-agent}
                                               :timeout (* 1000 timeout-s)
                                               :throw   false})]
      {:status status :body body})
    (catch Exception e
      {:error (str (.getSimpleName (class e)) ": " (ex-message e))})))

(defn fetch!
  "GETs `url`, one request at a time, waiting --delay-ms before each dd
   request and retrying once on errors. Returns {:status :body} or {:error}.
   With --cache-dir, a saved 200 response is returned without a request."
  [url {:keys [cache-dir delay-ms] :as opts}]
  (let [host       (if (str/starts-with? url dd-base) :dd :api)
        cache-file (some-> cache-dir (url->cache-file url))]
    (if (and cache-file (fs/exists? cache-file))
      (do (swap! request-counts update-in [host :cached] (fnil inc 0))
          {:status 200 :body (slurp (str cache-file))})
      (loop [attempt 1]
        (when (= host :dd) (Thread/sleep delay-ms))
        (swap! request-counts update-in [host :sent] (fnil inc 0))
        (let [{:keys [status body error] :as result} (get-once! url opts)]
          (cond
            (and error (< attempt 2))
            (do (println "  retrying after" error "-" url)
                (recur (inc attempt)))

            (= status 200)
            (do (when cache-file
                  (fs/create-dirs cache-dir)
                  (spit (str cache-file) body))
                result)

            :else
            (do (println "  failed:" (or error status) "-" url)
                (swap! request-counts update-in [host :failed] (fnil inc 0))
                result)))))))

(defn fetch-json!
  [url opts]
  (let [{:keys [status body]} (fetch! url opts)]
    (when (= status 200)
      (json/parse-string body true))))

;;; REST API

(defn fetch-rest-stations!
  "Every feature of a REST station collection, following `next` links."
  [collection opts]
  (loop [url      (str api-base "/collections/" collection "/items?f=json&limit=10000")
         features []]
    (let [{:keys [links] :as body} (fetch-json! url opts)
          features (into features (:features body))
          next-url (some (fn [{:keys [rel href]}] (when (= rel "next") href)) links)]
      (cond
        (nil? body)                  (throw (ex-info "Could not fetch station list" {:url url}))
        (empty? (:features body))    features
        next-url                     (recur next-url features)
        :else                        features))))

(defn fetch-rest-observation-count!
  "How many observations the REST API returns for `msc-id` (0 or 1), or nil
   when the request fails."
  [msc-id opts]
  (some-> (fetch-json! (str api-base "/collections/swob-realtime/items?f=json&limit=1"
                            "&sortby=-date_tm-value&msc_id-value=" msc-id)
                       opts)
          :numberReturned))

;;; dd listings

(defn parse-listing
  "The entries of an Apache directory listing: {:dirs [...] :files [...]},
   names without the trailing slash. Skips sort links (?C=...) and absolute
   links (the parent folder)."
  [html]
  (let [hrefs (->> (re-seq #"href=\"([^\"]+)\"" html)
                   (map second)
                   (remove #(or (str/starts-with? % "?")
                                (str/starts-with? % "/")
                                (str/starts-with? % "../"))))]
    {:dirs  (->> hrefs (filter #(str/ends-with? % "/")) (mapv #(subs % 0 (dec (count %)))))
     :files (->> hrefs (remove #(str/ends-with? % "/")) vec)}))

(defn fetch-listing!
  "The listing at `url` (which must end in /), or nil when it can't be read."
  [url opts]
  (let [{:keys [status body]} (fetch! url opts)]
    (when (= status 200)
      (parse-listing body))))

(def ^DateTimeFormatter yyyymmdd (DateTimeFormatter/ofPattern "yyyyMMdd"))

(defn today-utc [] (.format (LocalDate/now ZoneOffset/UTC) yyyymmdd))

(defn yesterday-utc [] (.format (.minusDays (LocalDate/now ZoneOffset/UTC) 1) yyyymmdd))

(defn swob-root-url
  "today/ only holds the current UTC date; older dates live under
   <date>/WXO-DD/."
  [date]
  (if (= date (today-utc))
    (str dd-base "/today/observations/swob-ml/")
    (str dd-base "/" date "/WXO-DD/observations/swob-ml/")))

(defn partner-folder-url
  [date network folder]
  (str (swob-root-url date) "partners/" network "/" date "/" folder "/"))

(def latest-url (str dd-base "/today/observations/swob-ml/latest/"))

(defn fetch-partner-folders!
  "{folder #{network ...}} for every station folder in partners/<net>/<date>/."
  [date opts]
  (let [networks (:dirs (fetch-listing! (str (swob-root-url date) "partners/") opts))]
    (println (count networks) "networks in" (str (swob-root-url date) "partners/"))
    (reduce (fn [folder->networks network]
              (let [folders (:dirs (fetch-listing! (str (swob-root-url date) "partners/" network "/" date "/") opts))]
                (reduce #(update %1 %2 (fnil conj (sorted-set)) network) folder->networks folders)))
            {}
            networks)))

;;; Matching

(defn- msc-id-suffix
  "The part of `msc_id` after the first _, lowercased: AB-MAF_ALA -> ala."
  [{:keys [msc_id]}]
  (when-let [i (some-> msc_id (str/index-of "_"))]
    (str/lower-case (subs msc_id (inc i)))))

(defn- collapse-doubled
  "8224-8224 -> 8224; anything else unchanged."
  [s]
  (when s
    (let [[_ a b] (re-matches #"(.+)-(.+)" s)]
      (if (and a (= a b)) a s))))

(def partner-rules
  "Each rule turns a REST station record's properties into a candidate folder
   name. The first rule whose candidate is a folder on dd wins."
  [{:rule 1 :doc "lowercase iata_id"
    :candidate (fn [{:keys [iata_id]}] (some-> iata_id str/lower-case))}
   {:rule 2 :doc "msc_id after the first _, lowercased"
    :candidate msc-id-suffix}
   {:rule 3 :doc "rule 2 with a doubled suffix collapsed (8224-8224 -> 8224)"
    :candidate (comp collapse-doubled msc-id-suffix)}
   {:rule 4 :doc "lowercase iata_id, spaces as _ (TRIAL IS -> trial_is)"
    :candidate (fn [{:keys [iata_id]}] (some-> iata_id str/lower-case (str/replace " " "_")))}
   {:rule 5 :doc "lowercase msc_id (POM_quai_42 -> pom_quai_42)"
    :candidate (fn [{:keys [msc_id]}] (some-> msc_id str/lower-case))}])

(defn match-partner-station
  "The first rule that maps `properties` to a known folder, as
   {:rule :folder :networks}, or nil."
  [folder->networks properties]
  (some (fn [{:keys [rule candidate]}]
          (let [folder (candidate properties)]
            (when-let [networks (get folder->networks folder)]
              {:rule rule :folder folder :networks networks})))
        partner-rules))

(defn msc-id-provider
  "The part of msc_id before the last _ (BC_ENV-AQ_110 -> BC_ENV-AQ), used to
   group partner stations, since the REST records have no network field.
   Lighthouse msc_ids have no _ and form one group."
  [msc-id]
  (if-let [i (str/last-index-of msc-id "_")]
    (subs msc-id 0 i)
    "(no _ in msc_id)"))

(defn learn-provider-networks
  "{provider #{network ...}}: the networks holding at least 10% of a
   provider's single-network matches. Folder names like 110 or broc exist in
   several networks; a provider's stations almost all live in one network
   (NWT_ENR in two), so this resolves those collisions."
  [matched]
  (->> matched
       (filter #(= 1 (count (get-in % [:match :networks]))))
       (group-by (comp msc-id-provider :msc_id))
       (map (fn [[provider group]]
              (let [counts (frequencies (map #(first (get-in % [:match :networks])) group))]
                [provider (->> counts
                               (filter (fn [[_ n]] (>= n (* 0.1 (count group)))))
                               (map key)
                               set)])))
       (into {})))

(defn resolve-match
  "Narrows a match's networks to its provider's networks. A match outside
   them is kept but marked :suspect? (likely a folder of the same name in
   another network, while the station's own folder is missing)."
  [provider->networks {:keys [msc_id match] :as station}]
  (if-not match
    station
    (let [provider-networks (get provider->networks (msc-id-provider msc_id) #{})
          narrowed          (into (sorted-set) (filter provider-networks) (:networks match))]
      (assoc station :match (if (seq narrowed)
                              (assoc match :networks narrowed)
                              (assoc match :suspect? true))))))

(def core-rules
  "Each rule turns a non-partner REST station record into a candidate file
   name in latest/. The first rule whose file exists wins.
   Tried and dropped: <iata_id>-AUTO-swob.xml for MAN stations without a MAN
   file. The file exists for some, but belongs to a co-located automatic
   station with a different msc_id (CYXQ: 2100159 vs 2100155)."
  [{:rule 1 :doc "<iata_id>-<auto_man>-swob.xml"
    :candidate (fn [{:keys [iata_id auto_man]}] (str iata_id "-" auto_man "-swob.xml"))}])

(defn match-core-station
  "The first rule that maps `properties` to a file in latest/, as
   {:rule :file}, or nil."
  [latest-files properties]
  (some (fn [{:keys [rule candidate]}]
          (let [file (candidate properties)]
            (when (contains? latest-files file)
              {:rule rule :file file})))
        core-rules))

;;; XML

(def observation-elements
  "Observation element names the app shows (the CA display table in
   pyregence.weather-stations.observations.CA)."
  #{"fuel_temp" "avg_wnd_dir_pcpn_gag_pst10mts" "avg_wnd_dir_pcpn_gag_pst1hr"
    "globl_solr_radn" "avg_globl_solr_radn_pst1mt" "avg_globl_solr_radn_pst1hr"
    "tot_globl_solr_radn_pst1mt" "initl_sprd_indx" "drght_code" "cld_bas_hgt_1"
    "cld_bas_hgt_2" "cld_bas_hgt_3" "cld_bas_hgt_4" "stn_pres" "avg_stn_pres_pst1hr"
    "wetblb_temp" "pcpn_amt_pst1hr" "rnfl_amt_pst1hr" "pcpn_amt_pst10mts"
    "pcpn_amt_pst5mts" "rnfl_snc_last_reset" "pcpn_snc_last_reset" "prsnt_wx_1"
    "prsnt_wx_2" "prsnt_wx_3" "air_temp" "avg_air_temp_pst2mts" "avg_air_temp_pst1hr"
    "max_air_temp_pst1hr" "vis" "avg_vis_pst10mts" "max_vis_pst10mts"
    "min_vis_pst10mts" "wv_hgt" "refltd_shrtwv_radn" "fine_fuel_moist_code"
    "bldup_indx" "sfc_temp" "snw_dpth_wtr_equiv" "fuel_moist" "fire_wx_indx"
    "dff_moist_code" "mslp" "avg_mslp_pst1hr" "max_wnd_gst_spd_10m_pst10mts"
    "max_wnd_spd_10m_pst10mts" "max_pk_wnd_spd_10m_pst1hr" "max_wnd_spd_10m_pst1hr"
    "avg_wnd_dir_10m_pst10mts" "avg_wnd_dir_10m_pst2mts" "avg_wnd_dir_pst10mts"
    "avg_wnd_dir_10m_pst1mt" "rel_hum" "avg_rel_hum_pst2mts" "avg_rel_hum_pst5mts"
    "avg_rel_hum_pst1hr" "dly_svrty_ratng" "avg_vpr_pres_pst1hr" "snw_dpth"
    "avg_snw_dpth_pst5mts" "medn_snw_dpth_pst1mt" "snw_dpth_1"
    "wnd_dir_max_spd_2m_pst1hr" "avg_wnd_dir_max_spd_10m_pst10mts"
    "max_wnd_dir_max_spd_10m_pst1hr" "avg_wnd_dir_max_spd_10m_pst10mts_sensor1"
    "dwpt_temp" "avg_dwpt_temp_pst1hr" "pcpn_rt" "pcpn_rt_pst1hr"
    "avg_wnd_spd_10m_pst10mts" "wnd_spd" "avg_wnd_spd_10m_pst2mts"
    "avg_wnd_spd_pst10mts" "snwfl_amt_pst1hr" "sfc_frzng_pt_1" "sfc_frzng_pt_2"})

(defn parse-swob-elements
  "{name value} for every <element name=… value=…/> in a SWOB XML document.
   Qualifier elements nested inside an element also appear, which is fine
   for these checks."
  [xml-string]
  (->> (xml/parse-str xml-string)
       xml-seq
       (filter #(and (map? %) (= "element" (name (:tag %)))))
       (reduce (fn [m {{:keys [name value]} :attrs}]
                 (if (contains? m name) m (assoc m name value)))
               {})))

(defn check-xml
  "Fetches the XML at `url` and compares it with the station's msc_id."
  [url msc-id opts]
  (let [{:keys [status body error]} (fetch! url opts)]
    (if (not= status 200)
      {:ok? false :problem (str "XML fetch failed: " (or error status))}
      (try
        (let [elements (parse-swob-elements body)
              values   (filter observation-elements (keys elements))
              problem  (cond
                         (not= msc-id (elements "msc_id")) (str "msc_id in XML is " (pr-str (elements "msc_id")))
                         (nil? (elements "date_tm"))       "no date_tm"
                         (empty? values)                   "no observation values")]
          {:ok?     (nil? problem)
           :problem problem
           :date-tm (elements "date_tm")
           :values  (count values)})
        (catch Exception e
          {:ok? false :problem (str "XML parse failed: " (ex-message e))})))))

(defn check-partner-station!
  "Lists the station folder, takes the newest file (file names start with
   the observation time, so the newest sorts last) and checks it."
  [{:keys [msc_id match]} date opts]
  (let [folder-url (partner-folder-url date (first (:networks match)) (:folder match))
        newest     (some->> (fetch-listing! folder-url opts) :files (filter #(str/ends-with? % ".xml")) sort last)]
    (merge {:msc-id msc_id :network (first (:networks match)) :folder (:folder match) :rule (:rule match)}
           (if newest
             (assoc (check-xml (str folder-url newest) msc_id opts) :file newest)
             {:ok? false :problem "no XML file in the station folder"}))))

(defn interleave-groups
  "The items of `coll` grouped by `f`, taken round-robin across the groups,
   so the first few items cover as many groups as possible."
  [f coll]
  (loop [queues (->> coll (group-by f) (sort-by key) (map (comp #(sort-by :msc_id %) val)))
         acc    []]
    (if (empty? queues)
      acc
      (recur (keep next queues) (into acc (map first queues))))))

(defn pick-partner-check-stations!
  "Up to `n` unambiguously matched partner stations for which the REST API
   returns no observation, taken round-robin across networks so one network
   doesn't fill the sample. Queries the REST API at most 5n times."
  [matched n opts]
  (let [interleaved (->> matched
                         (filter #(= 1 (count (get-in % [:match :networks]))))
                         (remove #(get-in % [:match :suspect?]))
                         (interleave-groups #(first (get-in % [:match :networks]))))]
    (loop [[station & more] interleaved picked [] queries 0]
      (if (or (nil? station) (= n (count picked)) (>= queries (* 5 n)))
        {:picked picked :rest-queries queries}
        (let [rest-count (fetch-rest-observation-count! (:msc_id station) opts)]
          (recur more
                 (if (= 0 rest-count) (conj picked station) picked)
                 (inc queries)))))))

;;; Report

(defn percent
  [part total]
  (if (zero? total) "-" (format "%.1f%%" (* 100.0 (/ part total)))))

(defn print-section
  [title]
  (println)
  (println title)
  (println (apply str (repeat (count title) "="))))

(defn report-partners
  [stations matched folder->networks ambiguous-count sample-size]
  (let [unmatched    (remove :match stations)
        ambiguous    (filter #(< 1 (count (get-in % [:match :networks]))) matched)
        suspects     (filter #(get-in % [:match :suspect?]) matched)
        good         (- (count matched) (count ambiguous) (count suspects))]
    (print-section "Partner stations")
    (println (format "%d stations with an msc_id" (count stations)))
    (println (format "%d matched to a folder by name (%s)"
                     (count matched) (percent (count matched) (count stations))))
    (println (format "  %d of them had a folder name found in more than one network; %d of those stay ambiguous after narrowing to the provider's networks"
                     ambiguous-count (count ambiguous)))
    (println (format "  %d are suspect: their only folder is outside the provider's networks (likely a same-name folder of another network)"
                     (count suspects)))
    (println (format "%d matched to exactly one folder in one of the provider's networks (%s)"
                     good (percent good (count stations))))

    (println "\nBy rule (first rule that matched):")
    (print-table [:rule :doc :stations :share]
                 (for [{:keys [rule doc]} partner-rules
                       :let [n (count (filter #(= rule (get-in % [:match :rule])) matched))]]
                   {:rule rule :doc doc :stations n :share (percent n (count stations))}))

    (println "\nBy provider (msc_id prefix; REST station records have no network field):")
    (print-table [:provider :stations :matched :coverage :networks]
                 (->> stations
                      (group-by (comp msc-id-provider :msc_id))
                      (map (fn [[provider group]]
                             (let [m (filter :match group)]
                               {:provider provider
                                :stations (count group)
                                :matched  (count m)
                                :coverage (percent (count m) (count group))
                                :networks (->> m (mapcat #(get-in % [:match :networks])) distinct sort (str/join " "))})))
                      (sort-by (juxt #(- (:stations %) (:matched %)) :provider))
                      reverse))

    (println "\nBy network (station folders on dd, and how many some REST station maps to):")
    (println "Folders no station maps to belong to stations missing from the REST lists, so the app never shows them.")
    (print-table [:network :folders :matched :coverage :unmatched-folders]
                 (->> folder->networks
                      (mapcat (fn [[folder networks]] (map #(vector % folder) networks)))
                      (group-by first)
                      (map (fn [[network pairs]]
                             (let [used-here (set (keep (fn [{{:keys [folder networks]} :match}]
                                                          (when (contains? networks network) folder))
                                                        matched))
                                   folders   (sort (map second pairs))
                                   m         (filter used-here folders)
                                   leftover  (remove used-here folders)]
                               {:network           network
                                :folders           (count folders)
                                :matched           (count m)
                                :coverage          (percent (count m) (count folders))
                                :unmatched-folders (str (str/join " " (take 4 leftover))
                                                        (when (< 4 (count leftover)) " ..."))})))
                      (sort-by :network)))

    (doseq [[title group] [["Still ambiguous" ambiguous] ["Suspect" suspects]]
            :when (seq group)]
      (println (str "\n" title " matches:"))
      (print-table [:msc_id :name_en :folder :networks]
                   (->> group
                        (take 10)
                        (map (fn [{:keys [msc_id name_en match]}]
                               {:msc_id msc_id :name_en name_en :folder (:folder match)
                                :networks (str/join " " (:networks match))})))))

    (println (format "\nUnmatched stations (sample of %d of %d):" (min sample-size (count unmatched)) (count unmatched)))
    (println "When their network's folders are all matched above, these stations had no folder on that date (not reporting).")
    (print-table [:msc_id :iata_id :name_en :province_territory]
                 (->> unmatched (sort-by :msc_id) (take-nth (max 1 (quot (count unmatched) (max 1 sample-size)))) (take sample-size)))))

(defn report-core
  [stations matched]
  (print-section "Non-partner stations")
  (println (format "%d stations with an msc_id, %d matched to a file in latest/ (%s)"
                   (count stations) (count matched) (percent (count matched) (count stations))))
  (println "\nBy rule (first rule that matched):")
  (print-table [:rule :doc :stations :share]
               (for [{:keys [rule doc]} core-rules
                     :let [n (count (filter #(= rule (get-in % [:match :rule])) matched))]]
                 {:rule rule :doc doc :stations n :share (percent n (count stations))}))
  (let [unmatched (remove :match stations)]
    (when (seq unmatched)
      (println "\nWithout a file in latest/ (first 10):")
      (print-table [:msc_id :iata_id :auto_man :name]
                   (->> unmatched (sort-by :msc_id) (take 10))))))

(defn report-checks
  [title columns checks extra-line]
  (print-section title)
  (when extra-line (println extra-line))
  (println (format "%d of %d passed (XML found, parsed, msc_id matches, has date_tm and observation values)"
                   (count (filter :ok? checks)) (count checks)))
  (when (seq checks)
    (print-table columns checks)))

(defn report-summary
  [partner-stations partner-matched core-stations core-matched partner-checks core-checks]
  (let [good-partners (remove #(or (get-in % [:match :suspect?])
                                   (< 1 (count (get-in % [:match :networks]))))
                              partner-matched)]
    (print-section "Summary")
    (println (format "Partner stations mapped to one XML folder:  %d of %d (%s)"
                     (count good-partners) (count partner-stations) (percent (count good-partners) (count partner-stations))))
    (println (format "Non-partner stations mapped to a latest/ file: %d of %d (%s)"
                     (count core-matched) (count core-stations) (percent (count core-matched) (count core-stations))))
    (println (format "Spot checks passed: partner %d of %d, non-partner %d of %d"
                     (count (filter :ok? partner-checks)) (count partner-checks)
                     (count (filter :ok? core-checks)) (count core-checks)))))

;;; Main

(defn -main
  [args]
  (let [opts (:opts (cli/parse-args args {:spec cli-spec}))
        date (or (:date opts) (yesterday-utc))
        _    (println "Partner listings from" date "- core listing from today/ -" (today-utc) "UTC")

        _                (println "\nFetching REST station lists...")
        partner-stations (->> (fetch-rest-stations! "swob-partner-stations" opts)
                              (map :properties)
                              (filter :msc_id))
        core-stations    (->> (fetch-rest-stations! "swob-stations" opts)
                              (map :properties)
                              (filter :msc_id))

        _                (println "Fetching dd listings...")
        folder->networks (fetch-partner-folders! date opts)
        latest-files     (set (:files (fetch-listing! latest-url opts)))

        raw-results     (map #(assoc % :match (match-partner-station folder->networks %)) partner-stations)
        ambiguous-count (count (filter #(< 1 (count (get-in % [:match :networks]))) raw-results))
        provider->networks (learn-provider-networks (filter :match raw-results))
        partner-results (map #(resolve-match provider->networks %) raw-results)
        partner-matched (filter :match partner-results)
        core-results    (map #(assoc % :match (match-core-station latest-files %)) core-stations)
        core-matched    (filter :match core-results)

        _ (println "Running spot checks...")
        {:keys [picked rest-queries]} (pick-partner-check-stations! partner-matched (:checks opts) opts)
        partner-checks (mapv #(check-partner-station! % date opts) picked)
        core-checks    (->> core-matched
                            (sort-by :msc_id)
                            (take-nth (max 1 (quot (count core-matched) (max 1 (:core-checks opts)))))
                            (take (:core-checks opts))
                            (mapv (fn [{:keys [msc_id] {:keys [rule file]} :match}]
                                    (merge {:msc-id msc_id :rule rule :file file}
                                           (check-xml (str latest-url file) msc_id opts)))))]

    (report-partners partner-results partner-matched folder->networks ambiguous-count (:unmatched-sample opts))
    (report-core core-results core-matched)
    (report-checks "Spot checks: partner stations with no REST observation"
                   [:ok? :msc-id :network :folder :rule :file :date-tm :values :problem]
                   partner-checks
                   (format "%d REST observation queries to find %d stations with no REST observation (asked for %d)"
                           rest-queries (count picked) (:checks opts)))
    (report-checks "Spot checks: non-partner stations (latest/)"
                   [:ok? :msc-id :file :date-tm :values :problem]
                   core-checks
                   nil)
    (report-summary partner-results partner-matched core-results core-matched partner-checks core-checks)

    (print-section "Requests")
    (print-table [:host :sent :failed :cached]
                 (for [[host label] [[:api api-base] [:dd dd-base]]]
                   (merge {:host label :sent 0 :failed 0 :cached 0} (get @request-counts host))))

    (when-let [out (:out opts)]
      (spit out (with-out-str (pprint {:date             date
                                       :partner-results  (vec partner-results)
                                       :core-results     (vec core-results)
                                       :partner-checks   partner-checks
                                       :core-checks      core-checks
                                       :folder->networks folder->networks})))
      (println "\nWrote" out))))

(-main *command-line-args*)
